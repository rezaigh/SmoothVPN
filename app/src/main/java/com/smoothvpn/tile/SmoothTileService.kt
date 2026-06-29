package com.smoothvpn.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.smoothvpn.service.XrayVpnService
import com.smoothvpn.ui.MainActivity

/**
 * One-tap connect tile. Connecting needs VPN consent + a chosen profile, so the
 * tile opens the app to connect; when already connected it stops the service directly.
 */
class SmoothTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (XrayVpnService.isRunning) {
            // Stop directly.
            startService(
                Intent(this, XrayVpnService::class.java).setAction(XrayVpnService.ACTION_STOP)
            )
        } else {
            // Open the app to pick/confirm and connect (handles VPN consent).
            val open = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(open)
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = XrayVpnService.isRunning
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SmoothVPN"
        tile.updateTile()
    }
}
