package com.smoothvpn.service

import com.smoothvpn.core.FragmentOutbound
import org.json.JSONObject
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.smoothvpn.R
import com.smoothvpn.core.Profile
import com.smoothvpn.core.XrayConfigBuilder
import com.smoothvpn.data.ProfileRepository
import com.smoothvpn.data.SettingsStore
import com.smoothvpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * The system VPN, built on AndroidLibXrayLite's modern CoreController API.
 *
 *   TUN fd  ──▶  hev-socks5-tunnel  ──▶  127.0.0.1:10808 (Xray SOCKS)  ──▶  proxy
 *
 * Adds TLS fragmentation (anti-DPI) and auto health-check + failover: the active
 * server is probed through the proxy, and if it dies the service silently brings
 * up the fastest reachable server in the list.
 */
class XrayVpnService : VpnService() {

    companion object {
        const val TAG = "SmoothVPN"
        const val ACTION_START = "com.smoothvpn.START"
        const val ACTION_STOP = "com.smoothvpn.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        // Anti-DPI TLS fragmentation. Flip to false if a specific server ever stops working.
        private const val ENABLE_FRAGMENTATION = true

        // Auto health-check + failover.
        private const val ENABLE_AUTO_FAILOVER = true
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val FAILS_BEFORE_SWITCH = 2
        private const val PROBE_TIMEOUT_MS = 5_000
        private const val PROBE_URL = "http://cp.cloudflare.com/generate_204"

        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"

        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIF_ID = 1

        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var activeRemark: String = ""
        @Volatile var connectedSince: Long = 0L
        @Volatile var lastError: String = ""

        // True for the demo flavor that has no native engine.
        val isMock: Boolean get() = com.smoothvpn.BuildConfig.MOCK_ENGINE
    }

    private var controller: CoreController? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var tproxy: TProxyService? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repo: ProfileRepository
    private lateinit var settings: SettingsStore

    @Volatile private var currentProfileId: String? = null
    @Volatile private var switching: Boolean = false
    private var monitorJob: Job? = null

    // libv2ray talks back through this 3-method callback.
    private inner class Callback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long { stopVpn(); return 0 }
        override fun onEmitStatus(l: Long, s: String?): Long {
            Log.d(TAG, "core: $s"); return 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = ProfileRepository(applicationContext)
        settings = SettingsStore(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { lastError = ""; stopVpn(); return START_NOT_STICKY }
            else -> {
                val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId == null) { stopSelf(); return START_NOT_STICKY }
                scope.launch {
                    val profile = repo.getProfile(profileId)
                    if (profile == null) stopSelf() else startVpn(profile)
                }
            }
        }
        return START_STICKY
    }

    // ---- start --------------------------------------------------------------

    private fun startVpn(profile: Profile) {
        startForeground(NOTIF_ID, buildNotification(profile.remark))

        // Demo flavor: reflect "connected" so the UI is testable, no real tunnel.
        if (isMock) {
            isRunning = true
            activeRemark = "${profile.remark}  (demo — no tunnel)"
            currentProfileId = profile.id
            connectedSince = System.currentTimeMillis()
            Log.i(TAG, "mock connect: ${profile.remark}")
            return
        }

        if (!bringUp(profile)) { stopVpn(); return }
        if (ENABLE_AUTO_FAILOVER) startMonitor()
    }

    /** Brings up the engine for [profile]. Returns false on any failure. */
    private fun bringUp(profile: Profile): Boolean {
        val geoOk = GeoAssets.ensure(applicationContext)
        Libv2ray.initCoreEnv(GeoAssets.assetDir(applicationContext), "")

        val rawConfig = XrayConfigBuilder.build(profile, settings.toRoutingOptions(geoOk))
        val config = maybeFragment(rawConfig)

        if (!establishTun()) { lastError = "Couldn't create the VPN interface"; return false }
        val fd = tunFd?.fd ?: run { lastError = "VPN interface unavailable"; return false }

        val ctrl = Libv2ray.newCoreController(Callback())
        controller = ctrl
        try {
            ctrl.startLoop(config, 0)   // 0 = SOCKS-only; hev bridges the TUN below
        } catch (e: Exception) {
            Log.e(TAG, "core startLoop failed", e); lastError = e.message ?: "core start failed"; return false
        }

        try {
            val bridge = TProxyService(
                filesDir = filesDir,
                tunFd = fd,
                mtu = VPN_MTU,
                ipv4 = PRIVATE_VLAN4_CLIENT,
                socksPort = XrayConfigBuilder.SOCKS_PORT
            )
            bridge.start()
            tproxy = bridge
        } catch (t: Throwable) {
            Log.e(TAG, "tun2socks bridge failed", t); lastError = t.message ?: "tunnel bridge failed"; return false
        }

        isRunning = true
        lastError = ""
        activeRemark = profile.remark
        currentProfileId = profile.id
        connectedSince = System.currentTimeMillis()
        Log.i(TAG, "VPN up: ${profile.remark}")
        return true
    }

    /** Rewrites the config to fragment the TLS ClientHello (defeats most SNI/DPI blocking). */
    private fun maybeFragment(rawConfig: String): String {
        if (!ENABLE_FRAGMENTATION) return rawConfig
        return try {
            FragmentOutbound.apply(JSONObject(rawConfig), FragmentOutbound.Options()).toString()
        } catch (e: Exception) {
            Log.w(TAG, "fragmentation skipped: ${e.message}")
            rawConfig
        }
    }

    private fun establishTun(): Boolean {
        val builder = Builder()
            .setSession("SmoothVPN")
            .setMtu(VPN_MTU)
            .addAddress(PRIVATE_VLAN4_CLIENT, 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        runCatching { builder.addAddress(PRIVATE_VLAN6_CLIENT, 126).addRoute("::", 0) }

        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        tunFd = builder.establish()
        return tunFd != null
    }

    // ---- auto failover ------------------------------------------------------

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var fails = 0
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (!isRunning || switching) continue
                if (probeThroughProxy()) { fails = 0; continue }
                fails++
                Log.w(TAG, "health probe failed ($fails/$FAILS_BEFORE_SWITCH)")
                if (fails >= FAILS_BEFORE_SWITCH) { fails = 0; doFailover() }
            }
        }
    }

    /** Quick connectivity check sent THROUGH the proxy's local SOCKS inbound. */
    private fun probeThroughProxy(): Boolean = try {
        val proxy = java.net.Proxy(
            java.net.Proxy.Type.SOCKS,
            java.net.InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT)
        )
        val conn = (java.net.URL(PROBE_URL).openConnection(proxy) as java.net.HttpURLConnection).apply {
            connectTimeout = PROBE_TIMEOUT_MS
            readTimeout = PROBE_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
        }
        val code = conn.responseCode
        conn.disconnect()
        code == 204 || code in 200..299
    } catch (_: Exception) { false }

    private suspend fun doFailover() {
        switching = true
        try {
            val all = repo.profiles.first()
            val current = currentProfileId
            val candidates = all.filter { it.id != current }
            if (candidates.isEmpty()) { Log.w(TAG, "no failover candidates"); return }

            var best: Profile? = null
            var bestMs = Int.MAX_VALUE
            for (c in candidates) {
                val ms = repo.testLatency(c)
                if (ms in 0 until bestMs) { bestMs = ms; best = c }
            }
            val target = best ?: run { Log.w(TAG, "no reachable failover server"); return }
            switchTo(target)
        } finally {
            switching = false
        }
    }

    private fun switchTo(profile: Profile) {
        Log.i(TAG, "failover -> ${profile.remark}")
        activeRemark = "Switching to ${profile.remark}…"
        updateNotification(activeRemark)
        teardownEngine()
        if (!bringUp(profile)) { stopVpn(); return }
        updateNotification(profile.remark)
    }

    /** Tears down core + bridge + tun WITHOUT stopping the service (used for switching). */
    private fun teardownEngine() {
        runCatching { tproxy?.stop() }; tproxy = null
        runCatching { if (controller?.isRunning == true) controller?.stopLoop() }
        controller = null
        runCatching { tunFd?.close() }; tunFd = null
    }

    // ---- stop ---------------------------------------------------------------

    private fun stopVpn() {
        monitorJob?.cancel(); monitorJob = null
        switching = false
        currentProfileId = null
        isRunning = false
        activeRemark = ""
        connectedSince = 0L
        runCatching { tproxy?.stop() }; tproxy = null
        runCatching { if (controller?.isRunning == true) controller?.stopLoop() }
        controller = null
        runCatching { tunFd?.close() }; tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }

    // ---- notification -------------------------------------------------------

    private fun updateNotification(remark: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(remark))
        }
    }

    private fun buildNotification(remark: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN status", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, XrayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SmoothVPN connected")
            .setContentText(remark)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stopIntent).build())
            .build()
    }
}
