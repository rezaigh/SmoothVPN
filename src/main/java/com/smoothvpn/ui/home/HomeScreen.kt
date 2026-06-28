package com.smoothvpn.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smoothvpn.ui.theme.SmoothColor

@Composable
fun HomeScreen(
    ui: HomeUiState,
    onToggleConnect: () -> Unit,
    onPickServer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // slow drifting background gradient
    val drift = rememberInfiniteTransition(label = "bg")
    val shift by drift.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(12000), RepeatMode.Reverse),
        label = "shift",
    )
    val bg = Brush.linearGradient(
        colors = listOf(SmoothColor.Ink, Color0x14(SmoothColor.Indigo), SmoothColor.Ink),
        start = Offset(0f, shift),
        end = Offset(900f, 1400f - shift),
    )

    Column(
        modifier = modifier.fillMaxSize().background(bg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SmoothVPN", color = SmoothColor.Mist, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Settings, "Settings", tint = SmoothColor.Muted,
                modifier = Modifier.size(24.dp).clickable { onOpenSettings() },
            )
        }

        Spacer(Modifier.weight(1f))

        ConnectOrb(state = ui.state, onToggle = onToggleConnect)

        Spacer(Modifier.height(28.dp))

        AnimatedContent(
            targetState = ui.state,
            transitionSpec = { (fadeIn(tween(300))).togetherWith(fadeOut(tween(200))) },
            label = "status",
        ) { s ->
            val (title, sub, color) = statusText(s, ui)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = color, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(sub, color = SmoothColor.Muted, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        ServerCard(ui = ui, onClick = onPickServer)

        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = ui.state == ConnState.Connected,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
        ) {
            StatsRow(ui)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ServerCard(ui: HomeUiState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SmoothColor.Ink2)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(SmoothColor.flow)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Bolt, null, tint = SmoothColor.Ink, modifier = Modifier.size(20.dp)) }

        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ui.serverName, color = SmoothColor.Mist, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            val meta = buildList {
                ui.serverGroup?.let { add(it) }
                ui.pingMs?.let { add("$it ms") }
            }.joinToString(" · ").ifEmpty { "Tap to choose a server" }
            Text(meta, color = SmoothColor.Muted, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, "Change", tint = SmoothColor.Muted)
    }
}

@Composable
private fun StatsRow(ui: HomeUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatPill("Download", ui.downMbps?.let { "%.1f Mbps".format(it) } ?: "—", Modifier.weight(1f))
        StatPill("Upload", ui.upMbps?.let { "%.1f Mbps".format(it) } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(SmoothColor.Ink2).padding(14.dp)
    ) {
        Text(label, color = SmoothColor.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = SmoothColor.Teal, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusText(s: ConnState, ui: HomeUiState) = when (s) {
    ConnState.Connected -> Triple("Connected", "Your connection is protected", SmoothColor.Teal)
    ConnState.Connecting -> Triple("Connecting…", "Securing your tunnel", SmoothColor.Mist)
    ConnState.Error -> Triple("Couldn't connect", ui.message ?: "Tap to try again", SmoothColor.Danger)
    ConnState.Disconnected -> Triple("Not connected", "Tap the orb to connect", SmoothColor.Mist)
}

// tiny helper: brand color at low alpha for the background mid-stop
private fun Color0x14(c: androidx.compose.ui.graphics.Color) = c.copy(alpha = 0.08f)
