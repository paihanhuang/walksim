package com.pikmin.osm

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.sim.WalkPlayer
import com.pikmin.sim.WalkPlayerConfig
import com.pikmin.sim.sweepRoute
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Continuity guard for the harvest-sweep route on the real baked Shibuya graph. The avatar walks
 * `route.points` as a polyline; if any leg's geometry is appended in the wrong direction the polyline
 * TELEPORTS (the reported "avatar suddenly jumps" bug). A continuous route traces the road distance, so
 * the polyline length must ~equal `totalLengthM`; a jump inflates it. Black-box over the polyline.
 */
class RouteContinuityTest {

    private val center = LatLng(35.6595, 139.7006) // Shibuya preset pin

    @Test
    fun shibuyaSweep_isContinuous_noTeleports() {
        val graph = OverpassGraph.fromOverpassJson(OsmTestSupport.readResource("shibuya.json"))
        val route = sweepRoute(graph, center, targetLengthM = 10_000.0, laneSpacingM = 500.0) // Shibuya preset params

        val pts = route.points
        val gaps = (1 until pts.size).map { Geo.haversineMeters(pts[it - 1], pts[it]) }
        val polylineLen = gaps.sum()
        val maxGap = gaps.maxOrNull() ?: 0.0
        val jumps = gaps.count { it > 60.0 } // road geometry vertices are dense; >60 m between consecutive points is a teleport
        println(
            "[continuity] points=${pts.size} polylineLen=${"%.0f".format(polylineLen)}m " +
                "totalLengthM=${"%.0f".format(route.totalLengthM)}m maxGap=${"%.0f".format(maxGap)}m jumps>60m=$jumps",
        )

        // The polyline the avatar actually walks must trace the road distance. Teleport jumps add extra
        // straight-line distance that the road-length sum (totalLengthM) never counts, so polyline >> road.
        assertTrue(
            polylineLen <= route.totalLengthM * 1.05,
            "route teleports: polyline=${"%.0f".format(polylineLen)}m vs road=${"%.0f".format(route.totalLengthM)}m " +
                "(maxGap=${"%.0f".format(maxGap)}m, ${jumps} gaps >60m)",
        )
    }

    @Test
    fun shibuyaWalk_emittedPositionStream_hasNoTeleport() = runTest {
        val graph = OverpassGraph.fromOverpassJson(OsmTestSupport.readResource("shibuya.json"))
        // App config for a single closed-loop preset: loop harvest-sweep, Shibuya spacing 500.
        val cfg = WalkPlayerConfig(loop = true, closeLoop = true, laneSpacingM = 500.0, seed = 20260724L)
        val samples = WalkPlayer(graph, cfg).play(center, durationS = 600L).toList()

        val gaps = (1 until samples.size).map { Geo.haversineMeters(samples[it - 1].pos, samples[it].pos) }
        val maxGap = gaps.maxOrNull() ?: 0.0
        val over = gaps.count { it > 15.0 }
        println("[motion] samples=${samples.size} maxGap=${"%.1f".format(maxGap)}m over15m=$over")

        // The engine bounds truePos to MAX_STEP_M (4.5 m/1 Hz) and adds smooth OU noise, so consecutive
        // emitted fixes can't teleport. >15 m between ticks is a discontinuity.
        assertTrue(maxGap <= 15.0, "avatar teleport: consecutive emitted fix jumped ${"%.1f".format(maxGap)} m (>15 m)")
    }
}
