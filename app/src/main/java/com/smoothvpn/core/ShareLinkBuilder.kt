package com.smoothvpn.core

import android.util.Base64
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Turns a [Profile] back into a shareable link (vmess:// / vless:// / trojan:// /
 * ss://). The inverse of [OutboundParser]. Used by the config-detail screen for
 * "Copy link" and the QR export.
 */
object ShareLinkBuilder {

    fun build(p: Profile): String = when (p.protocol) {
        Protocol.VMESS -> vmess(p)
        Protocol.VLESS -> uri("vless", p.userId, p)
        Protocol.TROJAN -> uri("trojan", p.password.ifBlank { p.userId }, p)
        Protocol.SHADOWSOCKS -> shadowsocks(p)
    }

    private fun vmess(p: Profile): String {
        val o = JSONObject()
            .put("v", "2")
            .put("ps", p.remark)
            .put("add", p.address)
            .put("port", p.port.toString())
            .put("id", p.userId)
            .put("aid", p.alterId.toString())
            .put("scy", p.encryption.ifBlank { "auto" })
            .put("net", p.network.value)
            .put("type", p.headerType.ifBlank { "none" })
            .put("host", p.host)
            .put("path", p.path)
            .put("tls", if (p.security == Security.TLS) "tls" else "")
            .put("sni", p.sni)
            .put("alpn", p.alpn)
            .put("fp", p.fingerprint)
        val b64 = Base64.encodeToString(
            o.toString().toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "vmess://$b64"
    }

    private fun uri(scheme: String, userPart: String, p: Profile): String {
        val q = LinkedHashMap<String, String>()
        q["type"] = p.network.value
        q["security"] = p.security.value
        if (p.flow.isNotBlank()) q["flow"] = p.flow
        if (p.encryption.isNotBlank() && scheme == "vless") q["encryption"] = p.encryption
        if (p.sni.isNotBlank()) q["sni"] = p.sni
        if (p.host.isNotBlank()) q["host"] = p.host
        if (p.path.isNotBlank()) {
            if (p.network == Network.GRPC) q["serviceName"] = p.path else q["path"] = p.path
        }
        if (p.headerType.isNotBlank() && p.headerType != "none") q["headerType"] = p.headerType
        if (p.alpn.isNotBlank()) q["alpn"] = p.alpn
        if (p.fingerprint.isNotBlank()) q["fp"] = p.fingerprint
        if (p.publicKey.isNotBlank()) q["pbk"] = p.publicKey
        if (p.shortId.isNotBlank()) q["sid"] = p.shortId
        if (p.spiderX.isNotBlank()) q["spx"] = p.spiderX

        val query = q.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val frag = if (p.remark.isNotBlank()) "#${enc(p.remark)}" else ""
        return "$scheme://${enc(userPart)}@${p.address}:${p.port}?$query$frag"
    }

    private fun shadowsocks(p: Profile): String {
        // SIP002: ss://base64(method:password)@host:port#remark
        val userInfo = Base64.encodeToString(
            "${p.method}:${p.password}".toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING
        )
        val frag = if (p.remark.isNotBlank()) "#${enc(p.remark)}" else ""
        return "ss://$userInfo@${p.address}:${p.port}$frag"
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
