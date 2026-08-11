package com.pikmin.sim

import com.pikmin.model.DensePath
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.model.WalkProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Rich per-tick state. [truePos] is the on-path position; [emittedPos] adds correlated noise and is
 * what a consumer sees. [modelSpeedMps]/[bearingDeg] describe the *true* (Doppler-like) motion, so they
 * stay consistent with the noise-free trajectory (AC-9). [offsetEastM] is the raw east OU noise sample
 * (the per-axis process is symmetric) used to verify amplitude/correlation (AC-10). Module-internal:
 * exposed only for white-box tests; [SimSample] is the public projection.
 */
internal data class MotionFrame(
    val tickIndex: Long,
    val truePos: LatLng,
    val emittedPos: LatLng,
    val modelSpeedMps: Double,
    val bearingDeg: Double,
    val offsetEastM: Double,
    val accuracyM: Double,
    val cumDistM: Double,
    val stepCount: Int,
    val paused: Boolean,
)

/**
 * Tick-indexed, deterministic walking motion: `sample[n] = f(n, seed)`. Integrates a fixed 1 s timestep
 * along a densified path, modelling speed (mean-reverting + Poisson pauses), step count, true bearing/speed,
 * and correlated GPS noise. No wall-clock is read here — the injected clock only stamps emission time later.
 */
object WalkingMotionEngine {

    private const val TICK_MS = 1000L
    private const val DT = TICK_MS / 1000.0                 // fixed timestep, seconds
    /** What a plausible [topSpeedMps] covers in one tick, plus room for a noise excursion. */
    private fun noTeleportBoundM(topSpeedMps: Double) = topSpeedMps * DT + 2.0

    private val MAX_STEP_M = noTeleportBoundM(2.5)          // AC-7 bound at the default profile (4.5 m at 1 Hz)

    /**
     * Per-tick displacement ceiling. The guard exists to stop a NOISE spike looking like a teleport, so it has
     * to sit above what the profile can legitimately cover in one tick — otherwise a fast pace is silently
     * throttled to 4.5 m/s no matter what the user asked for. Never tighter than [MAX_STEP_M], so the default
     * 1.3 m/s profile keeps its exact 4.5 m bound.
     */
    private fun maxStepFor(profile: WalkProfile) =
        maxOf(MAX_STEP_M, noTeleportBoundM(profile.speedRange.endInclusive))

    // Speed: discrete Ornstein–Uhlenbeck pull toward the profile mean.
    private const val SPEED_THETA = 0.3                     // mean-reversion strength per tick
    private const val SPEED_SIGMA = 0.15                    // speed diffusion (m/s per √tick)

    // Pause: each pause lasts a uniform number of ticks in [MIN, MAX].
    private const val PAUSE_MIN_TICKS = 3
    private const val PAUSE_MAX_TICKS = 15

    // Closed-run (runUntilPathEnd) completion: stop within END_EPS_M of the path end; hard-cap ticks at
    // PATH_END_TICK_CAP× the nominal estimate so a pathological pause streak can never spin unbounded.
    private const val END_EPS_M = 0.5
    private const val PATH_END_TICK_CAP = 2L

    // Noise: per-axis OU with high autocorrelation so the offset is smooth (AC-7) yet correlated (AC-10).
    private const val NOISE_PHI = 0.98                      // lag-1 autocorrelation
    private const val NOISE_STAT_STDDEV = 1.5               // stationary per-axis stddev (m): tight on-road snap (Stage 1)
    private val NOISE_SIGMA = NOISE_STAT_STDDEV * sqrt(1.0 - NOISE_PHI * NOISE_PHI)

    private const val M_PER_DEG_LAT = 111_320.0

    /**
     * Public stream: one [SimSample] per tick, paced at 1 Hz (virtual time under `runTest`).
     * [runUntilPathEnd] (closed runs, AC-24e): keep emitting until the whole path is walked — so a closed loop
     * actually reaches its end (the start) despite pauses stealing distance — instead of stopping at [durationMs].
     */
    fun play(path: DensePath, profile: WalkProfile, durationMs: Long, seed: Long, runUntilPathEnd: Boolean = false): Flow<SimSample> = flow {
        for (f in frames(path, profile, durationMs, seed, runUntilPathEnd)) {
            emit(
                SimSample(
                    pos = f.emittedPos,
                    bearingDeg = f.bearingDeg.toFloat(),
                    speedMps = f.modelSpeedMps.toFloat(),
                    accuracyM = f.accuracyM.toFloat(),
                    stepCount = f.stepCount,
                    tickIndex = f.tickIndex,
                    cumulativeDistanceM = f.cumDistM,
                ),
            )
            delay(TICK_MS)
        }
    }

    /** White-box generator: the full per-tick trajectory. Pure function of (path, profile, durationMs, seed). */
    internal fun frames(path: DensePath, profile: WalkProfile, durationMs: Long, seed: Long, runUntilPathEnd: Boolean = false): List<MotionFrame> {
        require(durationMs > 0) { "durationMs must be > 0" }
        val ticks = (durationMs / TICK_MS).toInt()
        if (path.size < 2 || ticks <= 0) return emptyList()
        val maxStepM = maxStepFor(profile) // per-run no-teleport bound (R4: must not throttle a fast pace)

        // Precompute segment lengths/bearings once; advance a monotonic cursor as cumDist grows.
        val segLen = DoubleArray(path.size - 1) { Geo.haversineMeters(path[it], path[it + 1]) }
        val segBrg = DoubleArray(path.size - 1) { Geo.bearingDegrees(path[it], path[it + 1]) }
        val pathArcLen = segLen.sum()

        val rng = Random(seed)
        val pauseProb = profile.pauseRatePerMin * DT / 60.0
        val out = ArrayList<MotionFrame>(ticks)
        // Closed runs run to path-completion; cap ticks well above the pause-inflated need (~+8%) as a backstop.
        val maxTicks = if (runUntilPathEnd) ticks.toLong() * PATH_END_TICK_CAP else ticks.toLong()

        var cumDist = 0.0
        var speed = profile.meanSpeedMps                    // internal walking speed (resets to mean after a pause)
        var pauseTicksLeft = 0
        var offE = 0.0
        var offN = 0.0
        var prevTrue = path[0]
        var prevEmitted = path[0]
        var bearing = segBrg[0]
        var segIdx = 0
        var segBase = 0.0                                   // arc length at the start of segment segIdx

        // Time-driven (n < ticks) by default; distance-driven for closed runs (stop once the path is walked).
        var n = 0
        while (if (runUntilPathEnd) (n < maxTicks && cumDist < pathArcLen - END_EPS_M) else (n < ticks)) {
            // 1) Speed / pause state for this tick.
            val paused: Boolean
            val reportSpeed: Double
            if (pauseTicksLeft > 0) {
                paused = true
                reportSpeed = 0.0
                pauseTicksLeft--
            } else if (rng.nextDouble() < pauseProb) {
                paused = true
                reportSpeed = 0.0
                pauseTicksLeft = PAUSE_MIN_TICKS + rng.nextInt(PAUSE_MAX_TICKS - PAUSE_MIN_TICKS + 1) - 1
                speed = profile.meanSpeedMps               // re-accelerate from the mean when the pause ends
            } else {
                paused = false
                val target = speed + SPEED_THETA * (profile.meanSpeedMps - speed) * DT +
                    SPEED_SIGMA * rng.nextGaussian() * sqrt(DT)
                val maxDv = profile.maxAccelMpsSq * DT
                val dv = (target - speed).coerceIn(-maxDv, maxDv)
                speed = (speed + dv).coerceIn(profile.speedRange.start, profile.speedRange.endInclusive)
                reportSpeed = speed
            }

            // 2) Advance true position along the densified path by reportSpeed·Δt (arc length).
            cumDist = (cumDist + reportSpeed * DT).coerceAtMost(pathArcLen)
            while (segIdx < segLen.size - 1 && segBase + segLen[segIdx] <= cumDist) {
                segBase += segLen[segIdx]; segIdx++
            }
            val along = (cumDist - segBase).coerceIn(0.0, segLen[segIdx])
            val truePos = Geo.destinationPoint(path[segIdx], segBrg[segIdx], along)

            // Bearing tracks the true movement direction; held through pauses / zero-displacement ticks.
            val moved = Geo.haversineMeters(prevTrue, truePos)
            if (moved > 1e-6) bearing = Geo.bearingDegrees(prevTrue, truePos)

            // 3) Correlated horizontal noise (OU per axis) → emitted position.
            offE = NOISE_PHI * offE + NOISE_SIGMA * rng.nextGaussian()
            offN = NOISE_PHI * offN + NOISE_SIGMA * rng.nextGaussian()
            val mPerDegLng = M_PER_DEG_LAT * cos(Math.toRadians(truePos.lat))
            var emitted = LatLng(
                lat = truePos.lat + offN / M_PER_DEG_LAT,
                lng = truePos.lng + offE / mPerDegLng,
            )

            // 4) No-teleport guard (AC-7): clamp emitted displacement to maxStepM.
            if (n > 0) {
                val dEmit = Geo.haversineMeters(prevEmitted, emitted)
                if (dEmit > maxStepM) {
                    emitted = Geo.destinationPoint(prevEmitted, Geo.bearingDegrees(prevEmitted, emitted), maxStepM)
                }
            }

            val offsetMag = sqrt(offE * offE + offN * offN)
            val accuracy = (5.0 + offsetMag * 1.5).coerceIn(5.0, 50.0)
            val steps = Math.round(cumDist / profile.strideM).toInt()

            out += MotionFrame(
                tickIndex = n.toLong(),
                truePos = truePos,
                emittedPos = emitted,
                modelSpeedMps = reportSpeed,
                bearingDeg = bearing,
                offsetEastM = offE,
                accuracyM = accuracy,
                cumDistM = cumDist,
                stepCount = steps,
                paused = paused,
            )
            prevTrue = truePos
            prevEmitted = emitted
            n++
        }
        return out
    }

    /** Standard-normal sample via Box–Muller (deterministic given a seeded [Random]). */
    private fun Random.nextGaussian(): Double {
        val u1 = nextDouble().coerceIn(1e-12, 1.0)
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
