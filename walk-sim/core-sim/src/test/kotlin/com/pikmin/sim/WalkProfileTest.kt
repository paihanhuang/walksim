package com.pikmin.sim

import com.pikmin.model.WalkProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WalkProfileTest {

    // AC-1 — the configured default mean walking speed is 1.3 m/s.
    @Test
    fun defaultMeanSpeedIs1_3() {
        assertEquals(1.3, WalkProfile().meanSpeedMps, 0.0)
    }
}
