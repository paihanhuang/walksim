package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.model.WalkProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WalkingMotionEngineTest {

    private val origin = LatLng(37.4220, -122.0841)
    private val profile = WalkProfile()

    private fun angDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Independent reference: the densified path's own tangent (segment bearing) at arc-length [cumDist]. */
    private fun tangentAt(path: List<LatLng>, cumDist: Double): Double {
        var base = 0.0
        for (i in 0 until path.size - 1) {
            val len = Geo.haversineMeters(path[i], path[i + 1])
            if (base + len >= cumDist || i == path.size - 2) return Geo.bearingDegrees(path[i], path[i + 1])
            base += len
        }
        return Geo.bearingDegrees(path[path.size - 2], path.last())
    }

    // ---- T12 / AC-8: speed range, bounded acceleration, pauses, determinism ----

    @Test
    fun speed_inRange_andAccelBounded() {
        val path = Fixtures.straightPath(origin, totalM = 2000.0)
        val frames = WalkingMotionEngine.frames(path, profile, durationMs = 600_000, seed = 7L)
        for (f in frames) {
            assertTrue(
                f.modelSpeedMps == 0.0 || f.modelSpeedMps in 0.8 - 1e-9..1.8 + 1e-9,
                "speed out of range at tick ${f.tickIndex}: ${f.modelSpeedMps}",
            )
        }
        // Acceleration ≤0.5 m/s² applies between consecutive *walking* ticks (pause entry/exit is a mode change).
        for (i in 1 until frames.size) {
            val a = frames[i - 1]; val b = frames[i]
            if (!a.paused && !b.paused) {
                assertTrue(
                    abs(b.modelSpeedMps - a.modelSpeedMps) <= 0.5 + 1e-9,
                    "accel exceeded at tick ${b.tickIndex}: ${a.modelSpeedMps} -> ${b.modelSpeedMps}",
                )
            }
        }
    }

    @Test
    fun pausesPresent_inEveryTenMinuteWalk_acrossSeeds() {
        val path = Fixtures.straightPath(origin, totalM = 2000.0)
        for (seed in 1L..6L) {
            val frames = WalkingMotionEngine.frames(path, profile, durationMs = 600_000, seed = seed)
            assertTrue(
                frames.any { it.paused && it.modelSpeedMps == 0.0 },
                "expected ≥1 pause in a 10-min walk for seed $seed",
            )
        }
    }

    @Test
    fun frames_deterministic_andSeedSensitive() {
        val path = Fixtures.straightPath(origin, totalM = 500.0)
        val a = WalkingMotionEngine.frames(path, profile, durationMs = 120_000, seed = 42L)
        val b = WalkingMotionEngine.frames(path, profile, durationMs = 120_000, seed = 42L)
        val c = WalkingMotionEngine.frames(path, profile, durationMs = 120_000, seed = 43L)
        assertEquals(a, b, "same seed must reproduce identical frames")
        assertTrue(a != c, "different seed must change the trajectory")
    }

    // ---- T13 / stepCount invariant: cumulativeDistance / stride within ±5% ----

    @Test
    fun stepCount_matchesDistanceOverStride() {
        val path = Fixtures.straightPath(origin, totalM = 1000.0)
        val noPause = profile.copy(pauseRatePerMin = 0.0)
        // Run long enough to traverse the full 1000 m path; cumDist then clamps to the path length.
        val frames = WalkingMotionEngine.frames(path, noPause, durationMs = 1_300_000, seed = 5L)
        val last = frames.last()
        assertTrue(abs(last.cumDistM - 1000.0) < 1.5, "should reach end of path: ${last.cumDistM}")
        // 1000 m / 0.75 m = 1333 steps; ±5% band [1267, 1400].
        assertTrue(last.stepCount in 1267..1400, "step count out of band: ${last.stepCount}")
        val expected = last.cumDistM / noPause.strideM
        assertTrue(abs(last.stepCount - expected) / expected <= 0.05, "steps not within ±5% of dist/stride")
    }

    // ---- T14 / AC-9: bearing within ±10° of movement direction, speed within ±0.1 of Δdist/Δt ----

    @Test
    fun bearingAndSpeed_consistentWithTrueMotion() {
        val path = Fixtures.gentleCurvePath(origin, totalM = 300.0, turnDegPerStep = 0.1)
        val noPause = profile.copy(pauseRatePerMin = 0.0)
        val frames = WalkingMotionEngine.frames(path, noPause, durationMs = 200_000, seed = 11L)

        for (i in 1 until frames.size) {
            val prev = frames[i - 1]; val cur = frames[i]
            if (cur.cumDistM <= prev.cumDistM + 1e-6) continue // skip non-advancing (end-of-path) ticks
            // Bearing vs an INDEPENDENT reference (the path's own tangent), not the engine's own stored value.
            assertTrue(angDiff(cur.bearingDeg, tangentAt(path, cur.cumDistM)) <= 10.0, "bearing off at tick ${cur.tickIndex}")
            // Reported speed vs the position-derived speed a consumer would compute (chord ≈ arc on smooth motion).
            val chordSpeed = Geo.haversineMeters(prev.truePos, cur.truePos) / 1.0
            assertTrue(abs(cur.modelSpeedMps - chordSpeed) <= 0.1, "speed inconsistent at tick ${cur.tickIndex}")
        }
        // Prove bearing actually tracks the curve (catches a stale/constant-bearing bug the ±10° check alone can't).
        val brgSpan = frames.maxOf { it.bearingDeg } - frames.minOf { it.bearingDeg }
        assertTrue(brgSpan > 5.0, "bearing should rotate along the curve, span=$brgSpan")
    }

    /**
     * Honest characterisation of a sharp (90°) corner under 1 Hz sampling: reported speed is Doppler/arc-rate
     * (stays in walking range), while the position-derived chord speed legitimately dips — a real-GNSS artifact,
     * not a teleport. Bearing tracks BOTH legs through the turn.
     */
    @Test
    fun sharpCorner_speedStaysInRange_bearingTracksBothLegs_chordNeverExceedsArc() {
        val path = Fixtures.cornerPath(origin, legM = 200.0) // east leg (≈90°) then north leg (≈0°)
        val noPause = profile.copy(pauseRatePerMin = 0.0)
        val frames = WalkingMotionEngine.frames(path, noPause, durationMs = 350_000, seed = 4L)

        for (i in 1 until frames.size) {
            val prev = frames[i - 1]; val cur = frames[i]
            assertTrue(cur.modelSpeedMps in 0.8 - 1e-9..1.8 + 1e-9, "reported speed left range at tick ${cur.tickIndex}")
            // Foreshortening only ever reduces position-derived speed; it must never exceed the reported arc-rate.
            val chordSpeed = Geo.haversineMeters(prev.truePos, cur.truePos)
            assertTrue(chordSpeed <= cur.modelSpeedMps + 1e-6, "chord exceeded arc at tick ${cur.tickIndex}")
        }
        val eastLeg = frames.first { it.cumDistM in 50.0..150.0 }
        val northLeg = frames.first { it.cumDistM in 250.0..350.0 }
        assertTrue(angDiff(eastLeg.bearingDeg, 90.0) <= 10.0, "east-leg bearing off: ${eastLeg.bearingDeg}")
        assertTrue(angDiff(northLeg.bearingDeg, 0.0) <= 10.0, "north-leg bearing off: ${northLeg.bearingDeg}")
    }

    // ---- T15 / AC-10 (v1.3 on-road): noise stddev ∈[1,15] (engine tuned to ~1.5), accuracy ∈[5,50], lag-1 autocorr >0.5 ----

    @Test
    fun noise_amplitudeAccuracyAndAutocorrelation() {
        val path = Fixtures.straightPath(origin, totalM = 6000.0)
        val frames = WalkingMotionEngine.frames(path, profile.copy(pauseRatePerMin = 0.0), durationMs = 3_600_000, seed = 3L)
        val off = frames.map { it.offsetEastM }
        val mean = off.average()
        val variance = off.sumOf { (it - mean) * (it - mean) } / off.size
        val stddev = Math.sqrt(variance)
        assertTrue(stddev in 1.0..3.0, "noise stddev out of on-road-tuned [1,3]: $stddev")

        var cov = 0.0
        for (i in 1 until off.size) cov += (off[i] - mean) * (off[i - 1] - mean)
        val autocorr = cov / off.sumOf { (it - mean) * (it - mean) }
        assertTrue(autocorr > 0.5, "lag-1 autocorrelation not >0.5: $autocorr")

        assertTrue(frames.all { it.accuracyM in 5.0..50.0 }, "accuracy out of [5,50]")
        assertTrue(frames.maxOf { it.accuracyM } > frames.minOf { it.accuracyM }, "accuracy should vary with noise")
    }

    // ---- T16 / AC-7: no teleport + Flow<SimSample> assembly ----

    @Test
    fun noTeleport_onEmittedTrajectory() {
        val path = Fixtures.cornerPath(origin, legM = 200.0)
        val frames = WalkingMotionEngine.frames(path, profile, durationMs = 300_000, seed = 9L)
        for (i in 1 until frames.size) {
            val d = Geo.haversineMeters(frames[i - 1].emittedPos, frames[i].emittedPos)
            assertTrue(d <= 4.5 + 1e-6, "teleport at tick ${frames[i].tickIndex}: ${d}m")
        }
    }

    @Test
    fun play_emitsOnePerTick_deterministically_underVirtualTime() = runTest {
        val path = Fixtures.cornerPath(origin, legM = 200.0)
        val a = ArrayList<SimSample>()
        WalkingMotionEngine.play(path, profile, durationMs = 120_000, seed = 21L).collect { a += it }
        assertEquals(120, a.size, "120 s at 1 Hz must yield 120 samples")
        for (i in 1 until a.size) {
            val d = Geo.haversineMeters(a[i - 1].pos, a[i].pos)
            assertTrue(d <= 4.5 + 1e-6, "teleport in emitted stream at $i: ${d}m")
        }
        val b = ArrayList<SimSample>()
        WalkingMotionEngine.play(path, profile, durationMs = 120_000, seed = 21L).collect { b += it }
        assertEquals(a, b, "play() must be deterministic for a fixed seed")
    }
}
