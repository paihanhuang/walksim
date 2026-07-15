package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * [RoadSource] backed by Overpass. Fetches once per bbox and caches the built [WalkGraph] IN MEMORY for
 * the process session only — no file/DB cache (T3.6).
 *
 * [fetch] is injectable so the cache is unit-testable without network; production uses [OverpassClient].
 */
class OverpassRoadSource(
    private val fetch: (LatLng, Int) -> String = OverpassClient::fetch,
) : RoadSource {

    private data class Key(val lat: Double, val lng: Double, val radiusM: Int)

    private val cache = ConcurrentHashMap<Key, WalkGraph>()

    override suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph {
        val key = Key(center.lat, center.lng, radiusM)
        cache[key]?.let { return it }
        val graph = withContext(Dispatchers.IO) { OverpassGraph.fromOverpassJson(fetch(center, radiusM)) }
        return cache.getOrPut(key) { graph }
    }
}
