package com.smoothvpn.core

import org.json.JSONObject

/**
 * TLS / packet fragmentation (anti-DPI).
 *
 * This is the single highest-impact reliability feature for censored networks.
 * It rewrites a generated Xray config so the real proxy outbound dials *through*
 * a "freedom" outbound that fragments the TLS ClientHello, defeating most
 * SNI/keyword based DPI blocking. It also pins a realistic uTLS fingerprint.
 *
 * Wire-in: call FragmentOutbound.apply(config, options) at the end of your
 * XrayConfigBuilder, right before the JSON is handed to libv2ray.
 *
 *   val json = buildConfig(profile)            // your existing builder output
 *   val finalJson = FragmentOutbound.apply(json, settings.fragmentOptions())
 */
object FragmentOutbound {

    data class Options(
        val enabled: Boolean = true,
        /** "1-3" packets, or "tlshello" to fragment only the ClientHello. */
        val packets: String = "tlshello",
        /** byte length range of each fragment. */
        val length: String = "100-200",
        /** delay range in ms between fragments. */
        val interval: String = "10-20",
        /** uTLS fingerprint to imitate; "" leaves the config untouched. */
        val fingerprint: String = "chrome",
    )

    private const val FRAGMENT_TAG = "frag-out"

    /** Returns a new JSONObject; the input is not mutated. */
    fun apply(config: JSONObject, opts: Options): JSONObject {
        if (!opts.enabled) return config
        val cfg = JSONObject(config.toString())
        val outbounds = cfg.optJSONArray("outbounds") ?: return config

        val primary = firstProxyOutbound(outbounds) ?: return config
        pinFingerprint(primary, opts.fingerprint)
        chainThroughFragment(primary)

        // Insert the fragment outbound once.
        if (!hasTag(outbounds, FRAGMENT_TAG)) {
            outbounds.put(buildFragmentOutbound(opts))
        }
        return cfg
    }

    private fun firstProxyOutbound(outbounds: org.json.JSONArray): JSONObject? {
        for (i in 0 until outbounds.length()) {
            val o = outbounds.getJSONObject(i)
            val proto = o.optString("protocol")
            if (proto != "freedom" && proto != "blackhole" && proto != "dns") return o
        }
        return null
    }

    private fun pinFingerprint(outbound: JSONObject, fp: String) {
        if (fp.isBlank()) return
        val stream = outbound.optJSONObject("streamSettings") ?: return
        val security = stream.optString("security")
        val key = when (security) {
            "tls" -> "tlsSettings"
            "reality" -> "realitySettings"
            else -> return
        }
        val sec = stream.optJSONObject(key) ?: JSONObject().also { stream.put(key, it) }
        if (!sec.has("fingerprint") || sec.optString("fingerprint").isBlank()) {
            sec.put("fingerprint", fp)
        }
    }

    private fun chainThroughFragment(outbound: JSONObject) {
        val stream = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = stream.optJSONObject("sockopt")
            ?: JSONObject().also { stream.put("sockopt", it) }
        sockopt.put("dialerProxy", FRAGMENT_TAG)
    }

    private fun buildFragmentOutbound(opts: Options) = JSONObject().apply {
        put("tag", FRAGMENT_TAG)
        put("protocol", "freedom")
        put("settings", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("fragment", JSONObject().apply {
                put("packets", opts.packets)
                put("length", opts.length)
                put("interval", opts.interval)
            })
        })
    }

    private fun hasTag(arr: org.json.JSONArray, tag: String): Boolean {
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("tag") == tag) return true
        }
        return false
    }
}
