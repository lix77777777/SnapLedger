package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateFinderTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, 28, hour, minute).atZone(seoul).toInstant().toEpochMilli()

    private fun entry(id: Long, minor: Long, merchant: String, time: Long, currency: Currency = Currency.KRW) =
        Entry(id = id, amount = Money(minor, currency), category = Category.CAFE, merchant = merchant, time = time)

    private val saved = listOf(
        entry(1, 6500, "Starbucks", at(14, 32)),
        entry(2, 3800, "GS25", at(9, 5)),
    )

    @Test
    fun sameAmountMinutesApart_evenWithoutAMerchant() {
        assertEquals(saved[0], DuplicateFinder.find(saved, entry(0, 6500, "", at(14, 40)), seoul))
    }

    @Test
    fun sameAmountAndMerchantLaterThatDay() {
        assertEquals(saved[1], DuplicateFinder.find(saved, entry(0, 3800, "gs25 ", at(21, 0)), seoul))
    }

    @Test
    fun notDuplicates() {
        // 同一天同样的金额，但商家不同、时间差得远。
        assertNull(DuplicateFinder.find(saved, entry(0, 6500, "Ediya", at(19, 0)), seoul))
        // 数字一样，货币不同。
        assertNull(DuplicateFinder.find(saved, entry(0, 6500, "Starbucks", at(14, 32), Currency.JPY), seoul))
        // 修改已有的一笔时，不跟自己比。
        assertNull(DuplicateFinder.find(saved, saved[0], seoul))
    }
}
