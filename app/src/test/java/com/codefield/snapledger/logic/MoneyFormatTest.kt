package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun wholeUnitCurrencies_haveNoDecimals() {
        assertEquals("₩153,846", MoneyFormat.format(Money(153_846, Currency.KRW)))
        assertEquals("日元写成 JP¥，和人民币分开", "JP¥1,200", MoneyFormat.format(Money(1_200, Currency.JPY)))
    }

    @Test
    fun centCurrencies_haveTwoDecimals() {
        assertEquals("¥23.50", MoneyFormat.format(Money(2_350, Currency.CNY)))
        assertEquals("$0.05", MoneyFormat.format(Money(5, Currency.USD)))
        assertEquals("€1,234.00", MoneyFormat.format(Money(123_400, Currency.EUR)))
    }
}
