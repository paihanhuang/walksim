package com.pikmin.sim

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic synthetic graphs for the env-gated perf harness ([PerfBaselineTest]) — test-support only,
 * never a behavioural golden. Mirrors the inline grid builder in RouteGoldenTest but sized to an Okubo-scale
 * dense core.
 */
object SyntheticGraphs {

    /**
     * A square lattice of ~[nodes] nodes (side = round(sqrt(nodes))) at ~[stepDeg] spacing (~100 m at Tokyo
     * latitude), with bidirectional edges to the right/down neighbours. Deterministic node ids and iteration
     * order (LinkedHashMap) so it is one connected component and `nodes.values.first()` is the (0,0) corner.
     */
    fun denseGrid(nodes: Int, stepDeg: Double = 0.0009): WalkGraph {
        val side = sqrt(nodes.toDouble()).roundToInt().coerceAtLeast(2)
        val pos = LinkedHashMap<Long, LatLng>(side * side)
        val adj = LinkedHashMap<Long, MutableList<Edge>>(side * side)
        fun id(r: Int, c: Int) = r.toLong() * side + c
        for (r in 0 until side) for (c in 0 until side) pos[id(r, c)] = LatLng(35.0 + r * stepDeg, 139.0 + c * stepDeg)
        fun link(a: Long, b: Long) {
            val ga = listOf(pos.getValue(a), pos.getValue(b))
            val len = Geo.haversineMeters(ga[0], ga[1])
            adj.getOrPut(a) { ArrayList() }.add(Edge(b, ga, len))
            adj.getOrPut(b) { ArrayList() }.add(Edge(a, ga.reversed(), len))
        }
        for (r in 0 until side) for (c in 0 until side) {
            if (c + 1 < side) link(id(r, c), id(r, c + 1))
            if (r + 1 < side) link(id(r, c), id(r + 1, c))
        }
        return WalkGraph(pos, adj)
    }
}
