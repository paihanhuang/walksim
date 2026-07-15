package com.pikmin.walksim.ui

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.walksim.WalkState

/**
 * Immutable snapshot of everything [WalkScreen] renders — the `WalkBus` flow values plus the local picker
 * state — hoisted out of `MainActivity` so the composable stays stateless (Plan B, Stage 1).
 *
 * @param status        playback state (from `WalkBus.status`) → control enablement via [controlsFor].
 * @param sample        latest injected fix (from `WalkBus.sample`) → the HUD via [formatHud].
 * @param durationS     requested walk seconds (read from `WalkBus.durationS`) → HUD remaining/progress math.
 * @param mockAppOk     AC-16 flag (from `WalkBus.mockAppOk`) → banner via [bannerText].
 * @param setupError    AC-23 message (from `WalkBus.setupError`) → banner via [bannerText].
 * @param selectedPosition dropdown index: 0 = "All areas (sequential)", 1.. = `PRESET_LOCATIONS[index-1]`.
 * @param startPin      current start-pin coordinate (moved by preset select; map drag/tap comes in Stage 2).
 * @param durationMin   the duration field's raw text (parsed to Long at START).
 * @param paceMps       the pace field's raw text (parsed to Double at START / on preset select).
 * @param permissionHint transient START-without-permission banner; overrides [bannerText] while set.
 */
data class WalkViewState(
    val status: WalkState,
    val sample: SimSample?,
    val durationS: Long,
    val mockAppOk: Boolean,
    val setupError: String?,
    val selectedPosition: Int,
    val startPin: LatLng,
    val durationMin: String,
    val paceMps: String,
    val permissionHint: String?,
)
