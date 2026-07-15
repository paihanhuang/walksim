package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph

/** Tries [primary]; on any failure signals [onFallback] and returns [fallback]. Encapsulates WalkService's inline try/fallback. */
class CompositeRoadSource(
    private val primary: RoadSource,
    private val fallback: RoadSource,
    private val onFallback: (Throwable) -> Unit = {},
) : RoadSource {
    override suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph =
        try {
            primary.graphAround(center, radiusM)
        } catch (t: Throwable) {
            onFallback(t)
            fallback.graphAround(center, radiusM)
        }
}
