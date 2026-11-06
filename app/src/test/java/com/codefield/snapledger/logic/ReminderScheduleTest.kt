package com.codefield.snapledger.logic

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, seoul)

    @Test
    fun middleOfTheMonth_goesToTheNextFirst() {
        assertEquals(at(2026, 12, 1, 10), ReminderSchedule.next(at(2026, 11, 6, 15, 30)))
    }

    @Test
    fun theFirstBeforeTen_isToday() {
        assertEquals(at(2026, 11, 1, 10), ReminderSchedule.next(at(2026, 11, 1, 9, 59)))
    }

    @Test
    fun theFirstAtTenOrLater_isNextMonth() {
        assertEquals(at(2026, 12, 1, 10), ReminderSchedule.next(at(2026, 11, 1, 10)))
    }

    @Test
    fun december_goesToNextYear() {
        assertEquals(at(2027, 1, 1, 10), ReminderSchedule.next(at(2026, 12, 15, 8)))
    }
}
