package com.pikmin.walksim

import com.pikmin.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure "All areas full-route" planner: each preset gets the time to walk its OWN routeLengthKm route
 * (so each city completes before the next begins), NOT a slice of a shared total.
 */
class FullRoutePlanTest {

    @Test
    fun eachPresetGetsItsFullRouteTime_orderPreserved() {
        val presets = listOf(
            NamedLocation("a", LatLng(0.0, 0.0), routeLengthKm = 10.0),
            NamedLocation("b", LatLng(1.0, 1.0), routeLengthKm = 20.0),
        )
        // 10 km / 2 m/s = 5000 s; 20 km / 2 m/s = 10000 s
        val plan = fullRoutePlan(presets, speedMps = 2.0)
        assertEquals(listOf(5000L, 10000L), plan.map { it.second })
        assertEquals(listOf("a", "b"), plan.map { it.first.label }) // order preserved
    }

    @Test
    fun nonPositiveSpeed_fallsBackTo1point3_notInfinity() {
        val presets = listOf(NamedLocation("a", LatLng(0.0, 0.0), routeLengthKm = 13.0))
        // 13 km at the 1.3 m/s fallback = 13000/1.3 = 10000 s; a "0" or negative speed must NOT divide-by-zero.
        assertEquals(listOf(10000L), fullRoutePlan(presets, speedMps = 0.0).map { it.second })
        assertEquals(listOf(10000L), fullRoutePlan(presets, speedMps = -5.0).map { it.second })
    }

    @Test
    fun emptyPresets_yieldEmptyPlan() {
        assertTrue(fullRoutePlan(emptyList(), speedMps = 1.3).isEmpty())
    }

    @Test
    fun realPresets_areAll500m10km_soEqualPerCitySeconds() {
        val plan = fullRoutePlan(PRESET_LOCATIONS, speedMps = 1.3)
        assertEquals(PRESET_LOCATIONS.size, plan.size)
        val expected = Math.round(10_000.0 / 1.3) // every preset is a 10 km route (v1.9 standardized)
        assertTrue(plan.all { it.second == expected }, "every 10 km city gets the same full-route seconds")
    }
}
