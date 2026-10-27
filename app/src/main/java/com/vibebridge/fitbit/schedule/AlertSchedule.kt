package com.vibebridge.fitbit.schedule

import java.time.ZonedDateTime

data class AlertWindow(
    val id: String,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    /** ISO weekdays: Monday=1 ... Sunday=7. Overnight windows belong to their start day. */
    val days: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true
) {
    fun isValid(): Boolean = id.isNotBlank() && label.isNotBlank() && label.length <= 40 &&
        startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute &&
        days.isNotEmpty() && days.all { it in 1..7 }

    fun contains(now: ZonedDateTime): Boolean {
        if (!enabled || !isValid()) return false
        val minute = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        return if (startMinute < endMinute) {
            day in days && minute >= startMinute && minute < endMinute
        } else {
            (minute >= startMinute && day in days) ||
                (minute < endMinute && now.minusDays(1).dayOfWeek.value in days)
        }
    }
}

data class AlertSchedule(val enabled: Boolean = false, val windows: List<AlertWindow> = presets()) {
    fun allows(now: ZonedDateTime): Boolean = !enabled || windows.any { it.contains(now) }

    companion object {
        const val MAX_WINDOWS = 6
        fun presets() = listOf(
            AlertWindow("workout", "Workout", 6 * 60, 8 * 60),
            AlertWindow("sleep", "Sleep", 22 * 60, 7 * 60)
        )
    }
}
