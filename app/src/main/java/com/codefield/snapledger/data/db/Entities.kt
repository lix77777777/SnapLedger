package com.codefield.snapledger.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一笔账。货币、类别、来源用名字（字符串）存，以后加新的也不会弄乱旧数据。 */
@Entity(tableName = "entries", indices = [Index("time")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 最小单位：韩元按元，人民币按分。 */
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val merchant: String,
    val time: Long,
    val note: String,
    val latitude: Double?,
    val longitude: Double?,
    val source: String,
    val imagePath: String?,
)

/** 某一天、某种货币的汇率：1 欧元能换多少。用字符串存，避免小数误差。 */
@Entity(tableName = "rates", primaryKeys = ["date", "currency"])
data class RateEntity(
    /** ISO 日期，比如 2026-10-27。 */
    val date: String,
    val currency: String,
    val perEuro: String,
)
