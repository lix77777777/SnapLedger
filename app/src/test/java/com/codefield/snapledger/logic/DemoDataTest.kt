package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, seoul)

    private fun months() = DemoData.entries(now).groupBy { YearMonth.from(Instant.ofEpochMilli(it.time).atZone(seoul)) }

    @Test
    fun coversLastMonthAndThisMonthUpToNow() {
        val months = months()
        assertEquals(setOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months.keys)
        assertTrue("上个月有几十笔", months.getValue(YearMonth.of(2026, 10)).size >= 40)
        assertTrue("没有比现在还晚的账", months.values.flatten().all { it.time <= now.toInstant().toEpochMilli() })
    }

    @Test
    fun sameEveryTime_andHasForeignCurrencyAndPlaces() {
        val entries = DemoData.entries(now)
        assertEquals(entries, DemoData.entries(now))
        assertTrue(entries.any { it.amount.currency == Currency.CNY })
        assertTrue("大部分有位置", entries.count { it.location != null } > entries.size / 2)
    }
}
