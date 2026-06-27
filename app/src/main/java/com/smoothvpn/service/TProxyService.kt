package com.smoothvpn.service

import java.io.File

/**
 * Bridges the VPN TUN device into Xray's local SOCKS inbound using
 * hev-socks5-tunnel — the same engine v2rayNG uses by default.
 *
 * The native library is built in CI with -DPKGNAME=com/smoothvpn/service, so its
 * JNI entry points resolve to exactly this package + class name. The library is
 * only loaded the first time this class is touched, which never happens in the
 * `mock` flavor, so the demo build runs fine without the .so present.
 */
class TProxyService(
    private val filesDir: File,
    private val tunFd: Int,
    private val mtu: Int,
    private val ipv4: String,
    private val socksPort: Int
) {
    companion object {
        @JvmStatic external fun TProxyStartService(configPath: String, fd: Int)
        @JvmStatic external fun TProxyStopService()
        @JvmStatic external fun TProxyGetStats(): LongArray?

        init { System.loadLibrary("hev-socks5-tunnel") }
    }

    /** hev reads everything from a small YAML config; tunFd is handed in directly. */
    fun start() {
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: $ipv4")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        val cfg = File(filesDir, "hev-socks5-tunnel.yaml").apply { writeText(yaml) }
        TProxyStartService(cfg.absolutePath, tunFd)   // non-blocking; runs its own thread
    }

    fun stop() {
        runCatching { TProxyStopService() }
    }
}
