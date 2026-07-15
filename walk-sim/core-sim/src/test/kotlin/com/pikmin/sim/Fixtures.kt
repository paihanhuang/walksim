package com.pikmin.sim

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph

/** Synthetic graph fixtures + geometry helpers for the pure-core tests. */
object Fixtures {

    /** n×n grid of nodes, [spacingM] apart, 4-neighbour bidirectional edges. id = i*1000 + j. */
    fun gridGraph(origin: LatLng, n: Int, spacingM: Double): WalkGraph {
        val nodes = HashMap<Long, LatLng>()
        fun id(i: Int, j: Int) = (i * 1000 + j).toLong()
        for (i in 0 until n) for (j in 0 until n) {
            val east = Geo.destinationPoint(origin, 90.0, i * spacingM)
            nodes[id(i, j)] = Geo.destinationPoint(east, 0.0, j * spacingM)
        }
        val adj = HashMap<Long, MutableList<Edge>>()
        fun link(a: Long, b: Long) {
            val pa = nodes.getValue(a); val pb = nodes.getValue(b)
            adj.getOrPut(a) { mutableListOf() }.add(Edge(b, listOf(pa, pb), Geo.haversineMeters(pa, pb)))
        }
        for (i in 0 until n) for (j in 0 until n) {
            if (i + 1 < n) { link(id(i, j), id(i + 1, j)); link(id(i + 1, j), id(i, j)) }
            if (j + 1 < n) { link(id(i, j), id(i, j + 1)); link(id(i, j + 1), id(i, j)) }
        }
        return WalkGraph(nodes, adj)
    }

    /** Straight line of [count] nodes [spacingM] apart (interior degree 2, ends degree 1). */
    fun lineGraph(origin: LatLng, count: Int, spacingM: Double): WalkGraph {
        val nodes = HashMap<Long, LatLng>()
        for (k in 0 until count) nodes[k.toLong()] = Geo.destinationPoint(origin, 90.0, k * spacingM)
        val adj = HashMap<Long, MutableList<Edge>>()
        for (k in 0 until count - 1) {
            val a = k.toLong(); val b = (k + 1).toLong()
            val pa = nodes.getValue(a); val pb = nodes.getValue(b)
            val len = Geo.haversineMeters(pa, pb)
            adj.getOrPut(a) { mutableListOf() }.add(Edge(b, listOf(pa, pb), len))
            adj.getOrPut(b) { mutableListOf() }.add(Edge(a, listOf(pb, pa), len))
        }
        return WalkGraph(nodes, adj)
    }

    /** Same graph with every adjacency list reversed — to prove canonicalisation (AC-5). */
    fun withReversedAdjacency(g: WalkGraph): WalkGraph =
        WalkGraph(g.nodes, g.adjacency.mapValues { (_, e) -> e.reversed() })

    /** A straight densified path of [totalM] heading due east, points [stepM] apart. */
    fun straightPath(origin: LatLng, totalM: Double, stepM: Double = 1.0): List<LatLng> {
        val out = ArrayList<LatLng>()
        var d = 0.0
        while (d <= totalM) { out += Geo.destinationPoint(origin, 90.0, d); d += stepM }
        return out
    }

    /**
     * A gently curving densified path (constant small turn per step) — bearing rotates slowly so
     * bearing-tracking is exercised while per-tick chord ≈ arc (isolates AC-9 from corner foreshortening).
     */
    fun gentleCurvePath(origin: LatLng, totalM: Double, turnDegPerStep: Double = 0.1, stepM: Double = 1.0): List<LatLng> {
        val out = ArrayList<LatLng>()
        var p = origin
        var brg = 0.0
        var d = 0.0
        out += p
        while (d < totalM) { p = Geo.destinationPoint(p, brg, stepM); out += p; brg += turnDegPerStep; d += stepM }
        return out
    }

    /** A right-angle "L" densified path: [legM] east then [legM] north — a sharp corner for teleport tests. */
    fun cornerPath(origin: LatLng, legM: Double, stepM: Double = 1.0): List<LatLng> {
        val corner = Geo.destinationPoint(origin, 90.0, legM)
        val out = ArrayList<LatLng>()
        var d = 0.0
        while (d <= legM) { out += Geo.destinationPoint(origin, 90.0, d); d += stepM }
        d = stepM
        while (d <= legM) { out += Geo.destinationPoint(corner, 0.0, d); d += stepM }
        return out
    }

    /** Planar (local-tangent) point-to-segment distance in metres — adequate at neighbourhood scale. */
    fun pointToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val mPerLat = 111_320.0
        val mPerLng = 111_320.0 * Math.cos(Math.toRadians(a.lat))
        val px = (p.lng - a.lng) * mPerLng; val py = (p.lat - a.lat) * mPerLat
        val bx = (b.lng - a.lng) * mPerLng; val by = (b.lat - a.lat) * mPerLat
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        return Math.hypot(px - t * bx, py - t * by)
    }

    fun minDistToAnyEdge(p: LatLng, g: WalkGraph): Double {
        var best = Double.MAX_VALUE
        for ((_, edges) in g.adjacency) for (e in edges) {
            val geom = e.geometry
            for (i in 0 until geom.size - 1) best = minOf(best, pointToSegmentMeters(p, geom[i], geom[i + 1]))
        }
        return best
    }
}
