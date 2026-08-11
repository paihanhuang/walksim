package com.pikmin.osm

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Turns an Overpass `out geom` JSON body into a connected, foot-walkable [WalkGraph]:
 *  1. parse each `way` (aligned `nodes` id list + `geometry` [{lat,lon}] list),
 *  2. keep only foot-walkable highways (allow-list; drop foot=no / access=private)                — AC-4,
 *  3. stitch by OSM node id: endpoint/shared nodes become graph vertices and the run between two of
 *     them becomes one bidirectional [Edge] (polyline geometry, lengthM = Σ haversine),
 *  4. keep only the largest connected component (silent-disconnection guard).
 */
object OverpassGraph {

    /**
     * Foot-walkable ways Pikmin Bloom renders as roads: street classes + pedestrian/service (plazas, alleys).
     * pedestrian/service are kept because in dense grids they carry the E-W links that connect residential
     * blocks — without them the street graph fragments into narrow N-S strips and the coverage loop can only
     * thread one strip. footway/path/track/steps/cycleway are dropped: sidewalk/trail noise that bloats the
     * graph (~2x nodes) without adding reachable streets. motorway/trunk excluded (not walked).
     */
    internal val WALKABLE = setOf(
        "living_street", "residential", "unclassified", "tertiary", "tertiary_link",
        "secondary", "secondary_link", "primary", "primary_link", "road",
        "pedestrian", "service",
    )

    /**
     * Foot-ONLY way classes a flower tour opts into. Deliberately NOT in [WALKABLE]: they roughly double the
     * node count and add sidewalk/trail noise, which is why every sweep preset excludes them. A tour preset
     * needs them because its fixed sites can be foot-only — Enoshima island's paths, Haneda's terminal decks —
     * and [largestComponent] would otherwise discard those areas entirely.
     */
    val FOOT_ONLY_WAYS = setOf("footway", "path", "steps", "cycleway", "track")

    private class Way(val nodes: List<Long>, val geometry: List<LatLng>)

    /**
     * [extraWalkable] adds highway classes to the allow-list for THIS build only (default empty → the
     * classic street-only graph every sweep preset uses, byte-identical). A flower-tour preset opts in to
     * footway/path/steps where its sites are reachable only on foot — an island's paths, an airport's
     * pedestrian decks — which would otherwise be dropped and then discarded by [largestComponent].
     */
    fun fromOverpassJson(json: String, extraWalkable: Set<String> = emptySet()): WalkGraph =
        largestComponent(stitch(parseWalkableWays(json, extraWalkable)))

    // --- 1 + 2: parse + WALKABLE filter -------------------------------------------------------------

    private fun parseWalkableWays(json: String, extraWalkable: Set<String>): List<Way> {
        val elements = Json.parseToJsonElement(json).jsonObject["elements"]?.jsonArray ?: return emptyList()
        val out = ArrayList<Way>()
        for (el in elements) {
            val obj = el.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "way") continue
            val tags = obj["tags"]?.jsonObject ?: continue
            if (!isWalkable(tags, extraWalkable)) continue
            val nodes = obj["nodes"]?.jsonArray?.map { it.jsonPrimitive.long } ?: continue
            val geometry = obj["geometry"]?.jsonArray?.map {
                val g = it.jsonObject
                LatLng(g.getValue("lat").jsonPrimitive.double, g.getValue("lon").jsonPrimitive.double)
            } ?: continue
            if (nodes.size >= 2 && nodes.size == geometry.size) out += Way(nodes, geometry)
        }
        return out
    }

    private fun isWalkable(tags: JsonObject, extraWalkable: Set<String>): Boolean {
        fun tag(key: String) = tags[key]?.jsonPrimitive?.contentOrNull
        val highway = tag("highway") ?: return false
        if (highway !in WALKABLE && highway !in extraWalkable) return false
        if (tag("foot") == "no") return false
        if (tag("access") == "private") return false
        return true
    }

    // --- 3: stitch by OSM node id -------------------------------------------------------------------

    private fun stitch(ways: List<Way>): WalkGraph {
        // A node becomes a graph vertex iff it is a way endpoint or is shared/visited by more than one segment.
        val occurrences = HashMap<Long, Int>()
        for (way in ways) for (id in way.nodes) occurrences[id] = (occurrences[id] ?: 0) + 1

        val nodes = HashMap<Long, LatLng>()
        val adjacency = HashMap<Long, MutableList<Edge>>()
        for (way in ways) {
            val last = way.nodes.size - 1
            var segStart = 0 // index of the vertex the current edge starts from (index 0 is an endpoint)
            for (i in 1..last) {
                val isVertex = i == last || (occurrences[way.nodes[i]] ?: 0) > 1
                if (!isVertex) continue
                val a = way.nodes[segStart]
                val b = way.nodes[i]
                val geom = way.geometry.subList(segStart, i + 1)
                val len = polylineLength(geom)
                if (a != b && len > 0.0) {
                    nodes[a] = geom.first()
                    nodes[b] = geom.last()
                    adjacency.getOrPut(a) { ArrayList() }.add(Edge(b, geom.toList(), len))
                    adjacency.getOrPut(b) { ArrayList() }.add(Edge(a, geom.reversed(), len))
                }
                segStart = i
            }
        }
        return WalkGraph(nodes, adjacency)
    }

    // --- 4: largest connected component -------------------------------------------------------------

    private fun largestComponent(g: WalkGraph): WalkGraph {
        if (g.nodes.isEmpty()) return g
        val visited = HashSet<Long>()
        var best = emptySet<Long>()
        for (start in g.nodes.keys) {
            if (start in visited) continue
            val comp = HashSet<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(start); visited.add(start); comp.add(start)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                for (e in g.adjacency[cur].orEmpty()) {
                    if (comp.add(e.toNode)) { visited.add(e.toNode); queue.add(e.toNode) }
                }
            }
            // Largest by node count; deterministic tie-break by smallest member id (avoids map-order dependence).
            if (best.isEmpty() || comp.size > best.size || (comp.size == best.size && comp.min() < best.min())) {
                best = comp
            }
        }
        val nodes = g.nodes.filterKeys { it in best }
        val adjacency = g.adjacency.filterKeys { it in best }
            .mapValues { (_, edges) -> edges.filter { it.toNode in best } }
        return WalkGraph(nodes, adjacency)
    }

    // --- edge length (haversine via :core-model Geo) ------------------------------------------------

    private fun polylineLength(points: List<LatLng>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += Geo.haversineMeters(points[i], points[i + 1])
        return sum
    }
}
