package com.codefield.snapledger.logic

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarHeatTest {

    // 2026 年 10 月 1 日是周四，2 月 1 日是周日。
    private val october = YearMonth.of(2026, 10)
    private val february = YearMonth.of(2026, 2)

    @Test
    fun leadingBlanks_dependOnTheFirstDayOfTheWeek() {
        assertEquals("从周一开始：一二三空着", 3, CalendarHeat.leadingBlanks(october, DayOfWeek.MONDAY))
        assertEquals("从周日开始：日一二三空着", 4, CalendarHeat.leadingBlanks(october, DayOfWeek.SUNDAY))
        assertEquals(0, CalendarHeat.leadingBlanks(february, DayOfWeek.SUNDAY))
    }

    @Test
    fun rows() {
        assertEquals(5, CalendarHeat.rows(october, DayOfWeek.MONDAY))
        assertEquals("28 天正好从周日开始，4 行", 4, CalendarHeat.rows(february, DayOfWeek.SUNDAY))
        assertEquals(5, CalendarHeat.rows(february, DayOfWeek.MONDAY))
    }

    @Test
    fun dayAt_skipsTheBlanks() {
        assertNull(CalendarHeat.dayAt(october, DayOfWeek.MONDAY, 2))
        assertEquals(1, CalendarHeat.dayAt(october, DayOfWeek.MONDAY, 3))
        assertEquals(31, CalendarHeat.dayAt(october, DayOfWeek.MONDAY, 33))
        assertNull("月底之后", CalendarHeat.dayAt(october, DayOfWeek.MONDAY, 34))
    }

    @Test
    fun level_growsWithTheShareOfTheBiggestDay() {
        assertEquals(0, CalendarHeat.level(0, 50_000))
        assertEquals("花得再少也是 1 级", 1, CalendarHeat.level(100, 50_000))
        assertEquals(2, CalendarHeat.level(25_000, 50_000))
        assertEquals(3, CalendarHeat.level(30_000, 50_000))
        assertEquals(CalendarHeat.LEVELS, CalendarHeat.level(50_000, 50_000))
        assertEquals(0, CalendarHeat.level(100, 0))
    }
}
