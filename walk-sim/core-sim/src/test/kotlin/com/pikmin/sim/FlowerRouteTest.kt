package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Edge
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Flower-waypoint route: the shortest road walk that passes EVERY surveyed big flower (R2/R3). Unlike
 * [sweepRoute] — which blankets an area on a spiral and is judged by coverage — this route is judged by
 * "did it visit all the flowers, and is the tour short". Everything here is reconstructed BLACK-BOX from
 * the returned polyline; the builder's own bookkeeping is never trusted.
 */
class FlowerRouteTest {

    private val origin = LatLng(37.4220, -122.0841)

    /** 21×21 Manhattan grid, 150 m blocks (3×3 km). */
    private fun grid(): WalkGraph = Fixtures.gridGraph(origin, n = 21, spacingM = 150.0)

    /** Flowers placed exactly on graph nodes, so "passed" can be asserted tightly. */
    private fun flowersOn(graph: WalkGraph, vararg index: Int): List<LatLng> {
        val nodes = graph.nodes.values.toList()
        return index.map { nodes[it] }
    }

    /** Closest approach of [route]'s polyline to [p], in metres. */
    private fun closestApproachM(route: Route, p: LatLng): Double =
        route.points.minOf { Geo.haversineMeters(it, p) }

    /** 1-D chain of [n] nodes spaced [stepM] due east: road distance is exactly |Δindex| × [stepM]. */
    private fun lineGraph(n: Int, stepM: Double = 100.0): WalkGraph {
        val pos = LinkedHashMap<Long, LatLng>()
        val adj = LinkedHashMap<Long, MutableList<Edge>>()
        for (i in 0 until n) pos[i.toLong()] = Geo.destinationPoint(origin, 90.0, i * stepM)
        for (i in 0 until n - 1) {
            val a = i.toLong()
            val b = (i + 1).toLong()
            val g = listOf(pos.getValue(a), pos.getValue(b))
            val len = Geo.haversineMeters(g[0], g[1])
            adj.getOrPut(a) { ArrayList() }.add(Edge(b, g, len))
            adj.getOrPut(b) { ArrayList() }.add(Edge(a, g.reversed(), len))
        }
        return WalkGraph(pos, adj)
    }

    /** Length of the shortest closed tour start → all flowers → start, by exhaustive permutation. */
    private fun bruteForceOptimalM(start: LatLng, flowers: List<LatLng>): Double {
        fun permutations(items: List<LatLng>): List<List<LatLng>> =
            if (items.size <= 1) listOf(items)
            else items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }
        return permutations(flowers).minOf { order ->
            var total = 0.0
            var prev = start
            for (p in order) { total += Geo.haversineMeters(prev, p); prev = p }
            total + Geo.haversineMeters(prev, start)
        }
    }

    /**
     * A deliberate nearest-neighbour trap, with no distance ties anywhere (so the outcome cannot hinge on
     * float noise in a tie-break). Flowers sit at +1, −2, +5, −3 steps from the start along a line: greedy
     * takes the near +1, doubles back to −2 and −3, then has to cross the whole line to +5 — 18 steps —
     * while the optimal tour sweeps right to +5 then left to −3, 16 steps. Passes only if the greedy order
     * is subsequently untangled; VERIFIED to fail (1800 vs 1600 m) when 2-opt is disabled.
     */
    @Test
    fun toursTheFlowersByTheShortestClosedWalk() {
        val graph = lineGraph(n = 25, stepM = 100.0)
        val nodes = graph.nodes
        val start = nodes.getValue(10L)
        val flowers = listOf(nodes.getValue(11L), nodes.getValue(8L), nodes.getValue(15L), nodes.getValue(7L))

        val route = flowerRoute(graph, start, flowers, closeLoop = true)

        val optimal = bruteForceOptimalM(start, flowers)
        assertEquals(1600.0, optimal, 1.0, "oracle sanity: the optimal closed tour is 16 × 100 m")
        assertEquals(
            optimal, route.totalLengthM, 1.0,
            "tour is longer than the optimal closed walk over the same flowers",
        )
    }

    /**
     * The fetch disc must CONTAIN the whole survey — a tour's waypoints are fixed, so a disc that is too small
     * silently drops the far sites (this is exactly how Haneda's T1/T2 were missed by ~2 km). Mirrors the
     * floor/ceiling/monotonic assertions [SweepRouteTest] makes for `sweepFetchRadiusM`.
     */
    @Test
    fun fetchRadiusContainsTheSurvey_withinClamps() {
        val start = origin
        fun at(m: Double) = Geo.destinationPoint(origin, 90.0, m)

        // Floor: a tiny survey still fetches a usable disc.
        assertEquals(800.0, flowerFetchRadiusM(start, listOf(at(10.0))), 1e-9)
        // Contains the farthest site plus detour buffer.
        assertTrue(
            flowerFetchRadiusM(start, listOf(at(1500.0))) >= 1500.0,
            "disc must reach the farthest surveyed site",
        )
        // Monotonic in the survey's span.
        assertTrue(flowerFetchRadiusM(start, listOf(at(2500.0))) > flowerFetchRadiusM(start, listOf(at(1500.0))))
        // Ceiling: clamped so the Overpass payload stays parseable on-device.
        assertEquals(4000.0, flowerFetchRadiusM(start, listOf(at(50_000.0))), 1e-9)
        // No sites at all → the floor, never NaN/negative.
        assertEquals(800.0, flowerFetchRadiusM(start, emptyList()), 1e-9)
    }

    @Test
    fun isDeterministic() {
        val graph = grid()
        val flowers = flowersOn(graph, 46, 89, 200, 331, 417)
        val start = graph.nodes.values.first()

        val a = flowerRoute(graph, start, flowers)
        val b = flowerRoute(graph, start, flowers)

        assertEquals(a.points, b.points, "same inputs must yield an identical polyline")
        assertEquals(a.totalLengthM, b.totalLengthM, 0.0)
    }

    @Test
    fun closedTourReturnsHomeAndOpenOneDoesNot() {
        val graph = grid()
        val flowers = flowersOn(graph, 46, 200, 417)
        val start = graph.nodes.values.first()

        val closed = flowerRoute(graph, start, flowers, closeLoop = true)
        val open = flowerRoute(graph, start, flowers, closeLoop = false)

        assertTrue(
            Geo.haversineMeters(closed.points.last(), start) <= 1.0,
            "closed tour must end where it started",
        )
        assertTrue(
            Geo.haversineMeters(open.points.last(), start) > 1.0,
            "open tour must end at the last flower, not back home",
        )
    }

    @Test
    fun passesEverySurveyedFlower() {
        val graph = grid()
        val flowers = flowersOn(graph, 35, 120, 300, 420)
        val start = graph.nodes.values.first()

        val route = flowerRoute(graph, start, flowers)

        for (f in flowers) {
            assertTrue(
                closestApproachM(route, f) <= 1.0,
                "route never reaches flower $f (closest ${closestApproachM(route, f)} m)",
            )
        }
    }
}
