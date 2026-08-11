package com.pikmin.osm

import com.pikmin.model.LatLng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * One-time LIVE Overpass fetch that bakes the offline fixtures for the R2/R3 flower-tour presets, so
 * `TourPresetReachTest` can gate the "passes every surveyed site within 250 m" criterion OFFLINE (it was
 * previously provable only by the network-gated, normally-skipped `FlowerRouteDiagnostic`). Normally SKIPPED;
 * run explicitly to (re)create the fixtures:
 *
 *   BAKE_TOURS=true ./gradlew :core-osm:test --tests "*TourFixtureBaker*" --rerun-tasks
 *
 * Centre/radius match what the app actually requests for each preset (`flowerFetchRadiusM`), so the fixture is
 * the same disc production sees. Unlike [ShibuyaFixtureBaker] the payload is PRUNED — kept to ways whose
 * `highway` is walkable (street classes + the foot-only classes a tour opts into) and to the three tags the
 * graph builder reads — which halves it (Haneda 4.8 → 2.1 MB) without changing the built graph at all.
 */
class TourFixtureBaker {

    private val keptHighways = OverpassGraph.WALKABLE + OverpassGraph.FOOT_ONLY_WAYS
    private val keptTags = setOf("highway", "foot", "access")

    @Test
    @EnabledIfEnvironmentVariable(named = "BAKE_TOURS", matches = "true")
    fun bakeTourFixtures() {
        bake("haneda", LatLng(35.5449, 139.7699), 3391)
        bake("enoshima", LatLng(35.3095, 139.4838), 2097)
    }

    private fun bake(name: String, center: LatLng, radiusM: Int) {
        val pruned = prune(OverpassClient.fetch(center, radiusM))
        val out = File("src/test/resources/$name.json")
        out.parentFile.mkdirs()
        out.writeText(pruned)
        println("Baked $name fixture: ${out.absolutePath} (${pruned.length} bytes)")
    }

    /** Drops non-way elements, non-walkable highways, and every tag the graph builder never reads. */
    private fun prune(json: String): String {
        val elements = Json.parseToJsonElement(json).jsonObject["elements"]?.jsonArray ?: JsonArray(emptyList())
        val kept = buildJsonArray {
            for (el in elements) {
                val obj = el.jsonObject
                if (obj["type"]?.jsonPrimitive?.contentOrNull != "way") continue
                val tags = obj["tags"]?.jsonObject ?: continue
                if (tags["highway"]?.jsonPrimitive?.contentOrNull !in keptHighways) continue
                add(
                    buildJsonObject {
                        put("type", "way")
                        obj["id"]?.let { put("id", it) }
                        obj["nodes"]?.let { put("nodes", it) }
                        obj["geometry"]?.let { put("geometry", it) }
                        put("tags", JsonObject(tags.filterKeys { it in keptTags }))
                    },
                )
            }
        }
        return Json.encodeToString(JsonArray.serializer(), kept).let { """{"elements":$it}""" }
    }
}
