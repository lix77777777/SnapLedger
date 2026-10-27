package com.codefield.snapledger.data

import com.codefield.snapledger.data.db.EntryDao
import com.codefield.snapledger.data.db.EntryEntity
import com.codefield.snapledger.data.db.RateDao
import com.codefield.snapledger.data.db.RateEntity
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.GeoPoint
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.Source
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 用内存里的假数据库测试 Repository 和转换；真正的 Room 在模拟器上验证。 */
class LedgerRepositoryTest {

    private class FakeEntryDao : EntryDao {
        val rows = MutableStateFlow<List<EntryEntity>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<EntryEntity>> =
            rows.map { list -> list.sortedWith(compareByDescending<EntryEntity> { it.time }.thenByDescending { it.id }) }

        override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }

        override suspend fun insert(entity: EntryEntity): Long {
            val id = nextId++
            rows.value = rows.value + entity.copy(id = id)
            return id
        }

        override suspend fun update(entity: EntryEntity) {
            rows.value = rows.value.map { if (it.id == entity.id) entity else it }
        }

        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    private class FakeRateDao : RateDao {
        val rows = MutableStateFlow<List<RateEntity>>(emptyList())

        override fun observeAll(): Flow<List<RateEntity>> = rows

        override suspend fun upsertAll(rows: List<RateEntity>) {
            val keys = rows.map { it.date to it.currency }.toSet()
            this.rows.value = this.rows.value.filterNot { (it.date to it.currency) in keys } + rows
        }
    }

    private val entryDao = FakeEntryDao()
    private val rateDao = FakeRateDao()
    private val repository = LedgerRepository(entryDao, rateDao)

    private fun entry(time: Long, merchant: String = "스타벅스") =
        Entry(amount = Money(6500, Currency.KRW), category = Category.CAFE, merchant = merchant, time = time)

    @Test
    fun add_assignsIds_andNewestComesFirst() = runBlocking {
        val first = repository.add(entry(time = 100, merchant = "A"))
        val second = repository.add(entry(time = 300, merchant = "B"))
        repository.add(entry(time = 200, merchant = "C"))
        assertEquals(1L, first)
        assertEquals(2L, second)
        assertEquals(listOf("B", "C", "A"), repository.observeEntries().first().map { it.merchant })
    }

    @Test
    fun update_andDelete() = runBlocking {
        val id = repository.add(entry(time = 100))
        repository.update(repository.get(id)!!.copy(merchant = "투썸플레이스", note = "친구랑"))
        assertEquals("투썸플레이스", repository.get(id)?.merchant)
        assertEquals("친구랑", repository.get(id)?.note)
        repository.delete(id)
        assertNull(repository.get(id))
    }

    @Test
    fun everyField_survivesTheRoundTrip() = runBlocking {
        val full = Entry(
            amount = Money(2350, Currency.CNY),
            category = Category.FOOD,
            merchant = "美团外卖订单",
            time = 1_792_000_000_000,
            note = "午饭",
            location = GeoPoint(37.5665, 126.978),
            source = Source.SCREENSHOT,
            imagePath = "receipts/1.jpg",
        )
        val id = repository.add(full)
        assertEquals(full.copy(id = id), repository.get(id))
    }

    @Test
    fun unknownValues_inOldRows() = runBlocking {
        entryDao.rows.value = listOf(
            EntryEntity(1, 100, "XYZ", "CAFE", "a", 1, "", null, null, "MANUAL", null),
            EntryEntity(2, 100, "KRW", "PETS", "b", 2, "", null, null, "FAX", null),
        )
        val loaded = repository.observeEntries().first()
        assertEquals("不认识的货币没法统计，跳过", listOf(2L), loaded.map { it.id })
        assertEquals(Category.OTHER, loaded.single().category)
        assertEquals(Source.MANUAL, loaded.single().source)
    }

    @Test
    fun rates_roundTrip_andEuroIsAlwaysOne() = runBlocking {
        val day = DailyRates(LocalDate.of(2026, 10, 27), mapOf(Currency.KRW to BigDecimal("1490.50"), Currency.CNY to BigDecimal("7.5012")))
        repository.saveRates(listOf(day))
        repository.saveRates(listOf(day.copy(perEuro = mapOf(Currency.KRW to BigDecimal("1491.00")))))
        val saved = repository.observeRates().first().single()
        assertEquals(LocalDate.of(2026, 10, 27), saved.date)
        assertEquals(BigDecimal("1491.00"), saved.perEuro[Currency.KRW])
        assertEquals(BigDecimal("7.5012"), saved.perEuro[Currency.CNY])
        assertEquals(BigDecimal.ONE, saved.perEuro[Currency.EUR])
    }
}
