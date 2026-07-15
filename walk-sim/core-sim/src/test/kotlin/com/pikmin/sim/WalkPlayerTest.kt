package com.pikmin.sim

import com.pikmin.model.LatLng
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WalkPlayerTest {

    private val origin = LatLng(37.4220, -122.0841)

    // AC-1 — WalkPlayer turns a start pin + duration into a non-empty, road-snapped 1 Hz SimSample stream.
    @Test
    fun play_emitsRoadSnappedStream_atOneHz() = runTest {
        // Thin line graph so "on-graph" is a real perpendicular-distance constraint (not trivially satisfied).
        val graph = Fixtures.lineGraph(origin, count = 30, spacingM = 50.0)
        val durationS = 60L
        val cfg = WalkPlayerConfig(seed = 20250630L) // profile/radius/spacing = defaults (mean 1.3, 800 m, 1 m)

        val samples = WalkPlayer(graph, cfg).play(origin, durationS).toList()

        assertTrue(samples.isNotEmpty(), "stream must be non-empty")
        assertEquals(durationS.toInt(), samples.size, "1 Hz: one sample per second over the duration")
        // Each emitted (noisy) fix stays within its own reported GPS accuracy of the road it was snapped to;
        // a gross road-snapping failure would put points far off every edge and trip this.
        samples.forEach { s ->
            val offGraph = Fixtures.minDistToAnyEdge(s.pos, graph)
            assertTrue(offGraph <= s.accuracyM + 2.0, "off-graph by $offGraph m (accuracy ${s.accuracyM} m)")
        }

        // T2.4 target length: the generated route spans ≈ meanSpeed × duration (AC-1 length contract).
        val target = cfg.profile.meanSpeedMps * durationS
        val routeLen = GraphRandomWalker(graph)
            .generate(origin, targetLengthM = target, radiusM = cfg.radiusM, seed = cfg.seed).totalLengthM
        assertTrue(abs(routeLen - target) < 1.0, "route length $routeLen m != target $target m")
    }

    // Loop mode, graph ≪ budget: the sweep shortfall is closed out-and-back and lapped to fill the duration.
    @Test
    fun play_loopMode_fillsSweepShortfall_byLapping() = runTest {
        val graph = Fixtures.gridGraph(origin, n = 2, spacingM = 50.0) // 50 m square ≪ first spiral ring
        val durationS = 600L                                            // target ≈ 1.3 × 600 = 780 m ≫ the graph
        val cfg = WalkPlayerConfig(loop = true, seed = 20250701L)

        val samples = WalkPlayer(graph, cfg).play(origin, durationS).toList()

        assertEquals(durationS.toInt(), samples.size, "1 Hz: one sample per second over the duration")
        // The whole graph holds ≈ 200 m of street; walking well past that proves the shortfall was lapped,
        // not walked once and stopped dead.
        val lapM = 4 * 50.0
        assertTrue(
            samples.last().cumulativeDistanceM > lapM + 50.0,
            "shortfall must be lapped: walked only ${samples.last().cumulativeDistanceM} m (graph ≈ $lapM m)",
        )
    }

    // Loop mode, closeLoop: the PLAYED walk (not just the Route) must return to its start (AC-24e). This is
    // the gap qa-function caught — closure at the Route level is worthless if time-limited playback stops
    // partway up the spiral. The closed loop here exceeds meanSpeed×duration, so playback MUST extend past
    // the nominal duration to finish the loop; otherwise the avatar ends far from home.
    @Test
    fun play_loopMode_closeLoop_playedWalkReturnsToStart() = runTest {
        val graph = Fixtures.gridGraph(origin, n = 20, spacingM = 100.0) // 1.9 km square — multi-ring spiral
        val durationS = 600L                                              // target ≈ 780 m ≪ spiral+return
        val cfg = WalkPlayerConfig(loop = true, closeLoop = true, laneSpacingM = 250.0, seed = 20250701L)

        val samples = WalkPlayer(graph, cfg).play(origin, durationS).toList()

        assertTrue(samples.isNotEmpty(), "closed run must emit samples")
        // Playback extended beyond the nominal duration to walk the whole closed loop home.
        assertTrue(
            samples.size > durationS.toInt(),
            "closed playback must run the full loop (>${durationS}s), got ${samples.size}s",
        )
        // THE property: the played walk ends back at the start, within a couple of GPS-noise steps.
        val backHomeM = Geo.haversineMeters(samples.first().pos, samples.last().pos)
        assertTrue(backHomeM <= 10.0, "AC-24e: played walk must end at start, ended ${"%.0f".format(backHomeM)} m away")
        samples.forEach { s ->
            val offGraph = Fixtures.minDistToAnyEdge(s.pos, graph)
            assertTrue(offGraph <= s.accuracyM + 2.0, "off-graph by $offGraph m (accuracy ${s.accuracyM} m)")
        }
        // Every emitted fix stays snapped to the square's streets.
        samples.forEach { s ->
            val offGraph = Fixtures.minDistToAnyEdge(s.pos, graph)
            assertTrue(offGraph <= s.accuracyM + 2.0, "off-graph by $offGraph m (accuracy ${s.accuracyM} m)")
        }
    }
}
