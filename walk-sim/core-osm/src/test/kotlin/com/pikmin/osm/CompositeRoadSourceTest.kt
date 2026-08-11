package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Task 3.2 — [CompositeRoadSource] tries primary; on ANY throw it signals [onFallback] and serves the fallback. */
class CompositeRoadSourceTest {

    private val g1 = WalkGraph(mapOf(1L to LatLng(0.0, 0.0)), emptyMap())
    private val g2 = WalkGraph(mapOf(2L to LatLng(1.0, 1.0)), emptyMap())
    private fun src(g: WalkGraph) = object : RoadSource {
        override suspend fun graphAround(center: LatLng, radiusM: Int, extraWalkable: Set<String>) = g
    }
    private fun failing() = object : RoadSource {
        override suspend fun graphAround(center: LatLng, radiusM: Int, extraWalkable: Set<String>): WalkGraph = throw java.io.IOException("net")
    }

    @Test
    fun primaryOk_returnsPrimary_noFallbackSignal() = runTest {
        var banner: String? = null // models WalkService's setupError banner
        val out = CompositeRoadSource(src(g1), src(g2), onFallback = { banner = "offline" })
            .graphAround(LatLng(0.0, 0.0), 800)

        assertEquals(g1, out, "a live fetch must be used as-is")
        assertNull(banner, "no banner is raised when the primary succeeds")
    }

    @Test
    fun primaryFails_signalsBanner_returnsFallback() = runTest {
        var banner: String? = null
        val out = CompositeRoadSource(failing(), src(g2), onFallback = { banner = "offline" })
            .graphAround(LatLng(0.0, 0.0), 800)

        assertEquals(g2, out, "on failure the fallback graph must be served")
        assertEquals("offline", banner, "fallback must raise the banner — no silent wrong-city")
    }
}
