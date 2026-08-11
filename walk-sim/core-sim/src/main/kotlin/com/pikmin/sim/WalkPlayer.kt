package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.SimSample
import com.pikmin.model.WalkGraph
import com.pikmin.model.WalkProfile
import kotlinx.coroutines.flow.Flow

/**
 * Tuning for a [WalkPlayer] run. [seed] makes the whole stream deterministic (AC-5).
 * [loop] (default) walks the harvest sweep ([sweepRoute]): an outward spiral, rings [laneSpacingM] apart,
 * sized to the duration so every minute sweeps NEW harvestable ground (AC-24); a graph smaller than the
 * budget is filled by retracing the sweep out-and-back. [closeLoop] makes the sweep a closed run (AC-24e:
 * shortest path home appended). With [loop] off, the plain bounded random walk
 * ([GraphRandomWalker.generate]) is used instead.
 */
data class WalkPlayerConfig(
    val profile: WalkProfile = WalkProfile(),
    val radiusM: Double = 800.0,
    val spacingM: Double = 1.0,
    val loop: Boolean = true,
    val laneSpacingM: Double = DEFAULT_LANE_SPACING_M,
    val closeLoop: Boolean = false,
    val seed: Long,
    /**
     * Surveyed big-flower sites. EMPTY (default) keeps every existing mode untouched; non-empty replaces the
     * sweep with [flowerRoute] — the shortest closed road tour passing all of them, played to completion.
     */
    val flowers: List<LatLng> = emptyList(),
)

/**
 * Thin facade over the walk engine: a start pin + duration become a road-snapped 1 Hz [SimSample] stream.
 * Chains route → densify → play. The start is snapped to the nearest connected graph node inside the route
 * builder ([sweepRoute] / [GraphRandomWalker]) before walking, so no separate snap step is needed.
 */
class WalkPlayer(private val graph: WalkGraph, private val cfg: WalkPlayerConfig) {

    fun play(start: LatLng, durationS: Long): Flow<SimSample> {
        val targetLengthM = cfg.profile.meanSpeedMps * durationS
        val route: Route
        val playMs: Long
        // Flower tour (R2/R3): a fixed closed circuit over the censused sites. Degenerates to null when NO site
        // is reachable from the start — the offline-Shibuya fallback graph, or a fetch too poor to snap them —
        // in which case this preset walks the normal sweep rather than stalling the whole session on a
        // zero-length route (in SEQUENTIAL that would abort every city after it).
        val tour = if (cfg.flowers.isEmpty()) null else {
            flowerRoute(graph, start, cfg.flowers, closeLoop = true).takeIf { it.totalLengthM > 0.0 }
        }
        if (tour != null) {
            // Played IN FULL (runUntilPathEnd) like the closed sweep, so the avatar completes the tour rather
            // than stopping partway; the played time tracks the tour's own length, not the requested duration.
            route = tour
            playMs = Math.round(route.totalLengthM / cfg.profile.meanSpeedMps * 1000)
        } else if (cfg.loop && cfg.closeLoop) {
            // Closed harvest run (AC-24e): ONE closed sweep (spiral out + shortest path home), sized up to
            // the budget or the fetched graph, played IN FULL so the avatar returns to its start. Playback is
            // time-limited, so it must run for the loop's own length — not D — or it would stop partway up the
            // spiral and never walk the return leg. The loop is NOT padded to D: re-walking swept ground
            // harvests nothing, so a district smaller than the budget yields a shorter (still closed) loop.
            // Played duration therefore tracks the loop length; AC-1's [0.9D,1.1D] is scoped to the open modes.
            route = sweepRoute(graph, start, targetLengthM, cfg.laneSpacingM, closeLoop = true)
            // Estimate walk time from loop length; the engine (runUntilPathEnd) extends past it as pauses
            // require, so the avatar completes the loop home rather than stopping short at this estimate.
            playMs = Math.round(route.totalLengthM / cfg.profile.meanSpeedMps * 1000)
        } else {
            route = if (cfg.loop) {
                val sweep = sweepRoute(graph, start, targetLengthM, cfg.laneSpacingM, closeLoop = false)
                if (sweep.totalLengthM >= targetLengthM) sweep
                // Spiral exhausted the graph before the budget (AC-24c): residual off-spiral pockets are
                // foregone (bounded by AC-24a's tolerance) — retrace the sweep out-and-back to fill the time.
                else repeatCircuit(closeOutAndBack(sweep), targetLengthM)
            } else {
                // loop=false fallback: the plain bounded random walk (sweepRoute is the loop=true default).
                GraphRandomWalker(graph).generate(start, targetLengthM, cfg.radiusM, cfg.seed)
            }
            playMs = durationS * 1000
        }
        val path = PathEngine.densify(route, spacingM = cfg.spacingM)
        return WalkingMotionEngine.play(
            path, cfg.profile, durationMs = playMs, seed = cfg.seed,
            runUntilPathEnd = tour != null || (cfg.loop && cfg.closeLoop),
        )
    }

    /** Closes an open [route] by retracing it end→start, so [repeatCircuit] can lap it (shortfall fill). */
    private fun closeOutAndBack(route: Route): Route =
        if (route.points.size < 2) route
        else Route(route.points + route.points.asReversed().drop(1), route.totalLengthM * 2)

    /**
     * Repeats the closed [circuit] end-to-start until the walk reaches [targetLengthM], truncating the final lap.
     * The circuit is closed (first point == last), so each lap begins where the previous ended — the join adds no
     * vertex and the re-tour is seamless.
     */
    private fun repeatCircuit(circuit: Route, targetLengthM: Double): Route {
        val lap = circuit.points
        if (lap.size < 2 || circuit.totalLengthM <= 0.0) return circuit
        val out = ArrayList<LatLng>().apply { add(lap.first()) }
        var length = 0.0
        while (length < targetLengthM) {
            for (i in 1 until lap.size) {
                val a = lap[i - 1]; val b = lap[i]
                val seg = Geo.haversineMeters(a, b)
                if (seg <= 0.0) continue
                val remaining = targetLengthM - length
                if (seg <= remaining) {
                    out += b; length += seg
                } else {
                    out += Geo.destinationPoint(a, Geo.bearingDegrees(a, b), remaining); length += remaining
                    return Route(out, length)
                }
            }
        }
        return Route(out, length)
    }
}
