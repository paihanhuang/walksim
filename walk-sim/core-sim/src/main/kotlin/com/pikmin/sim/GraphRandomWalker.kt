package com.pikmin.sim

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import kotlin.math.abs
import kotlin.random.Random

/**
 * Bounded correlated *random* walk over a pedestrian [WalkGraph] (S1b), via [generate] — the loop=false
 * fallback (the harvest sweep is [sweepRoute]'s job).
 *
 * Guarantees of the random walk, for a given seed (deterministic):
 *  - every emitted point lies on a graph edge (route is built from edge geometry)         [AC-4]
 *  - every point stays within [radiusM] of the start                                       [AC-2]
 *  - no >150° bearing change at a node of degree > 1 (dead-ends / radius-boundary may reverse) [AC-3]
 *  - the route's geodesic length equals the requested target (final edge truncated)         [AC-1]
 *  - identical output for identical (start, target, radius, seed, graph) — adjacency is
 *    canonicalised so input ordering cannot change the result                               [AC-5]
 */
class GraphRandomWalker(graph: WalkGraph) {

    private val nodes: Map<Long, LatLng> = graph.nodes

    // Canonical ordering so determinism does not depend on map/list insertion order.
    private val adjacency: Map<Long, List<Edge>> =
        graph.adjacency.mapValues { (_, edges) -> edges.sortedWith(compareBy({ it.toNode }, { it.lengthM })) }

    /** Nearest connected node (one with outgoing edges) within [maxM] metres, or null. Ties broken by id. */
    fun snapStart(p: LatLng, maxM: Double = 50.0): Long? {
        var best: Long? = null
        var bestD = Double.MAX_VALUE
        for ((id, pos) in nodes) {
            if (adjacency[id].isNullOrEmpty()) continue
            val d = Geo.haversineMeters(p, pos)
            if (d < bestD || (d == bestD && (best == null || id < best!!))) {
                bestD = d
                best = id
            }
        }
        return if (best != null && bestD <= maxM) best else null
    }

    fun generate(start: LatLng, targetLengthM: Double, radiusM: Double, seed: Long): Route =
        walk(start, targetLengthM, radiusM, seed).route

    /** Internal so tests can assert on the visited-node sequence (AC-3/AC-5). */
    internal data class Detailed(val route: Route, val nodePath: List<Long>)

    internal fun walk(start: LatLng, targetLengthM: Double, radiusM: Double, seed: Long): Detailed {
        require(targetLengthM > 0) { "targetLengthM must be > 0" }
        require(radiusM > 0) { "radiusM must be > 0" }
        // 200 m (not 50): preset city-centre pins may sit a block or two from the nearest mapped road;
        // the route is still fully on-road from the snapped node.
        val startNode = snapStart(start, 200.0)
            ?: throw IllegalArgumentException("no connected graph node within 200 m of start")

        val origin = nodes.getValue(startNode)
        val rnd = Random(seed)
        val points = ArrayList<LatLng>().apply { add(origin) }
        val nodePath = ArrayList<Long>().apply { add(startNode) }
        var length = 0.0
        var current = startNode
        var previous: Long? = null
        var incomingBearing: Double? = null           // travel bearing used to ARRIVE at current; null at the start
        var steps = 0
        val maxSteps = 1_000_000

        fun withinRadius(e: Edge) = e.geometry.all { Geo.haversineMeters(origin, it) <= radiusM }

        while (length < targetLengthM && steps++ < maxSteps) {
            val all = adjacency[current].orEmpty()
            if (all.isEmpty()) break

            val inRadius = all.filter { withinRadius(it) }
            val nonPrev = inRadius.filter { it.toNode != previous }
            // AC-3: among the non-backtracking in-radius edges, keep only those that do not reverse the
            // incoming travel direction by >150°. No incoming bearing at the very first node → no constraint.
            val gentle = if (incomingBearing == null) nonPrev
                         else nonPrev.filter { turnDegrees(incomingBearing!!, it) <= 150.0 }
            val pick = when {
                // Prefer a gentle (<=150°), non-backtracking, in-radius edge.
                gentle.isNotEmpty() -> gentle[rnd.nextInt(gentle.size)]
                // No gentle way on (every forward edge hairpins) — treat as a dead-end; AC-3 permits reversing.
                nonPrev.isNotEmpty() -> nonPrev[rnd.nextInt(nonPrev.size)]
                // Dead-end inside radius: only the way back remains.
                inRadius.isNotEmpty() -> inRadius[rnd.nextInt(inRadius.size)]
                // At the radius boundary: turn back (prefer not retracing the very last node).
                else -> {
                    val back = all.filter { it.toNode != previous }.ifEmpty { all }
                    back[rnd.nextInt(back.size)]
                }
            }

            val remaining = targetLengthM - length
            if (pick.lengthM <= remaining) {
                appendGeometry(points, pick.geometry)
                length += pick.lengthM
                previous = current
                current = pick.toNode
                nodePath += current
                incomingBearing = Geo.bearingDegrees(pick.geometry[pick.geometry.size - 2], pick.geometry.last())
            } else {
                val (truncated, used) = truncate(pick.geometry, remaining)
                appendGeometry(points, truncated)
                length += used
                break
            }
        }
        return Detailed(Route(points, length), nodePath)
    }

    /** Angular difference in [0,180]° between the [incoming] travel bearing and the initial bearing of [edge]. */
    private fun turnDegrees(incoming: Double, edge: Edge): Double {
        val outgoing = Geo.bearingDegrees(edge.geometry[0], edge.geometry[1])
        val d = abs(incoming - outgoing) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Appends edge geometry, skipping the leading vertex that duplicates the current last point. */
    private fun appendGeometry(points: MutableList<LatLng>, geom: List<LatLng>) {
        for (i in geom.indices) {
            if (i == 0 && points.isNotEmpty() && points.last() == geom[0]) continue
            points += geom[i]
        }
    }

    /** Sub-polyline of [geom] covering exactly [dist] metres (interpolating the final segment). */
    private fun truncate(geom: List<LatLng>, dist: Double): Pair<List<LatLng>, Double> {
        if (geom.size < 2 || dist <= 0.0) return geom.take(1) to 0.0
        val out = ArrayList<LatLng>().apply { add(geom.first()) }
        var acc = 0.0
        for (i in 0 until geom.size - 1) {
            val a = geom[i]
            val b = geom[i + 1]
            val seg = Geo.haversineMeters(a, b)
            if (acc + seg >= dist) {
                out += Geo.destinationPoint(a, Geo.bearingDegrees(a, b), dist - acc)
                return out to dist
            }
            out += b
            acc += seg
        }
        return out to acc
    }
}
