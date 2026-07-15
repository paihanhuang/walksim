package com.pikmin.walksim

import com.pikmin.model.SimSample
import com.pikmin.model.WalkProfile
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-process coordination between [WalkService] (producer), [MainActivity] (HUD/banners) and
 * [PaceProvider] (pace channel). All three live in the app process, so plain shared state is enough;
 * the cross-process hop to Pikmin's :stephook is over the exported [PaceProvider] only.
 */
object WalkBus {
    /** Latest injected fix — drives the HUD and the pace derivation. `null` when no walk is active. */
    val sample = MutableStateFlow<SimSample?>(null)

    /** Playback status for enabling/disabling the UI controls. */
    val status = MutableStateFlow(WalkState.IDLE)

    /** AC-16: false once injection hit a SecurityException (app not selected as the mock-location app). */
    val mockAppOk = MutableStateFlow(true)

    /** AC-23: a human-readable setup error naming the missing prerequisite, or `null`. */
    val setupError = MutableStateFlow<String?>(null)

    /** Total requested walk duration, seconds — HUD remaining/progress math. */
    @Volatile var durationS: Long = 0L

    /** Stride used for the steps-per-minute derivation (the active profile's stride). */
    @Volatile var strideM: Double = WalkProfile().strideM

    /** AC-23 presence detection: SystemClock.elapsedRealtime() of the last [PaceProvider] query. */
    @Volatile var lastQueriedElapsedMs: Long = 0L

    /** Reset to the idle baseline at the end of a walk. */
    fun clear() {
        sample.value = null
        durationS = 0L
    }
}
