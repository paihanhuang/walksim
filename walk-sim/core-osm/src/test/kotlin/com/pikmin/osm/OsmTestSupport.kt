package com.pikmin.osm

import com.pikmin.model.WalkGraph

/** Shared helpers for the :core-osm tests. */
object OsmTestSupport {

    fun readResource(name: String): String =
        OsmTestSupport::class.java.getResource("/$name")?.readText()
            ?: error("missing test resource: $name")

    /** Connected components of the (bidirectional) graph, as sets of node ids. */
    fun connectedComponents(g: WalkGraph): List<Set<Long>> {
        val visited = HashSet<Long>()
        val comps = ArrayList<Set<Long>>()
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
            comps.add(comp)
        }
        return comps
    }
}
