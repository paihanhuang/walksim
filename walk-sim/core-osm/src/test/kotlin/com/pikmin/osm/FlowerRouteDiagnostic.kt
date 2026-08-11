package com.pikmin.osm

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.sim.flowerFetchRadiusM
import com.pikmin.sim.flowerRoute
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * Ad-hoc R2/R3 flower-tour diagnostic on the LIVE Overpass graph — the proof that each new preset's tour
 * actually reaches every censused big-flower site on real roads (the synthetic-graph unit tests prove the
 * algorithm; this proves the real data). NOT a gate — run explicitly:
 *   FLOWER_DIAG=true ./gradlew :core-osm:test --tests '*FlowerRouteDiagnostic*' -i
 * Reports the tour length (→ each preset's routeLengthKm) and the closest approach to every flower site.
 */
class FlowerRouteDiagnostic {

    private data class Preset(val label: String, val start: LatLng, val flowers: List<LatLng>)

    private val presets = listOf(
        Preset(
            "Haneda",
            LatLng(35.5449, 139.7699),
            listOf(
                LatLng(35.5468, 139.7462), LatLng(35.5490, 139.7440), LatLng(35.5533, 139.7452),
                LatLng(35.5525, 139.7405), LatLng(35.5455, 139.7520), LatLng(35.5510, 139.7530),
                LatLng(35.5570, 139.7480), LatLng(35.5449, 139.7699), LatLng(35.5494, 139.7857),
                LatLng(35.5533, 139.7876),
            ),
        ),
        Preset(
            "Enoshima",
            LatLng(35.3095, 139.4838),
            listOf(
                LatLng(35.2989, 139.4803), LatLng(35.2965, 139.4795), LatLng(35.3020, 139.4810),
                LatLng(35.3060, 139.4830), LatLng(35.3095, 139.4838), LatLng(35.3130, 139.4880),
                LatLng(35.3140, 139.4790), LatLng(35.3060, 139.4910),
            ),
        ),
    )

    @Test
    @EnabledIfEnvironmentVariable(named = "FLOWER_DIAG", matches = "true") // ad-hoc; normally SKIPPED
    fun tourReachesEveryFlower() = runBlocking {
        val out = StringBuilder()
        fun log(s: String) { println(s); out.appendLine(s) }

        for (p in presets) {
            val radius = flowerFetchRadiusM(p.start, p.flowers)
            val json = OverpassClient.fetch(p.start, radius.toInt())
            val graph = OverpassGraph.fromOverpassJson(json, OverpassGraph.FOOT_ONLY_WAYS)
            val route = requireNotNull(flowerRoute(graph, p.start, p.flowers, closeLoop = true)) { "${p.label}: no surveyed site reachable" }
            log("[${p.label}] fetchR=${"%.0f".format(radius)}m nodes=${graph.nodes.size} tour=${"%.0f".format(route.totalLengthM)}m (${"%.2f".format(route.totalLengthM / 1000)}km) vertices=${route.points.size}")
            var worst = 0.0
            p.flowers.forEachIndexed { i, f ->
                val d = route.points.minOf { Geo.haversineMeters(it, f) }
                worst = maxOf(worst, d)
                log("  flower[%02d] %.4f,%.4f  closestApproach=%.0f m %s".format(i + 1, f.lat, f.lng, d, if (d <= 250.0) "REACHED" else "MISSED"))
            }
            log("[${p.label}] worstApproach=${"%.0f".format(worst)}m reachedAll@250m=${worst <= 250.0}")
        }
        File("/tmp/flower-route-diagnostic.txt").writeText(out.toString()) // /tmp, matching UenoRouteDiagnostic
    }
}
