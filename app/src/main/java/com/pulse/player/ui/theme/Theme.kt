package com.pulse.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Neon = Color(0xFFB14BFF)
val Cyan = Color(0xFF22D3EE)
val Pink = Color(0xFFFF3D9A)
val Bg = Color(0xFF07070D)
val Card = Color(0xFF13131F)
val CardHi = Color(0xFF1D1D2E)
val TextDim = Color(0xFF9A9AB5)

private val scheme = darkColorScheme(
    primary = Neon,
    onPrimary = Color.White,
    secondary = Cyan,
    tertiary = Pink,
    background = Bg,
    onBackground = Color.White,
    surface = Card,
    onSurface = Color.White,
    surfaceVariant = CardHi,
    onSurfaceVariant = TextDim,
    surfaceContainer = Card,
    surfaceContainerHigh = CardHi,
    surfaceContainerLow = Card,
    outline = Color(0xFF2E2E45),
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
