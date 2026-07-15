package com.pikmin.walksim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import com.pikmin.model.WalkProfile
import com.pikmin.osm.OverpassGraph
import com.pikmin.osm.OverpassRoadSource
import com.pikmin.sim.DEFAULT_LANE_SPACING_M
import com.pikmin.sim.WalkPlayer
import com.pikmin.sim.WalkPlayerConfig
import com.pikmin.sim.sweepFetchRadiusM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Wakelocked foreground service (location type) that plays a [WalkPlayer] stream as a mock GNSS fix stream
 * (gps + network + fused) via [LocationInjector]. The engine self-paces at 1 Hz, so collecting it off the
 * main thread gives the real-time fix cadence; pause is realised by back-pressuring that collection.
 *
 *  - AC-20 : start / pause / resume / stop, driven by the pure [WalkStateMachine].
 *  - AC-16 : engage mock BEFORE the (slower) graph build so a not-mock-app SecurityException surfaces ≤2 s.
 *  - Robustness: PARTIAL_WAKE_LOCK + off-Main collection (design "Coordination transport & robustness").
 *  - Graph : every pin → live Overpass fetch, radius sized to the walk's sweep (AC-2); on fetch failure,
 *    fall back to the baked Shibuya graph.
 */
class WalkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val machine = WalkStateMachine()
    private var job: Job? = null
    private var wakelock: PowerManager.WakeLock? = null
    private var injector: LocationInjector? = null
    @Volatile private var holdTarget: LatLng? = null // live-updatable hold point (census teleport / freeze re-point)
    @Volatile private var holdActive = false // true only while a hold session runs → re-point targets a hold, not a walk
    private var radiusOverrideM: Int? = null // radius_s extra: manual fetch-radius override for tuning runs
    private var laneSpacingM = DEFAULT_LANE_SPACING_M
    private var closeLoop = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> if (machine.pause()) { WalkBus.status.value = WalkState.PAUSED; updateNotification("Paused") }
            ACTION_RESUME -> if (machine.resume()) { WalkBus.status.value = WalkState.RUNNING; updateNotification("Walking…") }
            ACTION_STOP -> stopWalk()
            else -> {
                // Re-point an already-running hold live (no STOP → no real-GPS detour between census hops);
                // otherwise a fresh start.
                if (intent?.getStringExtra(EXTRA_HOLD_STR) == "1" && holdActive) {
                    holdTarget = holdPointOf(intent)
                } else {
                    startWalk(intent)
                }
            }
        }
        return START_STICKY
    }

    /** Parse the hold target (lat_s/lng_s) for a live re-point. */
    private fun holdPointOf(intent: Intent?): LatLng = LatLng(
        intent?.getStringExtra(EXTRA_LAT_STR)?.toDoubleOrNull() ?: SHIBUYA.lat,
        intent?.getStringExtra(EXTRA_LNG_STR)?.toDoubleOrNull() ?: SHIBUYA.lng,
    )

    private fun startWalk(intent: Intent?) {
        if (machine.state != WalkState.IDLE) return // ignore duplicate starts
        // Prefer string extras (lat_s/lng_s/speed_s) so a walk can be scripted straight from an
        // `am start-foreground-service` intent — adb `am` cannot pass Double extras; the UI path uses Doubles.
        val lat = intent?.getStringExtra(EXTRA_LAT_STR)?.toDoubleOrNull()
            ?: intent?.getDoubleExtra(EXTRA_LAT, SHIBUYA.lat) ?: SHIBUYA.lat
        val lng = intent?.getStringExtra(EXTRA_LNG_STR)?.toDoubleOrNull()
            ?: intent?.getDoubleExtra(EXTRA_LNG, SHIBUYA.lng) ?: SHIBUYA.lng
        val durationS = intent?.getLongExtra(EXTRA_DURATION_S, DEFAULT_DURATION_S) ?: DEFAULT_DURATION_S
        val speed = intent?.getStringExtra(EXTRA_SPEED_STR)?.toDoubleOrNull()
            ?: intent?.getDoubleExtra(EXTRA_SPEED_MPS, WalkProfile().meanSpeedMps) ?: WalkProfile().meanSpeedMps
        val stride = intent?.getStringExtra(EXTRA_STRIDE_STR)?.toDoubleOrNull() ?: WalkProfile().strideM
        val profile = WalkProfile(meanSpeedMps = speed, strideM = stride)
        radiusOverrideM = intent?.getStringExtra(EXTRA_RADIUS_STR)?.toIntOrNull()
        laneSpacingM = intent?.getStringExtra(EXTRA_SPACING_STR)?.toDoubleOrNull()?.coerceIn(50.0, 2000.0)
            ?: DEFAULT_LANE_SPACING_M // clamp: a typo'd tiny spacing would explode waypoint count (qa-quality)
        closeLoop = intent?.getStringExtra(EXTRA_CLOSE_STR) == "1"
        val sequential = intent?.getStringExtra(EXTRA_SEQUENTIAL) == "1" // default "All areas" mode
        val holdMode = intent?.getStringExtra(EXTRA_HOLD_STR) == "1" // hold_s=1 → static position hold (freeze/census), no route/steps
        if (holdMode) { holdTarget = LatLng(lat, lng); holdActive = true }

        WalkBus.durationS = durationS
        WalkBus.strideM = profile.strideM
        WalkBus.setupError.value = null
        WalkBus.mockAppOk.value = true
        machine.start()
        WalkBus.status.value = WalkState.RUNNING

        startForeground(NOTIF_ID, buildNotification("Starting walk…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        wakelock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "walksim:inject")
            .apply { acquire(WAKELOCK_TIMEOUT_MS) }
        injector = LocationInjector(this)

        job = scope.launch { runWalk(LatLng(lat, lng), durationS, profile, seed = System.currentTimeMillis(), sequential, holdMode) }
    }

    /** All blocking LM/FLP + Overpass calls run here, off the main thread. */
    private suspend fun runWalk(start: LatLng, durationS: Long, profile: WalkProfile, seed: Long, sequential: Boolean, holdMode: Boolean) {
        val inj = injector ?: return
        // AC-16 first (≤2 s): engage mock before the slower graph build so a not-mock-app fault surfaces fast.
        if (!inj.start()) {
            WalkBus.mockAppOk.value = false
            WalkBus.setupError.value = "Select WalkSim as the mock-location app in Developer Options, then start again."
            finish(); return
        }
        try {
            if (holdMode) {
                // Static hold: pin the mock at [start] and refresh at 1 Hz until STOP. No graph/route/steps
                // (playing stays 0) — used for freeze-in-place and for census teleport-sampling.
                updateNotification("Holding position")
                while (!machine.isTerminal) { inj.holdAt(holdTarget ?: start); delay(HOLD_REFRESH_MS) }
            } else if (sequential) {
                // Default "All areas": one pass over the presets in order, each for its slice of the total.
                val plan = sequencePlan(PRESET_LOCATIONS, durationS)
                for ((i, entry) in plan.withIndex()) {
                    val (preset, segS) = entry
                    val label = "Walking ${preset.label} · ${i + 1}/${plan.size}"
                    updateNotification(label) // during the (slow) per-preset graph fetch
                    inj.holdAt(preset.at) // cover the fetch gap so real GPS never shows between presets
                    val (graph, effStart) = resolveGraph(preset.at, segS, profile)
                    playRoute(graph, effStart, segS, profile, seed + i, inj, label) // distinct seed per preset
                    if (machine.isTerminal) break // stopped: fall through to machine.complete()/finish()
                }
            } else {
                inj.holdAt(start) // cover the initial fetch gap so real GPS never shows before the first route fix
                val (graph, effStart) = resolveGraph(start, durationS, profile)
                playRoute(graph, effStart, durationS, profile, seed, inj)
            }
            machine.complete()
            Log.i(TAG, "walk complete: duration ${durationS}s elapsed")
        } catch (_: CancellationException) {
            // stop requested
        } catch (e: IllegalArgumentException) {
            WalkBus.setupError.value = "No walkable road near the start pin — move it onto a street."
            Log.w(TAG, "route generation failed", e)
        } catch (e: Exception) {
            WalkBus.setupError.value = "Walk failed: ${e.message}"
            Log.w(TAG, "walk failed", e)
        } finally {
            finish()
        }
    }

    /**
     * Streams one route ([WalkPlayer] over [graph] from [start] for [segS] s, seeded [seed]) into [inj], honoring
     * pause/stop back-pressure. [notifLabel] (per-preset in sequential mode) prefixes the progress notification;
     * null → progress only, as the single-route path shows.
     */
    private suspend fun playRoute(
        graph: WalkGraph,
        start: LatLng,
        segS: Long,
        profile: WalkProfile,
        seed: Long,
        inj: LocationInjector,
        notifLabel: String? = null,
    ) {
        val cfg = WalkPlayerConfig(profile = profile, laneSpacingM = laneSpacingM, closeLoop = closeLoop, seed = seed)
        var lastNotifBucket = -1L
        WalkPlayer(graph, cfg).play(start, segS).collect { sample ->
            awaitRunnable() // suspends while paused (back-pressure); throws when stopped
            inj.push(sample)
            WalkBus.sample.value = sample
            val bucket = sample.tickIndex / NOTIF_EVERY_TICKS
            if (bucket != lastNotifBucket) {
                lastNotifBucket = bucket
                val progress = progressText(sample.cumulativeDistanceM, sample.tickIndex + 1, segS)
                updateNotification(if (notifLabel != null) "$notifLabel · $progress" else progress)
            }
        }
    }

    /** Suspends while paused; returns when runnable; throws [CancellationException] once stopped. */
    private suspend fun awaitRunnable() {
        while (machine.isPaused) delay(PAUSE_POLL_MS)
        if (machine.isTerminal) throw CancellationException("stopped")
    }

    /**
     * Every pin → live Overpass fetch; on fetch failure, fall back to the baked Shibuya graph.
     * Radius: [radiusOverrideM] if given, else sized so the disc contains the segment's sweep spiral (AC-2).
     */
    private suspend fun resolveGraph(start: LatLng, segS: Long, profile: WalkProfile): Pair<WalkGraph, LatLng> {
        val radiusM = radiusOverrideM ?: sweepFetchRadiusM(profile.meanSpeedMps * segS, laneSpacingM).toInt()
        return runCatching { OverpassRoadSource().graphAround(start, radiusM) to start }
            .getOrElse {
                Log.w(TAG, "Overpass fetch failed; falling back to baked Shibuya", it)
                WalkBus.setupError.value = "Map fetch failed — using the offline Shibuya map."
                shibuyaGraph() to SHIBUYA
            }
    }

    private fun shibuyaGraph(): WalkGraph {
        bakedShibuya?.let { return it }
        val json = assets.open(SHIBUYA_ASSET).bufferedReader().use { it.readText() }
        return OverpassGraph.fromOverpassJson(json).also { bakedShibuya = it }
    }

    private fun stopWalk() {
        machine.stop()
        job?.cancel()
        job = null
    }

    /** Idempotent teardown: restore providers (AC-15), release the wakelock, clear state, drop the FGS. */
    private fun finish() {
        if (!machine.isTerminal) machine.stop()
        holdActive = false
        runCatching { injector?.restore() }
        wakelock?.let { if (it.isHeld) it.release() }
        wakelock = null
        WalkBus.clear()
        WalkBus.status.value = WalkState.IDLE
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        machine.stop()
        runCatching { injector?.restore() }
        wakelock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun progressText(distanceM: Double, elapsedS: Long, durationS: Long): String {
        val pct = if (durationS > 0) (elapsedS * 100.0 / durationS).coerceAtMost(100.0) else 0.0
        return "%.2f km · %d%% · %d/%d min".format(distanceM / 1000.0, pct.toInt(), elapsedS / 60, durationS / 60)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Walk injector", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("WalkSim — injecting mock location")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WalkService"
        private const val CHANNEL = "walksim_inject"
        private const val NOTIF_ID = 1
        private const val SHIBUYA_ASSET = "shibuya.json"
        private const val DEFAULT_DURATION_S = 3600L
        private const val PAUSE_POLL_MS = 100L
        private const val NOTIF_EVERY_TICKS = 10L
        private const val WAKELOCK_TIMEOUT_MS = 11L * 60 * 60 * 1000 // 11 h — covers a 10 h soak + margin
        private const val HOLD_REFRESH_MS = 1000L // re-push the held fix at 1 Hz so FLP never serves it as stale

        val SHIBUYA = LatLng(35.6595, 139.7006)

        const val ACTION_START = "com.pikmin.walksim.START"
        const val ACTION_PAUSE = "com.pikmin.walksim.PAUSE"
        const val ACTION_RESUME = "com.pikmin.walksim.RESUME"
        const val ACTION_STOP = "com.pikmin.walksim.STOP"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_DURATION_S = "duration_s"
        const val EXTRA_SPEED_MPS = "speed_mps"
        const val EXTRA_LAT_STR = "lat_s"      // string forms so adb `am` can start a walk (no Double extras)
        const val EXTRA_LNG_STR = "lng_s"
        const val EXTRA_SPEED_STR = "speed_s"
        const val EXTRA_STRIDE_STR = "stride_s"
        const val EXTRA_RADIUS_STR = "radius_s"
        const val EXTRA_SPACING_STR = "spacing_s" // sweep lane spacing (m) — tune to the observed harvest reach
        const val EXTRA_CLOSE_STR = "close_s"     // "1" → closed run: shortest path home appended (AC-24e)
        const val EXTRA_SEQUENTIAL = "seq"     // "1" → default "All areas" mode: walk every preset in sequence
        const val EXTRA_HOLD_STR = "hold_s"    // "1" → static position hold (freeze / census sampling): pin mock, no route/steps

        /** Parsed once per process — the baked graph is ~1.5 MB of JSON. */
        @Volatile
        private var bakedShibuya: WalkGraph? = null
    }
}
