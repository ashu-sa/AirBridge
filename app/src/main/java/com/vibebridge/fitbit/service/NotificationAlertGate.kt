package com.vibebridge.fitbit.service

/** Metadata only. Failed service starts must not suppress a subsequent alert. */
internal class NotificationAlertGate {
    private val recent = LinkedHashMap<String, Long>()
    private val perApp = HashMap<String, Long>()
    private var lastGlobal: Long? = null

    fun canAlert(signature: String, app: String, now: Long): Boolean {
        recent.entries.removeAll { now - it.value >= 1_500L }
        perApp.entries.removeAll { now - it.value >= 3_000L }
        if (recent.containsKey(signature)) return false
        if (perApp[app]?.let { now - it < 3_000L } == true) return false
        return lastGlobal?.let { now - it >= 1_500L } ?: true
    }

    fun recordAccepted(signature: String, app: String, now: Long) {
        recent[signature] = now
        perApp[app] = now
        lastGlobal = now
        while (recent.size > 512) recent.remove(recent.keys.first())
    }
}
