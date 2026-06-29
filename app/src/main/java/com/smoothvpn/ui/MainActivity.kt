package com.smoothvpn.ui

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothvpn.core.Profile
import com.smoothvpn.service.XrayVpnService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ needs a runtime grant for the foreground-service notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SmoothVpnTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen(vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    // Poll service state (service runs in its own process).
    var connected by remember { mutableStateOf(XrayVpnService.isRunning) }
    var activeRemark by remember { mutableStateOf(XrayVpnService.activeRemark) }
    LaunchedEffect(Unit) {
        while (true) {
            connected = XrayVpnService.isRunning
            activeRemark = XrayVpnService.activeRemark
            delay(800)
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it.text); vm.consumeMessage() }
    }

    var showSubDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()

    // QR scanner -> import the scanned config link.
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.importFromClipboard(it) }
    }

    // VPN consent launcher -> start service on success.
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selected?.let { startVpn(context, it.id) }
        }
    }

    fun toggleConnection() {
        if (connected) {
            stopVpn(context)
        } else {
            val target = selected
            if (target == null) {
                vm.notify("Add and select a server first")
                return
            }
            val intent = VpnService.prepare(context)
            if (intent != null) vpnPermission.launch(intent)
            else startVpn(context, target.id)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("∿", color = Accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("SmoothVPN", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButtonText("Ping") { vm.pingAll() }
                    IconButtonText("Fastest") { vm.selectFastest() }
                    IconButtonText("⚙") { showSettings = true }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSubDialog = true },
                text = { Text("Subscription") },
                icon = { Text("＋", fontSize = 20.sp) }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)
        ) {
            ConnectButton(
                connected = connected,
                remark = if (connected) activeRemark else (selected?.remark ?: "No server selected"),
                busy = busy,
                onClick = { toggleConnection() }
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Servers (${profiles.size})", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                val sortOn = sortMode == MainViewModel.SortMode.PING
                TextButton(onClick = { vm.toggleSort() }) {
                    Text(
                        if (sortOn) "Ping ▲" else "Sort by ping",
                        color = if (sortOn) Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontWeight = if (sortOn) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val clip = readClipboard(context)
                    vm.importFromClipboard(clip)
                }) { Text("Paste link") }
                TextButton(onClick = {
                    qrLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan a VMess / VLESS / Trojan / SS QR")
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        }
                    )
                }) { Text("Scan QR") }
                Spacer(Modifier.weight(1f))
                if (subscriptions.isNotEmpty()) {
                    TextButton(onClick = { vm.updateSubscriptions() }) { Text("Refresh") }
                }
            }

            if (profiles.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // clear the FAB + the gesture / navigation-bar inset so the
                    // last config can scroll up into a comfortable tap zone
                    contentPadding = PaddingValues(
                        bottom = 96.dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                    items(profiles, key = { it.id }) { p ->
                        ServerRow(
                            profile = p,
                            selected = p.id == (selectedId ?: selected?.id),
                            onClick = { vm.select(p.id) },
                            onDelete = { vm.delete(p) }
                        )
                    }
                }
            }
        }
    }
    }

    if (showSubDialog) {
        SubscriptionDialog(
            onDismiss = { showSubDialog = false },
            onAdd = { name, url -> vm.addSubscription(name, url); showSubDialog = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onMux = vm::setMux,
            onBypassLan = vm::setBypassLan,
            onBlockAds = vm::setBlockAds,
            onDomestic = vm::setDomesticDirect,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: MainViewModel.Settings,
    onMux: (Boolean) -> Unit,
    onBypassLan: (Boolean) -> Unit,
    onBlockAds: (Boolean) -> Unit,
    onDomestic: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                SettingRow("Mux — connection reuse (snappier)", settings.mux, onMux)
                SettingRow("Bypass LAN", settings.bypassLan, onBypassLan)
                SettingRow("Block ads (needs geo files)", settings.blockAds, onBlockAds)
                SettingRow("Direct .ir routing (needs geo files)", settings.domesticDirect, onDomestic)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ConnectButton(connected: Boolean, remark: String, busy: Boolean, onClick: () -> Unit) {
    val core by animateColorAsState(
        if (connected) Accent else Color(0xFF2A333C), label = "core"
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.96f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow"
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(224.dp)) {
            // outer glow halo — gently pulses when connected
            Box(
                Modifier.size(212.dp)
                    .scale(if (connected) glow else 1f)
                    .clip(CircleShape)
                    .background(core.copy(alpha = if (connected) 0.16f else 0.05f))
            )
            // mid ring
            Box(
                Modifier.size(170.dp).clip(CircleShape)
                    .background(core.copy(alpha = if (connected) 0.20f else 0.09f))
                    .border(1.dp, core.copy(alpha = 0.35f), CircleShape)
            )
            // core button with emerald gradient
            Box(
                Modifier.size(134.dp).clip(CircleShape)
                    .background(
                        if (connected)
                            Brush.verticalGradient(listOf(Color(0xFF4CEBAB), AccentDeep))
                        else
                            Brush.verticalGradient(listOf(Color(0xFF20272E), Color(0xFF151B21)))
                    )
                    .clickable(enabled = !busy) { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("∿", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            color = if (connected) Color(0xFF05231A) else Accent)
                        Text(if (connected) "ON" else "OFF",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (connected) Color(0xFF05231A) else Color(0xFF8A97A3))
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (connected) "Protected" else "Tap to connect",
            fontWeight = FontWeight.Bold, fontSize = 18.sp,
            color = if (connected) Accent else MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(3.dp))
        Text(remark, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
}

@Composable
private fun ServerRow(profile: Profile, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .then(
                if (selected) Modifier.border(1.5.dp, Accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Accent.copy(alpha = 0.12f) else Color(0xFF141A20)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (selected) Accent else Color(0xFF3A444E))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.remark, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${profile.protocol.tag.uppercase()} · ${profile.address}:${profile.port}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            LatencyChip(profile.latencyMs)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete) {
                Text("✕", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun LatencyChip(ms: Int) {
    val (label, color) = when {
        ms < 0 -> "—" to Color.Gray
        ms < 150 -> "${ms}ms" to Color(0xFF3DDC97)
        ms < 350 -> "${ms}ms" to Color(0xFFE0B341)
        else -> "${ms}ms" to Color(0xFFE05B5B)
    }
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No servers yet", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste a vmess/vless/trojan/ss link,\nor add a subscription with ＋",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add subscription") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Subscription URL") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onAdd(name, url.trim()) }) { Text("Add & fetch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun IconButtonText(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}

// ---- plain helpers ----------------------------------------------------------

private fun readClipboard(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
}

private fun startVpn(context: Context, profileId: String) {
    val intent = Intent(context, XrayVpnService::class.java).apply {
        action = XrayVpnService.ACTION_START
        putExtra(XrayVpnService.EXTRA_PROFILE_ID, profileId)
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopVpn(context: Context) {
    val intent = Intent(context, XrayVpnService::class.java).apply {
        action = XrayVpnService.ACTION_STOP
    }
    context.startService(intent)
}
