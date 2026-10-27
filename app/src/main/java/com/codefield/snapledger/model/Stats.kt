package com.codefield.snapledger.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** 某一天的汇率：1 欧元能换多少各种货币（Frankfurter 默认以欧元为基准）。 */
data class DailyRates(val date: LocalDate, val perEuro: Map<Currency, BigDecimal>)

data class CategoryTotal(val category: Category, val minor: Long, val share: Double)

data class MerchantTotal(val merchant: String, val minor: Long, val count: Int)

/** 一个月的统计结果，金额都已经换算成主货币（最小单位）。 */
data class MonthSummary(
    val month: YearMonth,
    val currency: Currency,
    val totalMinor: Long,
    val entryCount: Int,
    /** 按金额从大到小。 */
    val byCategory: List<CategoryTotal>,
    /** 每天花了多少：日期 → 金额。没有消费的日子不在里面。 */
    val byDay: Map<LocalDate, Long>,
    /** 花钱最多的几家店，按金额从大到小。 */
    val topMerchants: List<MerchantTotal>,
    /** 最大的一笔。 */
    val biggest: Entry?,
    val biggestMinor: Long,
    /** 上个月一共花了多少；上个月没有记账时是 null。 */
    val previousTotalMinor: Long?,
    /** 缺少汇率、没能换算的笔数（这些不算进总额）。 */
    val unconvertedCount: Int,
)
