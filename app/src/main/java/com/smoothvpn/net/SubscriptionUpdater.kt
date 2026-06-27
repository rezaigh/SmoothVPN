package com.smoothvpn.net

import java.net.HttpURLConnection
import java.net.URL

object SubscriptionUpdater {

    private const val UA = "SmoothVPN/1.0 (Android)"
    private const val TIMEOUT_MS = 15_000

    /**
     * Fetches a subscription URL and returns the raw body.
     * Many providers gate on a recognisable UA, so we send one and also
     * accept the v2rayN-style "Subscription-Userinfo" header silently.
     */
    fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code from subscription")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
