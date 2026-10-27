package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {

    @Test
    fun validInputs() {
        assertEquals(Money(6500, Currency.KRW), AmountInput.parse("6500", Currency.KRW))
        assertEquals(Money(6500, Currency.KRW), AmountInput.parse("6,500", Currency.KRW))
        assertEquals(Money(1234567, Currency.KRW), AmountInput.parse(" 1,234,567 ", Currency.KRW))
        assertEquals(Money(2350, Currency.CNY), AmountInput.parse("23.5", Currency.CNY))
        assertEquals(Money(2350, Currency.CNY), AmountInput.parse("23.50", Currency.CNY))
        assertEquals(Money(50, Currency.USD), AmountInput.parse(".5", Currency.USD))
        assertEquals("全角也可以", Money(12000, Currency.KRW), AmountInput.parse("１２，０００", Currency.KRW))
    }

    @Test
    fun invalidInputs() {
        assertNull(AmountInput.parse("", Currency.KRW))
        assertNull(AmountInput.parse("0", Currency.KRW))
        assertNull(AmountInput.parse("-500", Currency.KRW))
        assertNull(AmountInput.parse("abc", Currency.KRW))
        assertNull("韩元没有小数", AmountInput.parse("6500.5", Currency.KRW))
        assertNull("人民币最多两位小数", AmountInput.parse("23.505", Currency.CNY))
        assertNull("逗号位置不对", AmountInput.parse("65,00", Currency.KRW))
    }

    @Test
    fun format_isWhatTheEditDialogShows() {
        assertEquals("6500", AmountInput.format(Money(6500, Currency.KRW)))
        assertEquals("23.50", AmountInput.format(Money(2350, Currency.CNY)))
        assertEquals("0.05", AmountInput.format(Money(5, Currency.USD)))
        assertEquals(Money(2350, Currency.CNY), AmountInput.parse(AmountInput.format(Money(2350, Currency.CNY)), Currency.CNY))
    }
}
