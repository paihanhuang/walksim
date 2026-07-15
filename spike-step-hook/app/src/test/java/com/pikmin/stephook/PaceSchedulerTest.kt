package com.pikmin.stephook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pure unit tests for [PaceScheduler] (S5 / T5.1) — fake clock, no device. */
class PaceSchedulerTest {

    /** Advance [clock] by [dtMs] and tick [count] times, summing the pulses. */
    private fun runTicks(rate: Float, dtMs: Long, count: Int): Int {
        var t = 0L
        val sched = PaceScheduler { t }
        var total = 0
        repeat(count) {
            t += dtMs
            total += sched.onTick(playing = true, stepsPerMin = rate)
        }
        return total
    }

    @Test
    fun playingFalse_emitsZero() { // AC-18
        var t = 0L
        val sched = PaceScheduler { t }
        t += 10_000
        assertEquals(0, sched.onTick(playing = false, stepsPerMin = 120f))
        t += 60_000
        assertEquals(0, sched.onTick(playing = false, stepsPerMin = 999f))
    }

    @Test
    fun higherStepsPerMin_emitsProportionallyMorePulses() { // AC-19
        // 10 ticks x 1000 ms = 10 s. 60/min -> 10 pulses; 120/min -> 20 pulses.
        val slow = runTicks(rate = 60f, dtMs = 1000L, count = 10)
        val fast = runTicks(rate = 120f, dtMs = 1000L, count = 10)
        assertEquals(10, slow)
        assertEquals(20, fast)
        assertEquals(2 * slow, fast) // proportional
    }

    @Test
    fun fractionalAccumulation_isLossless() {
        // 30/min at a 500 ms cadence = 0.25 steps/tick: naive per-tick truncation would emit 0 forever.
        // 40 ticks x 500 ms = 20 s -> exactly 30 * 20/60 = 10 pulses, none lost.
        assertEquals(10, runTicks(rate = 30f, dtMs = 500L, count = 40))
    }
}
