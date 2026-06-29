package com.smoothvpn.core

import org.json.JSONObject

/**
 * TLS / packet fragmentation (anti-DPI).
 * Rewrites a generated Xray config so the real proxy outbound dials through a
 * "freedom" outbound that fragments the TLS ClientHello, defeating most SNI/DPI
 * blocking. Also pins a realistic uTLS fingerprint.
 */
object FragmentOutbound {

    data class Options(
        val enabled: Boolean = true,
        val packets: String = "tlshello",
        val length: String = "100-200",
        val interval: String = "10-20",
        val fingerprint: String = "chrome",
    )

    private const val FRAGMENT_TAG = "frag-out"

    fun apply(config: JSONObject, opts: Options): JSONObject {
        if (!opts.enabled) return config
        val cfg = JSONObject(config.toString())
        val outbounds = cfg.optJSONArray("outbounds") ?: return config

        val primary = firstProxyOutbound(outbounds) ?: return config
        pinFingerprint(primary, opts.fingerprint)
        chainThroughFragment(primary)

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
        val key = when (stream.optString("security")) {
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
