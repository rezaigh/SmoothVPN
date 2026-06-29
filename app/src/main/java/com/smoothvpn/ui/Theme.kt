package com.smoothvpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — kept in sync with the launcher icon (teal -> indigo flow).
val Accent = Color(0xFF2DD4BF)     // teal (icon primary)
val AccentDeep = Color(0xFF6366F1) // indigo (icon end)
val BgTop = Color(0xFF0F1A22)      // dark teal-blue (gradient top)
val BgBottom = Color(0xFF0A0E13)   // near-black (gradient bottom)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF04231E),
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
