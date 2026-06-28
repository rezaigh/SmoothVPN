package com.smoothvpn.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.smoothvpn.ui.theme.SmoothColor
import com.smoothvpn.ui.theme.ringSpin

/**
 * The big tappable power orb. It breathes when connected, spins faster while
 * connecting, and sits calm when off — all in the icon's gradient.
 */
@Composable
fun ConnectOrb(
    state: ConnState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = ringSpin(if (state == ConnState.Connecting) 2200 else 9000),
        label = "spin",
    )
    val breathe by transition.animateFloat(
        initialValue = 1f, targetValue = if (state == ConnState.Connected) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breathe",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = when (state) {
            ConnState.Connected -> 1f
            ConnState.Connecting -> 0.9f
            ConnState.Error -> 0.4f
            ConnState.Disconnected -> 0.5f
        },
        animationSpec = tween(500), label = "ringAlpha",
    )
    val coreColor by animateColorAsState(
        targetValue = when (state) {
            ConnState.Connected -> SmoothColor.Teal
            ConnState.Error -> SmoothColor.Danger
            else -> SmoothColor.Ink2
        },
        animationSpec = tween(500), label = "core",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(240.dp)) {
        Canvas(Modifier.size(240.dp).scale(breathe)) {
            drawCircle(brush = SmoothColor.glowBrush(), radius = size.minDimension / 2f)
        }
        Canvas(Modifier.size(196.dp).rotate(spin)) {
            drawArc(
                brush = SmoothColor.orbBrush(),
                startAngle = 0f, sweepAngle = 300f, useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                alpha = ringAlpha,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .scale(if (state == ConnState.Connected) breathe else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(coreColor.copy(alpha = 0.9f), SmoothColor.Ink2)
                    )
                )
                .clickable { onToggle() },
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = if (state == ConnState.Connected) "Disconnect" else "Connect",
                tint = if (state == ConnState.Connected) SmoothColor.Ink else SmoothColor.Mist,
                modifier = Modifier.size(54.dp),
            )
        }
    }
}
