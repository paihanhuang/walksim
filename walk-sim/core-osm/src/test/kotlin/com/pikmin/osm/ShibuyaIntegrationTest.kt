package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.sim.Geo
import com.pikmin.sim.GraphRandomWalker
import com.pikmin.sim.WalkPlayer
import com.pikmin.sim.WalkPlayerConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * T3.5 — offline integration on the REAL baked Shibuya graph (src/test/resources/shibuya.json):
 *  - the built graph is a single connected component and is substantial,
 *  - WalkPlayer turns the centre pin + 600 s into a road-snapped 1 Hz stream of the target length (AC-1),
 *  - the rigorous AC-3 check the synthetic S2 fixture could not give.
 *
 * AC-3 on real data: `GraphRandomWalker`'s documented contract forbids a >150° bearing change at a
 * node of degree > 1 UNLESS the reversal is forced — i.e. no gentle (≤150°), non-backtracking, in-radius
 * alternative exists (a functional dead-end / radius boundary). We reconstruct the visited-node sequence
 * from the route (a graph vertex appears in the route only at a junction) and assert every sharp junction
 * turn was forced: the real Shibuya walk makes no AVOIDABLE U-turn at any junction. (Verified against the
 * seed=1 walk, which does hit one forced degree-2 hairpin ~92 m from origin whose only non-backtracking
 * option is a 5.8 m 163° stub — a genuine functional dead-end, not an avoidable turn.)
 */
class ShibuyaIntegrationTest {

    private val center = LatLng(35.6595, 139.7006)
    private val seed = 1L
    private val durationS = 600L
    private val radiusM = WalkPlayerConfig(seed = seed).radiusM // 800 m
    private val target = WalkPlayerConfig(seed = seed).profile.meanSpeedMps * durationS // 1.3 × 600 = 780 m

    private fun angDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    @Test
    fun shibuyaGraph_isConnected_walkable_andHasNoAvoidableUTurn() = runTest {
        val graph = OverpassGraph.fromOverpassJson(OsmTestSupport.readResource("shibuya.json"))

        // Connectivity + substantial size.
        assertTrue(graph.nodes.size > 100, "real Shibuya graph should be substantial: ${graph.nodes.size} nodes")
        assertEquals(1, OsmTestSupport.connectedComponents(graph).size, "graph must be a single connected component")

        // AC-1 — WalkPlayer yields a road-snapped 1 Hz stream over the duration.
        val samples = WalkPlayer(graph, WalkPlayerConfig(seed = seed)).play(center, durationS).toList()
        assertEquals(durationS.toInt(), samples.size, "1 Hz: one sample per second over the duration")

        // AC-1 — the generated route length matches the target (final edge truncated).
        val walker = GraphRandomWalker(graph)
        val route = walker.generate(center, targetLengthM = target, radiusM = radiusM, seed = seed)
        assertTrue(abs(route.totalLengthM - target) < 1.0, "route length ${route.totalLengthM} m != target $target m")

        // AC-3 — no avoidable >150° U-turn at any degree>1 node on the real graph.
        val origin = graph.nodes.getValue(walker.snapStart(center, 50.0)!!)
        val coordToNode = HashMap<LatLng, Long>().apply { graph.nodes.forEach { (id, pos) -> put(pos, id) } }
        val degree = graph.adjacency.mapValues { it.value.size }
        val pts = route.points
        // A vertex appears in the route only at a junction, so this is the exact visited-node sequence.
        val nodeStops = pts.indices.mapNotNull { i -> coordToNode[pts[i]]?.let { i to it } }
        assertTrue(nodeStops.size > 5, "route visited too few graph nodes to be meaningful: ${nodeStops.size}")

        var checkedDegreeGt1 = 0
        for (k in 1 until nodeStops.size - 1) {
            val (i, id) = nodeStops[k]
            if ((degree[id] ?: 0) <= 1 || i == 0 || i == pts.size - 1) continue
            checkedDegreeGt1++
            val incoming = Geo.bearingDegrees(pts[i - 1], pts[i])
            if (angDiff(incoming, Geo.bearingDegrees(pts[i], pts[i + 1])) <= 150.0) continue
            // Sharp turn: legal only if forced — no gentle, non-backtracking, in-radius alternative existed.
            val previousNode = nodeStops[k - 1].second
            val gentleAlternativeExisted = graph.adjacency.getValue(id).any { e ->
                e.toNode != previousNode &&
                    e.geometry.all { Geo.haversineMeters(origin, it) <= radiusM } &&
                    angDiff(incoming, Geo.bearingDegrees(e.geometry[0], e.geometry[1])) <= 150.0
            }
            assertFalse(gentleAlternativeExisted, "avoidable >150° U-turn at degree>1 node $id")
        }
        assertTrue(checkedDegreeGt1 > 0, "no degree>1 node traversed — the bearing check would be vacuous")
    }
}
