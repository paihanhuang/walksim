package com.pikmin.osm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T3.4 (AC-4, connectivity) — stitch by OSM node id + keep only the largest connected component.
 *
 * Fixture: two walkable ways sharing node id 3 (→ one component; intermediate nodes 2,4 are geometry-only,
 * so vertices = {1,3,5}) plus a disjoint walkable way {100,101}. The result must be a single connected
 * component and the disjoint way must be dropped.
 */
class OverpassGraphBuildTest {

    @Test
    fun sharedNodeStitches_andDisjointWayIsDropped() {
        val graph = OverpassGraph.fromOverpassJson(OsmTestSupport.readResource("connectivity-fixture.json"))

        // Stitched into exactly one connected component.
        assertEquals(1, OsmTestSupport.connectedComponents(graph).size, "expected one connected component")

        // The shared-node component survived with its intersection vertices.
        assertTrue(1L in graph.nodes && 3L in graph.nodes && 5L in graph.nodes, "main component vertices missing")
        // Node 3 really is the stitch point: it reaches both ways' endpoints and nothing else.
        assertEquals(setOf(1L, 5L), graph.adjacency.getValue(3L).map { it.toNode }.toSet(), "node 3 not stitched to both ways")

        // The disjoint way was dropped as the smaller component.
        assertFalse(100L in graph.nodes || 101L in graph.nodes, "disjoint way was not dropped")
    }
}
