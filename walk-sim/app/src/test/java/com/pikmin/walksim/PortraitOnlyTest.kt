package com.pikmin.walksim

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the launcher Activity to portrait-only (R1). WalkSim has a single tall Column layout and no
 * landscape variant: rotated, the weighted map slot collapses to a letterbox strip, the duration/pace
 * field labels disappear and the HUD card falls off-screen. Removing or loosening the
 * `android:screenOrientation="portrait"` lock is exactly what makes this go RED — nothing else.
 *
 * The manifest attribute IS the seam here: orientation is resolved by the platform from the declaration,
 * so there is no app-side function to assert against. The on-device rotate test is the acceptance proof.
 */
class PortraitOnlyTest {

    @Test
    fun mainActivityDeclaresPortraitOnly() {
        val declaration = mainActivityDeclaration()
        assertTrue(
            declaration.contains("android:screenOrientation=\"portrait\""),
            "MainActivity must declare android:screenOrientation=\"portrait\"; found: $declaration",
        )
    }

    /** The `<activity …>` opening tag that declares `.MainActivity`. */
    private fun mainActivityDeclaration(): String =
        Regex("<activity\\b[^>]*>", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifestFile().readText())
            .map { it.value }
            .firstOrNull { it.contains("android:name=\".MainActivity\"") }
            ?: error("no <activity android:name=\".MainActivity\"> in ${manifestFile()}")

    private fun manifestFile(): java.io.File {
        var dir: java.io.File? = java.io.File("").absoluteFile
        val rel = "walk-sim/app/src/main/AndroidManifest.xml"
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        return java.io.File(
            requireNotNull(dir) { "AndroidManifest.xml not found above ${java.io.File("").absoluteFile}" },
            rel,
        )
    }
}
