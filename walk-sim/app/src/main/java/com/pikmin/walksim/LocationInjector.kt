package com.pikmin.walksim

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.pikmin.model.LatLng
import com.pikmin.model.SimSample

/**
 * Ported from the proven spike-s0a `MockGnssInjector` (Pixel-class device, this OS). Mocks the raw gps +
 * network test providers AND the GMS fused provider in lock-step, so every location surface agrees and
 * mock (AC-12). Without mocking network + fused too, FLP keeps fusing a STALE REAL fix and leaks it as a
 * teleport. The BLE/HealthConnect/baked-RoadPath/A-B-target parts of the spike are dropped.
 *
 * The pure [SimSample] → fix mapping lives in [LocationMapping]; this class only copies [FixFields] onto a
 * real [Location] and drives the platform providers. Blocking Tasks.await calls must run off the main thread.
 *
 * The mock-location gate is the Developer-Options "mock location app" selection; when this app is not
 * selected, addTestProvider throws SecurityException — caught reactively in [start] to raise the AC-16 flag.
 */
class LocationInjector(context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Mock BOTH gps and network so FLP never leaks a stale real network fix (which reads as a teleport).
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    /** @return true if mock mode is engaged; false if this app is not the selected mock-location app (AC-16). */
    @Suppress("DEPRECATION") // multi-arg addTestProvider is the min-SDK-compatible overload
    fun start(): Boolean {
        return try {
            for (p in providers) {
                runCatching { lm.removeTestProvider(p) }
                lm.addTestProvider(
                    p,
                    false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
                )
                lm.setTestProviderEnabled(p, true)
            }
            // Make FLP serve ONLY our mock; without this it leaks a stale real fused fix (teleport).
            runCatching { Tasks.await(fused.setMockMode(true)) }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Not selected as the mock-location app — set it in Developer Options.", e)
            runCatching { restore() }
            false
        }
    }

    /**
     * Push a STATIONARY holding fix at [p] to every mocked surface. Called right after [start], before the
     * (multi-second) Overpass graph fetch: without it the mock providers are enabled but hold no location, so
     * the consumer reads the last REAL GPS fix during the fetch and the avatar visibly jumps to the real
     * location until the first route fix lands (worse for live-fetched cities; baked Shibuya has no fetch gap).
     */
    fun holdAt(p: LatLng) = push(
        SimSample(pos = p, bearingDeg = 0f, speedMps = 0f, accuracyM = 12f, stepCount = 0, tickIndex = 0L, cumulativeDistanceM = 0.0),
    )

    /** Push one fully-populated fix (mapped from [sample]) to gps + network + fused, stamped with the real clock. */
    fun push(sample: SimSample) {
        val fields = LocationMapping.fromSample(
            sample,
            timeMs = System.currentTimeMillis(),
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
        )
        for (p in providers) {
            runCatching { lm.setTestProviderLocation(p, build(p, fields)) }
        }
        // Keep fused in lock-step with gps/network so FLP never serves a stale real fix.
        runCatching { Tasks.await(fused.setMockLocation(build(LocationManager.GPS_PROVIDER, fields))) }
    }

    /** AC-15: remove the test providers and disengage fused mock mode, restoring the real location stack. */
    fun restore() {
        for (p in providers) runCatching { lm.removeTestProvider(p) }
        runCatching { Tasks.await(fused.setMockMode(false)) }
    }

    private fun build(provider: String, f: FixFields): Location =
        Location(provider).apply {
            latitude = f.lat
            longitude = f.lng
            altitude = f.altitudeM
            accuracy = f.accuracyM
            speed = f.speedMps
            bearing = f.bearingDeg
            time = f.timeMs
            elapsedRealtimeNanos = f.elapsedRealtimeNanos
            verticalAccuracyMeters = f.verticalAccuracyM
            speedAccuracyMetersPerSecond = f.speedAccuracyMps
            bearingAccuracyDegrees = f.bearingAccuracyDeg
        }

    private companion object {
        const val TAG = "LocationInjector"
    }
}
