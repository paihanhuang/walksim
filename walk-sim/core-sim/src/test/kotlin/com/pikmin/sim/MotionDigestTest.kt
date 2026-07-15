package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.WalkProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Characterization golden for the deterministic motion engine (Stage 0 safety net). Record-then-assert on a
 * pure, delay-free digest of [WalkingMotionEngine.frames] at a fixed seed: frame count, first/last emitted
 * position, cumulative distance, step count, and pause count. A second-run drift means the motion engine is
 * non-deterministic (violates AC-5) — raise a defect, never loosen the assertion.
 *
 * Uses JUnit Jupiter (this module's convention; kotlin-test is not on the classpath).
 */
class MotionDigestTest {
    private fun straightPath(meters: Int): List<LatLng> =
        (0..meters).map { Geo.destinationPoint(LatLng(35.66, 139.70), 90.0, it.toDouble()) }

    private fun digest(frames: List<MotionFrame>): String {
        val first = frames.first(); val last = frames.last()
        val pauses = frames.count { it.paused }
        return listOf(
            frames.size,
            "%.6f,%.6f".format(first.emittedPos.lat, first.emittedPos.lng),
            "%.6f,%.6f".format(last.emittedPos.lat, last.emittedPos.lng),
            "%.3f".format(last.cumDistM),
            last.stepCount,
            pauses,
        ).joinToString("|")
    }

    private fun check(name: String, frames: List<MotionFrame>) {
        val golden = File("src/test/resources/golden/$name.txt")
        val actual = digest(frames)
        if (!golden.exists()) { golden.parentFile.mkdirs(); golden.writeText(actual); fail<Unit>("golden '$name' recorded — re-run") }
        assertEquals(golden.readText().trim(), actual, "motion digest '$name' drifted")
    }

    @Test fun open60s() =
        check("motion-open-60s-seed42", WalkingMotionEngine.frames(
            PathEngine.densify(com.pikmin.model.Route(straightPath(120), 120.0), 1.0),
            WalkProfile(), durationMs = 60_000, seed = 42L))
}
