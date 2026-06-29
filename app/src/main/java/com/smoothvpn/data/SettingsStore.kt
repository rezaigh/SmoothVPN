package com.smoothvpn.data

import android.content.Context
import com.smoothvpn.core.RoutingOptions

/**
 * SharedPreferences wrapper for all user-tunable behaviour: routing toggles,
 * anti-DPI fragmentation, IPv6, per-app proxy mode + list, mux concurrency and
 * the last-used / sort state. Everything the Settings screen writes lives here,
 * and the VpnService + XrayConfigBuilder read straight from it.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("smoothvpn_settings", Context.MODE_PRIVATE)

    // ---- routing ------------------------------------------------------------

    var enableMux: Boolean
        get() = prefs.getBoolean(KEY_MUX, true)
        set(v) = prefs.edit().putBoolean(KEY_MUX, v).apply()

    var muxConcurrency: Int
        get() = prefs.getInt(KEY_MUX_N, 8)
        set(v) = prefs.edit().putInt(KEY_MUX_N, v.coerceIn(1, 32)).apply()

    var bypassLan: Boolean
        get() = prefs.getBoolean(KEY_LAN, true)
        set(v) = prefs.edit().putBoolean(KEY_LAN, v).apply()

    var blockAds: Boolean
        get() = prefs.getBoolean(KEY_ADS, false)
        set(v) = prefs.edit().putBoolean(KEY_ADS, v).apply()

    var domesticDirect: Boolean
        get() = prefs.getBoolean(KEY_DOMESTIC, false)
        set(v) = prefs.edit().putBoolean(KEY_DOMESTIC, v).apply()

    // ---- transport / anti-DPI ----------------------------------------------

    /** Fragment the TLS ClientHello to defeat most SNI-based DPI blocking. */
    var fragmentation: Boolean
        get() = prefs.getBoolean(KEY_FRAG, true)
        set(v) = prefs.edit().putBoolean(KEY_FRAG, v).apply()

    /** Route IPv6 through the tunnel too. Off by default (some servers are v4-only). */
    var ipv6: Boolean
        get() = prefs.getBoolean(KEY_IPV6, false)
        set(v) = prefs.edit().putBoolean(KEY_IPV6, v).apply()

    // ---- per-app proxy (split tunnelling) -----------------------------------

    /** off = all apps tunnelled · allow = only [perAppPackages] · disallow = all except them. */
    var perAppMode: String
        get() = prefs.getString(KEY_PERAPP_MODE, MODE_OFF) ?: MODE_OFF
        set(v) = prefs.edit().putString(KEY_PERAPP_MODE, v).apply()

    var perAppPackages: Set<String>
        get() = prefs.getStringSet(KEY_PERAPP_LIST, emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_PERAPP_LIST, v).apply()

    // ---- bookkeeping --------------------------------------------------------

    var lastProfileId: String?
        get() = prefs.getString(KEY_LAST, null)
        set(v) = prefs.edit().putString(KEY_LAST, v).apply()

    var sortByPing: Boolean
        get() = prefs.getBoolean(KEY_SORT, false)
        set(v) = prefs.edit().putBoolean(KEY_SORT, v).apply()

    fun toRoutingOptions(geoAvailable: Boolean) = RoutingOptions(
        enableMux = enableMux,
        muxConcurrency = muxConcurrency,
        bypassLan = bypassLan,
        blockAds = blockAds,
        domesticDirect = domesticDirect,
        geoAssetsAvailable = geoAvailable
    )

    companion object {
        const val MODE_OFF = "off"
        const val MODE_ALLOW = "allow"      // only listed apps go through the VPN
        const val MODE_DISALLOW = "disallow" // listed apps bypass the VPN

        private const val KEY_MUX = "mux"
        private const val KEY_MUX_N = "mux_concurrency"
        private const val KEY_LAN = "bypass_lan"
        private const val KEY_ADS = "block_ads"
        private const val KEY_DOMESTIC = "domestic_direct"
        private const val KEY_FRAG = "fragmentation"
        private const val KEY_IPV6 = "ipv6"
        private const val KEY_PERAPP_MODE = "perapp_mode"
        private const val KEY_PERAPP_LIST = "perapp_list"
        private const val KEY_LAST = "last_profile"
        private const val KEY_SORT = "sort_by_ping"
    }
}
