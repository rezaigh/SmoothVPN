package com.smoothvpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = SmoothColor.Teal,
    onPrimary = SmoothColor.Ink,
    secondary = SmoothColor.Indigo,
    onSecondary = SmoothColor.Mist,
    background = SmoothColor.Ink,
    onBackground = SmoothColor.Mist,
    surface = SmoothColor.Ink2,
    onSurface = SmoothColor.Mist,
    surfaceVariant = SmoothColor.Ink2,
    onSurfaceVariant = SmoothColor.Muted,
    outline = SmoothColor.Line,
    error = SmoothColor.Danger,
)

private val LightScheme = lightColorScheme(
    primary = SmoothColor.Indigo,
    secondary = SmoothColor.Teal,
    error = SmoothColor.Danger,
)

/** Wrap your app content in SmoothTheme { ... } in MainActivity. Dark by default. */
@Composable
fun SmoothTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
