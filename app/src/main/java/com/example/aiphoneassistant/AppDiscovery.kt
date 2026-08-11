package com.example.aiphoneassistant

import android.content.Context
import android.content.Intent

object AppDiscovery {
    fun installedLaunchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return pm.queryIntentActivities(intent, 0).map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
    fun findApp(context: Context, query: String): AppInfo? {
        val q = query.trim().lowercase()
        val apps = installedLaunchableApps(context)
        return apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) || q.contains(it.label.lowercase()) }
    }
}
