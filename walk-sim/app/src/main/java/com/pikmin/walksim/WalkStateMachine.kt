package com.pikmin.walksim

/** Playback lifecycle of a walk. */
enum class WalkState { IDLE, RUNNING, PAUSED, STOPPED }

/**
 * Pure start/pause/resume/stop state machine for [WalkService] (AC-20), extracted so the transition
 * table is JVM-unit-testable without a device. Each mutator returns whether the transition was legal;
 * illegal transitions are no-ops. Terminal [WalkState.STOPPED] is reached by [stop] (user/error) or by
 * [complete] (the walk duration elapsed) and accepts no further transitions.
 *
 * Mutated from the service's onStartCommand (main thread) and read by the off-Main collection loop, so
 * the state is @Volatile and the mutators are synchronized.
 */
class WalkStateMachine {

    @Volatile
    var state: WalkState = WalkState.IDLE
        private set

    @Synchronized
    fun start(): Boolean = transitionIf(WalkState.IDLE, WalkState.RUNNING)

    @Synchronized
    fun pause(): Boolean = transitionIf(WalkState.RUNNING, WalkState.PAUSED)

    @Synchronized
    fun resume(): Boolean = transitionIf(WalkState.PAUSED, WalkState.RUNNING)

    /** User-/error-initiated stop: legal from RUNNING or PAUSED. */
    @Synchronized
    fun stop(): Boolean = transitionFromActive()

    /** Natural end when the duration elapses: same terminal transition as [stop]. */
    @Synchronized
    fun complete(): Boolean = transitionFromActive()

    val isRunning: Boolean get() = state == WalkState.RUNNING
    val isPaused: Boolean get() = state == WalkState.PAUSED
    val isTerminal: Boolean get() = state == WalkState.STOPPED

    private fun transitionIf(from: WalkState, to: WalkState): Boolean {
        if (state != from) return false
        state = to
        return true
    }

    private fun transitionFromActive(): Boolean {
        if (state != WalkState.RUNNING && state != WalkState.PAUSED) return false
        state = WalkState.STOPPED
        return true
    }
}
