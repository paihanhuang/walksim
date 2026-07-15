package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A [RoadSource] backed by a baked JSON graph (offline / fallback / tests). The fixed graph ignores center/radius. */
class FixtureRoadSource(private val load: (LatLng, Int) -> String) : RoadSource {
    constructor(load: () -> String) : this({ _, _ -> load() })

    private var cached: WalkGraph? = null

    override suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph =
        cached ?: withContext(Dispatchers.IO) { OverpassGraph.fromOverpassJson(load(center, radiusM)) }.also { cached = it }
}
