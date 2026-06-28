package com.smoothvpn.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smoothvpn.service.SplitTunnel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * All new power-user options in one place, persisted with DataStore. Expose these
 * in a new "Advanced" settings screen in ui/. Pair the read flow with your config
 * builder and VpnService so every option actually takes effect.
 */
private val Context.advancedStore: DataStore<Preferences> by preferencesDataStore("advanced_settings")

class AdvancedSettings(private val context: Context) {

    data class Snapshot(
        val fragmentEnabled: Boolean,
        val fragmentPackets: String,
        val fragmentLength: String,
        val fragmentInterval: String,
        val tlsFingerprint: String,
        val autoFailover: Boolean,
        val failoverThreshold: Int,
        val killSwitch: Boolean,
        val splitMode: SplitTunnel.Mode,
        val splitPackages: Set<String>,
        val dnsMode: String,        // "system" | "doh" | "dot"
        val dnsServer: String,      // e.g. https://1.1.1.1/dns-query
        val routingMode: String,    // "global" | "bypass_lan" | "bypass_domestic"
        val connectOnBoot: Boolean,
        val connectOnUntrustedWifi: Boolean,
        val ipv6Enabled: Boolean,
        val mtu: Int,
        val subAutoUpdateHours: Int,
    )

    private object Keys {
        val fragEnabled = booleanPreferencesKey("frag_enabled")
        val fragPackets = stringPreferencesKey("frag_packets")
        val fragLength = stringPreferencesKey("frag_length")
        val fragInterval = stringPreferencesKey("frag_interval")
        val fingerprint = stringPreferencesKey("tls_fingerprint")
        val autoFailover = booleanPreferencesKey("auto_failover")
        val failoverThreshold = intPreferencesKey("failover_threshold")
        val killSwitch = booleanPreferencesKey("kill_switch")
        val splitMode = stringPreferencesKey("split_mode")
        val splitPackages = stringPreferencesKey("split_packages")
        val dnsMode = stringPreferencesKey("dns_mode")
        val dnsServer = stringPreferencesKey("dns_server")
        val routingMode = stringPreferencesKey("routing_mode")
        val connectOnBoot = booleanPreferencesKey("connect_on_boot")
        val connectUntrusted = booleanPreferencesKey("connect_untrusted_wifi")
        val ipv6 = booleanPreferencesKey("ipv6_enabled")
        val mtu = intPreferencesKey("mtu")
        val subHours = intPreferencesKey("sub_auto_update_hours")
    }

    val snapshot: Flow<Snapshot> = context.advancedStore.data.map { p ->
        Snapshot(
            fragmentEnabled = p[Keys.fragEnabled] ?: true,
            fragmentPackets = p[Keys.fragPackets] ?: "tlshello",
            fragmentLength = p[Keys.fragLength] ?: "100-200",
            fragmentInterval = p[Keys.fragInterval] ?: "10-20",
            tlsFingerprint = p[Keys.fingerprint] ?: "chrome",
            autoFailover = p[Keys.autoFailover] ?: true,
            failoverThreshold = p[Keys.failoverThreshold] ?: 2,
            killSwitch = p[Keys.killSwitch] ?: false,
            splitMode = runCatching { SplitTunnel.Mode.valueOf(p[Keys.splitMode] ?: "OFF") }
                .getOrDefault(SplitTunnel.Mode.OFF),
            splitPackages = (p[Keys.splitPackages] ?: "").split(",").filter { it.isNotBlank() }.toSet(),
            dnsMode = p[Keys.dnsMode] ?: "doh",
            dnsServer = p[Keys.dnsServer] ?: "https://1.1.1.1/dns-query",
            routingMode = p[Keys.routingMode] ?: "bypass_lan",
            connectOnBoot = p[Keys.connectOnBoot] ?: false,
            connectOnUntrustedWifi = p[Keys.connectUntrusted] ?: false,
            ipv6Enabled = p[Keys.ipv6] ?: false,
            mtu = p[Keys.mtu] ?: 1500,
            subAutoUpdateHours = p[Keys.subHours] ?: 12,
        )
    }

    suspend fun setFragmentEnabled(v: Boolean) = put { it[Keys.fragEnabled] = v }
    suspend fun setAutoFailover(v: Boolean) = put { it[Keys.autoFailover] = v }
    suspend fun setKillSwitch(v: Boolean) = put { it[Keys.killSwitch] = v }
    suspend fun setSplit(mode: SplitTunnel.Mode, packages: Set<String>) = put {
        it[Keys.splitMode] = mode.name
        it[Keys.splitPackages] = packages.joinToString(",")
    }
    suspend fun setDns(mode: String, server: String) = put {
        it[Keys.dnsMode] = mode; it[Keys.dnsServer] = server
    }
    suspend fun setRoutingMode(v: String) = put { it[Keys.routingMode] = v }
    suspend fun setSubAutoUpdateHours(h: Int) = put { it[Keys.subHours] = h }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.advancedStore.edit(block)
    }
}
