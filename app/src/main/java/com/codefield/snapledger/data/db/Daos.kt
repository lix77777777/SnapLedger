package com.codefield.snapledger.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    /** 新的在前；同一时间的按记账顺序，后记的在前。 */
    @Query("SELECT * FROM entries ORDER BY time DESC, id DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    @Insert
    suspend fun insert(entity: EntryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<EntryEntity>)

    @Update
    suspend fun update(entity: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)

    /** 调试版换成演示数据时用。 */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates")
    fun observeAll(): Flow<List<RateEntity>>

    @Upsert
    suspend fun upsertAll(rows: List<RateEntity>)
}
