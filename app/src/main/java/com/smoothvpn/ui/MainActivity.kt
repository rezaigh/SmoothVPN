package com.smoothvpn.ui

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoothvpn.core.Profile
import com.smoothvpn.data.SettingsStore
import com.smoothvpn.data.SubscriptionEntity
import com.smoothvpn.diagnostics.ConnectionDiagnostics
import com.smoothvpn.service.XrayVpnService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            SmoothVpnTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }
}

private enum class Screen { HOME, SERVERS, DETAIL, SETTINGS, PERAPP, UPDATE }

@Composable
private fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var detailId by remember { mutableStateOf<String?>(null) }

    // live service state (same process — safe to read the volatile statics)
    var connected by remember { mutableStateOf(XrayVpnService.isRunning) }
    var activeRemark by remember { mutableStateOf(XrayVpnService.activeRemark) }
    var downBps by remember { mutableStateOf(0L) }
    var upBps by remember { mutableStateOf(0L) }
    var lastError by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            connected = XrayVpnService.isRunning
            activeRemark = XrayVpnService.activeRemark
            downBps = XrayVpnService.downlinkBps
            upBps = XrayVpnService.uplinkBps
            lastError = XrayVpnService.lastError
            delay(800)
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it.text); vm.consumeMessage() } }
    LaunchedEffect(lastError) {
        if (lastError.isNotBlank()) {
            val d = ConnectionDiagnostics.diagnose(lastError)
            snackbar.showSnackbar("${d.title} — ${d.suggestion}")
            XrayVpnService.lastError = ""
        }
    }

    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.importFromClipboard(it) }
    }
    var pendingStartId by remember { mutableStateOf<String?>(null) }
    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingStartId?.let { startVpn(context, it) }
        }
        pendingStartId = null
    }

    fun connectTo(id: String) {
        val intent = VpnService.prepare(context)
        if (intent != null) { pendingStartId = id; vpnPermission.launch(intent) }
        else startVpn(context, id)
    }

    fun toggleConnection() {
        if (connected) { stopVpn(context); return }
        val target = selected
        if (target == null) { vm.notify("Add and select a server first"); return }
        connectTo(target.id)
    }

    fun scanQr() = qrLauncher.launch(
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan a VMess / VLESS / Trojan / SS QR")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
    )

    // system-back returns to Home from any sub-screen
    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.DETAIL) Screen.SERVERS else Screen.HOME
    }

    val scaffoldBg = Brush.verticalGradient(listOf(BgTop, BgBottom))
    Box(Modifier.fillMaxSize().background(scaffoldBg)) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    connected = connected,
                    busy = busy,
                    activeRemark = activeRemark,
                    selected = selected,
                    downBps = downBps,
                    upBps = upBps,
                    onToggle = { toggleConnection() },
                    onOpenServers = { screen = Screen.SERVERS },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenUpdate = { screen = Screen.UPDATE }
                )
                Screen.SERVERS -> ServersScreen(
                    vm = vm,
                    onBack = { screen = Screen.HOME },
                    onScanQr = { scanQr() },
                    onOpenDetail = { id -> detailId = id; screen = Screen.DETAIL }
                )
                Screen.DETAIL -> {
                    val p = profiles.firstOrNull { it.id == detailId }
                    if (p == null) { screen = Screen.SERVERS }
                    else ServerDetailScreen(
                        profile = p,
                        vm = vm,
                        isActive = connected && p.id == selected?.id,
                        connected = connected,
                        onBack = { screen = Screen.SERVERS },
                        onConnect = { vm.select(p.id); connectTo(p.id) },
                        onDisconnect = { stopVpn(context) },
                        onDeleted = { screen = Screen.SERVERS }
                    )
                }
                Screen.SETTINGS -> SettingsScreen(
                    vm = vm,
                    onBack = { screen = Screen.HOME },
                    onOpenPerApp = { screen = Screen.PERAPP },
                    onOpenUpdate = { screen = Screen.UPDATE },
                    onOpenSystemVpn = { openSystemVpnSettings(context) }
                )
                Screen.PERAPP -> PerAppScreen(vm = vm, onBack = { screen = Screen.SETTINGS })
                Screen.UPDATE -> UpdateScreen(vm = vm, onBack = { screen = Screen.HOME })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

/* ───────────────────────────── HOME ───────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    connected: Boolean,
    busy: Boolean,
    activeRemark: String,
    selected: Profile?,
    downBps: Long,
    upBps: Long,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("SmoothVPN", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Servers & configs") },
                                leadingIcon = { Icon(Icons.Filled.Dns, null) },
                                onClick = { menu = false; onOpenServers() })
                            DropdownMenuItem(text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) },
                                onClick = { menu = false; onOpenSettings() })
                            DropdownMenuItem(text = { Text("Check for update") },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
                                onClick = { menu = false; onOpenUpdate() })
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            ConnectOrb(connected = connected, busy = busy, onClick = onToggle)

            Spacer(Modifier.height(18.dp))
            Text(
                if (connected) "Connected" else "Tap to connect",
                fontWeight = FontWeight.Bold, fontSize = 26.sp,
                color = if (connected) Accent else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (connected) "Your connection is protected"
                else (selected?.remark ?: "No server selected"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            ServerSummaryCard(
                title = selected?.remark ?: "Choose a server",
                subtitle = selected?.let {
                    val ms = if (it.latencyMs >= 0) " · ${it.latencyMs} ms" else ""
                    "Auto-failover on$ms"
                } ?: "Tap to add or import a config",
                onClick = onOpenServers
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Download, label = "Download",
                    value = if (connected) mbps(downBps) else "—"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Upload, label = "Upload",
                    value = if (connected) mbps(upBps) else "—"
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (connected) "Tap the orb to disconnect" else "Tap the orb to connect",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectOrb(connected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (connected) 5200 else 11000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val breathe by t.animateFloat(
        0.97f, if (connected) 1.05f else 1f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breathe"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(256.dp)) {
        Canvas(Modifier.size(256.dp).scale(if (connected) breathe else 1f)) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Accent.copy(alpha = if (connected) 0.30f else 0.10f), Color.Transparent)
                ),
                radius = size.minDimension / 2f
            )
        }
        // gradient ring with a gap at the top (matches the brand mark)
        Canvas(Modifier.size(212.dp).rotate(spin)) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Accent, AccentDeep, Color(0xFF8B5CF6), Accent)),
                startAngle = 300f, sweepAngle = 300f, useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                alpha = if (connected) 1f else 0.55f
            )
        }
        Box(
            Modifier.size(160.dp).scale(if (connected) breathe else 1f).clip(CircleShape)
                .background(
                    if (connected) Brush.radialGradient(listOf(Color(0xFF5FF3D0), Accent, Color(0xFF0B5E50)))
                    else Brush.radialGradient(listOf(Color(0xFF243039), Color(0xFF151B21)))
                )
                .clickable(enabled = !busy) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            else Text(
                "S", fontSize = 72.sp, fontWeight = FontWeight.Black,
                color = if (connected) Color(0xFF06231D) else Accent
            )
        }
    }
}

@Composable
private fun ServerSummaryCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A20)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Accent, AccentDeep))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Bolt, null, tint = Color(0xFF06231D)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11171D)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
    }
}

/* ──────────────────────────── SERVERS ─────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServersScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    var showSub by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Servers (${profiles.size})", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { vm.pingAll() }) { Text("Ping all") }
                    TextButton(onClick = { vm.selectFastest() }) { Text("Fastest") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSub = true },
                text = { Text("Subscription") },
                icon = { Icon(Icons.Filled.Add, null) },
                containerColor = Accent, contentColor = Color(0xFF06231D)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistRow(Icons.Filled.ContentPaste, "Paste link") {
                    vm.importFromClipboard(readClipboard(context))
                }
                AssistRow(Icons.Filled.QrCodeScanner, "Scan QR") { onScanQr() }
                Spacer(Modifier.weight(1f))
                val sortOn = sortMode == MainViewModel.SortMode.PING
                TextButton(onClick = { vm.toggleSort() }) {
                    Text(if (sortOn) "Ping ▲" else "Sort", color = if (sortOn) Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }

            if (subscriptions.isNotEmpty()) {
                SectionHeader("Subscriptions")
                subscriptions.forEach { sub ->
                    SubscriptionRow(sub, onRefresh = { vm.updateSubscriptions() }, onDelete = { vm.deleteSubscription(sub) })
                }
                Spacer(Modifier.height(8.dp))
                SectionHeader("Servers")
            }

            if (profiles.isEmpty()) {
                EmptyServers()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                    items(profiles, key = { it.id }) { p ->
                        ServerRow(
                            profile = p,
                            selected = p.id == selectedId,
                            onSelect = { vm.select(p.id) },
                            onOpen = { onOpenDetail(p.id) }
                        )
                    }
                }
            }
        }
    }

    if (showSub) SubscriptionDialog(
        onDismiss = { showSub = false },
        onAdd = { name, url -> vm.addSubscription(name, url); showSub = false }
    )
}

@Composable
private fun AssistRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label)
    }
}

@Composable
private fun ServerRow(profile: Profile, selected: Boolean, onSelect: () -> Unit, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .then(if (selected) Modifier.border(1.5.dp, Accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp)) else Modifier)
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = if (selected) Accent.copy(alpha = 0.12f) else Color(0xFF141A20)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = Accent))
            Column(Modifier.weight(1f)) {
                Text(profile.remark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${profile.protocol.tag.uppercase()} · ${profile.address}:${profile.port}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
            LatencyChip(profile.latencyMs)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun SubscriptionRow(sub: SubscriptionEntity, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11171D)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = Accent) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFE05B5B)) }
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
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyServers() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("No servers yet", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Paste a vmess/vless/trojan/ss link, scan a QR,\nor add a subscription with ＋",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

/* ─────────────────────────── DETAIL ──────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDetailScreen(
    profile: Profile,
    vm: MainViewModel,
    isActive: Boolean,
    connected: Boolean,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDeleted: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showRaw by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(profile.remark, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Button(
                onClick = { if (isActive) onDisconnect() else onConnect() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFF2A333B) else Accent,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onBackground else Color(0xFF06231D)
                )
            ) { Text(if (isActive) "Disconnect" else "Connect to this server", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(16.dp))
            DetailCard {
                InfoRow("Protocol", profile.protocol.tag.uppercase())
                InfoRow("Address", profile.address)
                InfoRow("Port", profile.port.toString())
                InfoRow("Transport", profile.network.value.uppercase())
                InfoRow("Security", profile.security.value.uppercase())
                if (profile.sni.isNotBlank()) InfoRow("SNI", profile.sni)
                if (profile.host.isNotBlank()) InfoRow("Host", profile.host)
                if (profile.path.isNotBlank()) InfoRow("Path", profile.path)
                if (profile.flow.isNotBlank()) InfoRow("Flow", profile.flow)
                InfoRow("Latency", if (profile.latencyMs >= 0) "${profile.latencyMs} ms" else "untested")
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.pingOne(profile) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Speed, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Test")
                }
                OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.shareLink(profile)))
                    vm.notify("Share link copied")
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy link")
                }
                OutlinedButton(onClick = { showQr = !showQr }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("QR")
                }
            }

            AnimatedVisibility(showQr) {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    QrImage(vm.shareLink(profile))
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { showRaw = !showRaw }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showRaw) "Hide raw config" else "View raw Xray config")
            }
            AnimatedVisibility(showRaw) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1116)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(vm.rawConfig(profile), Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
                }
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { vm.delete(profile); onDeleted() }) {
                Icon(Icons.Filled.Delete, null, tint = Color(0xFFE05B5B)); Spacer(Modifier.width(6.dp))
                Text("Delete this server", color = Color(0xFFE05B5B))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QrImage(text: String) {
    val bmp = remember(text) {
        runCatching {
            com.journeyapps.barcodescanner.BarcodeEncoder()
                .encodeBitmap(text, com.google.zxing.BarcodeFormat.QR_CODE, 600, 600)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
            Image(bmp, "QR", Modifier.size(220.dp))
        }
    } else Text("Couldn't render QR")
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A20)),
        shape = RoundedCornerShape(16.dp)
    ) { Column(Modifier.padding(4.dp), content = content) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/* ─────────────────────────── SETTINGS ─────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenPerApp: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenSystemVpn: () -> Unit
) {
    val s by vm.settingsState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {

            SectionHeader("Performance")
            SettingsCard {
                SwitchRow("Mux — reuse connections (snappier)", s.mux, vm::setMux)
                if (s.mux) StepperRow("Mux concurrency", s.muxConcurrency, 1, 16) { vm.setMuxConcurrency(it) }
            }

            SectionHeader("Anti-censorship")
            SettingsCard {
                SwitchRow("TLS fragmentation (defeats SNI/DPI blocking)", s.fragmentation, vm::setFragmentation)
                SwitchRow("Route IPv6 through the tunnel", s.ipv6, vm::setIpv6)
            }

            SectionHeader("Routing")
            SettingsCard {
                SwitchRow("Bypass LAN (local addresses go direct)", s.bypassLan, vm::setBypassLan)
                SwitchRow("Block ads (needs geo files)", s.blockAds, vm::setBlockAds)
                SwitchRow("Direct domestic .ir traffic (needs geo files)", s.domesticDirect, vm::setDomesticDirect)
            }

            SectionHeader("Apps & system")
            SettingsCard {
                NavRow(Icons.Filled.Apps, "Per-app proxy (split tunnel)",
                    subtitle = perAppSummary(s.perAppMode, s.perAppCount), onClick = onOpenPerApp)
                NavRow(Icons.Filled.Shield, "Always-on VPN & kill switch",
                    subtitle = "Open Android VPN settings", onClick = onOpenSystemVpn)
            }

            SectionHeader("About")
            SettingsCard {
                NavRow(Icons.Filled.SystemUpdate, "Check for updates",
                    subtitle = "Version ${com.smoothvpn.BuildConfig.VERSION_NAME}", onClick = onOpenUpdate)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun perAppSummary(mode: String, count: Int): String = when (mode) {
    SettingsStore.MODE_ALLOW -> "Only $count app(s) use the VPN"
    SettingsStore.MODE_DISALLOW -> "$count app(s) bypass the VPN"
    else -> "All apps use the VPN"
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A20)),
        shape = RoundedCornerShape(16.dp)
    ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun StepperRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        IconButton(onClick = { if (value > min) onChange(value - 1) }) { Text("–", fontSize = 22.sp, color = Accent) }
        Text("$value", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (value < max) onChange(value + 1) }) { Text("＋", fontSize = 18.sp, color = Accent) }
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
        color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

/* ─────────────────────────── PER-APP ──────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerAppScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settingsState.collectAsStateWithLifecycle()
    val selectedPkgs by vm.perAppPackages.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = AppList.load(context)
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Per-app proxy", fontWeight = FontWeight.Bold) },
                actions = { if (selectedPkgs.isNotEmpty()) TextButton(onClick = { vm.clearPerApp() }) { Text("Clear") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            ModeChips(s.perAppMode) { vm.setPerAppMode(it) }
            Spacer(Modifier.height(8.dp))

            val enabled = s.perAppMode != SettingsStore.MODE_OFF
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search apps") }, singleLine = true,
                enabled = enabled, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                !enabled -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Pick a mode above to choose apps.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                else -> {
                    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
                    LazyColumn(contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    )) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app, checked = app.packageName in selectedPkgs) { on ->
                                vm.togglePerApp(app.packageName, on)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChips(mode: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip("Off", mode == SettingsStore.MODE_OFF) { onPick(SettingsStore.MODE_OFF) }
        ModeChip("Only these", mode == SettingsStore.MODE_ALLOW) { onPick(SettingsStore.MODE_ALLOW) }
        ModeChip("All but these", mode == SettingsStore.MODE_DISALLOW) { onPick(SettingsStore.MODE_DISALLOW) }
    }
}

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (active) Accent.copy(alpha = 0.18f) else Color(0xFF141A20))
            .then(if (active) Modifier.border(1.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(label, color = if (active) Accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app.icon != null) Image(app.icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
        else Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF243039)), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = Accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = checked, onCheckedChange = onCheck, colors = CheckboxDefaults.colors(checkedColor = Accent))
    }
}

/* ─────────────────────────── UPDATE ──────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val checking by vm.updateChecking.collectAsStateWithLifecycle()
    val result by vm.updateState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Updates", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Icon(Icons.Filled.SystemUpdate, null, tint = Accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(14.dp))
            Text("SmoothVPN ${com.smoothvpn.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("github.com/rezaigh/SmoothVPN", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))

            Spacer(Modifier.height(24.dp))
            Button(onClick = { vm.checkForUpdate() }, enabled = !checking,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF06231D))) {
                if (checking) { CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF06231D), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (checking) "Checking…" else "Check for updates")
            }

            result?.let { r ->
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF141A20)), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (r.updateAvailable) "Update available: ${r.latest}" else "You're up to date",
                            fontWeight = FontWeight.Bold, color = if (r.updateAvailable) Accent else MaterialTheme.colorScheme.onBackground
                        )
                        if (r.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(r.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
                        }
                        if (r.updateAvailable && r.url.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { openUrl(context, r.url) },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF06231D))) {
                                Text("Open release page")
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ─────────────────────────── DIALOGS / HELPERS ─────────────────── */

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
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Subscription URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onAdd(name, url.trim()) }) { Text("Add & fetch") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun mbps(bps: Long): String {
    val m = bps / 1_000_000.0
    return if (m >= 100) "${m.toInt()} Mbps" else String.format("%.1f Mbps", m)
}

private fun readClipboard(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openSystemVpnSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    else runCatching { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun startVpn(context: Context, profileId: String) {
    val intent = Intent(context, XrayVpnService::class.java).apply {
        action = XrayVpnService.ACTION_START
        putExtra(XrayVpnService.EXTRA_PROFILE_ID, profileId)
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopVpn(context: Context) {
    context.startService(Intent(context, XrayVpnService::class.java).apply { action = XrayVpnService.ACTION_STOP })
}
