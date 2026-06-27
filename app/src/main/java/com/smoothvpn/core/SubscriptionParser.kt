package com.smoothvpn.core

import android.util.Base64

/**
 * Subscription bodies are (almost always) a base64 blob that decodes to a
 * newline-separated list of share links. Some providers serve the raw list
 * un-encoded, so we try base64 first and fall back to plain text.
 */
object SubscriptionParser {

    fun parse(responseBody: String, subscriptionId: String): List<Profile> {
        val text = tryDecodeBase64(responseBody) ?: responseBody
        return OutboundParser.parseMany(text)
            .map { it.copy(subscriptionId = subscriptionId) }
    }

    private fun tryDecodeBase64(body: String): String? {
        val cleaned = body.trim().replace("\n", "").replace("\r", "")
        // A links-list will contain "://" once decoded; if decoding doesn't
        // yield that, assume it was already plain text.
        return try {
            var s = cleaned.replace('-', '+').replace('_', '/')
            when (s.length % 4) { 2 -> s += "=="; 3 -> s += "="; }
            val decoded = String(Base64.decode(s, Base64.DEFAULT))
            if (decoded.contains("://")) decoded else null
        } catch (e: Exception) {
            null
        }
    }
}
