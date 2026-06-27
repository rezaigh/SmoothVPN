package com.smoothvpn.net

import java.net.InetSocketAddress
import java.net.Socket

object LatencyTester {

    private const val TIMEOUT_MS = 3_000

    /**
     * Measures TCP handshake time to host:port in milliseconds.
     * Returns -1 on failure/timeout.
     *
     * This is a connectivity ping, not a real proxied speed test — but it's
     * cheap, needs no running tunnel, and is exactly what's used to auto-rank
     * servers and pick the fastest one before connecting.
     */
    fun tcpPing(host: String, port: Int): Int {
        return try {
            val socket = Socket()
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            val elapsed = ((System.nanoTime() - start) / 1_000_000).toInt()
            socket.close()
            elapsed
        } catch (e: Exception) {
            -1
        }
    }
}
