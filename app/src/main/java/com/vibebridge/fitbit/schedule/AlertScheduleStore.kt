package com.vibebridge.fitbit.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

object AlertScheduleStore {
    private const val PREFS = "air_bridge_schedule"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AlertSchedule {
        val prefs = prefs(context)
        val enabled = prefs.getBoolean("enabled", false)
        val raw = prefs.getString("windows", null)
            ?: return AlertSchedule(enabled, if (enabled) emptyList() else AlertSchedule.presets())
        val windows = runCatching {
            val array = JSONArray(raw)
            require(array.length() <= AlertSchedule.MAX_WINDOWS)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val days = item.getJSONArray("days")
                AlertWindow(item.getString("id"), item.getString("label"),
                    item.getInt("start"), item.getInt("end"),
                    (0 until days.length()).map { days.getInt(it) }.toSet(),
                    item.optBoolean("enabled", true))
            }.also { items ->
                require(items.all { it.isValid() })
                require(items.map { it.id }.distinct().size == items.size)
            }
        }.getOrElse { emptyList() } // An enabled, damaged schedule must fail closed.
        return AlertSchedule(enabled, windows)
    }

    fun save(context: Context, schedule: AlertSchedule) {
        require(schedule.windows.size <= AlertSchedule.MAX_WINDOWS)
        require(schedule.windows.all { it.isValid() })
        require(schedule.windows.map { it.id }.distinct().size == schedule.windows.size)
        val windows = JSONArray()
        schedule.windows.forEach { window ->
            windows.put(JSONObject().put("id", window.id).put("label", window.label)
                .put("start", window.startMinute).put("end", window.endMinute)
                .put("days", JSONArray(window.days.sorted())).put("enabled", window.enabled))
        }
        prefs(context).edit().putBoolean("enabled", schedule.enabled)
            .putString("windows", windows.toString()).apply()
    }

    fun allowsNow(context: Context): Boolean = load(context).allows(ZonedDateTime.now())

    fun pauseForSync(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong("sync_pause_until", now + 5 * 60_000L).apply()
    }

    fun clearSyncPause(context: Context) { prefs(context).edit().remove("sync_pause_until").apply() }

    fun isSyncPaused(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        prefs(context).getLong("sync_pause_until", 0L) > now

    fun blockReason(context: Context): String? = when {
        isSyncPaused(context) -> "Alerts paused for companion-app sync (up to 5 minutes)."
        !allowsNow(context) -> "Outside your active schedule. Alerts are paused."
        else -> null
    }
}
