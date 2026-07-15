package com.pikmin.stephook

/**
 * Pure, clock-injectable pace scheduler (S5 / T5.1). Converts a live pace (steps/min) into the whole
 * number of `STEP_DETECTOR` pulses to emit on each driver tick, accumulating the fractional remainder
 * across ticks so no step is lost to per-tick truncation. No Android/Xposed imports → JVM-unit-testable.
 *
 * [clock] returns a monotonic millisecond timestamp (e.g. `SystemClock.elapsedRealtime`). Emission is
 * time-based (rate x elapsed), so the driver's tick cadence only affects batching granularity, not the
 * step rate.
 *
 *  - `playing == false` → 0 pulses; the parked interval is not counted and any partial step is cleared (AC-18).
 *  - higher `stepsPerMin` → proportionally more pulses over the same wall-clock span (AC-19).
 */
class PaceScheduler(private val clock: () -> Long) {

    private var lastTickMs: Long = clock()
    private var fractionalSteps: Double = 0.0

    /** @return whole `STEP_DETECTOR` pulses to emit at this tick (>= 0). */
    fun onTick(playing: Boolean, stepsPerMin: Float): Int {
        val now = clock()
        val elapsedMs = now - lastTickMs
        lastTickMs = now
        if (!playing) {
            fractionalSteps = 0.0 // parked: clear the partial step, don't count parked time
            return 0
        }
        if (stepsPerMin <= 0f || elapsedMs <= 0L) return 0 // nothing to add; keep the accumulator
        fractionalSteps += stepsPerMin * (elapsedMs / 60_000.0)
        val whole = fractionalSteps.toInt()
        fractionalSteps -= whole
        return whole
    }
}
