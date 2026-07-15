package com.pikmin.osm

import com.pikmin.model.Edge
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import com.pikmin.sim.sweepRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

/**
 * Characterization goldens for [sweepRoute] on frozen fixtures (Stage 0 safety net). Record-then-assert:
 * the first run writes each golden and FAILS ("recorded — re-run"); every later run asserts the route
 * fingerprint is byte-identical. A drift means the route math changed — later stages must never rewrite
 * these; a change requires a new golden + human note.
 *
 * Uses JUnit Jupiter (this module's convention; kotlin-test is not on the classpath).
 */
class RouteGoldenTest {

    private val shibuyaStart = LatLng(35.6595, 139.7006)

    private fun shibuyaGraph(): WalkGraph {
        val json = javaClass.getResource("/shibuya.json")!!.readText()
        return OverpassGraph.fromOverpassJson(json)
    }

    /** Small deterministic square lattice so a golden exists independent of the baked asset. */
    private fun gridGraph(n: Int = 12, stepDeg: Double = 0.0009): WalkGraph {
        val nodes = HashMap<Long, LatLng>()
        val adj = HashMap<Long, MutableList<Edge>>()
        fun id(r: Int, c: Int) = (r * 1000 + c).toLong()
        for (r in 0 until n) for (c in 0 until n) nodes[id(r, c)] = LatLng(35.0 + r * stepDeg, 139.0 + c * stepDeg)
        fun link(a: Long, b: Long) {
            val ga = listOf(nodes.getValue(a), nodes.getValue(b))
            val len = com.pikmin.model.Geo.haversineMeters(ga[0], ga[1])
            adj.getOrPut(a) { ArrayList() }.add(Edge(b, ga, len))
            adj.getOrPut(b) { ArrayList() }.add(Edge(a, ga.reversed(), len))
        }
        for (r in 0 until n) for (c in 0 until n) {
            if (c + 1 < n) link(id(r, c), id(r, c + 1))
            if (r + 1 < n) link(id(r, c), id(r + 1, c))
        }
        return WalkGraph(nodes, adj)
    }

    private fun fingerprint(route: Route): String {
        val sb = StringBuilder().append(route.points.size).append('|')
            .append("%.3f".format(route.totalLengthM)).append('|')
        for (p in route.points) sb.append("%.6f,%.6f;".format(p.lat, p.lng))
        return MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun check(name: String, route: Route) {
        val golden = File("src/test/resources/golden/$name.txt")
        val actual = "${route.points.size} ${"%.3f".format(route.totalLengthM)} ${fingerprint(route)}"
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(actual)
            fail<Unit>("golden '$name' recorded (was absent) — re-run to verify")
        }
        assertEquals(golden.readText().trim(), actual, "route golden '$name' drifted")
    }

    @Test fun shibuyaOpen() =
        check("shibuya-open-500-10k", sweepRoute(shibuyaGraph(), shibuyaStart, 10_000.0, 500.0, closeLoop = false))
    @Test fun shibuyaClosed() =
        check("shibuya-closed-500-10k", sweepRoute(shibuyaGraph(), shibuyaStart, 10_000.0, 500.0, closeLoop = true))
    @Test fun gridShortfall() =
        check("grid-open-500-50k", sweepRoute(gridGraph(), LatLng(35.0049, 139.0049), 50_000.0, 500.0, closeLoop = false))
}
