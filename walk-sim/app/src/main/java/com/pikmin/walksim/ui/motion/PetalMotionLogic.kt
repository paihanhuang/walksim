package com.pikmin.walksim.ui

import com.pikmin.model.SimSample
import com.pikmin.walksim.WalkState

/**
 * Compose-free pure seams that the Stage-3 motion visuals ([PetalProgress], [CompletionBurst]) animate over.
 * Kept out of `PetalMotion.kt` on purpose: that file is Compose-compiled and cannot be loaded by the plain unit
 * JVM, whereas these functions are the parity-relevant logic and must stay JVM-unit-testable (`PetalMotionTest`).
 */

/**
 * Continuous [0f, 1f] walk progress driving the animated petal fill — the smooth analog of the integer `pct`
 * in [formatHud] (`elapsed = tickIndex + 1`, over [durationS]). Null sample or non-positive duration → 0.
 */
fun progressFraction(sample: SimSample?, durationS: Long): Float {
    if (sample == null || durationS <= 0L) return 0f
    val elapsed = sample.tickIndex + 1
    return (elapsed.toFloat() / durationS.toFloat()).coerceIn(0f, 1f)
}

/**
 * True exactly when a walk has just *finished*: a RUNNING or PAUSED session transitioning to IDLE. This is the
 * only completion signal the UI can see — `WalkBus.status` never becomes STOPPED (both a natural finish and a
 * user STOP tear down through `WalkService.finish()` → IDLE), so this predicate covers both, per the Stage-3 spec.
 */
fun isWalkCompletion(previous: WalkState, next: WalkState): Boolean =
    next == WalkState.IDLE && (previous == WalkState.RUNNING || previous == WalkState.PAUSED)
