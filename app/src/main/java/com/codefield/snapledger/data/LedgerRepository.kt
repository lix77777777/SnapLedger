package com.codefield.snapledger.data

import com.codefield.snapledger.data.db.EntryDao
import com.codefield.snapledger.data.db.RateDao
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 账目和汇率的唯一入口。界面和规则都不直接碰数据库。 */
class LedgerRepository(
    private val entries: EntryDao,
    private val rates: RateDao,
) {

    /** 所有账目，新的在前。数据库一变，这里就会收到新的列表。 */
    fun observeEntries(): Flow<List<Entry>> = entries.observeAll().map { rows -> rows.mapNotNull(EntryMapper::toModel) }

    suspend fun get(id: Long): Entry? = entries.get(id)?.let(EntryMapper::toModel)

    /** 新记一笔，返回它的编号。 */
    suspend fun add(entry: Entry): Long = entries.insert(EntryMapper.toEntity(entry.copy(id = 0)))

    /** 一次记好几笔（列表截图），账本只刷新一次。 */
    suspend fun addAll(list: List<Entry>) = entries.insertAll(list.map { EntryMapper.toEntity(it.copy(id = 0)) })

    suspend fun update(entry: Entry) = entries.update(EntryMapper.toEntity(entry))

    /** 撤销删除：用原来的编号放回去。 */
    suspend fun restore(entry: Entry) {
        entries.insert(EntryMapper.toEntity(entry))
    }

    suspend fun delete(id: Long) = entries.delete(id)

    /** 清空再换成另一批账（调试版的演示数据）。 */
    suspend fun replaceAll(list: List<Entry>) {
        entries.deleteAll()
        addAll(list)
    }

    /** 存下来的各天汇率，按日期从早到晚。 */
    fun observeRates(): Flow<List<DailyRates>> = rates.observeAll().map(RateMapper::toModel)

    suspend fun saveRates(days: List<DailyRates>) = rates.upsertAll(days.flatMap(RateMapper::toEntities))
}
