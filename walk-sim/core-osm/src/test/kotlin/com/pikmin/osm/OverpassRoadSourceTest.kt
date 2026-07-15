package com.pikmin.osm

import com.pikmin.model.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** T3.6 — OverpassRoadSource caches bbox→WalkGraph in memory (no re-fetch for the same bbox; no file/DB). */
class OverpassRoadSourceTest {

    @Test
    fun graphAround_cachesPerBbox() = runTest {
        val calls = AtomicInteger(0)
        val json = OsmTestSupport.readResource("connectivity-fixture.json")
        val source = OverpassRoadSource(fetch = { _, _ -> calls.incrementAndGet(); json })
        val center = LatLng(35.6595, 139.7006)

        val first = source.graphAround(center, 800)
        val second = source.graphAround(center, 800)

        assertTrue(first.nodes.isNotEmpty(), "graph should be built from the fetched JSON")
        assertEquals(1, calls.get(), "second call for the same bbox must hit the cache, not re-fetch")
        assertEquals(first, second, "cached graph should be returned unchanged")

        source.graphAround(center, 400) // different radius → different bbox → one more fetch
        assertEquals(2, calls.get(), "a different bbox must trigger a fetch")
    }
}
