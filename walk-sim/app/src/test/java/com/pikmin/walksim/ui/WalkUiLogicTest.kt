package com.pikmin.walksim.ui

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.walksim.PRESET_LOCATIONS
import com.pikmin.walksim.WalkState
import com.pikmin.walksim.presetDurationMinutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Parity proof for the pure UI logic extracted out of [com.pikmin.walksim.MainActivity]. Each test pins the
 * EXACT behavior of the old imperative View screen so the Compose renderer can be swapped in without any
 * behavioral drift. `file:line` anchors below point at the original `MainActivity` code each function mirrors.
 */
class WalkUiLogicTest {

    // --- controlsFor mirrors MainActivity.renderControls (MainActivity.kt:213-217) ---
    @Test
    fun controlsPerState() {
        assertEquals(Controls(start = true, pause = false, resume = false, stop = false), controlsFor(WalkState.IDLE))
        assertEquals(Controls(start = false, pause = true, resume = false, stop = true), controlsFor(WalkState.RUNNING))
        assertEquals(Controls(start = false, pause = false, resume = true, stop = true), controlsFor(WalkState.PAUSED))
        assertEquals(Controls(start = false, pause = false, resume = false, stop = false), controlsFor(WalkState.STOPPED))
    }

    // --- startSpec mirrors the START onClick extras (MainActivity.kt:177-196) ---
    @Test
    fun sequentialWhenPositionZero() {
        // Position 0 = "All areas": EXTRA_SEQUENTIAL="1" + duration + speed; NO lat/lng/spacing. durationS = min*60.
        val s = startSpec(selectedPosition = 0, startPin = LatLng(35.0, 139.0), durationMin = 60, speedMps = 1.3)
        assertEquals(StartSpec.Sequential(durationS = 3600L, speedMps = 1.3), s)
    }

    @Test
    fun singleCarriesPinAndPresetSpacing() {
        // Position>=1 = single preset: EXTRA_LAT/LNG from the pin + EXTRA_SPACING_STR from the preset's spacingM.
        val pin = LatLng(35.6595, 139.7006)
        val s = startSpec(selectedPosition = 1, startPin = pin, durationMin = 60, speedMps = 1.3) as StartSpec.Single
        assertEquals(3600L, s.durationS)
        assertEquals(1.3, s.speedMps)
        assertEquals(pin, s.start)
        assertEquals(PRESET_LOCATIONS[0].spacingM, s.spacingM) // Shibuya preset lane spacing (500 m)
    }

    @Test
    fun durationConvertsMinutesToSeconds() {
        // The old onClick put `minutes * 60` into EXTRA_DURATION_S; startSpec does the same conversion.
        assertEquals(
            StartSpec.Sequential(durationS = 600L, speedMps = 1.3),
            startSpec(selectedPosition = 0, startPin = LatLng(0.0, 0.0), durationMin = 10, speedMps = 1.3),
        )
    }

    // --- durationForSelection mirrors the spinner listener (MainActivity.kt:162-165) ---
    @Test
    fun presetSelectionSetsDuration() {
        assertNull(durationForSelection(selectedPosition = 0, speedMps = 1.3)) // "All areas" keeps its own duration
        assertEquals(
            presetDurationMinutes(PRESET_LOCATIONS[0].routeLengthKm, 1.3),
            durationForSelection(selectedPosition = 1, speedMps = 1.3),
        )
    }

    // --- formatHud mirrors MainActivity.renderHud + mmss (MainActivity.kt:221-230, 247) ---
    @Test
    fun hudFormatsSampleLikeRenderHud() {
        val sample = SimSample(
            pos = LatLng(35.0, 139.0), bearingDeg = 0f, speedMps = 1.3f, accuracyM = 5f,
            stepCount = 100, tickIndex = 59, cumulativeDistanceM = 78.0,
        )
        val hud = formatHud(sample, durationS = 3600L)
        // elapsed = tickIndex+1 = 60; remaining = 3600-60 = 3540; pct = 60*100/3600 = 1
        assertEquals("speed 1.30 m/s   distance 78 m   steps 100", hud.line1)
        assertEquals("elapsed 01:00   remaining 59:00   progress 1%", hud.line2)
    }

    @Test
    fun hudClampsRemainingAndProgress() {
        val sample = SimSample(
            pos = LatLng(0.0, 0.0), bearingDeg = 0f, speedMps = 0f, accuracyM = 0f,
            stepCount = 0, tickIndex = 5000, cumulativeDistanceM = 0.0,
        )
        // elapsed = 5001 > duration → remaining coerced to 0, pct coerced to 100
        assertEquals("elapsed 83:21   remaining 00:00   progress 100%", formatHud(sample, durationS = 3600L).line2)
    }

    @Test
    fun hudZeroDurationProgressIsZero() {
        val sample = SimSample(
            pos = LatLng(0.0, 0.0), bearingDeg = 0f, speedMps = 0f, accuracyM = 0f,
            stepCount = 0, tickIndex = 0, cumulativeDistanceM = 0.0,
        )
        // durationS == 0 → the pct branch guards divide-by-zero → 0%
        assertEquals("elapsed 00:01   remaining 00:00   progress 0%", formatHud(sample, durationS = 0L).line2)
    }

    @Test
    fun hudNullSampleIsIdle() {
        // renderHud no-ops on a null sample; the idle screen shows "idle" (renderControls sets it on IDLE).
        assertEquals(Hud("idle", ""), formatHud(null, durationS = 0L))
    }

    // --- bannerText mirrors MainActivity.refreshBanner priority (MainActivity.kt:232-240) ---
    @Test
    fun bannerPriority() {
        assertTrue(bannerText(mockAppOk = false, setupError = null)!!.contains("mock"))
        assertTrue(bannerText(mockAppOk = false, setupError = "boom")!!.contains("mock")) // mock-app takes priority
        assertEquals("boom", bannerText(mockAppOk = true, setupError = "boom"))
        assertNull(bannerText(mockAppOk = true, setupError = null))
    }
}
