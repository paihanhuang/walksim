package com.pikmin.walksim.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the "Petal Pop" palette (Plan B design tokens). PetalTokens are pure JVM Int ARGB constants
 * (no Compose import) so this parity check runs as a plain JVM unit test. A typo in any hex value is
 * caught here. Values are verbatim from the Plan B implementation plan / redesign spec.
 */
class PetalTokensTest {

    @Test
    fun paletteMatchesSpec() {
        assertEquals(0xFFF0554E.toInt(), PetalTokens.START, "START")
        assertEquals(0xFFFFC531.toInt(), PetalTokens.PAUSE, "PAUSE")
        assertEquals(0xFF4FA3FF.toInt(), PetalTokens.RESUME, "RESUME")
        assertEquals(0xFFFF8FB1.toInt(), PetalTokens.STOP, "STOP")
        assertEquals(0xFF8FD3B6.toInt(), PetalTokens.MAP, "MAP")
        assertEquals(0xFFFFF5F8.toInt(), PetalTokens.SURFACE, "SURFACE")
        assertEquals(0xFF7A4A5E.toInt(), PetalTokens.TEXT, "TEXT")
    }
}
