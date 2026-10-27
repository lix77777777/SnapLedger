package com.codefield.snapledger.data

import com.codefield.snapledger.data.db.EntryEntity
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

/** 数据库的行和 model 之间的转换。读到不认识的值时尽量保住数据，实在不行才跳过这一行。 */
internal object EntryMapper {

    fun toEntity(entry: Entry) = EntryEntity(
        id = entry.id,
        amountMinor = entry.amount.minor,
        currency = entry.amount.currency.name,
        category = entry.category.name,
        merchant = entry.merchant,
        time = entry.time,
        note = entry.note,
        latitude = entry.location?.latitude,
        longitude = entry.location?.longitude,
        source = entry.source.name,
        imagePath = entry.imagePath,
    )

    /** 货币不认识就没法统计，跳过这一行；类别、来源不认识时用「其他」「手动」。 */
    fun toModel(row: EntryEntity): Entry? {
        val currency = enumOrNull<Currency>(row.currency) ?: return null
        return Entry(
            id = row.id,
            amount = Money(row.amountMinor, currency),
            category = enumOrNull<Category>(row.category) ?: Category.OTHER,
            merchant = row.merchant,
            time = row.time,
            note = row.note,
            location = if (row.latitude != null && row.longitude != null) GeoPoint(row.latitude, row.longitude) else null,
            source = enumOrNull<Source>(row.source) ?: Source.MANUAL,
            imagePath = row.imagePath,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}

internal object RateMapper {

    fun toEntities(day: DailyRates): List<RateEntity> =
        day.perEuro.map { (currency, rate) -> RateEntity(day.date.toString(), currency.name, rate.toPlainString()) }

    /** 按日期合成一天一份；欧元自己总是 1。 */
    fun toModel(rows: List<RateEntity>): List<DailyRates> =
        rows.groupBy { it.date }.mapNotNull { (date, list) ->
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
            val rates = list.mapNotNull { row ->
                val currency = Currency.entries.firstOrNull { it.name == row.currency } ?: return@mapNotNull null
                val rate = row.perEuro.toBigDecimalOrNull() ?: return@mapNotNull null
                currency to rate
            }.toMap()
            DailyRates(day, rates + (Currency.EUR to BigDecimal.ONE))
        }.sortedBy { it.date }
}
