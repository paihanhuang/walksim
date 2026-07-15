package com.pikmin.osm

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import com.pikmin.sim.sweepRoute
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * Ad-hoc Ueno route diagnostic for the v1.9 "best route" recriteria (harvest reach 250 m, 10 km budget,
 * both-sides distinct-flower objective, minimal overlap). NOT a gate — run explicitly:
 *   ./gradlew :core-osm:test --tests '*UenoRouteDiagnostic*' -i
 * Fetches the LIVE Overpass graph once, runs the sweep for the OLD tuning (850 m) and candidate NEW spacings,
 * prints coverage@250 m + revisit for spacing selection, and dumps each route's waypoint polyline resampled
 * every 250 m (== the in-Pikmin big-flower census sample points; grep 'WP,').
 */
class UenoRouteDiagnostic {

    private val uenoAmeyoko = LatLng(35.7089, 139.7745) // current preset start
    private val fetchCenter = LatLng(35.7110, 139.7735) // covers Ameyoko + Shinobazu Pond + Ueno Stn
    private val budgetM = 10_000.0
    private val out = StringBuilder()
    private fun log(s: String) { println(s); out.appendLine(s) }

    private fun resample(route: Route, stepM: Double): List<Pair<LatLng, Double>> {
        val pts = route.points
        val out = ArrayList<Pair<LatLng, Double>>()
        if (pts.isEmpty()) return out
        out.add(pts[0] to 0.0)
        var acc = 0.0
        var next = stepM
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val seg = Geo.haversineMeters(a, b)
            while (acc + seg >= next) {
                out.add(Geo.destinationPoint(a, Geo.bearingDegrees(a, b), next - acc) to next)
                next += stepM
            }
            acc += seg
        }
        return out
    }

    /** Fraction of 25 m samples within 100 m of a sample ≥500 m earlier along the route (path overlap / back-and-forth). */
    private fun revisitFraction(samples: List<Pair<LatLng, Double>>): Double {
        if (samples.size < 2) return 0.0
        var revisits = 0
        for (i in samples.indices) {
            val (pi, ai) = samples[i]
            var j = 0
            while (j < i && samples[j].second <= ai - 500.0) {
                if (Geo.haversineMeters(pi, samples[j].first) <= 100.0) { revisits++; break }
                j++
            }
        }
        return revisits.toDouble() / samples.size
    }

    /** Fraction of graph nodes inside [innerR] of [center] within [reachM] of some route sample (harvest coverage). */
    private fun coverageAt(g: WalkGraph, samples: List<Pair<LatLng, Double>>, center: LatLng, innerR: Double, reachM: Double): Double {
        val inner = g.nodes.values.filter { Geo.haversineMeters(center, it) <= innerR }
        if (inner.isEmpty()) return 1.0
        val ok = inner.count { n -> samples.any { Geo.haversineMeters(n, it.first) <= reachM } }
        return ok.toDouble() / inner.size
    }

    private fun report(tag: String, graph: WalkGraph, start: LatLng, spacing: Double, dumpWaypoints: Boolean) {
        val route = sweepRoute(graph, start, budgetM, spacing)
        val fine = resample(route, 25.0)
        val eff = route.points.first()
        val maxR = route.points.maxOf { Geo.haversineMeters(eff, it) }
        val cov = coverageAt(graph, fine, eff, innerR = maxR - 300.0, reachM = 250.0)
        val rev = revisitFraction(fine)
        log("[$tag] spacing=${"%.0f".format(spacing)} len=${"%.0f".format(route.totalLengthM)}m maxR=${"%.0f".format(maxR)}m cov@250=${"%.3f".format(cov)} revisit=${"%.3f".format(rev)} vertices=${route.points.size}")
        if (dumpWaypoints) {
            val census = resample(route, 250.0)
            log("[$tag WAYPOINTS n=${census.size}]")
            census.forEach { log("WP,$tag,${"%.6f".format(it.first.lat)},${"%.6f".format(it.first.lng)}") }
        }
    }

    private data class P(val label: String, val at: LatLng, val spacing: Double, val lengthKm: Double)

    @Test
    @EnabledIfEnvironmentVariable(named = "PRESET_DIAG", matches = "true") // ad-hoc all-preset sweep; normally SKIPPED
    fun allPresets() {
        val presets = listOf(
            P("Shibuya", LatLng(35.6595, 139.7006), 850.0, 6.0),
            P("Okubo", LatLng(35.6975, 139.7005), 1100.0, 20.0),
            P("Roppongi", LatLng(35.6628, 139.7314), 1200.0, 20.0),
            P("Azabudai", LatLng(35.6605, 139.7400), 1100.0, 20.0),
            P("Chuo-ku", LatLng(35.6717, 139.7648), 850.0, 12.0),
            P("OsakaMinami", LatLng(34.6687, 135.5013), 1000.0, 20.0),
            P("Xinyi", LatLng(25.0339, 121.5645), 800.0, 10.0),
            P("Seoul", LatLng(37.5636, 126.9848), 850.0, 12.0),
        )
        val sb = StringBuilder()
        fun line(s: String) { println(s); sb.appendLine(s) }
        line("preset,config,spacing,lenKm,actualLen_m,distinct@250,cov@250,revisit,time_min")
        for (p in presets) {
            try {
                var json: String? = null
                for (attempt in 1..3) {
                    try { json = OverpassClient.fetch(p.at, 3000); break }
                    catch (e: Exception) { line("# ${p.label} fetch attempt $attempt failed: ${e.message}"); Thread.sleep(8000) }
                }
                val g = OverpassGraph.fromOverpassJson(json ?: error("fetch failed"))
                fun eval(tag: String, sp: Double, lkm: Double) {
                    val r = sweepRoute(g, p.at, lkm * 1000, sp)
                    val fine = resample(r, 25.0)
                    val eff = r.points.first()
                    val maxR = r.points.maxOf { Geo.haversineMeters(eff, it) }
                    val distinct = g.nodes.values.count { n -> fine.any { Geo.haversineMeters(n, it.first) <= 250.0 } }
                    val cov = coverageAt(g, fine, eff, innerR = maxR - 300.0, reachM = 250.0)
                    line("${p.label},$tag,${"%.0f".format(sp)},${"%.0f".format(lkm)},${"%.0f".format(r.totalLengthM)},$distinct,${"%.3f".format(cov)},${"%.3f".format(revisitFraction(fine))},${"%.1f".format(r.totalLengthM / 1.3 / 60)}")
                }
                eval("current", p.spacing, p.lengthKm)     // existing tuning
                eval("500@same", 500.0, p.lengthKm)        // v1.9 spacing, SAME length (isolate spacing → flower wash?)
                eval("500@10km", 500.0, 10.0)              // fully standardized to v1.9 (250 m reach, 10 km)
            } catch (e: Exception) {
                line("${p.label},SKIPPED,,,,,,,${e.message}")
            }
            File("/tmp/preset_diag.txt").writeText(sb.toString()) // incremental — partial results survive a later failure
            Thread.sleep(3000)                                    // be gentle on Overpass between presets
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "UENO_DIAG", matches = "true") // ad-hoc: hits live Overpass; normally SKIPPED
    fun diagnose() {
        val json = OverpassClient.fetch(fetchCenter, 3000)
        val graph = OverpassGraph.fromOverpassJson(json)
        log("[ueno graph] nodes=${graph.nodes.size}")
        // OLD tuning at the new 10 km budget (current preset start), with waypoints for census.
        report("OLD", graph, uenoAmeyoko, 850.0, dumpWaypoints = true)
        // NEW candidate spacings (250 m reach → both-side corridors tile at ≤500 m spacing) — pick best cov@250/low revisit.
        for (sp in listOf(400.0, 450.0, 500.0, 550.0)) report("NEW$sp", graph, uenoAmeyoko, sp, dumpWaypoints = false)
        // Chosen NEW = spacing 500 (best cov@250 + low revisit) — dump its waypoints for the census.
        report("NEW", graph, uenoAmeyoko, 500.0, dumpWaypoints = true)

        // Length/time sweep — distinct harvestable LOCATIONS (graph nodes within 250 m of the route; absolute,
        // ∝ distinct big flowers, calibrated: ~census 124 flowers at 10 km) vs route length. Shows where
        // coverage saturates → the shortest walk time that reaches ~the same big-flower coverage.
        log("[length sweep] SWEEP,L_km,spacing,actualLen_m,distinctNodes@250,cov@250,revisit,time_min@1.3mps")
        for (lkm in listOf(3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 16.0)) {
            for (sp in listOf(850.0, 500.0)) {
                val r = sweepRoute(graph, uenoAmeyoko, lkm * 1000, sp)
                val fine = resample(r, 25.0)
                val eff = r.points.first()
                val distinct = graph.nodes.values.count { n -> fine.any { Geo.haversineMeters(n, it.first) <= 250.0 } }
                val maxR = r.points.maxOf { Geo.haversineMeters(eff, it) }
                val cov = coverageAt(graph, fine, eff, innerR = maxR - 300.0, reachM = 250.0)
                val timeMin = r.totalLengthM / 1.3 / 60.0
                log("SWEEP,${"%.0f".format(lkm)},${"%.0f".format(sp)},${"%.0f".format(r.totalLengthM)},$distinct,${"%.3f".format(cov)},${"%.3f".format(revisitFraction(fine))},${"%.1f".format(timeMin)}")
            }
        }
        File("/tmp/ueno_routes.txt").writeText(out.toString())
    }
}
