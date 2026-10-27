package com.vibebridge.fitbit.calls

import android.content.Context
import com.vibebridge.fitbit.AirHapticPattern

data class AppCallConfig(val enabled: Boolean = false, val packages: Set<String> = emptySet(),
                         val pattern: AirHapticPattern = AirHapticPattern.URGENT, val slots: List<String> = emptyList()) {
    fun allows(pkg: String) = enabled && pkg in packages
}
object AppCallSettings {
    const val MAX_APPS = CallingAppSlots.MAX
    val patterns = listOf(AirHapticPattern.URGENT, AirHapticPattern.SINGLE, AirHapticPattern.DOUBLE, AirHapticPattern.TRIPLE)
    private val validPackage = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private fun prefs(c: Context) = c.getSharedPreferences("air_bridge_app_calls", 0)
    fun load(c: Context): AppCallConfig = runCatching {
        val p = prefs(c)
        val apps = p.getStringSet("packages", emptySet()).orEmpty().toSet()
        require(apps.size <= 20 && apps.all { validPackage.matches(it) }) // Migrate earlier previews to five slots.
        val slots = p.getString("slots", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
        require(slots.size <= MAX_APPS && slots.distinct().size == slots.size && slots.all { validPackage.matches(it) })
        val selected = if (slots.isEmpty()) (CallingAppSlots.preferred + apps.sorted()).filter { it in apps }.distinct().take(MAX_APPS).toSet()
            else apps.intersect(slots.toSet())
        val pattern = AirHapticPattern.fromWireName(p.getString("pattern", "urgent").orEmpty())
        AppCallConfig(p.getBoolean("enabled", false), selected, pattern?.takeIf { it in patterns } ?: AirHapticPattern.URGENT, slots)
    }.getOrElse { AppCallConfig() }
    fun save(c: Context, config: AppCallConfig) {
        require(config.packages.size <= MAX_APPS && config.packages.all { validPackage.matches(it) })
        require(config.pattern in patterns)
        require(config.slots.size <= MAX_APPS && config.slots.distinct().size == config.slots.size && config.slots.all { validPackage.matches(it) })
        require(config.slots.isEmpty() || config.packages.all { it in config.slots })
        prefs(c).edit().putBoolean("enabled", config.enabled).putStringSet("packages", config.packages.toSet())
            .putString("pattern", config.pattern.wireName).putString("slots", config.slots.joinToString("\n")).apply()
        if (!config.enabled) AppCallSessions.notifications.clear()
    }
}
