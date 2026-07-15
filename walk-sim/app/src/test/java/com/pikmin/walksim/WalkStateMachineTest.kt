package com.pikmin.walksim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AC-20: the pure start / pause / resume / stop transition table. */
class WalkStateMachineTest {

    @Test
    fun startsIdle() {
        assertEquals(WalkState.IDLE, WalkStateMachine().state)
    }

    @Test
    fun happyPath_start_pause_resume_stop() {
        val m = WalkStateMachine()
        assertTrue(m.start()); assertTrue(m.isRunning)
        assertTrue(m.pause()); assertTrue(m.isPaused)
        assertTrue(m.resume()); assertTrue(m.isRunning)
        assertTrue(m.stop()); assertTrue(m.isTerminal)
    }

    @Test
    fun illegalTransitionsAreRejectedNoOps() {
        val m = WalkStateMachine()
        assertFalse(m.pause())  // can't pause from IDLE
        assertFalse(m.resume()) // can't resume from IDLE
        assertFalse(m.stop())   // can't stop from IDLE
        assertEquals(WalkState.IDLE, m.state)

        assertTrue(m.start())
        assertFalse(m.start())  // can't start twice
        assertFalse(m.resume()) // can't resume while running
    }

    @Test
    fun stopFromPaused_reachesTerminal() {
        val m = WalkStateMachine()
        m.start(); m.pause()
        assertTrue(m.stop())
        assertTrue(m.isTerminal)
    }

    @Test
    fun complete_isTheNaturalTerminalTransition() {
        val m = WalkStateMachine()
        assertFalse(m.complete()) // not while idle
        m.start()
        assertTrue(m.complete())
        assertTrue(m.isTerminal)
    }

    @Test
    fun terminalAcceptsNothing() {
        val m = WalkStateMachine()
        m.start(); m.stop()
        assertFalse(m.start()); assertFalse(m.pause()); assertFalse(m.resume()); assertFalse(m.complete())
        assertEquals(WalkState.STOPPED, m.state)
    }
}
