package com.codefield.snapledger.system

import com.codefield.snapledger.data.FakeEntryDao
import com.codefield.snapledger.data.FakeRateDao
import com.codefield.snapledger.data.LedgerRepository
import com.codefield.snapledger.logic.DateSpan
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.remote.RateSource
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用假的汇率接口和内存数据库测试：下载哪几段、存没存、断网时保住已有的。 */
class RateUpdaterTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 29)
    private val repository = LedgerRepository(FakeEntryDao(), FakeRateDao())

    /** 记下被问了哪几段，每段返回 from 那天和 to（或今天）那天的汇率。 */
    private class FakeSource(private val today: LocalDate, private val offline: Boolean = false) : RateSource {
        val asked = mutableListOf<DateSpan>()

        override suspend fun fetch(from: LocalDate, to: LocalDate?): List<DailyRates> {
            asked += DateSpan(from, to)
            if (offline) throw IOException("offline")
            return listOf(from, to ?: today).distinct().map { DailyRates(it, mapOf(Currency.KRW to BigDecimal("1490"))) }
        }
    }

    private fun entryOn(date: LocalDate) = Entry(
        amount = Money(3000, Currency.CNY),
        category = Category.FOOD,
        merchant = "",
        time = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun firstTime_downloadsFromAWeekBeforeTheFirstEntry() = runBlocking {
        val source = FakeSource(today)
        val saved = RateUpdater(source, repository).update(listOf(entryOn(LocalDate.of(2026, 10, 10))), today, zone, checkNewer = true)
        assertEquals(listOf(DateSpan(LocalDate.of(2026, 10, 3), null)), source.asked)
        assertEquals(2, saved)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), today), repository.observeRates().first().map { it.date })
    }

    @Test
    fun upToDate_doesNotTouchTheNetwork() = runBlocking {
        repository.saveRates(listOf(LocalDate.of(2026, 10, 1), today).map { DailyRates(it, mapOf(Currency.KRW to BigDecimal("1490"))) })
        val source = FakeSource(today)
        val saved = RateUpdater(source, repository).update(listOf(entryOn(LocalDate.of(2026, 10, 10))), today, zone, checkNewer = true)
        assertTrue(source.asked.isEmpty())
        assertEquals(0, saved)
    }

    @Test
    fun offline_throws_andKeepsTheSavedRates() = runBlocking {
        val old = DailyRates(LocalDate.of(2026, 10, 20), mapOf(Currency.KRW to BigDecimal("1480")))
        repository.saveRates(listOf(old))
        val updater = RateUpdater(FakeSource(today, offline = true), repository)
        val failed = runCatching { updater.update(emptyList(), today, zone, checkNewer = true) }.exceptionOrNull()
        assertTrue(failed is IOException)
        assertEquals("断网时已经存下的汇率不受影响", listOf(old.date), repository.observeRates().first().map { it.date })
    }
}
