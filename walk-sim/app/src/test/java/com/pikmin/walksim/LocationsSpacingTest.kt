package com.pikmin.walksim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Preset harvest-route tuning. v1.9 standardized EVERY preset to a 500 m / 10 km sweep (harvest reach 250 m →
 * lane spacing 2×250 = 500 m tiles both sides gap-free; 10 km ≈ a ~2 h session). Decided by the real in-Pikmin
 * big-flower census: re-spacing is a wash on flowers (a wider spiral's bigger disc cancels its gaps), so 500 m
 * only wins walk time. Proof: proofs/walk-sim_acceptance_function_ueno-route-census.md. Pins the values so a
 * typo/regression is caught.
 */
class LocationsSpacingTest {

    @Test
    fun everySweepPreset_is500m10km() {
        val sweeps = PRESET_LOCATIONS.filter { it.flowers.isEmpty() }
        assertEquals(10, sweeps.size, "the ten v1.9 harvest-sweep presets")
        sweeps.forEach {
            assertEquals(500.0, it.spacingM, 1e-9, "${it.label} spacingM")
            assertEquals(10.0, it.routeLengthKm, 1e-9, "${it.label} routeLengthKm")
        }
    }

    /**
     * R2/R3 tour presets are NOT sweeps: lane spacing does not apply and the length is the censused tour's,
     * not the standard 10 km. What must hold is that each carries a real survey and starts on one of its own
     * flower sites (census criterion 3: begin amid the most flowers).
     */
    @Test
    fun everyTourPreset_carriesItsSurveyAndStartsOnAFlower() {
        val tours = PRESET_LOCATIONS.filter { it.flowers.isNotEmpty() }
        assertEquals(2, tours.size, "Haneda + Enoshima")
        tours.forEach {
            assertTrue(it.flowers.size >= 5, "${it.label} survey too small: ${it.flowers.size}")
            assertTrue(it.at in it.flowers, "${it.label} must start on a surveyed flower site")
        }
    }

    @Test
    fun presetDuration_isCorrect_andGuardsNonPositiveSpeed() {
        assertEquals(128L, presetDurationMinutes(10.0, 1.3)) // every preset: 10 km @ 1.3 m/s → 128 min
        assertEquals(77L, presetDurationMinutes(6.0, 1.3))   // formula check at another length
        // A pace of "0" parses to 0.0 (not null) — must NOT yield Infinity/Long.MAX_VALUE; falls back to 1.3.
        assertEquals(presetDurationMinutes(10.0, 1.3), presetDurationMinutes(10.0, 0.0))
        assertEquals(presetDurationMinutes(10.0, 1.3), presetDurationMinutes(10.0, -2.0))
        assertTrue(presetDurationMinutes(20.0, 0.0) in 1L..100_000L) // finite + sane, not Long.MAX_VALUE
    }

    @Test
    fun tuningsAreSane() {
        PRESET_LOCATIONS.forEach {
            assertTrue(it.spacingM in 450.0..1300.0, "${it.label} spacing out of range: ${it.spacingM}")
            assertTrue(it.routeLengthKm in 3.0..25.0, "${it.label} length out of range: ${it.routeLengthKm}")
        }
    }
}
