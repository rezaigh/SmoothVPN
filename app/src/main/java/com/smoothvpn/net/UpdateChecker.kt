package com.smoothvpn.net

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer build. Compares the latest release tag
 * against the installed versionName. Pure-stdlib, runs on an IO dispatcher.
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/rezaigh/SmoothVPN/releases/latest"
    private const val TIMEOUT_MS = 12_000

    data class Result(
        val current: String,
        val latest: String,
        val notes: String,
        val url: String,
        val updateAvailable: Boolean
    )

    fun check(currentVersion: String): Result {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "SmoothVPN")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val notes = json.optString("body").take(600)
            val url = json.optString("html_url")
            return Result(
                current = currentVersion,
                latest = tag,
                notes = notes,
                url = url,
                updateAvailable = isNewer(tag, currentVersion)
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Loose semantic-ish comparison; ignores a leading 'v' and any suffix. */
    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .takeWhile { it.isDigit() || it == '.' }
            .split('.').mapNotNull { it.toIntOrNull() }
        val l = parts(latest); val c = parts(current)
        if (l.isEmpty()) return false
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val a = l.getOrElse(i) { 0 }; val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
