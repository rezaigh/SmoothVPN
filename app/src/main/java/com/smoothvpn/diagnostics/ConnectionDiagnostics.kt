package com.smoothvpn.diagnostics

object ConnectionDiagnostics {

    data class Diagnosis(val title: String, val cause: String, val suggestion: String)

    fun diagnose(error: String?): Diagnosis {
        val e = (error ?: "").lowercase()
        return when {
            e.contains("no such host") || e.contains("dns") -> Diagnosis(
                "Can't find the server",
                "DNS lookup failed or the domain is poisoned.",
                "Switch DNS to DoH in Settings, or use a server with an IP address."
            )
            e.contains("refused") -> Diagnosis(
                "Server refused the connection",
                "The port was rejected — the server may be down or the port blocked.",
                "Try another server in the group."
            )
            e.contains("reset") -> Diagnosis(
                "Connection was reset",
                "The handshake was cut mid-way — a classic sign of SNI-based DPI blocking.",
                "Fragmentation is on by default; try a REALITY server or another node."
            )
            e.contains("tls") && (e.contains("handshake") || e.contains("certificate")) -> Diagnosis(
                "TLS handshake failed",
                "Wrong SNI/fingerprint or active interference.",
                "Try another server, or verify the config's TLS settings."
            )
            e.contains("reality") -> Diagnosis(
                "REALITY handshake rejected",
                "Stale publicKey, shortId, or serverName.",
                "Refresh your subscription — REALITY keys may have rotated."
            )
            e.contains("timeout") || e.contains("deadline") -> Diagnosis(
                "Connection timed out",
                "No reply in time — server unreachable, overloaded, or throttled.",
                "Auto-failover will try a faster server; or pick the lowest-latency one."
            )
            e.contains("eof") -> Diagnosis(
                "Server closed the connection",
                "Dropped right after opening — often throttling or an unstable node.",
                "Switch servers; if it repeats everywhere, the network may be tightening."
            )
            e.isBlank() -> Diagnosis(
                "Disconnected",
                "The tunnel stopped without a specific error.",
                "Reconnect; if it keeps happening, try another server."
            )
            else -> Diagnosis(
                "Connection problem",
                error?.take(120) ?: "Unknown error.",
                "Try another server."
            )
        }
    }
}
