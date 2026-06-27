package com.smoothvpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — kept in sync with the launcher icon.
val Accent = Color(0xFF34E0A1)
val AccentDeep = Color(0xFF0E8E5F)
val BgTop = Color(0xFF13261F)   // deep teal (gradient top)
val BgBottom = Color(0xFF07090C) // near-black (gradient bottom)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF052015),
    secondary = AccentDeep,
    background = BgBottom,
    surface = Color(0xFF141A20),
    surfaceVariant = Color(0xFF1A222B),
    onBackground = Color(0xFFE7ECF1),
    onSurface = Color(0xFFE7ECF1),
    outline = Color(0xFF2C3A44)
)

/** SmoothVPN always uses its signature dark theme for a consistent, premium look. */
@Composable
fun SmoothVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
