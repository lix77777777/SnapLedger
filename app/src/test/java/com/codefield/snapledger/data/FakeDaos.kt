package com.codefield.snapledger.data

import com.codefield.snapledger.data.db.EntryDao
import com.codefield.snapledger.data.db.EntryEntity
import com.codefield.snapledger.data.db.RateDao
import com.codefield.snapledger.data.db.RateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 内存里的假数据库，测试 Repository 和用到它的类时用；真正的 Room 在模拟器上验证。 */
internal class FakeEntryDao : EntryDao {
    val rows = MutableStateFlow<List<EntryEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<EntryEntity>> =
        rows.map { list -> list.sortedWith(compareByDescending<EntryEntity> { it.time }.thenByDescending { it.id }) }

    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }

    /** 和 Room 一样：编号是 0 时自动分配，否则用给定的编号。 */
    override suspend fun insert(entity: EntryEntity): Long {
        val id = if (entity.id != 0L) entity.id else nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(entities: List<EntryEntity>) {
        rows.value = rows.value + entities.map { it.copy(id = nextId++) }
    }

    override suspend fun update(entity: EntryEntity) {
        rows.value = rows.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

internal class FakeRateDao : RateDao {
    val rows = MutableStateFlow<List<RateEntity>>(emptyList())

    override fun observeAll(): Flow<List<RateEntity>> = rows

    override suspend fun upsertAll(rows: List<RateEntity>) {
        val keys = rows.map { it.date to it.currency }.toSet()
        this.rows.value = this.rows.value.filterNot { (it.date to it.currency) in keys } + rows
    }
}
