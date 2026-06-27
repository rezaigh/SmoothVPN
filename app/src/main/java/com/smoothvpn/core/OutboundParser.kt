package com.smoothvpn.core

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.util.UUID

/**
 * Parses proxy share links into [Profile]s.
 *
 * Supported:
 *   vmess://  (base64 JSON, the v2rayN standard)
 *   vless://  (URI with query params)
 *   trojan:// (URI with query params)
 *   ss://     (SIP002 and the older fully-base64 form)
 *
 * Returns null on anything it can't understand so callers can skip bad lines
 * in a subscription instead of crashing.
 */
object OutboundParser {

    fun parse(rawLink: String): Profile? {
        val link = rawLink.trim()
        if (link.isEmpty()) return null
        val scheme = link.substringBefore("://", "").lowercase()
        return try {
            when (Protocol.fromScheme(scheme)) {
                Protocol.VMESS -> parseVmess(link)
                Protocol.VLESS -> parseUriProtocol(link, Protocol.VLESS)
                Protocol.TROJAN -> parseUriProtocol(link, Protocol.TROJAN)
                Protocol.SHADOWSOCKS -> parseShadowsocks(link)
                null -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseMany(text: String): List<Profile> =
        text.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parse(it) }

    // ---- vmess:// : base64 of a JSON blob -----------------------------------

    private fun parseVmess(link: String): Profile? {
        val body = link.removePrefix("vmess://")
        val json = JSONObject(String(decodeB64(body)))

        val net = Network.from(json.optString("net", "tcp"))
        val tls = json.optString("tls", "")
        return Profile(
            id = UUID.randomUUID().toString(),
            remark = json.optString("ps").ifBlank { json.optString("add") },
            protocol = Protocol.VMESS,
            address = json.optString("add"),
            port = json.optString("port").toIntOrNull() ?: 0,
            userId = json.optString("id"),
            alterId = json.optString("aid", "0").toIntOrNull() ?: 0,
            encryption = json.optString("scy", "auto").ifBlank { "auto" },
            network = net,
            security = if (tls.equals("tls", true) || tls.equals("reality", true))
                Security.from(tls) else Security.NONE,
            sni = json.optString("sni").ifBlank { json.optString("host") },
            host = json.optString("host"),
            path = json.optString("path").ifBlank { grpcOrWsDefault(net) },
            alpn = json.optString("alpn"),
            fingerprint = json.optString("fp"),
            headerType = json.optString("type", "none").ifBlank { "none" }
        )
    }

    // ---- vless:// and trojan:// : standard URI with query --------------------

    private fun parseUriProtocol(link: String, proto: Protocol): Profile? {
        val uri = Uri.parse(link)
        val userInfo = uri.userInfo ?: return null   // uuid (vless) or password (trojan)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else return null

        val net = Network.from(uri.getQueryParameter("type"))
        val security = Security.from(uri.getQueryParameter("security"))

        return Profile(
            id = UUID.randomUUID().toString(),
            remark = Uri.decode(uri.fragment ?: "").ifBlank { host },
            protocol = proto,
            address = host,
            port = port,
            userId = if (proto == Protocol.VLESS) userInfo else "",
            password = if (proto == Protocol.TROJAN) userInfo else "",
            encryption = uri.getQueryParameter("encryption") ?: "none",
            flow = uri.getQueryParameter("flow") ?: "",
            network = net,
            security = security,
            sni = uri.getQueryParameter("sni")
                ?: uri.getQueryParameter("peer") ?: "",
            host = uri.getQueryParameter("host") ?: "",
            path = uri.getQueryParameter("path")
                ?: uri.getQueryParameter("serviceName")
                ?: grpcOrWsDefault(net),
            alpn = uri.getQueryParameter("alpn") ?: "",
            fingerprint = uri.getQueryParameter("fp") ?: "",
            publicKey = uri.getQueryParameter("pbk") ?: "",
            shortId = uri.getQueryParameter("sid") ?: "",
            spiderX = uri.getQueryParameter("spx") ?: "",
            headerType = uri.getQueryParameter("headerType") ?: "none"
        )
    }

    // ---- ss:// : SIP002 + legacy fully-base64 forms --------------------------

    private fun parseShadowsocks(link: String): Profile? {
        var body = link.removePrefix("ss://")
        val remark = if (body.contains('#'))
            Uri.decode(body.substringAfterLast('#')) else ""
        body = body.substringBeforeLast('#')

        // Form A (SIP002): ss://base64(method:pass)@host:port
        // Form B (legacy):  ss://base64(method:pass@host:port)
        val method: String; val password: String; val host: String; val port: Int

        if (body.contains('@')) {
            val creds = String(decodeB64(body.substringBefore('@')))
            method = creds.substringBefore(':')
            password = creds.substringAfter(':')
            val hp = body.substringAfter('@').substringBefore('?')
            host = hp.substringBeforeLast(':')
            port = hp.substringAfterLast(':').toIntOrNull() ?: return null
        } else {
            val decoded = String(decodeB64(body))
            method = decoded.substringBefore(':')
            val rest = decoded.substringAfter(':')
            password = rest.substringBeforeLast('@')
            val hp = rest.substringAfterLast('@')
            host = hp.substringBeforeLast(':')
            port = hp.substringAfterLast(':').toIntOrNull() ?: return null
        }

        return Profile(
            id = UUID.randomUUID().toString(),
            remark = remark.ifBlank { host },
            protocol = Protocol.SHADOWSOCKS,
            address = host,
            port = port,
            method = method,
            password = password
        )
    }

    private fun grpcOrWsDefault(net: Network) = if (net == Network.GRPC) "" else "/"

    /** Tolerant base64: handles url-safe alphabet and missing padding. */
    private fun decodeB64(input: String): ByteArray {
        var s = input.trim().replace('-', '+').replace('_', '/')
        when (s.length % 4) { 2 -> s += "=="; 3 -> s += "="; }
        return Base64.decode(s, Base64.DEFAULT)
    }
}
