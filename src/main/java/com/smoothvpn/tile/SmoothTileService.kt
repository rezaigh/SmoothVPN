package com.smoothvpn.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile + (pair this with a home-screen widget) for one-tap connect
 * without opening the app.
 *
 * Implement the toggle by sending an intent your XrayVpnService already understands.
 * Replace ACTION_* and the service class with yours, and keep the tile state in
 * sync by calling [refresh] from your service when the connection state changes.
 *
 * Register in AndroidManifest (see manifest/AndroidManifest.snippet.xml).
 */
class SmoothTileService : TileService() {

    companion object {
        const val ACTION_TOGGLE = "com.smoothvpn.action.TOGGLE"
        // Set by your service so the tile shows the right state.
        @Volatile var connected: Boolean = false
    }

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        // Hand off to your VpnService. Prepare-permission is handled in-app on first run.
        val intent = Intent(ACTION_TOGGLE).setPackage(packageName)
        sendBroadcast(intent)
        connected = !connected
        render()
    }

    private fun render() {
        qsTile?.apply {
            state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "SmoothVPN"
            subtitle = if (connected) "Connected" else "Tap to connect"
            updateTile()
        }
    }
}
