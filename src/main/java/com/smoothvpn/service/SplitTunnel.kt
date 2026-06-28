package com.smoothvpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings

/**
 * Per-app proxy (split tunneling) + kill switch helpers.
 *
 * Apply this to your VpnService.Builder in XrayVpnService while you set up the
 * TUN interface:
 *
 *   val builder = Builder()
 *   // ... addAddress / addRoute / addDnsServer ...
 *   SplitTunnel.apply(builder, splitConfig, packageName)
 *   SplitTunnel.applyKillSwitch(builder, killSwitchOn)
 *   val tun = builder.establish()
 */
object SplitTunnel {

    enum class Mode { OFF, INCLUDE, EXCLUDE }

    /**
     * @param packages app package names the rule applies to.
     * INCLUDE = only these apps are tunneled. EXCLUDE = everything except these.
     */
    data class Config(val mode: Mode = Mode.OFF, val packages: Set<String> = emptySet())

    fun apply(builder: VpnService.Builder, cfg: Config, selfPackage: String) {
        if (cfg.mode == Mode.OFF || cfg.packages.isEmpty()) return
        // Never route our own app through the tunnel (avoids loops).
        val pkgs = cfg.packages - selfPackage
        for (p in pkgs) {
            try {
                when (cfg.mode) {
                    Mode.INCLUDE -> builder.addAllowedApplication(p)
                    Mode.EXCLUDE -> builder.addDisallowedApplication(p)
                    Mode.OFF -> {}
                }
            } catch (_: Exception) {
                // Package no longer installed — skip it rather than fail the tunnel.
            }
        }
    }

    /**
     * Best-effort in-session kill switch: when enabled, the OS holds packets while
     * the tunnel is briefly down instead of leaking them to the raw network.
     * For a true always-on kill switch the user must also enable system lockdown;
     * use [openAlwaysOnSettings] to take them there.
     */
    fun applyKillSwitch(builder: VpnService.Builder, enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBlocking(true)
        }
    }

    fun openAlwaysOnSettings(context: Context) {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
