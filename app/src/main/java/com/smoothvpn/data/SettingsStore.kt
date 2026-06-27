package com.smoothvpn.data

import android.content.Context
import com.smoothvpn.core.RoutingOptions

/** Tiny SharedPreferences wrapper for the routing toggles. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("smoothvpn_settings", Context.MODE_PRIVATE)

    var enableMux: Boolean
        get() = prefs.getBoolean(KEY_MUX, true)
        set(v) = prefs.edit().putBoolean(KEY_MUX, v).apply()

    var bypassLan: Boolean
        get() = prefs.getBoolean(KEY_LAN, true)
        set(v) = prefs.edit().putBoolean(KEY_LAN, v).apply()

    var blockAds: Boolean
        get() = prefs.getBoolean(KEY_ADS, false)
        set(v) = prefs.edit().putBoolean(KEY_ADS, v).apply()

    var domesticDirect: Boolean
        get() = prefs.getBoolean(KEY_DOMESTIC, false)
        set(v) = prefs.edit().putBoolean(KEY_DOMESTIC, v).apply()

    var lastProfileId: String?
        get() = prefs.getString(KEY_LAST, null)
        set(v) = prefs.edit().putString(KEY_LAST, v).apply()

    fun toRoutingOptions(geoAvailable: Boolean) = RoutingOptions(
        enableMux = enableMux,
        bypassLan = bypassLan,
        blockAds = blockAds,
        domesticDirect = domesticDirect,
        geoAssetsAvailable = geoAvailable
    )

    companion object {
        private const val KEY_MUX = "mux"
        private const val KEY_LAN = "bypass_lan"
        private const val KEY_ADS = "block_ads"
        private const val KEY_DOMESTIC = "domestic_direct"
        private const val KEY_LAST = "last_profile"
    }
}
