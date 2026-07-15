package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Harvest-sweep route (AC-24): big flowers are harvestable within ~500 m, so the loop route must sweep
 * NEW area — an outward spiral, lane-spaced < 2×500 m — not cover streets. Every metric here is
 * reconstructed BLACK-BOX from the returned polyline; the builder's own bookkeeping is never trusted.
 */
class SweepRouteTest {

    private val origin = LatLng(37.4220, -122.0841)

    /** 21×21 Manhattan grid, 150 m blocks (3×3 km) — hosts a ~5 km spiral (R ≈ 950 m) with room to spare. */
    // 21×21 grid, 150 m blocks (3×3 km) — hosts an 850 m-spaced spiral (R ≈ 1080 m at 5 km budget) with margin.
    private fun bigGrid() = Fixtures.gridGraph(origin, n = 21, spacingM = 150.0)

    /** Grid centre = node(10,10)'s exact position (same construction as the fixture). */
    private fun gridCenter() = Geo.destinationPoint(Geo.destinationPoint(origin, 90.0, 1500.0), 0.0, 1500.0)

    // ---------- black-box helpers ----------

    /** Route resampled every [stepM] of arc; each sample paired with its cumulative arc length. */
    private fun resample(route: Route, stepM: Double = 25.0): List<Pair<LatLng, Double>> {
        val pts = route.points
        val out = ArrayList<Pair<LatLng, Double>>()
        if (pts.isEmpty()) return out
        out.add(pts[0] to 0.0)
        var acc = 0.0
        var next = stepM
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val seg = Geo.haversineMeters(a, b)
            while (acc + seg >= next) {
                out.add(Geo.destinationPoint(a, Geo.bearingDegrees(a, b), next - acc) to next)
                next += stepM
            }
            acc += seg
        }
        return out
    }

    /** Fraction of samples that RE-WALK swept ground: within 100 m of a sample ≥ 500 m earlier on the route. */
    private fun revisitFraction(samples: List<Pair<LatLng, Double>>): Double {
        if (samples.size < 2) return 0.0
        var revisits = 0
        for (i in samples.indices) {
            val (pi, ai) = samples[i]
            var j = 0
            while (j < i && samples[j].second <= ai - 500.0) {
                if (Geo.haversineMeters(pi, samples[j].first) <= 100.0) { revisits++; break }
                j++
            }
        }
        return revisits.toDouble() / samples.size
    }

    /** Fraction of graph nodes inside [innerR] of [center] lying within 500 m (harvest reach) of some route sample. */
    private fun harvestCoverage(g: WalkGraph, samples: List<Pair<LatLng, Double>>, center: LatLng, innerR: Double): Double {
        val inner = g.nodes.values.filter { Geo.haversineMeters(center, it) <= innerR }
        if (inner.isEmpty()) return 1.0
        val ok = inner.count { n -> samples.any { Geo.haversineMeters(n, it.first) <= 500.0 } }
        return ok.toDouble() / inner.size
    }

    private fun maxRadius(route: Route, center: LatLng) = route.points.maxOf { Geo.haversineMeters(center, it) }

    // ---------- AC-24 / AC-1: budget ----------

    @Test
    fun sweep_meetsBudget_withoutWildOvershoot() {
        val route = sweepRoute(bigGrid(), gridCenter(), targetLengthM = 5000.0)
        assertTrue(route.totalLengthM >= 5000.0, "budget not met: ${route.totalLengthM}")
        assertTrue(route.totalLengthM <= 5000.0 * 1.2, "wild overshoot: ${route.totalLengthM}")
        // totalLengthM must agree with the polyline's own geodesic length (black-box consistency).
        var sum = 0.0
        for (i in 1 until route.points.size) sum += Geo.haversineMeters(route.points[i - 1], route.points[i])
        assertTrue(Math.abs(sum - route.totalLengthM) < 1.0, "reported length ${route.totalLengthM} != polyline $sum")
    }

    // ---------- AC-24(d) / AC-5: determinism ----------

    @Test
    fun sweep_isDeterministic_andAdjacencyOrderInsensitive() {
        val g = bigGrid()
        val a = sweepRoute(g, gridCenter(), 5000.0)
        val b = sweepRoute(g, gridCenter(), 5000.0)
        val c = sweepRoute(Fixtures.withReversedAdjacency(g), gridCenter(), 5000.0)
        assertEquals(a, b, "same inputs must reproduce the identical route")
        assertEquals(a, c, "adjacency insertion order must not change the route")
    }

    // ---------- AC-24(b): new ground ----------

    @Test
    fun sweep_walksNewGround_lowRevisitFraction() {
        val route = sweepRoute(bigGrid(), gridCenter(), 5000.0)
        val revisit = revisitFraction(resample(route))
        println("[sweep grid] revisitFraction=${"%.3f".format(revisit)}")
        assertTrue(revisit <= 0.10, "sweep re-walks swept ground: revisit=$revisit (grid bound 0.10)")
    }

    // ---------- AC-24(a): no harvest gaps ----------

    @Test
    fun sweep_leavesNoHarvestGap_insideSweptDisc() {
        val g = bigGrid()
        val center = gridCenter()
        val route = sweepRoute(g, center, 5000.0)
        val maxR = maxRadius(route, center)
        assertTrue(maxR >= 800.0, "spiral never expanded: maxR=$maxR")
        val cov = harvestCoverage(g, resample(route), center, innerR = maxR - 300.0)
        println("[sweep grid] maxR=${"%.0f".format(maxR)}m harvestCoverage=${"%.3f".format(cov)}")
        assertTrue(cov >= 0.95, "harvest gap inside swept disc: coverage=$cov (need ≥0.95)")
    }

    // ---------- AC-4 / AC-6: road-snapped, starts at the snapped pin ----------

    @Test
    fun sweep_startsAtSnappedStart_andStaysOnGraph() {
        val g = bigGrid()
        val center = gridCenter()
        val route = sweepRoute(g, center, 5000.0)
        assertEquals(center, route.points.first(), "route must start at the snapped start node")
        for (i in route.points.indices step 10) {
            val off = Fixtures.minDistToAnyEdge(route.points[i], g)
            assertTrue(off < 0.1, "AC-4: point $i off-graph by $off m")
        }
    }

    // ---------- AC-24(e): optional closed run ----------

    @Test
    fun sweep_closeLoop_returnsToStart_withBoundedReturnLeg() {
        val g = bigGrid()
        val center = gridCenter()
        val open = sweepRoute(g, center, 5000.0)
        val closed = sweepRoute(g, center, 5000.0, closeLoop = true)
        assertEquals(closed.points.first(), closed.points.last(), "closed run must end where it started")
        assertTrue(closed.totalLengthM >= 5000.0, "closure must not shrink the budget")
        // The return leg is a shortest path from the outer ring: bounded by a Manhattan detour home.
        val maxR = maxRadius(open, center)
        assertTrue(
            closed.totalLengthM - open.totalLengthM <= 2 * maxR,
            "return leg ${closed.totalLengthM - open.totalLengthM} m exceeds 2×maxR ($maxR m)",
        )
        for (i in closed.points.indices step 10) {
            assertTrue(Fixtures.minDistToAnyEdge(closed.points[i], g) < 0.1, "AC-4: closed route left the graph")
        }
    }

    // ---------- graph smaller than the budget: shortfall, not a crash ----------

    @Test
    fun sweep_smallGraph_returnsShortfallWithoutCrash() {
        val tiny = Fixtures.gridGraph(origin, n = 2, spacingM = 50.0) // 50 m square ≪ first ring
        val route = sweepRoute(tiny, origin, targetLengthM = 2000.0)
        assertTrue(route.points.isNotEmpty(), "route must not be empty")
        assertTrue(route.totalLengthM < 2000.0, "a 50 m square cannot host 2 km of novel sweep")
    }

    // ---------- AC-2: duration-derived fetch radius ----------

    @Test
    fun fetchRadius_containsSpiral_andClamps() {
        // 1 h city slice at 1.56 m/s ≈ 5616 m → R ≈ √(850·5616/π + 300²) + 300 ≈ 1569 m (default spacing 850).
        val oneHour = sweepFetchRadiusM(5616.0)
        assertTrue(oneHour in 1530.0..1610.0, "1 h radius off: $oneHour")
        assertTrue(sweepFetchRadiusM(10.0) in 600.0..620.0, "natural floor ≈ first ring + buffer")
        assertEquals(2500.0, sweepFetchRadiusM(1_000_000.0), 1e-9) // payload cap (v1.6: 2000→2500 fits a 20 km spiral)
        assertTrue(sweepFetchRadiusM(2000.0) < sweepFetchRadiusM(6000.0), "radius must grow with the budget")
    }
}
