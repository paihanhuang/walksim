package com.pikmin.walksim

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** AC-13: the pure SimSample → fix-fields mapping copies motion fields and stamps the injected clocks. */
class LocationMappingTest {

    private val sample = SimSample(
        pos = LatLng(35.6595, 139.7006),
        bearingDeg = 123.5f,
        speedMps = 1.32f,
        accuracyM = 7.5f,
        stepCount = 42,
        tickIndex = 17L,
        cumulativeDistanceM = 21.0,
    )

    @Test
    fun mapsMotionFields_andStampsInjectedClocks() {
        val timeMs = 1_700_000_000_000L
        val elapsedNanos = 987_654_321L
        val f = LocationMapping.fromSample(sample, timeMs, elapsedNanos)

        assertEquals(35.6595, f.lat)
        assertEquals(139.7006, f.lng)
        assertEquals(7.5f, f.accuracyM)
        assertEquals(1.32f, f.speedMps)
        assertEquals(123.5f, f.bearingDeg)
        // Injected clocks are passed straight through (AC-13: real device uptime stamped at emission).
        assertEquals(timeMs, f.timeMs)
        assertEquals(elapsedNanos, f.elapsedRealtimeNanos)
    }

    @Test
    fun setsTheFixedSecondaryAccuracies() {
        val f = LocationMapping.fromSample(sample, 0L, 0L)
        assertEquals(LocationMapping.ALTITUDE_M, f.altitudeM)
        assertEquals(LocationMapping.VERTICAL_ACCURACY_M, f.verticalAccuracyM)
        assertEquals(LocationMapping.SPEED_ACCURACY_MPS, f.speedAccuracyMps)
        assertEquals(LocationMapping.BEARING_ACCURACY_DEG, f.bearingAccuracyDeg)
    }
}
