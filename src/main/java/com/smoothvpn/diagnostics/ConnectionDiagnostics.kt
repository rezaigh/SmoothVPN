package com.smoothvpn.diagnostics

/**
 * Turns cryptic Xray / network errors into a plain-language cause and a concrete
 * fix the user can act on. In a censorship context this is a real differentiator:
 * users otherwise just see "disconnected" and have no idea what to try next.
 *
 *   val d = ConnectionDiagnostics.diagnose(lastError, handshakeReached)
 *   showBanner(d.title, d.cause, d.suggestion)
 */
object ConnectionDiagnostics {

    data class Diagnosis(
        val title: String,
        val cause: String,
        val suggestion: String,
        val likelyBlocked: Boolean,
    )

    fun diagnose(error: String?, handshakeReached: Boolean = false): Diagnosis {
        val e = (error ?: "").lowercase()

        return when {
            e.contains("no such host") || e.contains("name resolution") || e.contains("dns") -> Diagnosis(
                "Can't find the server",
                "The server address couldn't be resolved — DNS is failing or the domain is poisoned.",
                "Switch DNS to DoH (e.g. 1.1.1.1/dns-query) in Settings, or use a server with an IP address.",
                likelyBlocked = true,
            )
            e.contains("connection refused") || e.contains("econnrefused") -> Diagnosis(
                "Server refused the connection",
                "The server is reachable but rejected the port — it may be down or the port is blocked.",
                "Try another server in the group, or check the config's port with your provider.",
                likelyBlocked = false,
            )
            e.contains("connection reset") || e.contains("econnreset") || (e.contains("reset") && handshakeReached) -> Diagnosis(
                "Connection was reset",
                "The TLS handshake was cut mid-way — a classic sign of SNI-based DPI blocking.",
                "Enable TLS fragmentation in Settings, or switch to a REALITY/Vision server.",
                likelyBlocked = true,
            )
            e.contains("tls") && (e.contains("handshake") || e.contains("certificate")) -> Diagnosis(
                "TLS handshake failed",
                "The secure handshake didn't complete — wrong SNI/fingerprint or active interference.",
                "Set fingerprint to chrome and turn on fragmentation, or verify the server's TLS settings.",
                likelyBlocked = true,
            )
            e.contains("reality") -> Diagnosis(
                "REALITY handshake rejected",
                "The REALITY parameters didn't match — usually a stale publicKey, shortId, or serverName.",
                "Re-import the config or refresh your subscription; REALITY keys may have rotated.",
                likelyBlocked = false,
            )
            e.contains("timeout") || e.contains("deadline") || e.contains("i/o timeout") -> Diagnosis(
                "Connection timed out",
                "No reply from the server in time — it's unreachable, overloaded, or throttled.",
                "Let auto-failover pick a faster server, or run a latency test and choose the lowest.",
                likelyBlocked = false,
            )
            e.contains("eof") -> Diagnosis(
                "Server closed the connection",
                "The link dropped right after opening — often throttling or an unstable node.",
                "Switch servers; if it repeats across nodes, enable fragmentation.",
                likelyBlocked = true,
            )
            e.contains("network is unreachable") || e.contains("no route") -> Diagnosis(
                "No network route",
                "The device itself has no usable network path right now.",
                "Check your Wi-Fi/mobile data, then reconnect.",
                likelyBlocked = false,
            )
            e.isBlank() -> Diagnosis(
                "Disconnected",
                "The tunnel stopped without a specific error.",
                "Reconnect; if it keeps happening, enable auto-failover and fragmentation.",
                likelyBlocked = false,
            )
            else -> Diagnosis(
                "Connection problem",
                "Unrecognized error: ${error?.take(140)}",
                "Try another server, then enable fragmentation if the issue persists.",
                likelyBlocked = false,
            )
        }
    }
}
