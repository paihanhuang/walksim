package com.pikmin.walksim.ui

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.walksim.WalkState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM proof for the two pure seams the Stage-3 motion layer animates over. The Compose visuals themselves
 * (press-squish, bobbing sprout, petal fill, completion burst) are cosmetic and proven by `@Preview` + the
 * queued on-device visual check; these tests pin only the pure inputs that drive them, so no device is needed.
 */
class PetalMotionTest {

    private fun sampleAtTick(tick: Long) = SimSample(
        pos = LatLng(35.0, 139.0), bearingDeg = 0f, speedMps = 1.3f, accuracyM = 5f,
        stepCount = 0, tickIndex = tick, cumulativeDistanceM = 0.0,
    )

    // --- progressFraction: continuous [0,1] analog of formatHud's integer pct (elapsed = tickIndex + 1) ---

    @Test fun nullSampleIsZero() = assertEquals(0f, progressFraction(null, 3600), 0f)

    @Test fun zeroDurationIsZero() = assertEquals(0f, progressFraction(sampleAtTick(10), 0), 0f)

    @Test fun halfwayIsHalf() =
        assertEquals(0.5f, progressFraction(sampleAtTick(1799), 3600), 1e-4f) // elapsed 1800 / 3600

    @Test fun firstTickIsSmallPositive() {
        val f = progressFraction(sampleAtTick(0), 3600) // elapsed 1 / 3600
        assertTrue(f > 0f && f < 0.01f)
    }

    @Test fun overrunClampsToOne() =
        assertEquals(1f, progressFraction(sampleAtTick(7200), 3600), 0f) // elapsed 7201 > duration → clamp 1

    // --- isWalkCompletion: fires on RUNNING/PAUSED → IDLE (a finished/torn-down walk), nothing else ---

    @Test fun completesFromRunning() = assertTrue(isWalkCompletion(WalkState.RUNNING, WalkState.IDLE))
    @Test fun completesFromPaused() = assertTrue(isWalkCompletion(WalkState.PAUSED, WalkState.IDLE))
    @Test fun noBurstOnStart() = assertFalse(isWalkCompletion(WalkState.IDLE, WalkState.RUNNING))
    @Test fun noBurstOnPause() = assertFalse(isWalkCompletion(WalkState.RUNNING, WalkState.PAUSED))
    @Test fun noBurstWhenAlreadyIdle() = assertFalse(isWalkCompletion(WalkState.IDLE, WalkState.IDLE))
}
