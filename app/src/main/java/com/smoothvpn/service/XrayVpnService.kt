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
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * The system VPN, built on AndroidLibXrayLite's modern CoreController API.
 *
 *   TUN fd  ──▶  hev-socks5-tunnel  ──▶  127.0.0.1:10808 (Xray SOCKS)  ──▶  proxy
 *
 * The core runs SOCKS-only (startLoop config, 0) and hev-socks5-tunnel pumps the
 * TUN device into that SOCKS inbound — the same default design as v2rayNG. We
 * exclude our own app from the tunnel so the core's connection out to the proxy
 * server bypasses the VPN (this replaces the old per-socket protect()).
 *
 * The `full` flavor links the real libv2ray.aar (built in CI). The `mock` flavor
 * links a tiny Kotlin stub exposing the same names, so the UI runs with no engine.
 */
class XrayVpnService : VpnService() {

    companion object {
        const val TAG = "SmoothVPN"
        const val ACTION_START = "com.smoothvpn.START"
        const val ACTION_STOP = "com.smoothvpn.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        // Anti-DPI TLS fragmentation. Flip to false if a specific server ever stops working.
        private const val ENABLE_FRAGMENTATION = true

        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"

        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIF_ID = 1

        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var activeRemark: String = ""
        @Volatile var connectedSince: Long = 0L

        // True for the demo flavor that has no native engine.
        val isMock: Boolean get() = com.smoothvpn.BuildConfig.MOCK_ENGINE
    }

    private var controller: CoreController? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var tproxy: TProxyService? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repo: ProfileRepository
    private lateinit var settings: SettingsStore

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
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
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
            connectedSince = System.currentTimeMillis()
            Log.i(TAG, "mock connect: ${profile.remark}")
            return
        }

        val geoOk = GeoAssets.ensure(applicationContext)
        Libv2ray.initCoreEnv(GeoAssets.assetDir(applicationContext), "")

        val rawConfig = XrayConfigBuilder.build(profile, settings.toRoutingOptions(geoOk))
        val config = maybeFragment(rawConfig)

        if (!establishTun()) { stopVpn(); return }
        val fd = tunFd?.fd ?: run { stopVpn(); return }

        val ctrl = Libv2ray.newCoreController(Callback())
        controller = ctrl
        try {
            ctrl.startLoop(config, 0)   // 0 = SOCKS-only; hev bridges the TUN below
        } catch (e: Exception) {
            Log.e(TAG, "core startLoop failed", e)
            stopVpn(); return
        }

        // Bridge the TUN device into the core's local SOCKS inbound (hev-socks5-tunnel).
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
            Log.e(TAG, "tun2socks bridge failed", t)
            stopVpn(); return
        }

        isRunning = true
        activeRemark = profile.remark
        connectedSince = System.currentTimeMillis()
        Log.i(TAG, "VPN up: ${profile.remark}")
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

        // Exclude ourselves so the core's outbound to the proxy server goes
        // OUTSIDE the tunnel (replaces the old per-socket protect()).
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

    // ---- stop ---------------------------------------------------------------

    private fun stopVpn() {
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
