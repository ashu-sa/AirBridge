package com.vibebridge.fitbit

import android.content.Context

data class AirNotificationRule(
    val packageName: String,
    val pattern: AirHapticPattern
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
    const val MAX_RULES = 10

    val DEFAULT_RULES_TEXT = """
        com.samsung.android.incallui=urgent
        com.google.android.dialer=urgent
        com.whatsapp=double
        org.telegram.messenger=single
        com.google.android.apps.messaging=single
    """.trimIndent()

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
                .putString(KEY_RULES, normalizeRules(parsed.rules))
                .apply()
            return true
        }
        return false
    }

    fun loadRules(context: Context): List<AirNotificationRule> {
        val parsed = parseRules(loadText(context))
        return if (parsed.isValid) parsed.rules else parseRules(DEFAULT_RULES_TEXT).rules
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
                errors += "Line ${index + 1}: Expected format 'package.name=pattern'"
                return@forEachIndexed
            }
            val packageName = parts[0]
            val pattern = AirHapticPattern.fromWireName(parts[1])
            when {
                !packageNameRegex.matches(packageName) ->
                    errors += "Line ${index + 1}: Invalid package name '$packageName'"
                !seenPackages.add(packageName) ->
                    errors += "Line ${index + 1}: Duplicate package '$packageName'"
                pattern == null ->
                    errors += "Line ${index + 1}: Unknown pattern '${parts[1]}'. Use single, double, triple, long_gap, or urgent."
                else -> rules += AirNotificationRule(packageName, pattern)
            }
        }
        return AirNotificationRuleParseResult(rules, errors)
    }

    fun normalizeRules(rules: List<AirNotificationRule>): String = rules.joinToString("\n") {
        "${it.packageName}=${it.pattern.wireName}"
    }
}
