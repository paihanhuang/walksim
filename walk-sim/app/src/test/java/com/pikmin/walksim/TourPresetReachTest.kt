package com.pikmin.walksim

import com.pikmin.model.Geo
import com.pikmin.osm.OverpassGraph
import com.pikmin.sim.flowerRoute
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The gate for R2.2/R2.3 and R3.2/R3.3: **every** surveyed big-flower site of a tour preset must be passed
 * within the 250 m harvest reach, on a REAL road graph.
 *
 * Why this test exists in `:app` and not `:core-osm`: it reads [PRESET_LOCATIONS] itself, so it can never drift
 * from what actually ships. `FlowerRouteDiagnostic` retypes the site lists and is `@EnabledIfEnvironmentVariable`
 * — it is normally SKIPPED and would happily prove a route no preset walks. Nothing gated the 250 m criterion
 * before this; `FlowerRouteTest.passesEverySurveyedFlower` puts flowers exactly on synthetic nodes, which proves
 * the algorithm, not the data.
 *
 * Offline: the graphs come from fixtures baked by `TourFixtureBaker` at the same centre/radius production
 * fetches. Deleting a site, moving a start pin off-graph, or narrowing the foot-only opt-in makes this go RED.
 */
class TourPresetReachTest {

    /** Project harvest reach (v1.9): a big flower is harvestable within 250 m of the avatar. */
    private val harvestReachM = 250.0

    private fun fixture(name: String): String {
        var dir: java.io.File? = java.io.File("").absoluteFile
        val rel = "walk-sim/core-osm/src/test/resources/$name.json"
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        return java.io.File(
            requireNotNull(dir) { "$rel not found above ${java.io.File("").absoluteFile}" },
            rel,
        ).readText()
    }

    private fun assertTourReachesEverySite(presetLabel: String, fixtureName: String) {
        val preset = PRESET_LOCATIONS.single { it.label == presetLabel }
        val graph = OverpassGraph.fromOverpassJson(fixture(fixtureName), OverpassGraph.FOOT_ONLY_WAYS)

        val route = requireNotNull(flowerRoute(graph, preset.at, preset.flowers, closeLoop = true)) {
            "${'$'}{preset.label}: no surveyed site reachable at all"
        }

        val missed = preset.flowers
            .map { it to route.points.minOf { p -> Geo.haversineMeters(p, it) } }
            .filter { (_, d) -> d > harvestReachM }
        assertTrue(
            missed.isEmpty(),
            "${preset.label}: ${missed.size}/${preset.flowers.size} surveyed sites are NOT passed within " +
                "$harvestReachM m — " + missed.joinToString { (site, d) -> "%.4f,%.4f=%.0fm".format(site.lat, site.lng, d) },
        )
    }

    @Test
    fun hanedaTourPassesEverySurveyedSite() =
        assertTourReachesEverySite("Haneda Airport, Tokyo, Japan", "haneda")

    @Test
    fun enoshimaTourPassesEverySurveyedSite() =
        assertTourReachesEverySite("Enoshima / Katase-Kaigan, Japan", "enoshima")
}
