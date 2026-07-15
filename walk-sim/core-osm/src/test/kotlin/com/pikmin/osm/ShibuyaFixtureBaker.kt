package com.pikmin.osm

import com.pikmin.model.LatLng
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * One-time LIVE Overpass fetch that bakes the offline Shibuya fixture (T3.2 + T3.5). Normally SKIPPED;
 * run explicitly to (re)create the fixture (Gradle forwards the env var to the forked test JVM):
 *
 *   BAKE_SHIBUYA=true ./gradlew :core-osm:test --tests "*ShibuyaFixtureBaker*" --rerun-tasks
 *
 * It exercises the real [OverpassClient] (HttpURLConnection, gzip, timeout, retry) and writes the raw JSON
 * to src/test/resources/shibuya.json, which every other run then parses fully OFFLINE.
 */
class ShibuyaFixtureBaker {

    @Test
    @EnabledIfEnvironmentVariable(named = "BAKE_SHIBUYA", matches = "true")
    fun bakeShibuyaFixture() {
        val json = OverpassClient.fetch(LatLng(35.6595, 139.7006), 800)
        val out = File("src/test/resources/shibuya.json")
        out.parentFile.mkdirs()
        out.writeText(json)
        println("Baked Shibuya fixture: ${out.absolutePath} (${json.length} bytes)")
    }
}
