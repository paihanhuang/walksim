package com.pikmin.walksim.ui

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.walksim.PRESET_LOCATIONS
import com.pikmin.walksim.WalkState
import com.pikmin.walksim.presetDurationMinutes

/**
 * Pure, JVM-testable UI logic lifted verbatim out of the old imperative [com.pikmin.walksim.MainActivity]
 * View screen (Plan B, Stage 1). The Compose renderer ([WalkScreen]) and the Android glue in `MainActivity`
 * are thin shells over these functions; [WalkUiLogicTest] pins each one against the original behavior so the
 * screen swap is provably behavior-preserving without a device. No Android or Compose imports live here.
 */

/** Enabled-state of the four transport controls. Mirrors `MainActivity.renderControls`. */
data class Controls(val start: Boolean, val pause: Boolean, val resume: Boolean, val stop: Boolean)

/** START-only in IDLE; PAUSE+STOP while RUNNING; RESUME+STOP while PAUSED; nothing when STOPPED. */
fun controlsFor(state: WalkState): Controls = Controls(
    start = state == WalkState.IDLE,
    pause = state == WalkState.RUNNING,
    resume = state == WalkState.PAUSED,
    stop = state == WalkState.RUNNING || state == WalkState.PAUSED,
)

/**
 * Decoded meaning of a START press — the same two branches the old onClick built into the WalkService intent.
 * [Sequential] ("All areas", position 0) carries only duration+speed (→ `EXTRA_SEQUENTIAL="1"`); [Single]
 * (a chosen preset) additionally carries the start pin (→ `EXTRA_LAT`/`EXTRA_LNG`) and the preset's tuned lane
 * spacing (→ `EXTRA_SPACING_STR`). `MainActivity` turns this into the real Intent (the only Android glue).
 */
sealed interface StartSpec {
    val durationS: Long
    val speedMps: Double

    data class Sequential(override val durationS: Long, override val speedMps: Double) : StartSpec
    data class Single(
        override val durationS: Long,
        override val speedMps: Double,
        val start: LatLng,
        val spacingM: Double,
    ) : StartSpec
}

/**
 * Mirror of the START onClick (MainActivity.kt:177-196): duration is [durationMin]*60 seconds (the old
 * `minutes * 60`). Position 0 → sequential; otherwise the single preset at `PRESET_LOCATIONS[position-1]`,
 * carrying the current [startPin] and that preset's `spacingM`.
 */
fun startSpec(selectedPosition: Int, startPin: LatLng, durationMin: Long, speedMps: Double): StartSpec {
    val durationS = durationMin * 60
    if (selectedPosition == 0) return StartSpec.Sequential(durationS, speedMps)
    val preset = PRESET_LOCATIONS[selectedPosition - 1]
    return StartSpec.Single(durationS, speedMps, startPin, preset.spacingM)
}

/**
 * Mirror of the spinner listener's duration write (MainActivity.kt:162-165): selecting a single preset
 * (position>=1) yields `presetDurationMinutes(routeLengthKm, speed)`; "All areas" (position 0) yields `null`
 * (it keeps its own total duration and never overwrites the field).
 */
fun durationForSelection(selectedPosition: Int, speedMps: Double): Long? {
    if (selectedPosition == 0) return null
    return presetDurationMinutes(PRESET_LOCATIONS[selectedPosition - 1].routeLengthKm, speedMps)
}

/** The two HUD lines. Mirrors the single `\n`-joined TextView the old `renderHud` produced. */
data class Hud(val line1: String, val line2: String)

/**
 * Mirror of `renderHud` + `mmss` (MainActivity.kt:221-230, 247). A `null` sample renders the idle HUD
 * ("idle"), matching the old screen where `renderHud` no-ops on null and `renderControls` shows "idle" in IDLE.
 */
fun formatHud(sample: SimSample?, durationS: Long): Hud {
    if (sample == null) return Hud("idle", "")
    val elapsed = sample.tickIndex + 1
    val remaining = (durationS - elapsed).coerceAtLeast(0)
    val pct = if (durationS > 0) (elapsed * 100 / durationS).coerceAtMost(100) else 0
    return Hud(
        "speed %.2f m/s   distance %.0f m   steps %d".format(sample.speedMps, sample.cumulativeDistanceM, sample.stepCount),
        "elapsed %s   remaining %s   progress %d%%".format(mmss(elapsed), mmss(remaining), pct),
    )
}

private fun mmss(totalS: Long): String = "%02d:%02d".format(totalS / 60, totalS % 60)

/**
 * Mirror of `refreshBanner`'s priority (MainActivity.kt:232-240): the AC-16 not-mock-app warning wins over
 * the AC-23 [setupError], which wins over no banner (`null`).
 */
fun bannerText(mockAppOk: Boolean, setupError: String?): String? = when {
    !mockAppOk -> "Not the selected mock-location app. Developer Options → Select mock location app → WalkSim."
    setupError != null -> setupError
    else -> null
}
