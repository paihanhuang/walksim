package com.pikmin.sim

import com.pikmin.model.Geo
import com.pikmin.model.LatLng
import com.pikmin.model.WalkProfile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R4: a pace the user types must actually be walked. The engine used to clamp every run to the DEFAULT
 * profile's 0.8..1.8 m/s band and to a fixed 4.5 m/tick no-teleport bound, so 5 / 7 / 10 / 20 m/s all played
 * at 1.8 m/s (144 steps/min at a 0.75 m stride — exactly what the on-device pace channel reported). Both
 * bounds must now scale with the requested mean, while the default 1.3 m/s profile stays bit-identical.
 */
class HighPaceTest {

    private val origin = LatLng(37.4220, -122.0841)

    /** A long straight path so the run is never limited by the route ending. */
    private fun straightPath(lengthM: Double): List<LatLng> =
        generateSequence(0.0) { it + 1.0 }.takeWhile { it <= lengthM }
            .map { Geo.destinationPoint(origin, 90.0, it) }
            .toList()

    private suspend fun meanEmittedSpeed(paceMps: Double): Double {
        val profile = WalkProfile(meanSpeedMps = paceMps)
        val path = straightPath(paceMps * 400)
        val samples = WalkingMotionEngine.play(path, profile, durationMs = 120_000, seed = 42L).toList()
        val moving = samples.filter { it.speedMps > 0f }
        assertTrue(moving.size > 30, "expected a sustained run, got ${moving.size} moving ticks")
        // Ground truth from the EMITTED positions, not the reported speed field: what the game actually sees.
        val groundM = samples.zipWithNext().sumOf { (a, b) -> Geo.haversineMeters(a.pos, b.pos) }
        return groundM / samples.size
    }

    @Test
    fun defaultPaceIsUnchanged() = runTest {
        assertEquals(1.3, meanEmittedSpeed(1.3), 0.35, "the default 1.3 m/s profile must keep walking at ~1.3")
    }

    @Test
    fun highPacesAreActuallyWalked() = runTest {
        for (pace in listOf(5.0, 7.0, 10.0, 20.0)) {
            val measured = meanEmittedSpeed(pace)
            assertTrue(
                measured >= pace * 0.75,
                "pace $pace m/s was clamped: emitted ground speed only ${"%.2f".format(measured)} m/s",
            )
        }
    }
}
