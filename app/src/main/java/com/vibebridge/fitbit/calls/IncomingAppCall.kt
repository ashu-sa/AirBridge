package com.vibebridge.fitbit.calls

/** Metadata only. Titles, message bodies, people and action labels are deliberately excluded. */
data class CallNotificationMetadata(
    val category: String? = null,
    val callType: Int? = null,
    val fullScreen: Boolean = false,
    val actionCount: Int = 0,
    val insistent: Boolean = false,
    val groupSummary: Boolean = false,
    val chronometer: Boolean = false
) {
    val isCallRelated get() = category == "call" || callType != null
    fun isIncoming(): Boolean {
        if (groupSummary) return false
        if (callType != null) return callType == 1 // Android/AndroidX CallStyle incoming; 2=ongoing, 3=screening.
        // Conservative fallback for older call notifications. Ambiguous calls are not guessed from text.
        return category == "call" && actionCount >= 2 && (fullScreen || insistent) && !chronometer
    }
}

/** Generation tokens prevent a removed/reused notification ID from reviving an old queued alert. */
class CallNotificationSessions(private val limit: Int = 64, private val lifetimeMillis: Long = 90_000) {
    init { require(limit > 0 && lifetimeMillis > 0) }
    private data class Session(val token: Long, val pkg: String, val started: Long)
    private val sessions = linkedMapOf<String, Session>()
    private var sequence = 0L
    @Synchronized fun begin(key: String, pkg: String, now: Long): Long? {
        if (sessions.containsKey(key)) return null
        while (sessions.size >= limit) sessions.remove(sessions.keys.first())
        val token = ++sequence
        sessions[key] = Session(token, pkg, now)
        return token
    }
    @Synchronized fun active(token: Long, pkg: String, now: Long): Boolean {
        return sessions.values.any { it.token == token && it.pkg == pkg && now - it.started < lifetimeMillis }
    }
    @Synchronized fun end(key: String) { sessions.remove(key) }
    @Synchronized fun clear() { sessions.clear() }
    // Expired tokens stay deduplicated until removal or bounded eviction; updates must not re-ring a stale call.
}

object AppCallSessions { val notifications = CallNotificationSessions() }
