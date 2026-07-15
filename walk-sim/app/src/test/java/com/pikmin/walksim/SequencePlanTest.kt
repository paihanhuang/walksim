package com.pikmin.walksim

import com.pikmin.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure "All areas (sequential)" planner: even split, last segment absorbs the remainder. */
class SequencePlanTest {

    private fun presets(n: Int) = (1..n).map { NamedLocation("p$it", LatLng(it.toDouble(), it.toDouble())) }

    @Test
    fun threePresets_evenTotal_eachGetsAThird() {
        val plan = sequencePlan(presets(3), 36_000L)
        assertEquals(listOf(12_000L, 12_000L, 12_000L), plan.map { it.second })
        assertEquals(listOf("p1", "p2", "p3"), plan.map { it.first.label }) // preserves order
    }

    @Test
    fun remainder_isAbsorbedByTheLastSegment_andSegmentsSumToTotal() {
        val plan = sequencePlan(presets(3), 10L)
        assertEquals(listOf(3L, 3L, 4L), plan.map { it.second })
        assertEquals(10L, plan.sumOf { it.second })
    }

    @Test
    fun singlePreset_getsTheWholeDuration() {
        assertEquals(listOf(3600L), sequencePlan(presets(1), 3600L).map { it.second })
    }

    @Test
    fun emptyPresets_yieldEmptyPlan() {
        assertTrue(sequencePlan(emptyList(), 3600L).isEmpty())
    }

    @Test
    fun zeroDuration_splitsIntoZeroSegments() {
        assertEquals(listOf(0L, 0L, 0L), sequencePlan(presets(3), 0L).map { it.second })
    }

    @Test
    fun realPresets_segmentsAlwaysSumToTotal() {
        for (total in listOf(1L, 59L, 3600L, 3601L, 100_000L)) {
            val plan = sequencePlan(PRESET_LOCATIONS, total)
            assertEquals(PRESET_LOCATIONS.size, plan.size)
            assertEquals(total, plan.sumOf { it.second }, "total=$total")
        }
    }
}
