package com.smoothvpn.ui.theme

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The SmoothVPN brand palette — the same teal -> indigo flow as the app icon. */
object SmoothColor {
    val Teal = Color(0xFF2DD4BF)
    val Aqua = Color(0xFF3BA8D8)
    val Indigo = Color(0xFF6366F1)
    val Violet = Color(0xFF8B5CF6)

    val Ink = Color(0xFF0E1417)      // page background
    val Ink2 = Color(0xFF141B20)     // cards / surfaces
    val Line = Color(0xFF243038)     // hairline borders
    val Mist = Color(0xFFF6FFFD)     // primary text on dark
    val Muted = Color(0xFF8C9AA4)    // secondary text

    val Danger = Color(0xFFF87171)
    val Success = Teal

    val flow = listOf(Teal, Aqua, Indigo)

    fun flowBrush(angleShift: Float = 0f): Brush = Brush.linearGradient(
        colors = flow,
        start = Offset(0f, angleShift),
        end = Offset(1000f, 1000f - angleShift),
    )

    fun orbBrush(): Brush = Brush.sweepGradient(
        colors = listOf(Teal, Indigo, Violet, Teal),
    )

    fun glowBrush(): Brush = Brush.radialGradient(
        colors = listOf(Teal.copy(alpha = 0.35f), Color.Transparent),
    )
}

/** Slow continuous spec used by the connect orb ring. */
fun ringSpin(durationMs: Int = 9000): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart)
