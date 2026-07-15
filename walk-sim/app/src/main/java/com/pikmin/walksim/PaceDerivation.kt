package com.pikmin.walksim

import com.pikmin.model.SimSample

/** The two values [PaceProvider] publishes for the step module. */
data class Pace(val playing: Boolean, val stepsPerMin: Float)

/**
 * Pure [SimSample] → ([playing], [stepsPerMin]) derivation for the pace channel (AC-17), extracted so it
 * is JVM-unit-testable without a device.
 *
 *   playing     := speedMps > 0            (a Poisson pause reports speed 0 → not playing → feed 0 steps)
 *   stepsPerMin := speedMps / strideM * 60 (0 when not playing)
 *
 * A `null` sample (no active walk) is not playing.
 */
object PaceDerivation {

    fun derive(sample: SimSample?, strideM: Double): Pace {
        val speed = sample?.speedMps ?: 0f
        if (speed <= 0f) return Pace(playing = false, stepsPerMin = 0f)
        return Pace(playing = true, stepsPerMin = (speed / strideM * 60.0).toFloat())
    }
}
