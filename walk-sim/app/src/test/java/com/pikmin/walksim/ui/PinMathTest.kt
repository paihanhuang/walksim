package com.pikmin.walksim.ui

import com.pikmin.model.LatLng
import com.pikmin.walksim.PRESET_LOCATIONS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Parity proof for the pure pin-selection seam behind the Stage-2 osmdroid map. Both functions mirror the old
 * imperative `MainActivity` map glue so the Compose [WalkMap] can stay dumb: [presetCenter] reproduces the
 * `wireLocationSpinner` preset lookup (position 0 → first preset; position N → `PRESET_LOCATIONS[N-1]`) and
 * [pickedStart] reproduces `setStart`'s `LatLng(p.latitude, p.longitude)` tap/drag passthrough.
 */
class PinMathTest {

    // --- presetCenter mirrors wireLocationSpinner's preset lookup (old MainActivity.kt:158) ---
    @Test
    fun presetCenterZeroIsFirstPreset() {
        // Position 0 = "All areas (sequential)" → centres on the first preset, keeping its own duration.
        assertEquals(PRESET_LOCATIONS.first().at, presetCenter(0))
    }

    @Test
    fun presetCenterOneIsFirstPreset() {
        // Position 1 → PRESET_LOCATIONS[0] (the "-1" index shift) — same coordinate as position 0 here.
        assertEquals(PRESET_LOCATIONS[0].at, presetCenter(1))
    }

    @Test
    fun presetCenterAppliesMinusOneShift() {
        // Position N → PRESET_LOCATIONS[N-1]: proves the shift is real (not always the first preset).
        assertEquals(PRESET_LOCATIONS[2].at, presetCenter(3))
    }

    // --- pickedStart mirrors setStart's coordinate build (old MainActivity.kt:141) ---
    @Test
    fun pickedStartPassesTapThrough() {
        assertEquals(LatLng(35.6595, 139.7006), pickedStart(35.6595, 139.7006))
    }
}
