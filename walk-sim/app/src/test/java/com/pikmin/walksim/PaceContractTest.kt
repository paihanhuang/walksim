package com.pikmin.walksim

import java.io.File
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
        File("/Users/davidhuang/Projects/pikmin-remote-control/docs/sdlc/walk-simulator/pace-contract.properties")
            .inputStream().use { load(it) }
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
