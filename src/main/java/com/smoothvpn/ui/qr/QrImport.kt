package com.smoothvpn.ui.qr

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * One-tap QR import that feeds straight into your existing share-link parser.
 *
 * Usage in a Compose screen (e.g. behind a "Scan QR" button):
 *
 *   val scan = rememberQrScanner { raw ->
 *       // raw is the decoded text, e.g. vless://... or a subscription URL
 *       viewModel.importFromShareLink(raw)   // your existing OutboundParser path
 *   }
 *   IconButton(onClick = scan) { Icon(Icons.Rounded.QrCodeScanner, "Scan QR") }
 *
 * Needs: implementation("com.journeyapps:zxing-android-embedded:4.3.0")
 */
@Composable
fun rememberQrScanner(onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onResult)
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan a config or subscription QR")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }
}
