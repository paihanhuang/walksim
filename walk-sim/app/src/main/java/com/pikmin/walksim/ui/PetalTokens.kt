package com.pikmin.walksim.ui

/**
 * "Petal Pop" design tokens (Plan B) — the palette as pure JVM Int ARGB constants with NO Compose import,
 * so they are unit-testable off-device (see PetalTokensTest) and can be wrapped by Compose `Color` in
 * Theme.kt. Light theme only. Hex values are verbatim from the redesign spec.
 */
object PetalTokens {
    const val START = 0xFFF0554E.toInt()
    const val PAUSE = 0xFFFFC531.toInt()
    const val RESUME = 0xFF4FA3FF.toInt()
    const val STOP = 0xFFFF8FB1.toInt()
    const val MAP = 0xFF8FD3B6.toInt()
    const val SURFACE = 0xFFFFF5F8.toInt()
    const val TEXT = 0xFF7A4A5E.toInt()
}
