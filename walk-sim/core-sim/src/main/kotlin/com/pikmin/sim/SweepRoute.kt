package com.pikmin.sim

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Harvest-sweep route — the loop-mode route for [WalkPlayer] (AC-24).
 *
 * A big flower is harvestable within ~350 m of the avatar, so one pass sweeps a ~700 m-wide corridor and
 * re-walking swept ground harvests NOTHING. Street coverage is therefore the wrong objective; this builder
 * maximizes NEW swept area per metre walked instead:
 *
 *  - An outward **Archimedean spiral** of shortest paths, centred on the snapped start (the pin is assumed
 *    to sit mid-cluster). Ring spacing [laneSpacingM] < 2×350 m keeps every point between adjacent rings
 *    harvestable — no gaps (AC-24a) — while wider spacing would sweep faster but miss flowers.
 *  - The first ring at [FIRST_RING_M] is close enough that its reach covers the whole centre disc: the
 *    cluster around the start is harvested before the route expands.
 *  - Spiral waypoints every ~[WAYPOINT_STEP_M] of arc snap to the nearest connected node ([SNAP_MAX_M];
 *    waypoints over parks/water are skipped) and are chained with Dijkstra shortest paths, so the route is
 *    road-snapped by construction (AC-4) and detours around unwalkable pockets.
 *  - The build stops once the accumulated length reaches [targetLengthM] (finishing its current leg, so it
 *    may overshoot by one short leg; the motion engine truncates by time — AC-1). If the graph is smaller
 *    than the budget the shortfall is returned as built and the caller fills the remaining time (AC-24c:
 *    novel ground is never skipped in favour of a lap).
 *  - [closeLoop] (AC-24e) appends the shortest path from the spiral's end back to the start — a closed
 *    run. The return leg re-crosses swept rings by necessity and is exempt from the new-ground criterion.
 *
 * Deterministic for identical (graph, start, targetLengthM, laneSpacingM): closed-form spiral, snap ties by
 * node id, Dijkstra ties by node id over canonically sorted adjacency, no RNG (AC-24d / AC-5).
 */
fun sweepRoute(
    graph: WalkGraph,
    start: LatLng,
    targetLengthM: Double,
    laneSpacingM: Double = DEFAULT_LANE_SPACING_M,
    closeLoop: Boolean = false,
): Route {
    require(targetLengthM > 0) { "targetLengthM must be > 0" }
    require(laneSpacingM > 0) { "laneSpacingM must be > 0" }
    // 200 m (not 50): preset city-centre pins may sit a block or two from the nearest mapped road.
    val startNode = GraphRandomWalker(graph).snapStart(start, 200.0)
        ?: throw IllegalArgumentException("no connected graph node within 200 m of start")
    val center = graph.nodes.getValue(startNode)

    // Connected component of the start, with canonically sorted adjacency (Dijkstra determinism).
    val connected = connectedAdjacency(graph, startNode)
    val adj = connected.adj
    val component = connected.component
    val maxGraphR = component.maxOf { Geo.haversineMeters(center, graph.nodes.getValue(it)) }

    val points = ArrayList<LatLng>().apply { add(center) }
    var length = 0.0
    var current = startNode
    val walked = HashSet<Pair<Long, Long>>() // undirected keys of edges already toured — connectors avoid re-treading them

    // Spiral r = b·θ with θ starting at the first ring; each waypoint advances ~WAYPOINT_STEP_M of arc.
    // A graph smaller than the first ring (degenerate fetch) clamps the ring inside it — the spiral then
    // circles what exists and the caller laps the shortfall. The θ-step cap keeps waypoints landing on such
    // tiny rings; at real ring radii (≥ ~286 m) the arc step alone is the binding limit.
    val b = laneSpacingM / (2 * PI)
    var theta = minOf(FIRST_RING_M, maxGraphR * 0.8).coerceAtLeast(1.0) / b
    var guard = 0
    while (length < targetLengthM && guard++ < MAX_WAYPOINTS) {
        val r = b * theta
        if (r > maxGraphR + SNAP_MAX_M) break // the spiral has left the graph: return the shortfall as built
        val waypoint = Geo.destinationPoint(center, Math.toDegrees(theta) % 360.0, r)
        theta += (WAYPOINT_STEP_M / r).coerceAtMost(MAX_DTHETA_RAD)
        val target = snapWaypoint(graph, component, waypoint) ?: continue
        if (target == current) continue
        val leg = shortestPath(adj, current, target, walked) ?: continue // connector prefers fresh streets
        var legFrom = current
        for (e in leg) {
            walked.add(key(legFrom, e.toNode))
            appendGeometry(points, e.geometry)
            length += e.lengthM
            legFrom = e.toNode
        }
        current = target
    }
    if (closeLoop && current != startNode) {
        shortestPath(adj, current, startNode)?.let { legs -> // return leg: plain shortest path home (AC-24e)
            for (e in legs) {
                appendGeometry(points, e.geometry)
                length += e.lengthM
            }
        }
    }
    return if (points.size == 1) Route(listOf(center, center), 0.0) else Route(points, length)
}

/**
 * Radius of the Overpass fetch disc that will contain the spiral for a [plannedLengthM] walk (spiral area
 * ≈ spacing × length, plus the first ring and a snap/detour buffer), clamped to keep the payload sane on
 * a 2016-class device (AC-2). The walk degrades gracefully if clamped: the sweep fills the disc, then the
 * caller fills the remaining time.
 */
fun sweepFetchRadiusM(plannedLengthM: Double, laneSpacingM: Double = DEFAULT_LANE_SPACING_M): Double =
    (sqrt(laneSpacingM * plannedLengthM / PI + FIRST_RING_M * FIRST_RING_M) + FETCH_BUFFER_M)
        .coerceAtMost(FETCH_MAX_M) // the formula's own floor is FIRST_RING_M + FETCH_BUFFER_M ≈ 600 m

/**
 * Default ring spacing: 2×harvest-reach (500 m) minus a ~150 m margin = 850 m. Adjacent rings' 500 m coverage
 * bands meet with a ~75 m overlap → no gaps even on a perfectly-regular grid (a synthetic 150 m lattice keeps
 * coverage ≥0.95 only at ≤850; 950-1000 graze ~0.94 gaps there, though real irregular cities hold ≥0.98). The
 * v1.6 "more flowers" win comes from the fetch cap + open default below, NOT wider spacing. Tunable via
 * `spacing_s`, and set **per-preset** in the app (Locations.kt): dense city cores were MEASURED gap-free well
 * past the 2×reach heuristic — Okubo 1100 m, Roppongi 1200 m (v1.7) — because there connector deadhead, not
 * ring spacing, bounds coverage. Measure per-city (density doesn't predict it); don't assume the 2×reach cap.
 */
const val DEFAULT_LANE_SPACING_M = 850.0

private const val FIRST_RING_M = 300.0    // first ring radius; its 500 m reach covers the centre disc
private const val WAYPOINT_STEP_M = 300.0 // spiral arc between waypoints (chord ≈ arc at ring radii)
private const val SNAP_MAX_M = 250.0      // waypoint→node snap limit; beyond it the waypoint is skipped
private const val FETCH_BUFFER_M = 300.0
// v1.6: 2000→2500 so a full 20 km spiral at 850 m spacing FITS the fetched disc (π·2500²/850 ≈ 23 km) and does
// NOT shortfall into re-walk (open would lap the shortfall, closed would return home early) — the actual cause
// of the "back-and-forth without new flowers" on a 20 km run. ~2400 m fetches parse fine on the Pixel XL (proven).
private const val FETCH_MAX_M = 2500.0
private const val MAX_WAYPOINTS = 100_000 // safety guard; a 2 km spiral uses ~10² waypoints
private const val MAX_DTHETA_RAD = 1.05   // ≥ ~6 waypoints per turn even on a clamped tiny ring

/** The start's connected component plus its canonically sorted adjacency (Dijkstra determinism). */
internal class ConnectedGraph(val adj: Map<Long, List<Edge>>, val component: Set<Long>)

/** BFS out from [startNode], sorting each node's edges canonically so downstream Dijkstras are deterministic. */
internal fun connectedAdjacency(graph: WalkGraph, startNode: Long): ConnectedGraph {
    val adj = HashMap<Long, List<Edge>>()
    val component = HashSet<Long>().apply { add(startNode) }
    val queue = ArrayDeque<Long>().apply { add(startNode) }
    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        val edges = graph.adjacency[u].orEmpty().sortedWith(compareBy({ it.toNode }, { it.lengthM }))
        adj[u] = edges
        for (e in edges) if (component.add(e.toNode)) queue.addLast(e.toNode)
    }
    return ConnectedGraph(adj, component)
}

/** Nearest component node within [maxM] of [p]; ties by id (deterministic), null if none. */
internal fun snapWaypoint(graph: WalkGraph, component: Set<Long>, p: LatLng, maxM: Double = SNAP_MAX_M): Long? {
    var best: Long? = null
    var bestD = maxM
    for (id in component) {
        val d = Geo.haversineMeters(p, graph.nodes.getValue(id))
        if (d < bestD || (d == bestD && best != null && id < best!!)) {
            bestD = d
            best = id
        }
    }
    return best
}

/**
 * Dijkstra [src]→[dst] over [adj]; returns the travel-ordered edges, or null if unreachable.
 * Edges whose undirected key is in [walked] cost [WALKED_EDGE_PENALTY]× their length, so a connector prefers
 * a comparable-length FRESH street over re-treading one the spiral already covered (the fresh detour carries
 * new big flowers; a plain shortest path would deadhead over swept ground harvesting nothing). The penalty is
 * moderate, so a genuinely-forced retrace (only road out, e.g. a bridge) is still taken. Pass an empty [walked]
 * for a plain shortest path (the closure return leg, exempt per AC-24e).
 */
internal fun shortestPath(
    adj: Map<Long, List<Edge>>,
    src: Long,
    dst: Long,
    walked: Set<Pair<Long, Long>> = emptySet(),
): List<Edge>? {
    if (src == dst) return emptyList()
    val dist = HashMap<Long, Double>().apply { put(src, 0.0) }
    val prevEdge = HashMap<Long, Edge>()
    val prevNode = HashMap<Long, Long>()
    val done = HashSet<Long>()
    val pq = PriorityQueue<NodeDist>(compareBy({ it.d }, { it.node })).apply { add(NodeDist(0.0, src)) }
    while (pq.isNotEmpty()) {
        val (d, u) = pq.poll()
        if (!done.add(u)) continue
        if (u == dst) break
        for (e in adj[u].orEmpty()) {
            if (e.toNode in done) continue
            val stepCost = if (walked.isNotEmpty() && key(u, e.toNode) in walked) e.lengthM * WALKED_EDGE_PENALTY else e.lengthM
            val nd = d + stepCost
            if (nd < (dist[e.toNode] ?: Double.MAX_VALUE)) {
                dist[e.toNode] = nd
                prevEdge[e.toNode] = e
                prevNode[e.toNode] = u
                pq.add(NodeDist(nd, e.toNode))
            }
        }
    }
    if (dst !in done) return null
    val out = ArrayList<Edge>()
    var c = dst
    while (c != src) {
        out.add(prevEdge.getValue(c))
        c = prevNode.getValue(c)
    }
    out.reverse()
    return out
}

/** Appends edge geometry, skipping the leading vertex that duplicates the current last point. */
internal fun appendGeometry(points: MutableList<LatLng>, geom: List<LatLng>) {
    for (i in geom.indices) {
        if (i == 0 && points.isNotEmpty() && points.last() == geom[0]) continue
        points += geom[i]
    }
}

/** Undirected edge key (endpoint order-independent) so a retrace is caught whichever way it is walked. */
private fun key(a: Long, b: Long): Pair<Long, Long> = if (a <= b) a to b else b to a

/**
 * Cost multiplier on an already-walked edge when routing a connector. Moderate by design: high enough to
 * prefer a comparable-length fresh street (new flowers) over deadheading swept ground, low enough that a
 * genuinely-forced retrace (the only road through) is still taken rather than a wild fresh detour.
 */
private const val WALKED_EDGE_PENALTY = 3.0

private data class NodeDist(val d: Double, val node: Long)
