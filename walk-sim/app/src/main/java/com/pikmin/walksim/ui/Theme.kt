package com.pikmin.walksim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Petal Pop Material3 color scheme (light theme only) built from the PetalTokens palette. */
private val petalColorScheme = lightColorScheme(
    primary = Color(PetalTokens.START),
    surface = Color(PetalTokens.SURFACE),
    onSurface = Color(PetalTokens.TEXT),
    background = Color.White,
)

/** App theme: wraps [content] in the Petal Pop scheme with soft, pill-friendly extra-large corners. */
@Composable
fun WalkSimTheme(content: @Composable () -> Unit) =
    MaterialTheme(
        colorScheme = petalColorScheme,
        shapes = Shapes(extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )

/**
 * Palette swatch preview (Stage 0 quality proof). Present as code only; captured visually later in
 * Android Studio / on-device, not rendered by this stage's headless build.
 */
@Preview(showBackground = true)
@Composable
private fun PetalPalettePreview() {
    WalkSimTheme {
        Column(Modifier.background(Color(PetalTokens.SURFACE)).padding(16.dp)) {
            Swatch("START", PetalTokens.START)
            Swatch("PAUSE", PetalTokens.PAUSE)
            Swatch("RESUME", PetalTokens.RESUME)
            Swatch("STOP", PetalTokens.STOP)
            Swatch("MAP", PetalTokens.MAP)
            Swatch("SURFACE", PetalTokens.SURFACE)
            Swatch("TEXT", PetalTokens.TEXT)
        }
    }
}

@Composable
private fun Swatch(name: String, argb: Int) {
    Row(Modifier.padding(4.dp)) {
        Spacer(
            Modifier
                .size(28.dp)
                .background(Color(argb), RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(name, color = Color(PetalTokens.TEXT))
    }
}
