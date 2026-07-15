package com.pikmin.osm

import com.pikmin.model.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** Task 3.1 — [FixtureRoadSource] serves a baked JSON graph offline, ignoring center/radius, parsed once. */
class FixtureRoadSourceTest {

    private val anywhere = LatLng(35.6595, 139.7006)

    @Test
    fun loadsBakedShibuya_connectedNonEmptyGraph() = runTest {
        val json = OsmTestSupport.readResource("shibuya.json")
        val g = FixtureRoadSource { json }.graphAround(anywhere, 800)

        assertTrue(g.nodes.size > 100, "baked Shibuya should be substantial: ${g.nodes.size} nodes")
        assertTrue(g.adjacency.isNotEmpty(), "graph must have edges")
        assertEquals(1, OsmTestSupport.connectedComponents(g).size, "graph must be a single connected component")
    }

    @Test
    fun parsesOnce_thenCaches_ignoringCenterRadius() = runTest {
        val json = OsmTestSupport.readResource("shibuya.json")
        val loads = AtomicInteger(0)
        val src = FixtureRoadSource { loads.incrementAndGet(); json }

        val first = src.graphAround(anywhere, 800)
        val second = src.graphAround(LatLng(0.0, 0.0), 5) // different center/radius → still the same fixed graph

        assertEquals(first, second, "a fixture is a fixed graph — center/radius are ignored")
        assertEquals(1, loads.get(), "the baked JSON must be parsed once, then cached")
    }
}
