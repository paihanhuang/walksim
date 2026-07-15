package com.pikmin.walksim

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AC-17: the pure SimSample → (playing, stepsPerMin) derivation. */
class PaceDerivationTest {

    private fun sample(speed: Float) = SimSample(
        pos = LatLng(0.0, 0.0), bearingDeg = 0f, speedMps = speed, accuracyM = 5f,
        stepCount = 0, tickIndex = 0L, cumulativeDistanceM = 0.0,
    )

    @Test
    fun nullSample_isNotPlaying() {
        val p = PaceDerivation.derive(null, 0.75)
        assertFalse(p.playing)
        assertEquals(0f, p.stepsPerMin)
    }

    @Test
    fun zeroSpeed_isNotPlaying() {
        val p = PaceDerivation.derive(sample(0f), 0.75)
        assertFalse(p.playing)
        assertEquals(0f, p.stepsPerMin)
    }

    @Test
    fun movingSpeed_derivesStepsPerMinFromStride() {
        // 1.5 m/s ÷ 0.75 m stride × 60 = 120 steps/min.
        val p = PaceDerivation.derive(sample(1.5f), 0.75)
        assertTrue(p.playing)
        assertEquals(120f, p.stepsPerMin, 1e-3f)
    }

    @Test
    fun stepsPerMinScalesWithStride() {
        // Same 1.5 m/s over a longer 1.0 m stride = 90 steps/min.
        val p = PaceDerivation.derive(sample(1.5f), 1.0)
        assertEquals(90f, p.stepsPerMin, 1e-3f)
    }
}
