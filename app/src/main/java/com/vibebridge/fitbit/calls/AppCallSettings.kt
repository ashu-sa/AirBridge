package com.vibebridge.fitbit.calls

import android.content.Context
import com.vibebridge.fitbit.AirHapticPattern

data class AppCallConfig(val enabled: Boolean = false, val packages: Set<String> = emptySet(),
                         val pattern: AirHapticPattern = AirHapticPattern.URGENT) {
    fun allows(pkg: String) = enabled && pkg in packages
}
data class SuggestedCallApp(val label: String, val packages: Set<String>)
object AppCallSettings {
    const val MAX_APPS = 20
    val patterns = listOf(AirHapticPattern.URGENT, AirHapticPattern.SINGLE, AirHapticPattern.DOUBLE, AirHapticPattern.TRIPLE)
    val suggested = listOf(
        SuggestedCallApp("WhatsApp", setOf("com.whatsapp")),
        SuggestedCallApp("Slack", setOf("com.Slack")),
        SuggestedCallApp("Google Meet", setOf("com.google.android.apps.tachyon", "com.google.android.apps.meetings")),
        SuggestedCallApp("Zoom", setOf("us.zoom.videomeetings"))
    )
    private val validPackage = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private fun prefs(c: Context) = c.getSharedPreferences("air_bridge_app_calls", 0)
    fun load(c: Context): AppCallConfig = runCatching {
        val p = prefs(c)
        val apps = p.getStringSet("packages", emptySet()).orEmpty().toSet()
        require(apps.size <= MAX_APPS && apps.all { validPackage.matches(it) })
        val pattern = AirHapticPattern.fromWireName(p.getString("pattern", "urgent").orEmpty())
        AppCallConfig(p.getBoolean("enabled", false), apps, pattern?.takeIf { it in patterns } ?: AirHapticPattern.URGENT)
    }.getOrElse { AppCallConfig() }
    fun save(c: Context, config: AppCallConfig) {
        require(config.packages.size <= MAX_APPS && config.packages.all { validPackage.matches(it) })
        require(config.pattern in patterns)
        prefs(c).edit().putBoolean("enabled", config.enabled).putStringSet("packages", config.packages.toSet())
            .putString("pattern", config.pattern.wireName).apply()
        if (!config.enabled) AppCallSessions.notifications.clear()
    }
}
