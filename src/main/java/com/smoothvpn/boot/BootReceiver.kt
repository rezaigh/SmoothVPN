package com.smoothvpn.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smoothvpn.settings.AdvancedSettings
import com.smoothvpn.tile.SmoothTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Connects automatically after a reboot, but only if the user enabled
 * "connect on boot" in Advanced settings. Requires RECEIVE_BOOT_COMPLETED
 * and the receiver entry in the manifest snippet.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AdvancedSettings(context).snapshot.first()
                if (settings.connectOnBoot) {
                    // Reuse the tile's toggle path so there's a single connect entry point.
                    context.sendBroadcast(
                        Intent(SmoothTileService.ACTION_TOGGLE).setPackage(context.packageName)
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
