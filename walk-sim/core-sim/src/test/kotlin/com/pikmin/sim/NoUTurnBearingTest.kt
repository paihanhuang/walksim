package com.pikmin.sim

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * AC-3 (bearing-based). The topological anti-backtrack proxy (`toNode != previous`) does not by itself
 * forbid a *geometric* U-turn onto a different node. This graph has exactly such a trap — a >150° edge at
 * a degree>1 hub that is genuinely inside the walk radius and reachable — and asserts the generated walk
 * never realises a >150° bearing change at any degree>1 node. (A gentle approach to the trap edge is legal;
 * only a >150° turn is forbidden. Dead-ends / the radius boundary may still reverse — AC-3 exemptions.)
 */
class NoUTurnBearingTest {

    private val origin = LatLng(35.6595, 139.7005)

    private fun angDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** East corridor 0-1-2-3-4 that turns north at hub node 2, plus an in-radius hairpin stub edge 2->5. */
    private fun hairpinGraph(): WalkGraph {
        val p = HashMap<Long, LatLng>()
        p[0] = origin
        p[1] = Geo.destinationPoint(origin, 90.0, 50.0)                                      // 50 m E
        p[2] = Geo.destinationPoint(origin, 90.0, 100.0)                                     // 100 m E (hub)
        p[3] = Geo.destinationPoint(p.getValue(2), 0.0, 50.0)                                // corridor bends north
        p[4] = Geo.destinationPoint(p.getValue(2), 0.0, 100.0)
        p[5] = Geo.destinationPoint(Geo.destinationPoint(origin, 270.0, 300.0), 0.0, 50.0)   // ~304 m out, inside radius 500

        val adj = HashMap<Long, MutableList<Edge>>()
        fun link(a: Long, b: Long) {
            val pa = p.getValue(a); val pb = p.getValue(b)
            adj.getOrPut(a) { mutableListOf() }.add(Edge(b, listOf(pa, pb), Geo.haversineMeters(pa, pb)))
        }
        fun biLink(a: Long, b: Long) { link(a, b); link(b, a) }
        biLink(0, 1); biLink(1, 2); biLink(2, 3); biLink(3, 4)
        biLink(2, 5) // hairpin trap: a >150° edge at hub 2, INSIDE the walk radius (only the bearing filter can reject it)
        return WalkGraph(p, adj)
    }

    @Test
    fun generatedWalk_hasNoBearingUTurn_atDegreeGt1Nodes() {
        val graph = hairpinGraph()
        val pos = graph.nodes

        // Fixture sanity: the trap edge 2->5 really is a >150° bend off the eastbound approach 1->2.
        val trapTurn = angDiff(
            Geo.bearingDegrees(pos.getValue(1), pos.getValue(2)),
            Geo.bearingDegrees(pos.getValue(2), pos.getValue(5)),
        )
        assertTrue(trapTurn > 150.0, "fixture is not a hairpin: trap bend only $trapTurn deg")

        val degree = graph.adjacency.mapValues { it.value.size }
        val nodePath = GraphRandomWalker(graph)
            .walk(origin, targetLengthM = 600.0, radiusM = 500.0, seed = 1L).nodePath

        // Coverage: the walk really does approach the hub from the east, where taking the trap would be a U-turn.
        assertTrue(
            (0 until nodePath.size - 1).any { nodePath[it] == 1L && nodePath[it + 1] == 2L },
            "walk never approached the hub from the east — trap opportunity not exercised",
        )

        // AC-3: every consecutive-segment bearing change at a degree>1 node is <= 150°.
        for (i in 1 until nodePath.size - 1) {
            val cur = nodePath[i]
            if ((degree[cur] ?: 0) <= 1) continue
            val turn = angDiff(
                Geo.bearingDegrees(pos.getValue(nodePath[i - 1]), pos.getValue(cur)),
                Geo.bearingDegrees(pos.getValue(cur), pos.getValue(nodePath[i + 1])),
            )
            assertTrue(turn <= 150.0, "bearing U-turn $turn deg at degree>1 node $cur")
        }
    }
}
