package com.pikmin.sim

import com.pikmin.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GeoTest {

    // 1 degree along the equator/meridian on the mean-radius sphere.
    private val oneDegM = 6_371_008.8 * Math.PI / 180.0 // ~111_194.9 m

    @Test
    fun haversine_oneDegreeLatitude() {
        val d = Geo.haversineMeters(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        assertTrue(abs(d - oneDegM) < 1.0, "expected ~$oneDegM, got $d")
    }

    @Test
    fun haversine_oneDegreeLongitudeAtEquator() {
        val d = Geo.haversineMeters(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        assertTrue(abs(d - oneDegM) < 1.0, "expected ~$oneDegM, got $d")
    }

    @Test
    fun bearing_cardinalDirections() {
        assertEquals(90.0, Geo.bearingDegrees(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 1e-6)  // east
        assertEquals(0.0, Geo.bearingDegrees(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 1e-6)   // north
    }

    @Test
    fun destinationPoint_roundTripsWithHaversine() {
        val start = LatLng(37.4220, -122.0841)
        val dest = Geo.destinationPoint(start, 42.0, 250.0)
        assertEquals(250.0, Geo.haversineMeters(start, dest), 0.01)
        assertEquals(42.0, Geo.bearingDegrees(start, dest), 0.01)
    }

    @Test
    fun destinationPoint_eastAlongEquator() {
        val dest = Geo.destinationPoint(LatLng(0.0, 0.0), 90.0, oneDegM)
        assertEquals(0.0, dest.lat, 1e-6)
        assertEquals(1.0, dest.lng, 1e-3)
    }
}
