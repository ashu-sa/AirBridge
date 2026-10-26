package com.vibebridge.fitbit

import android.content.Context
import timber.log.Timber

data class AirNotificationRule(
    val packageName: String,
    val pattern: AirHapticPattern,
    val isEnabled: Boolean = true
)

data class AirNotificationRuleParseResult(
    val rules: List<AirNotificationRule>,
    val errors: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty() && rules.isNotEmpty()
}

object AirBridgeNotificationSettings {
    private const val PREFS_NAME = "air_notification_bridge"
    private const val KEY_RULES = "notification_rules"
    const val MAX_RULES = 5

    val DEFAULT_RULES = listOf(
        AirNotificationRule("com.whatsapp", AirHapticPattern.DOUBLE, true),
        AirNotificationRule("org.telegram.messenger", AirHapticPattern.SINGLE, true),
        AirNotificationRule("com.google.android.apps.messaging", AirHapticPattern.SINGLE, true),
        AirNotificationRule("com.instagram.android", AirHapticPattern.DOUBLE, true),
        AirNotificationRule("com.google.android.gm", AirHapticPattern.SINGLE, true)
    )

    val DEFAULT_RULES_TEXT = normalizeRules(DEFAULT_RULES)

    private val packageNameRegex = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun loadText(context: Context): String = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_RULES, DEFAULT_RULES_TEXT)
        ?: DEFAULT_RULES_TEXT

    fun saveRulesText(context: Context, text: String): Boolean {
        val parsed = parseRules(text)
        if (parsed.isValid) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RULES, normalizeRules(parsed.rules.take(MAX_RULES)))
                .apply()
            return true
        }
        return false
    }

    fun saveRules(context: Context, rules: List<AirNotificationRule>) {
        val capped = rules.take(MAX_RULES)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, normalizeRules(capped))
            .apply()
    }

    fun loadRules(context: Context): List<AirNotificationRule> {
        val text = loadText(context)
        val parsed = parseRules(text)
        val baseRules = if (parsed.rules.isNotEmpty()) parsed.rules else DEFAULT_RULES
        val result = baseRules.take(MAX_RULES).toMutableList()
        if (result.size < MAX_RULES) {
            for (defaultRule in DEFAULT_RULES) {
                if (result.size >= MAX_RULES) break
                if (result.none { it.packageName.equals(defaultRule.packageName, ignoreCase = true) }) {
                    result.add(defaultRule)
                }
            }
        }
        return result.sortedWith(compareByDescending { it.isEnabled })
    }

    fun setAppEnabled(context: Context, packageName: String, enabled: Boolean): List<AirNotificationRule> {
        val current = loadRules(context)
        val updated = current.map { rule ->
            if (rule.packageName.equals(packageName.trim(), ignoreCase = true)) {
                rule.copy(isEnabled = enabled)
            } else {
                rule
            }
        }.sortedWith(compareByDescending { it.isEnabled })
        saveRules(context, updated)
        return updated
    }

    fun setAppPattern(context: Context, packageName: String, pattern: AirHapticPattern): List<AirNotificationRule> {
        val current = loadRules(context)
        val updated = current.map { rule ->
            if (rule.packageName.equals(packageName.trim(), ignoreCase = true)) {
                rule.copy(pattern = pattern)
            } else {
                rule
            }
        }
        saveRules(context, updated)
        return updated
    }

    fun updateRule(context: Context, index: Int, newRule: AirNotificationRule): List<AirNotificationRule> {
        val current = loadRules(context).toMutableList()
        if (index in current.indices) {
            current[index] = newRule
            val sorted = current.sortedWith(compareByDescending { it.isEnabled })
            saveRules(context, sorted)
            return sorted
        }
        return current
    }

    fun resetToDefaults(context: Context): List<AirNotificationRule> {
        saveRules(context, DEFAULT_RULES)
        return DEFAULT_RULES
    }

    fun findRule(context: Context, packageName: String): AirNotificationRule? =
        loadRules(context).firstOrNull { it.packageName.equals(packageName.trim(), ignoreCase = true) }

    fun parseRules(value: String): AirNotificationRuleParseResult {
        val lines = value.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val errors = mutableListOf<String>()
        val rules = mutableListOf<AirNotificationRule>()
        val seenPackages = mutableSetOf<String>()

        if (lines.isEmpty()) errors += "At least one app rule is required."
        if (lines.size > MAX_RULES) errors += "Maximum $MAX_RULES rules supported."

        lines.take(MAX_RULES).forEachIndexed { index, line ->
            val parts = line.split('=', limit = 2).map(String::trim)
            if (parts.size != 2) {
                errors += "Line ${index + 1}: Expected format 'package.name=pattern' or 'package.name=pattern:enabled'"
                return@forEachIndexed
            }
            val packageName = parts[0]
            val patternAndEnabled = parts[1].split(':', limit = 2)
            val patternStr = patternAndEnabled[0].trim()
            val isEnabled = if (patternAndEnabled.size > 1) {
                patternAndEnabled[1].trim().toBooleanStrictOrNull() ?: true
            } else {
                true
            }

            val pattern = AirHapticPattern.fromWireName(patternStr)
            when {
                !packageNameRegex.matches(packageName) ->
                    errors += "Line ${index + 1}: Invalid package name '$packageName'"
                !seenPackages.add(packageName.lowercase()) ->
                    errors += "Line ${index + 1}: Duplicate package '$packageName'"
                pattern == null ->
                    errors += "Line ${index + 1}: Unknown pattern '$patternStr'. Use single, double, triple, long_gap, or urgent."
                else -> rules += AirNotificationRule(packageName, pattern, isEnabled)
            }
        }
        return AirNotificationRuleParseResult(rules, errors)
    }

    fun normalizeRules(rules: List<AirNotificationRule>): String = rules.joinToString("\n") {
        "${it.packageName}=${it.pattern.wireName}:${it.isEnabled}"
    }

    fun getAppName(context: Context, packageName: String): String {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info).toString()
            if (label.isNotBlank()) return label
        } catch (_: Exception) {
        }
        return when (packageName.lowercase()) {
            "com.whatsapp" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.google.android.apps.messaging" -> "Google Messages"
            "com.samsung.android.messaging" -> "Samsung Messages"
            "com.instagram.android" -> "Instagram"
            "com.google.android.gm" -> "Gmail"
            "com.slack" -> "Slack"
            "com.discord" -> "Discord"
            "org.thoughtcrime.securesms" -> "Signal"
            "com.microsoft.teams" -> "Teams"
            "com.microsoft.office.outlook" -> "Outlook"
            "com.twitter.android" -> "X (Twitter)"
            "com.samsung.android.incallui" -> "Samsung Phone"
            "com.google.android.dialer" -> "Google Phone"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
