package com.vibebridge.fitbit.schedule

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AlertScheduleTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)
    private fun scheduled(vararg windows: AlertWindow) = AlertSchedule(true, windows.toList())
    private val workout = AlertWindow("w", "Workout", 360, 480, setOf(1))

    @Test fun presetsAreEditableButSchedulingIsOffByDefault() {
        val config = AlertSchedule()
        assertFalse(config.enabled)
        assertEquals(listOf("Workout", "Sleep"), config.windows.map { it.label })
        assertTrue(config.allows(at(27, 12)))
    }
    @Test fun daytimeStartsInclusiveEndsExclusive() {
        val config = scheduled(workout)
        assertFalse(config.allows(at(26, 5, 59)))
        assertTrue(config.allows(at(26, 6)))
        assertTrue(config.allows(at(26, 7, 59)))
        assertFalse(config.allows(at(26, 8)))
        assertFalse(config.allows(at(27, 6)))
    }
    @Test fun overnightWindowBelongsToItsStartDay() {
        val config = scheduled(AlertWindow("s", "Sleep", 1320, 420, setOf(1)))
        assertFalse(config.allows(at(26, 6)))
        assertTrue(config.allows(at(26, 22)))
        assertTrue(config.allows(at(27, 0)))
        assertTrue(config.allows(at(27, 6, 59)))
        assertFalse(config.allows(at(27, 7)))
        assertFalse(config.allows(at(27, 22)))
    }
    @Test fun sundayOvernightWrapsIntoMonday() {
        val config = scheduled(AlertWindow("s", "Sleep", 1320, 420, setOf(7)))
        assertTrue(config.allows(at(25, 23)))
        assertTrue(config.allows(at(26, 6)))
        assertFalse(config.allows(at(26, 22)))
    }
    @Test fun overlappingWindowsFormAUnion() {
        val config = scheduled(workout, workout.copy(id="b", startMinute=450, endMinute=540))
        assertTrue(config.allows(at(26, 8, 30)))
        assertFalse(config.allows(at(26, 9)))
    }
    @Test fun noEnabledWindowsFailsClosed() {
        assertFalse(scheduled().allows(at(26, 6)))
        assertFalse(scheduled(workout.copy(enabled=false)).allows(at(26, 6)))
    }
    @Test fun malformedWindowsCannotEnableAllDayAlerts() {
        val broken = listOf(workout.copy(startMinute=-1), workout.copy(endMinute=1440),
            workout.copy(days=emptySet()), workout.copy(days=setOf(8)),
            workout.copy(startMinute=360,endMinute=360), workout.copy(label=""))
        broken.forEach { assertFalse(scheduled(it).allows(at(26, 6))) }
    }
    @Test fun midnightBoundariesDoNotLeakAcrossDays() {
        val late = scheduled(AlertWindow("l", "Late", 1380, 0, setOf(1)))
        assertTrue(late.allows(at(26,23,59)))
        assertFalse(late.allows(at(27,0)))
        val early = scheduled(AlertWindow("e", "Early",0,60,setOf(2)))
        assertTrue(early.allows(at(27,0)))
        assertFalse(early.allows(at(27,1)))
    }
    @Test fun followsThePhonesLocalZoneRatherThanFixedUTC() {
        val config = scheduled(workout.copy(days=(1..7).toSet()))
        val india = at(26, 6, 30)
        assertTrue(config.allows(india))
        assertFalse(config.allows(india.withZoneSameInstant(ZoneId.of("America/New_York"))))
    }
    @Test fun daylightSavingRepeatedHourIsAllowedInBothOccurrences() {
        val ny = ZoneId.of("America/New_York")
        val config = scheduled(AlertWindow("d", "DST", 60,120))
        val local = LocalDateTime.of(2026,11,1,1,30)
        assertTrue(config.allows(local.atZone(ny).withEarlierOffsetAtOverlap()))
        assertTrue(config.allows(local.atZone(ny).withLaterOffsetAtOverlap()))
        assertFalse(config.allows(ZonedDateTime.of(2026,3,8,3,0,0,0,ny)))
    }
    @Test fun everyMinuteOfAWeekMatchesAnIndependentExpandedIntervalReference() {
        val windows = listOf(workout, AlertWindow("s","Sleep",1320,420,setOf(2,4,7)))
        val monday = at(26,0)
        val expected = BooleanArray(7*1440)
        windows.forEach { w -> w.days.forEach { day ->
            val length = if(w.endMinute>w.startMinute) w.endMinute-w.startMinute else 1440-w.startMinute+w.endMinute
            repeat(length) { offset -> expected[((day-1)*1440+w.startMinute+offset) % expected.size] = true }
        } }
        val config = AlertSchedule(true,windows)
        expected.indices.forEach { minute ->
            assertEquals("Minute $minute", expected[minute], config.allows(monday.plusMinutes(minute.toLong())))
        }
    }
}
