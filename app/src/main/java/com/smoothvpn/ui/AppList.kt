package com.smoothvpn.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One installable app shown in the per-app (split-tunnel) picker. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean
)

object AppList {

    /**
     * Lists apps that hold the INTERNET permission (the only ones a proxy can
     * meaningfully affect). User apps first, then system apps, each alphabetised.
     */
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val self = context.packageName
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        apps.asSequence()
            .filter { it.packageName != self }
            .filter { hasInternet(pm, it.packageName) }
            .map { ai ->
                AppEntry(
                    packageName = ai.packageName,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }
                        .getOrNull(),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            .toList()
    }

    private fun hasInternet(pm: PackageManager, pkg: String): Boolean = try {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
    } catch (_: Exception) { false }
}
