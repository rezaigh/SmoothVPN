package com.smoothvpn.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Real throughput test (not just latency) measured *through* the active tunnel by
 * routing the probe over Xray's local SOCKS inbound (127.0.0.1:10808). Lets users
 * compare servers meaningfully instead of guessing from ping alone.
 *
 *   val r = SpeedTester.measure()
 *   // r.mbps, r.latencyMs
 */
object SpeedTester {

    data class Result(val mbps: Double, val latencyMs: Long, val bytes: Long)

    /**
     * @param testUrl a reasonably large static file reachable through the proxy.
     * @param socksPort Xray local SOCKS port (matches your VpnService setup).
     */
    suspend fun measure(
        testUrl: String = "https://speed.cloudflare.com/__down?bytes=10000000",
        socksPort: Int = 10808,
        timeoutMs: Int = 15_000,
    ): Result = withContext(Dispatchers.IO) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val conn = (URL(testUrl).openConnection(proxy) as HttpsURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
        }

        val tConnect = System.currentTimeMillis()
        conn.connect()
        val latency = System.currentTimeMillis() - tConnect

        var total = 0L
        val start = System.nanoTime()
        conn.inputStream.use { input: InputStream ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
            }
        }
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0
        conn.disconnect()

        val mbps = if (seconds > 0) (total * 8.0) / (seconds * 1_000_000.0) else 0.0
        Result(mbps = mbps, latencyMs = latency, bytes = total)
    }
}
