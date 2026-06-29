package com.smoothvpn.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Placeholder boot receiver so the manifest entry resolves. Auto-connect on boot
 * is intentionally NOT enabled yet — starting a VPN from the background on modern
 * Android needs extra handling, so this is a safe no-op until we add that properly.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // No-op for now. Hook up auto-connect here later if you want it.
    }
}
