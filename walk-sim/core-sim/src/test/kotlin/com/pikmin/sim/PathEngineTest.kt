package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PathEngineTest {

    @Test
    fun densify_straightSegment_hasUniformSpacing() {
        val start = LatLng(37.4220, -122.0841)
        val end = Geo.destinationPoint(start, 90.0, 100.0) // 100 m east
        val pts = PathEngine.densify(Route(listOf(start, end), 100.0), spacingM = 10.0)

        // ~11 points (0,10,...,100); endpoints preserved.
        assertTrue(pts.size in 10..12, "got ${pts.size} points")
        assertTrue(pts.first() == start && pts.last() == end)

        // Interior gaps ~10 m.
        for (i in 0 until pts.size - 2) {
            val gap = Geo.haversineMeters(pts[i], pts[i + 1])
            assertTrue(abs(gap - 10.0) < 0.5, "gap $i = $gap")
        }
    }

    @Test
    fun densify_preservesTotalLength() {
        val start = LatLng(0.0, 0.0)
        val mid = Geo.destinationPoint(start, 90.0, 60.0)
        val end = Geo.destinationPoint(mid, 0.0, 80.0) // L-shape, total 140 m
        val pts = PathEngine.densify(Route(listOf(start, mid, end), 140.0), spacingM = 5.0)
        var total = 0.0
        for (i in 0 until pts.size - 1) total += Geo.haversineMeters(pts[i], pts[i + 1])
        assertTrue(abs(total - 140.0) < 1.0, "total = $total")
    }
}
