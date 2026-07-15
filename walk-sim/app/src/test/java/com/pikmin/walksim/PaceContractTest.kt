package com.pikmin.walksim

import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [PaceProvider]'s IPC constants to the canonical contract
 * (docs/sdlc/walk-simulator/pace-contract.properties). A rename/removal on the
 * provider side is exactly what makes this go RED — nothing else.
 */
class PaceContractTest {
    private val canonical = Properties().apply {
        canonicalFile().inputStream().use { load(it) }
    }

    private fun canonicalFile(): java.io.File {
        var dir: java.io.File? = java.io.File("").absoluteFile
        val rel = "docs/sdlc/walk-simulator/pace-contract.properties"
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        return java.io.File(requireNotNull(dir) { "pace-contract.properties not found above ${java.io.File("").absoluteFile}" }, rel)
    }

    @Test
    fun providerConstantsMatchCanonical() {
        assertEquals(canonical.getProperty("authority"), PaceProvider.AUTHORITY)
        assertEquals(canonical.getProperty("path"), "current")
        assertEquals(
            "content://${canonical.getProperty("authority")}/${canonical.getProperty("path")}",
            PaceProvider.CURRENT_URI_STR,
        )
        assertEquals(canonical.getProperty("col.playing"), PaceProvider.COL_PLAYING)
        assertEquals(canonical.getProperty("col.stepsPerMin"), PaceProvider.COL_STEPS_PER_MIN)
        assertEquals(canonical.getProperty("col.schemaVersion"), PaceProvider.COL_SCHEMA_VERSION)
        assertEquals(canonical.getProperty("schemaVersion").toInt(), PaceProvider.SCHEMA_VERSION)
    }
}
