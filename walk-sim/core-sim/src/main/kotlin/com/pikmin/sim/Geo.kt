package com.pikmin.sim

import com.pikmin.model.LatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure spherical-earth geodesy. Accurate to well under a centimetre at walking scale. */
object Geo {
    private const val R = 6_371_008.8 // IUGG mean Earth radius (m)

    /** Great-circle (haversine) distance in metres. */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLng / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(h)))
    }

    /** Initial bearing from [a] to [b], degrees in [0,360). */
    fun bearingDegrees(a: LatLng, b: LatLng): Double {
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Destination point [distM] metres from [from] along [bearingDeg] (haversine direct formula). */
    fun destinationPoint(from: LatLng, bearingDeg: Double, distM: Double): LatLng {
        val d = distM / R
        val brg = Math.toRadians(bearingDeg)
        val la1 = Math.toRadians(from.lat)
        val lo1 = Math.toRadians(from.lng)
        val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(brg))
        val lo2 = lo1 + atan2(sin(brg) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return LatLng(Math.toDegrees(la2), ((Math.toDegrees(lo2) + 540.0) % 360.0) - 180.0)
    }
}
