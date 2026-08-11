package com.pikmin.walksim.session

import com.pikmin.model.Edge
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.model.WalkGraph
import com.pikmin.model.WalkProfile
import com.pikmin.osm.RoadSource
import com.pikmin.walksim.PRESET_LOCATIONS
import com.pikmin.walksim.WalkBus
import com.pikmin.walksim.WalkStateMachine
import com.pikmin.walksim.fullRoutePlan
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The fake-sink sequence-matrix (Stage 4 / Task 4.2). Drives [WalkSessionController] against a fake
 * [LocationSink] + fake [RoadSource] that share one ordered `log`, then asserts the exact orchestration
 * order the ACs require: engage → hold → fetch → play → restore. Non-tautological: reordering a single call
 * in the controller flips one of these red (proven in Stage-4 QA by the reorder-bites check).
 */
class WalkSessionControllerTest {

    private val origin = LatLng(35.6595, 139.7006)
    private val home = LatLng(35.6595, 139.7006) // SHIBUYA re-home target
    private val log = mutableListOf<String>()

    @BeforeEach
    fun reset() {
        log.clear()
        WalkBus.mockAppOk.value = true
        WalkBus.setupError.value = null
        WalkBus.sample.value = null
    }

    private inner class FakeSink(val engageOk: Boolean = true) : LocationSink {
        override fun engage(): Boolean { log += "engage"; return engageOk }
        override fun hold(pos: LatLng) { log += "hold(${"%.4f".format(pos.lat)})" }
        override fun push(sample: SimSample) { log += "push" }
        override fun restore() { log += "restore" }
    }

    // Returns a small local lattice centred on the requested pin (as production does: every pin → its own graph),
    // so both the single start and every far preset snap onto walkable road.
    private inner class FakeRoadSource(val boom: Boolean = false) : RoadSource {
        override suspend fun graphAround(center: LatLng, radiusM: Int, extraWalkable: Set<String>): WalkGraph {
            log += "fetch"
            if (boom) throw RuntimeException("net down")
            return gridGraph(center)
        }
    }

    /** A small connected lattice around [at] so [com.pikmin.sim.WalkPlayer] yields a real (tiny) sample stream. */
    private fun gridGraph(at: LatLng, n: Int = 6, stepDeg: Double = 0.0009): WalkGraph {
        val nodes = HashMap<Long, LatLng>()
        val adj = HashMap<Long, MutableList<Edge>>()
        fun id(r: Int, c: Int) = (r * 100 + c).toLong()
        for (r in 0 until n) for (c in 0 until n) nodes[id(r, c)] = LatLng(at.lat + r * stepDeg, at.lng + c * stepDeg)
        fun link(a: Long, b: Long) {
            val ga = listOf(nodes.getValue(a), nodes.getValue(b))
            val len = Geo.haversineMeters(ga[0], ga[1])
            adj.getOrPut(a) { ArrayList() }.add(Edge(b, ga, len))
            adj.getOrPut(b) { ArrayList() }.add(Edge(a, ga.reversed(), len))
        }
        for (r in 0 until n) for (c in 0 until n) {
            if (c + 1 < n) link(id(r, c), id(r, c + 1))
            if (r + 1 < n) link(id(r, c), id(r + 1, c))
        }
        return WalkGraph(nodes, adj)
    }

    private fun controller(sink: LocationSink, roadSource: RoadSource, machine: WalkStateMachine, holdTarget: LatLng? = null) =
        WalkSessionController(roadSource, sink, machine, home, onNotify = {}, holdTarget = { holdTarget })

    private fun spec(mode: Mode, start: LatLng = origin, durationS: Long = 5L) = RunSpec(
        start = start, durationS = durationS, profile = WalkProfile(), seed = 42L, mode = mode,
        laneSpacingM = 500.0, closeLoop = false, radiusOverrideM = null,
    )

    // AC-16: engage the mock BEFORE any graph fetch or hold, so a not-mock-app fault surfaces fast.
    @Test
    fun engageBeforeAnyFetchOrHold() = runTest {
        controller(FakeSink(), FakeRoadSource(), WalkStateMachine().apply { start() }).run(spec(Mode.SINGLE))
        assertEquals("engage", log.first(), "engage must be the very first surface call")
        assertTrue(log.indexOf("engage") < log.indexOf("fetch"), "engage precedes the fetch (AC-16)")
        assertTrue(log.indexOf("engage") < log.indexOfFirst { it.startsWith("hold") }, "engage precedes any hold")
    }

    // AC-12: a hold covers the fetch gap so real GPS never shows before the first route fix.
    @Test
    fun holdBeforeFetchThenPlays() = runTest {
        controller(FakeSink(), FakeRoadSource(), WalkStateMachine().apply { start() }).run(spec(Mode.SINGLE))
        val hold = log.indexOfFirst { it.startsWith("hold") }
        val fetch = log.indexOf("fetch")
        val push = log.indexOf("push")
        assertTrue(hold in 0 until fetch, "hold must cover the fetch gap (AC-12): hold@$hold fetch@$fetch")
        assertTrue(fetch < push, "fetch precedes playback")
        assertTrue(hold < push, "the hold precedes the first pushed sample")
    }

    // AC-15: restore() runs in a finally even when the fetch throws.
    @Test
    fun restoreRunsEvenOnFetchException() = runTest {
        controller(FakeSink(), FakeRoadSource(boom = true), WalkStateMachine().apply { start() }).run(spec(Mode.SINGLE))
        assertEquals("restore", log.last(), "restore must run in finally even on a fetch exception (AC-15)")
        assertFalse(log.contains("push"), "a failed fetch pushes no samples")
        assertNotNull(WalkBus.setupError.value, "the failure raises a setup-error banner")
    }

    // Sequential "All areas": each preset is held before its own full-route fetch, in preset order.
    @Test
    fun sequentialHoldsEachCityBeforeItsSegment() = runTest {
        controller(FakeSink(), FakeRoadSource(), WalkStateMachine().apply { start() }).run(spec(Mode.SEQUENTIAL))
        val plan = fullRoutePlan(PRESET_LOCATIONS, WalkProfile().meanSpeedMps)
        val holds = log.filter { it.startsWith("hold") }
        assertEquals(plan.map { "hold(${"%.4f".format(it.first.at.lat)})" }, holds, "one hold per preset, in order")
        // Each preset interleaves as hold→fetch (hold covers that preset's fetch gap), never batched up front.
        val holdFetch = log.filter { it.startsWith("hold") || it == "fetch" }.map { if (it == "fetch") "fetch" else "hold" }
        assertEquals(plan.flatMap { listOf("hold", "fetch") }, holdFetch, "each preset: hold then its fetch, interleaved")
        assertTrue(log.contains("push"), "segments actually play")
        assertTrue(log.indexOfFirst { it.startsWith("hold") } < log.indexOf("push"), "first hold precedes first push")
    }

    // Hold mode (freeze / census): pins the position at 1 Hz, plays NO route → pace stays not-playing.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // advanceTimeBy / advanceUntilIdle
    @Test
    fun holdModePlaysNoRouteAndPaceNotPlaying() = runTest {
        val m = WalkStateMachine().apply { start() }
        val job = launch { controller(FakeSink(), FakeRoadSource(), m, holdTarget = origin).run(spec(Mode.HOLD)) }
        advanceTimeBy(2500)
        m.stop()
        advanceUntilIdle()
        job.join()
        assertEquals("engage", log.first())
        assertTrue(log.count { it.startsWith("hold") } >= 1, "hold mode pins the position at 1 Hz")
        assertFalse(log.contains("push"), "hold mode plays NO route — zero moving samples (pace stays not-playing)")
        assertFalse(log.contains("fetch"), "hold mode fetches no graph")
        assertEquals("restore", log.last(), "hold mode restores on stop")
    }

    // AC-16: engage() false → raise the banner and do NOT fetch/hold/play.
    @Test
    fun engageFailRaisesBannerNoFetch() = runTest {
        controller(FakeSink(engageOk = false), FakeRoadSource(), WalkStateMachine().apply { start() }).run(spec(Mode.SINGLE))
        assertFalse(WalkBus.mockAppOk.value, "AC-16: mockAppOk=false when not the selected mock app")
        assertFalse(log.contains("fetch"), "no fetch when the mock is not engaged")
        assertFalse(log.any { it.startsWith("hold") }, "no hold when the mock is not engaged")
        assertFalse(log.contains("push"), "no push when the mock is not engaged")
        assertNotNull(WalkBus.setupError.value, "a setup-error banner is raised")
    }
}
