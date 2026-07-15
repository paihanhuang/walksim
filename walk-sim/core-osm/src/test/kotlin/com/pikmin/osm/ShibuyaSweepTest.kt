package com.pikmin.osm

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import com.pikmin.sim.sweepRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Harvest-sweep integration on the REAL baked Shibuya graph (src/test/resources/shibuya.json, extends
 * ~2.3 km from the centre pin — room for a full 1 h-budget spiral).
 *
 * Proves AC-24 on real roads: the loop-mode route is an outward spiral that (a) leaves no harvest gap
 * inside its swept disc (every node within 500 m of the walked line), (b) keeps walking NEW ground instead
 * of re-treading, (c) meets its length budget without lapping, deterministically, well inside the 5 s
 * route-gen budget. Metrics are reconstructed BLACK-BOX from the polyline.
 */
class ShibuyaSweepTest {

    private val center = LatLng(35.6595, 139.7006)
    private val budgetM = 5616.0 // one 1 h city slice at 1.56 m/s
    private val spacingM = 850.0 // default: 2×500 m reach − 150 m margin (no gaps even on a regular grid)

    // ---------- black-box helpers ----------

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

    /** Fraction of samples within 100 m of a sample ≥ 500 m earlier along the route (re-walked ground). */
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

    @Test
    fun sweep_onRealShibuya_meetsBudget_sweepsNewGround_leavesNoHarvestGaps() {
        val graph = OverpassGraph.fromOverpassJson(OsmTestSupport.readResource("shibuya.json"))

        val t0 = System.nanoTime()
        val route = sweepRoute(graph, center, budgetM, spacingM)
        val genMs = (System.nanoTime() - t0) / 1_000_000

        val effCenter = route.points.first() // the snapped pin — radii measured from where the walk starts
        val samples = resample(route)
        val maxR = route.points.maxOf { Geo.haversineMeters(effCenter, it) }
        val revisit = revisitFraction(samples)
        val coverage = harvestCoverage(graph, samples, effCenter, innerR = maxR - 300.0)
        println(
            "[sweep Shibuya] len=${"%.0f".format(route.totalLengthM)}m maxR=${"%.0f".format(maxR)}m " +
                "revisit=${"%.3f".format(revisit)} harvestCov=${"%.3f".format(coverage)} genMs=$genMs",
        )

        assertTrue(route.totalLengthM >= budgetM, "AC-1 budget not met: ${route.totalLengthM}")
        assertTrue(route.totalLengthM <= budgetM * 1.25, "wild overshoot: ${route.totalLengthM}")
        assertTrue(maxR >= 700.0, "spiral never expanded past the first rings: maxR=$maxR")
        // Connector deadhead: fresh-connector penalty + the v1.5 850 m spacing (500 m reach) cut this to ~0.004
        // on this graph (was 0.175 plain, 0.078 penalty-only at 550 m) — wider rings make connectors direct.
        // Pin the gain so a regression (penalty removed or spacing narrowed) is caught, not silently reverted.
        assertTrue(revisit <= 0.05, "AC-24b re-walked ground: revisit=$revisit (need ≤0.05 at 850 m spacing)")
        assertTrue(coverage >= 0.95, "AC-24a harvest gap: coverage=$coverage (need ≥0.95)")
        assertEquals(route, sweepRoute(graph, center, budgetM, spacingM), "AC-24d deterministic")
        assertTrue(genMs < 5000, "route-gen ${genMs}ms exceeds the 5 s on-device budget")
    }
}
