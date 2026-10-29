package com.codefield.snapledger.logic

import com.codefield.snapledger.model.CategoryTotal
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.MerchantTotal
import com.codefield.snapledger.model.MonthSummary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 按月统计：每一笔先按付款那天的汇率换算成主货币，再汇总。 */
object MonthlyStats {

    private const val TOP_MERCHANTS = 5

    fun summarize(entries: List<Entry>, month: YearMonth, home: Currency, rates: RateTable, zone: ZoneId): MonthSummary {
        val current = convertIn(entries, month, home, rates, zone)
        val converted = current.converted
        val total = converted.sumOf { it.second }
        val byCategory = converted.groupBy { it.first.category }
            .map { (category, list) ->
                val sum = list.sumOf { it.second }
                CategoryTotal(category, sum, if (total == 0L) 0.0 else sum.toDouble() / total)
            }
            .sortedByDescending { it.minor }
        val byDay = converted.groupBy { localDate(it.first.time, zone) }
            .mapValues { (_, list) -> list.sumOf { it.second } }
            .toSortedMap()
        val topMerchants = converted.filter { it.first.merchant.isNotBlank() }
            .groupBy { it.first.merchant.trim() }
            .map { (name, list) -> MerchantTotal(name, list.sumOf { it.second }, list.size) }
            .sortedWith(compareByDescending<MerchantTotal> { it.minor }.thenByDescending { it.count })
            .take(TOP_MERCHANTS)
        val biggest = converted.maxByOrNull { it.second }
        val previous = convertIn(entries, month.minusMonths(1), home, rates, zone)
        return MonthSummary(
            month = month,
            currency = home,
            totalMinor = total,
            entryCount = current.count,
            byCategory = byCategory,
            byDay = byDay,
            topMerchants = topMerchants,
            biggest = biggest?.first,
            biggestMinor = biggest?.second ?: 0L,
            previousTotalMinor = if (previous.count == 0) null else previous.converted.sumOf { it.second },
            unconvertedCount = current.count - converted.size,
        )
    }

    /** 最早一笔账所在的月份，统计页最多翻到这里；没有账时是 null。 */
    fun firstMonth(entries: List<Entry>, zone: ZoneId): YearMonth? =
        entries.minOfOrNull { it.time }?.let { YearMonth.from(localDate(it, zone)) }

    /** 这个月记了几笔，以及能换算成主货币的那些（每笔配上换算后的金额）。 */
    private class Converted(val count: Int, val converted: List<Pair<Entry, Long>>)

    private fun convertIn(entries: List<Entry>, month: YearMonth, home: Currency, rates: RateTable, zone: ZoneId): Converted {
        val inMonth = entries.filter { YearMonth.from(localDate(it.time, zone)) == month }
        val converted = inMonth.mapNotNull { entry ->
            CurrencyConverter.convert(entry.amount, home, rates.ratesFor(localDate(entry.time, zone)))?.let { entry to it.minor }
        }
        return Converted(inMonth.size, converted)
    }

    private fun localDate(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
}
