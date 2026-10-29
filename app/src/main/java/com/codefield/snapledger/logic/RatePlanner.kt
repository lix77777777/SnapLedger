package com.codefield.snapledger.logic

import java.time.LocalDate

/** 要下载的一段汇率：from 到 to 这几天，to 是 null 表示一直到最新。 */
data class DateSpan(val from: LocalDate, val to: LocalDate?)

/**
 * 决定要下载哪几段汇率。汇率接口只在工作日有数据，周末和节假日用之前最近一天的，
 * 所以往前多取一周，保证最早那笔账之前有一个工作日。
 */
object RatePlanner {

    private const val LOOKBACK_DAYS = 7L

    /**
     * cached 是已经存下的最早、最新的一天（一天都没有时是 null）；earliest 是最早一笔账的日期（没有账时是 null）。
     * checkNewer 为 true 时，最新的一天之后的也要看看（从最新的那天开始取，接口总能返回至少一天）。
     */
    fun missing(cachedFirst: LocalDate?, cachedLast: LocalDate?, earliest: LocalDate?, today: LocalDate, checkNewer: Boolean): List<DateSpan> {
        val start = (earliest ?: today).minusDays(LOOKBACK_DAYS)
        if (cachedFirst == null || cachedLast == null) return listOf(DateSpan(start, null))
        val spans = mutableListOf<DateSpan>()
        // 有账比存下的最早一天还早：补上前面那段。
        if (earliest != null && earliest.isBefore(cachedFirst)) spans += DateSpan(start, cachedFirst)
        if (checkNewer && cachedLast.isBefore(today)) spans += DateSpan(cachedLast, null)
        return spans
    }
}
