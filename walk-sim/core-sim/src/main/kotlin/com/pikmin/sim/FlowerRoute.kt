package com.pikmin.sim

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import java.util.PriorityQueue

/**
 * Flower-waypoint route (R2/R3) — the shortest road walk that passes EVERY surveyed big flower.
 *
 * [sweepRoute] blankets a district on a spiral because a city core's flowers are everywhere; that is the
 * wrong shape for a place whose big flowers sit in a few known clusters (Haneda's landside, Enoshima's
 * island + beach strip), where a spiral spends most of its length over barren apron/sea. Here the flower
 * sites are surveyed up front (real in-Pikmin census — see the preset docs) and the route is a **tour**:
 *
 *  - Each [flowers] site snaps to the nearest node of the start's connected component ([FLOWER_SNAP_M] —
 *    looser than the sweep's, since a flower may sit in a park or across water from the nearest street).
 *  - Visiting order is nearest-neighbour from the start, then improved by **2-opt** over true road
 *    distances, so crossing legs are untangled. Both stages use one Dijkstra per waypoint, not per pair.
 *  - Consecutive waypoints are chained with Dijkstra shortest paths, so the polyline is road-snapped by
 *    construction and detours around unwalkable pockets exactly as the sweep does.
 *  - [closeLoop] appends the shortest path home, making the tour a closed run the walk can repeat.
 *
 * Deterministic for identical (graph, start, flowers): snap ties by node id, Dijkstra ties by node id over
 * canonically sorted adjacency, and a fixed 2-opt scan order — no RNG.
 */
fun flowerRoute(
    graph: WalkGraph,
    start: LatLng,
    flowers: List<LatLng>,
    closeLoop: Boolean = true,
): Route {
    val startNode = GraphRandomWalker(graph).snapStart(start, START_SNAP_M)
        ?: throw IllegalArgumentException("no connected graph node within $START_SNAP_M m of start")
    val connected = connectedAdjacency(graph, startNode)
    val center = graph.nodes.getValue(startNode)

    // Snap each surveyed flower onto the walkable component; a site with no road within reach is dropped
    // (its flower is unreachable on foot from here) rather than silently pulling the tour off-graph.
    val targets = flowers
        .mapNotNull { snapWaypoint(graph, connected.component, it, FLOWER_SNAP_M) }
        .distinct()
        .filter { it != startNode }
    if (targets.isEmpty()) return Route(listOf(center, center), 0.0)

    val order = tourOrder(connected.adj, startNode, targets)

    val points = ArrayList<LatLng>().apply { add(center) }
    var length = 0.0
    var current = startNode
    for (target in order) {
        val leg = shortestPath(connected.adj, current, target) ?: continue
        for (e in leg) {
            appendGeometry(points, e.geometry)
            length += e.lengthM
        }
        current = target
    }
    if (closeLoop && current != startNode) {
        shortestPath(connected.adj, current, startNode)?.let { legs ->
            for (e in legs) {
                appendGeometry(points, e.geometry)
                length += e.lengthM
            }
        }
    }
    return if (points.size == 1) Route(listOf(center, center), 0.0) else Route(points, length)
}

/**
 * Radius of the Overpass fetch disc that contains [start] and every flower, plus a buffer for the road
 * detours between them. Clamped like [sweepFetchRadiusM] so the payload stays parseable on-device.
 */
fun flowerFetchRadiusM(start: LatLng, flowers: List<LatLng>): Double {
    val spanM = flowers.maxOfOrNull { Geo.haversineMeters(start, it) } ?: 0.0
    return (spanM + FLOWER_FETCH_BUFFER_M).coerceIn(FLOWER_FETCH_MIN_M, FLOWER_FETCH_MAX_M)
}

/** Visiting order over [targets]: nearest-neighbour from [startNode], untangled by 2-opt. */
private fun tourOrder(adj: Map<Long, List<Edge>>, startNode: Long, targets: List<Long>): List<Long> {
    // One Dijkstra per waypoint (start + each flower) gives every pairwise road distance we need.
    val dist = (listOf(startNode) + targets).associateWith { dijkstraDistances(adj, it) }
    fun d(a: Long, b: Long) = dist[a]?.get(b) ?: Double.MAX_VALUE

    val remaining = targets.toMutableList()
    val order = ArrayList<Long>(targets.size)
    var current = startNode
    while (remaining.isNotEmpty()) {
        // Ties by node id keep the tour deterministic when two flowers are equidistant.
        val next = remaining.minWithOrNull(compareBy({ d(current, it) }, { it })) ?: break
        order.add(next)
        remaining.remove(next)
        current = next
    }
    return twoOpt(order, startNode, ::d)
}

/**
 * 2-opt: repeatedly reverse the segment between two positions when that shortens the closed tour
 * start → … → start. Fixed scan order and a strict improvement threshold make it deterministic and
 * terminating; [MAX_TWO_OPT_PASSES] bounds the work for a large survey.
 */
private fun twoOpt(order: List<Long>, startNode: Long, d: (Long, Long) -> Double): List<Long> {
    if (order.size < 3) return order
    val tour = order.toMutableList()
    fun at(i: Int) = if (i < 0) startNode else tour[i]
    var pass = 0
    var improved = true
    while (improved && pass++ < MAX_TWO_OPT_PASSES) {
        improved = false
        for (i in 0 until tour.size - 1) {
            for (j in i + 1 until tour.size) {
                // Replacing edges (i-1,i) and (j,j+1) with (i-1,j) and (i,j+1) reverses tour[i..j].
                val a = at(i - 1)
                val b = tour[i]
                val c = tour[j]
                val e = if (j + 1 < tour.size) tour[j + 1] else startNode
                val delta = (d(a, c) + d(b, e)) - (d(a, b) + d(c, e))
                if (delta < -TWO_OPT_EPSILON_M) {
                    var lo = i
                    var hi = j
                    while (lo < hi) {
                        val t = tour[lo]; tour[lo] = tour[hi]; tour[hi] = t
                        lo++; hi--
                    }
                    improved = true
                }
            }
        }
    }
    return tour
}

/** Road distance from [src] to every reachable node (one Dijkstra, all targets). */
private fun dijkstraDistances(adj: Map<Long, List<Edge>>, src: Long): Map<Long, Double> {
    val dist = HashMap<Long, Double>().apply { put(src, 0.0) }
    val done = HashSet<Long>()
    val pq = PriorityQueue<Pair<Double, Long>>(compareBy({ it.first }, { it.second })).apply { add(0.0 to src) }
    while (pq.isNotEmpty()) {
        val (d, u) = pq.poll()
        if (!done.add(u)) continue
        for (edge in adj[u].orEmpty()) {
            if (edge.toNode in done) continue
            val nd = d + edge.lengthM
            if (nd < (dist[edge.toNode] ?: Double.MAX_VALUE)) {
                dist[edge.toNode] = nd
                pq.add(nd to edge.toNode)
            }
        }
    }
    return dist
}

private const val START_SNAP_M = 200.0        // matches sweepRoute: a preset pin may sit a block off-road
internal const val FLOWER_SNAP_M = 400.0      // a big flower may sit in a park / across water from a street
private const val TWO_OPT_EPSILON_M = 1.0     // ignore sub-metre "improvements" (float noise → no oscillation)
private const val MAX_TWO_OPT_PASSES = 20
private const val FLOWER_FETCH_BUFFER_M = 600.0
private const val FLOWER_FETCH_MIN_M = 800.0
// Higher than the sweep's 2500 m: a flower tour's waypoints are FIXED, so the disc must contain the whole
// survey or the far sites silently fail to snap and get dropped. Haneda spans ~3.8 km from its start, and the
// airport's road net is sparse, so the payload stays modest. Target device is the Pixel 7 Pro, not the 2016
// Pixel XL the 2500 m ceiling was set for. On-device verified.
private const val FLOWER_FETCH_MAX_M = 4000.0
