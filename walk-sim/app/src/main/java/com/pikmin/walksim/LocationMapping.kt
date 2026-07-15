package com.pikmin.walksim

import com.pikmin.model.SimSample

/**
 * The pure, device-independent fields of one mock GNSS fix. Extracted from the Android [android.location.Location]
 * so the [SimSample] → fix mapping (AC-13) is JVM-unit-testable without a device: [LocationInjector] only
 * copies these onto a real `Location`.
 */
data class FixFields(
    val lat: Double,
    val lng: Double,
    val altitudeM: Double,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val verticalAccuracyM: Float,
    val speedAccuracyMps: Float,
    val bearingAccuracyDeg: Float,
    /** Wall-clock emission time (System.currentTimeMillis() at push). */
    val timeMs: Long,
    /** CRITICAL (AC-13): real device uptime at emission — SystemClock.elapsedRealtimeNanos(), never a sim clock. */
    val elapsedRealtimeNanos: Long,
)

/** Pure [SimSample] → [FixFields] mapping. The two clock stamps are injected so the mapping stays testable. */
object LocationMapping {

    // Fixed secondary accuracies — a plausible pedestrian GNSS fix (mirrors the proven spike-s0a injector).
    const val ALTITUDE_M = 30.0
    const val VERTICAL_ACCURACY_M = 3f
    const val SPEED_ACCURACY_MPS = 0.5f
    const val BEARING_ACCURACY_DEG = 5f

    fun fromSample(sample: SimSample, timeMs: Long, elapsedRealtimeNanos: Long): FixFields = FixFields(
        lat = sample.pos.lat,
        lng = sample.pos.lng,
        altitudeM = ALTITUDE_M,
        accuracyM = sample.accuracyM,
        speedMps = sample.speedMps,
        bearingDeg = sample.bearingDeg,
        verticalAccuracyM = VERTICAL_ACCURACY_M,
        speedAccuracyMps = SPEED_ACCURACY_MPS,
        bearingAccuracyDeg = BEARING_ACCURACY_DEG,
        timeMs = timeMs,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
    )
}
