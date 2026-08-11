package com.pikmin.walksim.session

import android.util.Log
import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import com.pikmin.osm.OverpassGraph
import com.pikmin.osm.RoadSource
import com.pikmin.sim.WalkPlayer
import com.pikmin.sim.WalkPlayerConfig
import com.pikmin.sim.flowerFetchRadiusM
import com.pikmin.sim.sweepFetchRadiusM
import com.pikmin.walksim.PRESET_LOCATIONS
import com.pikmin.walksim.WalkBus
import com.pikmin.walksim.WalkStateMachine
import com.pikmin.walksim.fullRoutePlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/** How a session drives the mock stream: one district, all districts in sequence, or a static hold. */
enum class Mode { SINGLE, SEQUENTIAL, HOLD }

/** The fully-resolved walk request an intent parses to — the pure orchestration input for [WalkSessionController]. */
data class RunSpec(
    val start: LatLng,
    val durationS: Long,
    val profile: com.pikmin.model.WalkProfile,
    val seed: Long,
    val mode: Mode,
    val laneSpacingM: Double,
    val closeLoop: Boolean,
    val radiusOverrideM: Int?,
    /** Surveyed big-flower sites; empty = harvest sweep (unchanged for every pre-R2 preset). */
    val flowers: List<LatLng> = emptyList(),
)

/**
 * Pure walk-session orchestrator, lifted verbatim out of [com.pikmin.walksim.WalkService] (Stage 4). Drives the
 * injected [LocationSink] in the AC order engage → hold → fetch → play → restore, back-pressured by [machine].
 * Android is confined to [onNotify] (the notification text) and a diagnostic [Log]; everything else is JVM-testable,
 * so the fake-sink sequence-matrix can pin the order without a device.
 *
 * Single-flight: the caller runs exactly ONE [run] at a time (WalkService's single job; [machine] never
 * re-enters IDLE mid-session). [fellBackToShibuya] is set by the RoadSource fallback signal during a
 * [resolveGraph] fetch and consumed immediately after that fetch — safe only because of single-flight.
 */
class WalkSessionController(
    private val roadSource: RoadSource,
    private val sink: LocationSink,
    private val machine: WalkStateMachine,
    private val home: LatLng,
    private val onNotify: (String) -> Unit,
    private val holdTarget: () -> LatLng?,
) {

    /** Set by the RoadSource fallback signal → [resolveGraph] re-homes to [home] (identical to pre-extraction). */
    @Volatile
    var fellBackToShibuya = false

    /** All blocking LM/FLP + Overpass calls run here, off the main thread. */
    suspend fun run(spec: RunSpec) {
        // AC-16 first (≤2 s): engage mock before the slower graph build so a not-mock-app fault surfaces fast.
        try {
            if (!sink.engage()) {
                WalkBus.mockAppOk.value = false
                WalkBus.setupError.value = "Select WalkSim as the mock-location app in Developer Options, then start again."
                return
            }
            when (spec.mode) {
                Mode.HOLD -> {
                    // Static hold: pin the mock at [start] and refresh at 1 Hz until STOP. No graph/route/steps
                    // (playing stays 0) — used for freeze-in-place and for census teleport-sampling.
                    onNotify("Holding position")
                    while (!machine.isTerminal) { sink.hold(holdTarget() ?: spec.start); delay(HOLD_REFRESH_MS) }
                }
                Mode.SEQUENTIAL -> {
                    // "All areas": each preset walks its OWN full route to completion (a closed loop back to its
                    // start), THEN teleports to the next — continuous within a city, a jump only at city→city.
                    val plan = fullRoutePlan(PRESET_LOCATIONS, spec.profile.meanSpeedMps)
                    for ((i, entry) in plan.withIndex()) {
                        val (preset, segS) = entry
                        val label = "Walking ${preset.label} · ${i + 1}/${plan.size}"
                        onNotify(label) // during the (slow) per-preset graph fetch
                        sink.hold(preset.at) // cover the fetch gap so real GPS never shows between presets
                        // Carry the preset's OWN flower survey (empty for every sweep preset, so their
                        // behaviour is unchanged) — otherwise a tour preset would be swept here instead.
                        val presetSpec = spec.copy(flowers = preset.flowers)
                        val (graph, effStart) = resolveGraph(preset.at, segS, presetSpec)
                        // closeLoop = the city's full route returns to its start = "complete" before the jump.
                        playRoute(graph, effStart, segS, presetSpec, spec.seed + i, label, closeLoop = true)
                        if (machine.isTerminal) break // stopped: fall through to machine.complete()
                    }
                }
                Mode.SINGLE -> {
                    sink.hold(spec.start) // cover the initial fetch gap so real GPS never shows before the first route fix
                    val (graph, effStart) = resolveGraph(spec.start, spec.durationS, spec)
                    playRoute(graph, effStart, spec.durationS, spec, spec.seed)
                }
            }
            machine.complete()
            Log.i(TAG, "walk complete (mode=${spec.mode})") // SEQUENTIAL runs the sum of all city routes, not spec.durationS
        } catch (_: CancellationException) {
            // stop requested
        } catch (e: IllegalArgumentException) {
            WalkBus.setupError.value = "No walkable road near the start pin — move it onto a street."
            Log.w(TAG, "route generation failed", e)
        } catch (e: Exception) {
            WalkBus.setupError.value = "Walk failed: ${e.message}"
            Log.w(TAG, "walk failed", e)
        } finally {
            sink.restore() // AC-15: restore the real location stack even on exception / cancel
        }
    }

    /**
     * Streams one route ([WalkPlayer] over [graph] from [start] for [segS] s, seeded [seed]) into [sink], honoring
     * pause/stop back-pressure. [notifLabel] (per-preset in sequential mode) prefixes the progress notification;
     * null → progress only, as the single-route path shows.
     */
    private suspend fun playRoute(
        graph: WalkGraph,
        start: LatLng,
        segS: Long,
        spec: RunSpec,
        seed: Long,
        notifLabel: String? = null,
        closeLoop: Boolean = spec.closeLoop,
    ) {
        val cfg = WalkPlayerConfig(
            profile = spec.profile, laneSpacingM = spec.laneSpacingM, closeLoop = closeLoop, seed = seed,
            flowers = spec.flowers,
        )
        var lastNotifBucket = -1L
        WalkPlayer(graph, cfg).play(start, segS).collect { sample ->
            awaitRunnable() // suspends while paused (back-pressure); throws when stopped
            sink.push(sample)
            WalkBus.sample.value = sample
            val bucket = sample.tickIndex / NOTIF_EVERY_TICKS
            if (bucket != lastNotifBucket) {
                lastNotifBucket = bucket
                val progress = progressText(sample.cumulativeDistanceM, sample.tickIndex + 1, segS)
                onNotify(if (notifLabel != null) "$notifLabel · $progress" else progress)
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
     * Radius: [RunSpec.radiusOverrideM] if given, else sized so the disc contains the segment's sweep spiral (AC-2).
     */
    private suspend fun resolveGraph(start: LatLng, segS: Long, spec: RunSpec): Pair<WalkGraph, LatLng> {
        // A flower tour's disc must contain every surveyed site (they are fixed points, not a length budget).
        val radiusM = spec.radiusOverrideM
            ?: if (spec.flowers.isNotEmpty()) flowerFetchRadiusM(start, spec.flowers).toInt()
            else sweepFetchRadiusM(spec.profile.meanSpeedMps * segS, spec.laneSpacingM).toInt()
        fellBackToShibuya = false
        // A flower tour also needs foot-only ways: its sites can sit on paths/decks the street-only graph
        // drops, and the largest-component guard would then discard them (proof: FlowerRouteDiagnostic).
        val extraWalkable = if (spec.flowers.isNotEmpty()) OverpassGraph.FOOT_ONLY_WAYS else emptySet()
        val graph = roadSource.graphAround(start, radiusM, extraWalkable)
        // On fallback the composite served the baked Shibuya graph and signalled a re-home: the effective start
        // becomes [home], not the unreachable pin (identical to the pre-injection behavior).
        return graph to if (fellBackToShibuya) home else start
    }

    private fun progressText(distanceM: Double, elapsedS: Long, durationS: Long): String {
        val pct = if (durationS > 0) (elapsedS * 100.0 / durationS).coerceAtMost(100.0) else 0.0
        return "%.2f km · %d%% · %d/%d min".format(distanceM / 1000.0, pct.toInt(), elapsedS / 60, durationS / 60)
    }

    private companion object {
        const val TAG = "WalkService" // keep the original logcat tag so on-device diagnostics are byte-identical
        private const val PAUSE_POLL_MS = 100L
        private const val NOTIF_EVERY_TICKS = 10L
        private const val HOLD_REFRESH_MS = 1000L // re-push the held fix at 1 Hz so FLP never serves it as stale
    }
}
