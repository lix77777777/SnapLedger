package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyConverterTest {

    private fun day(year: Int, month: Int, day: Int) = DailyRates(
        LocalDate.of(year, month, day),
        mapOf(Currency.EUR to BigDecimal.ONE, Currency.KRW to BigDecimal("1490.50"), Currency.CNY to BigDecimal("7.50")),
    )

    private val rates = day(2026, 10, 27)

    @Test
    fun sameCurrency_isUnchanged_evenWithoutRates() {
        assertEquals(Money(500, Currency.KRW), CurrencyConverter.convert(Money(500, Currency.KRW), Currency.KRW, null))
    }

    @Test
    fun wonToYuan_roundsToFen() {
        // 10000 ÷ 1490.50 × 7.50 = 50.3187…，四舍五入到分是 50.32。
        assertEquals(Money(5032, Currency.CNY), CurrencyConverter.convert(Money(10000, Currency.KRW), Currency.CNY, rates))
    }

    @Test
    fun yuanToWon_roundsToWon() {
        // 23.50 ÷ 7.50 × 1490.50 = 4670.23…
        assertEquals(Money(4670, Currency.KRW), CurrencyConverter.convert(Money(2350, Currency.CNY), Currency.KRW, rates))
    }

    @Test
    fun missingRate_givesNull() {
        assertNull(CurrencyConverter.convert(Money(100, Currency.USD), Currency.KRW, rates))
        assertNull(CurrencyConverter.convert(Money(100, Currency.CNY), Currency.KRW, null))
    }

    @Test
    fun rateTable_usesTheClosestEarlierDay() {
        val table = RateTable(listOf(day(2026, 10, 27), day(2026, 10, 24)))
        assertEquals("周日用周五的汇率", LocalDate.of(2026, 10, 24), table.ratesFor(LocalDate.of(2026, 10, 26))?.date)
        assertEquals(LocalDate.of(2026, 10, 27), table.ratesFor(LocalDate.of(2026, 10, 28))?.date)
        assertEquals("比所有记录都早时用最早的一天", LocalDate.of(2026, 10, 24), table.ratesFor(LocalDate.of(2026, 10, 1))?.date)
        assertNull(RateTable.EMPTY.ratesFor(LocalDate.of(2026, 10, 1)))
    }
}
