package com.pikmin.osm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T3.3 (AC-4) — WALKABLE filter at the use-site.
 *
 * The fixture mixes two connected walkable ways with eight EXCLUDED ways (motorway, motorway_link, trunk,
 * trunk_link, foot=no, access=private, elevator, corridor), each attached to the walkable component by a
 * shared node. Only the filter — not the largest-connected-component step — can remove them: unfiltered,
 * every excluded endpoint (ids 30..37) would join the single component, so asserting their absence is
 * NON-tautological.
 */
class OverpassFilterTest {

    private val excludedEndpoints = (30L..37L).toSet()

    @Test
    fun excludedWays_areAbsentFromGraph() {
        val graph = OverpassGraph.fromOverpassJson(OsmTestSupport.readResource("filter-fixture.json"))

        // Walkable content survived: shared junction node 3 is present; graph is non-empty and connected.
        assertTrue(graph.nodes.isNotEmpty(), "walkable ways were dropped — graph is empty")
        assertTrue(3L in graph.nodes, "walkable junction node 3 missing")
        assertEquals(1, OsmTestSupport.connectedComponents(graph).size, "walkable ways should form one component")

        // No excluded way's unique endpoint survived as a vertex...
        excludedEndpoints.forEach { id ->
            assertFalse(id in graph.nodes, "excluded node $id leaked into graph.nodes")
        }
        // ...nor as an edge target.
        graph.adjacency.forEach { (from, edges) ->
            edges.forEach { e ->
                assertFalse(e.toNode in excludedEndpoints, "edge $from -> ${e.toNode} to an excluded node leaked")
            }
        }
    }
}
