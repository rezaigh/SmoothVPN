package com.smoothvpn.service

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Copies geoip.dat / geosite.dat from APK assets into filesDir (where Xray looks
 * for them) on first run, and reports whether they're available.
 *
 * If you didn't bundle the assets, [available] is false and geo-based routing
 * rules are automatically skipped (see RoutingOptions.geoAssetsAvailable).
 */
object GeoAssets {

    private val files = listOf("geoip.dat", "geosite.dat")

    fun ensure(context: Context): Boolean {
        var ok = true
        for (name in files) {
            val out = File(context.filesDir, name)
            if (out.exists() && out.length() > 0) continue
            try {
                context.assets.open(name).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            } catch (e: Exception) {
                Log.w(XrayVpnService.TAG, "geo asset $name not bundled")
                ok = false
            }
        }
        return ok && files.all { File(context.filesDir, it).exists() }
    }

    fun assetDir(context: Context): String = context.filesDir.absolutePath
}
