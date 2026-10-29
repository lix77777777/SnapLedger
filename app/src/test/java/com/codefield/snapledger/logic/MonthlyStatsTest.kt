package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyStatsTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val october = YearMonth.of(2026, 10)
    private val rates = RateTable(
        listOf(
            DailyRates(
                LocalDate.of(2026, 10, 1),
                mapOf(Currency.EUR to BigDecimal.ONE, Currency.KRW to BigDecimal("1500"), Currency.CNY to BigDecimal("7.5")),
            ),
        )
    )

    private fun on(month: Int, day: Int): Long = LocalDateTime.of(2026, month, day, 12, 0).atZone(seoul).toInstant().toEpochMilli()

    private fun won(id: Long, amount: Long, category: Category, merchant: String, month: Int, day: Int) =
        Entry(id, Money(amount, Currency.KRW), category, merchant, on(month, day))

    private val entries = listOf(
        won(1, 6500, Category.CAFE, "스타벅스", 10, 3),
        won(2, 12000, Category.GROCERY, "GS25", 10, 3),
        // 23.50 元 ÷ 7.5 × 1500 = 4700 韩元
        Entry(3, Money(2350, Currency.CNY), Category.CAFE, "瑞幸咖啡", on(10, 10)),
        won(4, 50000, Category.SHOPPING, "쿠팡", 10, 15),
        won(5, 6500, Category.CAFE, "스타벅스 ", 10, 20),
        // 没有美元汇率，换算不了
        Entry(6, Money(9900, Currency.USD), Category.ENTERTAINMENT, "Steam", on(10, 21)),
        won(7, 30000, Category.FOOD, "국밥집", 9, 30),
        won(8, 1000, Category.OTHER, "", 11, 1),
    )

    private fun summary() = MonthlyStats.summarize(entries, october, Currency.KRW, rates, seoul)

    @Test
    fun total_convertsEveryEntryToTheHomeCurrency() {
        val s = summary()
        assertEquals(6500L + 12000 + 4700 + 50000 + 6500, s.totalMinor)
        assertEquals(6, s.entryCount)
        assertEquals("美元那笔没有汇率", 1, s.unconvertedCount)
    }

    @Test
    fun categories_areSortedByAmount() {
        val s = summary()
        assertEquals(listOf(Category.SHOPPING, Category.CAFE, Category.GROCERY), s.byCategory.map { it.category })
        assertEquals(17700L, s.byCategory[1].minor)
        assertEquals(50000.0 / 79700, s.byCategory[0].share, 1e-9)
    }

    @Test
    fun days_merchants_andTheBiggestEntry() {
        val s = summary()
        assertEquals(
            mapOf(
                LocalDate.of(2026, 10, 3) to 18500L,
                LocalDate.of(2026, 10, 10) to 4700L,
                LocalDate.of(2026, 10, 15) to 50000L,
                LocalDate.of(2026, 10, 20) to 6500L,
            ),
            s.byDay,
        )
        assertEquals(listOf("쿠팡", "스타벅스", "GS25", "瑞幸咖啡"), s.topMerchants.map { it.merchant })
        assertEquals("名字前后的空格不算", 2, s.topMerchants[1].count)
        assertEquals(4L, s.biggest?.id)
        assertEquals(50000L, s.biggestMinor)
    }

    @Test
    fun previousMonth_isComparedWhenItHasEntries() {
        assertEquals(30000L, summary().previousTotalMinor)
        assertNull(MonthlyStats.summarize(entries, YearMonth.of(2026, 9), Currency.KRW, rates, seoul).previousTotalMinor)
    }

    @Test
    fun emptyMonth_isAllZero() {
        val s = MonthlyStats.summarize(entries, YearMonth.of(2026, 12), Currency.KRW, rates, seoul)
        assertEquals(0L, s.totalMinor)
        assertEquals(0, s.entryCount)
        assertNull(s.biggest)
        assertEquals("11 月有一笔", 1000L, s.previousTotalMinor)
    }

    @Test
    fun firstMonth_isTheMonthOfTheOldestEntry() {
        assertEquals(YearMonth.of(2026, 9), MonthlyStats.firstMonth(entries, seoul))
        assertNull(MonthlyStats.firstMonth(emptyList(), seoul))
    }
}
