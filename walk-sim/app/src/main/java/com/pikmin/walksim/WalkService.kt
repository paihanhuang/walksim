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
import com.pikmin.model.WalkProfile
import com.pikmin.osm.CompositeRoadSource
import com.pikmin.osm.FixtureRoadSource
import com.pikmin.osm.OverpassRoadSource
import com.pikmin.osm.RoadSource
import com.pikmin.sim.DEFAULT_LANE_SPACING_M
import com.pikmin.walksim.session.Mode
import com.pikmin.walksim.session.RunSpec
import com.pikmin.walksim.session.WalkSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin wakelocked foreground service (location type): parses the start intent into a [RunSpec], stands up the
 * platform surfaces (FGS notification, wakelock, the real [LocationInjector] sink, the once-built [RoadSource]),
 * and delegates the walk to the pure [WalkSessionController]. All route/sequence/hold/restore ordering lives in
 * the controller (AC-11..16, AC-20); this class owns only Android I/O.
 */
class WalkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val machine = WalkStateMachine()
    private var job: Job? = null
    private var wakelock: PowerManager.WakeLock? = null
    private var injector: LocationInjector? = null
    private var session: WalkSessionController? = null // current session; the RoadSource fallback signal re-homes it
    private lateinit var roadSource: RoadSource // live Overpass + baked-Shibuya fallback, built once in onCreate
    @Volatile private var holdTarget: LatLng? = null // live-updatable hold point (census teleport / freeze re-point)
    @Volatile private var holdActive = false // true only while a hold session runs → re-point targets a hold, not a walk

    override fun onCreate() {
        super.onCreate()
        // Build the graph source once: live Overpass, falling back to the baked offline Shibuya map on ANY fetch
        // failure. The fallback signal raises the banner AND flags the session to re-home to SHIBUYA.
        roadSource = CompositeRoadSource(
            OverpassRoadSource(),
            FixtureRoadSource { assets.open(SHIBUYA_ASSET).bufferedReader().use { it.readText() } },
            onFallback = {
                Log.w(TAG, "Overpass fetch failed; falling back to baked Shibuya", it)
                WalkBus.setupError.value = "Map fetch failed — using the offline Shibuya map."
                session?.fellBackToShibuya = true
            },
        )
    }

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
        val radiusOverrideM = intent?.getStringExtra(EXTRA_RADIUS_STR)?.toIntOrNull() // radius_s: manual fetch-radius override
        val laneSpacingM = intent?.getStringExtra(EXTRA_SPACING_STR)?.toDoubleOrNull()?.coerceIn(50.0, 2000.0)
            ?: DEFAULT_LANE_SPACING_M // clamp: a typo'd tiny spacing would explode waypoint count (qa-quality)
        val closeLoop = intent?.getStringExtra(EXTRA_CLOSE_STR) == "1"
        val sequential = intent?.getStringExtra(EXTRA_SEQUENTIAL) == "1" // default "All areas" mode
        val holdMode = intent?.getStringExtra(EXTRA_HOLD_STR) == "1" // hold_s=1 → static position hold (freeze/census)
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
        val inj = LocationInjector(this).also { injector = it }

        val spec = RunSpec(
            start = LatLng(lat, lng), durationS = durationS, profile = profile, seed = System.currentTimeMillis(),
            mode = if (holdMode) Mode.HOLD else if (sequential) Mode.SEQUENTIAL else Mode.SINGLE,
            laneSpacingM = laneSpacingM, closeLoop = closeLoop, radiusOverrideM = radiusOverrideM,
        )
        val controller = WalkSessionController(roadSource, inj, machine, SHIBUYA, ::updateNotification, holdTarget = { holdTarget })
        session = controller
        // Single flight: exactly one job at a time. The controller restores providers in its own finally (AC-15);
        // this finally then tears down the FGS/wakelock/bus. stopWalk cancels the job → both finallys still run.
        job = scope.launch { try { controller.run(spec) } finally { finish() } }
    }

    private fun stopWalk() {
        machine.stop()
        job?.cancel()
        job = null
    }

    /** Idempotent teardown: release the wakelock, clear state, drop the FGS (providers are restored by the controller). */
    private fun finish() {
        if (!machine.isTerminal) machine.stop()
        holdActive = false
        wakelock?.let { if (it.isHeld) it.release() }
        wakelock = null
        WalkBus.clear()
        WalkBus.status.value = WalkState.IDLE
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        machine.stop()
        runCatching { injector?.restore() } // process-death path: scope.cancel()'s cleanup is async, so restore now
        wakelock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
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
        private const val WAKELOCK_TIMEOUT_MS = 11L * 60 * 60 * 1000 // 11 h — covers a 10 h soak + margin

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
    }
}
