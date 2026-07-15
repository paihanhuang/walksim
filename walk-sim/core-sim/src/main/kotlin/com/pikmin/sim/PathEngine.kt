package com.pikmin.sim

import com.pikmin.model.DensePath
import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route

/** Densifies a route polyline to ~[spacingM] resolution via geodesic interpolation. */
object PathEngine {

    fun densify(route: Route, spacingM: Double): DensePath {
        require(spacingM > 0) { "spacingM must be > 0" }
        val pts = route.points
        if (pts.size < 2) return pts.toList()

        val out = ArrayList<LatLng>()
        out += pts.first()
        var leftover = 0.0 // distance already covered toward the next emitted point
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val segLen = Geo.haversineMeters(a, b)
            if (segLen <= 0.0) continue
            val brg = Geo.bearingDegrees(a, b)
            var along = spacingM - leftover
            while (along < segLen) {
                out += Geo.destinationPoint(a, brg, along)
                along += spacingM
            }
            leftover = segLen - (along - spacingM)
            if (leftover >= spacingM) leftover -= spacingM
        }
        if (out.last() != pts.last()) out += pts.last()
        return out
    }
}
