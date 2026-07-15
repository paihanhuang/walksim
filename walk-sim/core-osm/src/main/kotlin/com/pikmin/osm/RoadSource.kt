package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph

/**
 * Supplies a connected, foot-walkable [WalkGraph] covering a bbox around [center].
 *
 * The bbox side is ≈ 2·[radiusM] (coverage only). The circular radius itself is enforced later by the
 * walker in `:core-sim` (`GraphRandomWalker`), NOT here — this source does not clip to a circle.
 */
interface RoadSource {
    suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph
}
