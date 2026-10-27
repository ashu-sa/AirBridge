package com.vibebridge.fitbit.calls

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.ConnectionService
import android.telecom.TelecomManager
import com.vibebridge.fitbit.util.InstalledAppsManager

data class DetectedCallingApp(val packageName: String, val label: String, val reason: CallingAppReason)

/** Package declarations and package-only observations. Never starts another app or reads call content. */
object CallingAppsDiscovery {
    private data class CachedApps(val apps: List<DetectedCallingApp>, val covered: Set<String>, val at: Long)
    @Volatile private var cached: CachedApps? = null
    private const val CACHE_MS = 5 * 60 * 1000L
    fun invalidate() { cached = null }
    fun cachedApps(selected: Set<String> = emptySet()): List<DetectedCallingApp>? {
        val entry = cached ?: return null
        return entry.apps.takeIf { android.os.SystemClock.elapsedRealtime() - entry.at < CACHE_MS && selected.all { it in entry.covered } }
    }
    private const val PREFS = "air_bridge_call_discovery"
    private fun observed(c: Context): Set<String> = runCatching {
        c.getSharedPreferences(PREFS, 0).getStringSet("observed", emptySet()).orEmpty().toSet()
    }.getOrDefault(emptySet())
    fun recordCallNotification(c: Context, pkg: String) {
        val previous = observed(c)
        if (pkg in previous || previous.size >= 64 || pkg == c.packageName) return
        c.getSharedPreferences(PREFS, 0).edit().putStringSet("observed", previous + pkg).apply()
        invalidate()
    }
    @Suppress("DEPRECATION")
    fun find(c: Context, selected: Set<String> = emptySet()): List<DetectedCallingApp> {
        cachedApps(selected)?.let { return it }
        val scanStarted = android.os.SystemClock.elapsedRealtime()
        val pm = c.packageManager
        val seenCalls = observed(c)
        val declaredServices = runCatching {
            pm.queryIntentServices(Intent(ConnectionService.SERVICE_INTERFACE), 0).map { it.serviceInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        val dialer = runCatching { c.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        val launchable = InstalledAppsManager.getInstalledLauncherApps(c, forceRefresh = true).map { it.packageName }.toSet()
        val candidates = launchable +
            KnownCallingApps.packages + declaredServices + seenCalls + selected
        val results = candidates.mapNotNull { pkg ->
            if (pkg == c.packageName) return@mapNotNull null
            runCatching {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
                val app = info.applicationInfo ?: return@runCatching null
                if (pkg !in launchable && pkg !in KnownCallingApps.packages && pkg !in selected &&
                    app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) return@runCatching null
                val declaresCalling = pkg in declaredServices ||
                    info.requestedPermissions?.contains("android.permission.MANAGE_OWN_CALLS") == true ||
                    info.services?.any { it.permission == "android.permission.BIND_TELECOM_CONNECTION_SERVICE" } == true
                val reason = CallingAppEvidence(app.enabled, pkg in KnownCallingApps.packages, declaresCalling,
                    pkg in seenCalls, pkg in selected,
                    pkg == dialer || pkg in setOf("com.android.server.telecom", "com.android.phone", "com.android.dialer",
                        "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui"))
                    .reason ?: return@runCatching null
                DetectedCallingApp(pkg, pm.getApplicationLabel(app).toString(), reason)
            }.getOrNull()
        }.sortedWith(compareBy<DetectedCallingApp> { it.reason == CallingAppReason.MANUAL }.thenBy { it.label.lowercase() })
        cached = CachedApps(results, selected + results.map { it.packageName }, scanStarted)
        return results
    }
}
