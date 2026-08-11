package com.pikmin.walksim

import com.pikmin.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `flowers_s` wire codec sits on the R2/R3 START path (MainActivity → Intent → WalkService), and a
 * silently-mangled survey degrades a tour into "no site reachable" without any error. It is also the form an
 * `adb am` command uses to script a tour, so the round-trip has to survive shell-quoted input.
 */
class FlowerCodecTest {

    @Test
    fun roundTripsASurvey() {
        val survey = PRESET_LOCATIONS.first { it.flowers.isNotEmpty() }.flowers

        assertEquals(survey, decodeFlowers(encodeFlowers(survey)))
    }

    @Test
    fun encodesAsLatCommaLngSemicolonSeparated() {
        val encoded = encodeFlowers(listOf(LatLng(35.5449, 139.7699), LatLng(35.5494, 139.7857)))

        assertEquals("35.5449,139.7699;35.5494,139.7857", encoded)
    }

    @Test
    fun dropsOnlyTheMalformedPairs_keepingTheRest() {
        val decoded = decodeFlowers("35.5449,139.7699;garbage;35.5494,;,139.7857; 35.5533 , 139.7876 ")

        // The two well-formed pairs survive (the last one despite padding); the three broken ones are dropped
        // rather than failing the whole start.
        assertEquals(listOf(LatLng(35.5449, 139.7699), LatLng(35.5533, 139.7876)), decoded)
    }

    @Test
    fun absentOrEmptyInputYieldsNoSites_soThePresetSweepsInstead() {
        assertTrue(decodeFlowers(null).isEmpty())
        assertTrue(decodeFlowers("").isEmpty())
    }
}
