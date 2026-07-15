package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GraphRandomWalkerTest {

    private val origin = LatLng(37.4220, -122.0841)

    // AC-6 — start snaps to nearest connected node within range, else error/null.
    @Test
    fun snapStart_nearestWithinRange_elseNull() {
        val w = GraphRandomWalker(Fixtures.gridGraph(origin, n = 5, spacingM = 50.0))
        val node22 = Geo.destinationPoint(Geo.destinationPoint(origin, 90.0, 100.0), 0.0, 100.0)
        val query = Geo.destinationPoint(node22, 90.0, 3.0) // 3 m from node (2,2)
        assertEquals(2002L, w.snapStart(query, maxM = 50.0))
        assertNull(w.snapStart(Geo.destinationPoint(origin, 90.0, 100_000.0), maxM = 50.0))
    }

    // AC-1 — route length equals the requested target (final edge truncated).
    @Test
    fun generate_lengthMatchesTarget() {
        val w = GraphRandomWalker(Fixtures.gridGraph(origin, n = 9, spacingM = 50.0))
        val route = w.generate(origin, targetLengthM = 375.0, radiusM = 1e6, seed = 7)
        assertTrue(abs(route.totalLengthM - 375.0) < 0.5, "length ${route.totalLengthM}")
    }

    // AC-2 — every point stays within the bounded radius of the start.
    @Test
    fun generate_allPointsWithinRadius() {
        val w = GraphRandomWalker(Fixtures.gridGraph(origin, n = 11, spacingM = 50.0))
        val radius = 120.0
        val route = w.generate(origin, targetLengthM = 5000.0, radiusM = radius, seed = 11)
        assertTrue(abs(route.totalLengthM - 5000.0) < 1.0, "length ${route.totalLengthM}")
        route.points.forEach { p ->
            val d = Geo.haversineMeters(origin, p)
            assertTrue(d <= radius + 0.5, "point $d m from start exceeds radius $radius")
        }
    }

    // AC-3 — no immediate U-turn at a node of degree > 1 (only dead-ends reverse).
    @Test
    fun generate_noUTurnExceptAtDeadEnds() {
        val count = 7
        val w = GraphRandomWalker(Fixtures.lineGraph(origin, count = count, spacingM = 50.0))
        val startMid = Geo.destinationPoint(origin, 90.0, 150.0) // node 3
        val path = w.walk(startMid, targetLengthM = 1000.0, radiusM = 1e6, seed = 3).nodePath
        assertTrue(path.size > 10, "path too short to exercise reversals: ${path.size}")
        for (i in 1 until path.size - 1) {
            if (path[i - 1] == path[i + 1]) {
                val reversedAt = path[i]
                assertTrue(
                    reversedAt == 0L || reversedAt == (count - 1).toLong(),
                    "U-turn at interior node $reversedAt (degree 2) is not allowed",
                )
            }
        }
    }

    // AC-5 — deterministic, and independent of adjacency-list input ordering.
    @Test
    fun generate_deterministic_andOrderIndependent() {
        val graph = Fixtures.gridGraph(origin, n = 7, spacingM = 50.0)
        val a = GraphRandomWalker(graph)
        val r1 = a.generate(origin, 800.0, 1e6, seed = 42)
        val r2 = a.generate(origin, 800.0, 1e6, seed = 42)
        assertEquals(r1, r2) // same seed → identical route

        val shuffled = GraphRandomWalker(Fixtures.withReversedAdjacency(graph))
        val r3 = shuffled.generate(origin, 800.0, 1e6, seed = 42)
        assertEquals(r1, r3) // canonical ordering → reversed input yields the same walk
    }

    // AC-4 — every generated point lies on a graph edge (within 0.1 m).
    @Test
    fun generate_allPointsLieOnGraphEdges() {
        val graph = Fixtures.gridGraph(origin, n = 9, spacingM = 50.0)
        val route = GraphRandomWalker(graph).generate(origin, 600.0, 1e6, seed = 5)
        route.points.forEach { p ->
            assertTrue(Fixtures.minDistToAnyEdge(p, graph) < 0.1, "point off-graph by ${Fixtures.minDistToAnyEdge(p, graph)} m")
        }
    }
}
