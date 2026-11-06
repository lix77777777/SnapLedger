package com.codefield.snapledger.logic

import com.codefield.snapledger.model.BusiestDay
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.CategoryTotal
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.FavoritePlace
import com.codefield.snapledger.model.MonthReview
import com.codefield.snapledger.model.Persona
import com.codefield.snapledger.model.ReviewCard
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 月度回顾的内容：在月度统计的基础上，再找出常去的店、花钱最多的一天和消费性格，排好卡片的顺序。 */
object MonthlyReview {

    /** 某一类占到多少，才算「主要花在这里」：类别、门槛、对应的性格。 */
    private val PERSONA_THRESHOLDS = listOf(
        Triple(Category.CAFE, 0.15, Persona.COFFEE),
        Triple(Category.FOOD, 0.40, Persona.FOODIE),
        Triple(Category.TRANSPORT, 0.25, Persona.COMMUTER),
        Triple(Category.SHOPPING, 0.30, Persona.SHOPPER),
        Triple(Category.GROCERY, 0.25, Persona.HOME_COOK),
        Triple(Category.ENTERTAINMENT, 0.20, Persona.FUN),
        Triple(Category.TRAVEL, 0.30, Persona.TRAVELER),
        Triple(Category.EDUCATION, 0.15, Persona.LEARNER),
        Triple(Category.HEALTH, 0.20, Persona.HEALTHY),
        Triple(Category.HOUSING, 0.40, Persona.HOMEBODY),
    )

    /** 外币占到这么多（而且用了两种以上货币），算「跨境生活」。 */
    private const val GLOBAL_THRESHOLD = 0.20

    fun build(entries: List<Entry>, month: YearMonth, home: Currency, rates: RateTable, zone: ZoneId): MonthReview {
        val summary = MonthlyStats.summarize(entries, month, home, rates, zone)
        val converted = MonthlyStats.converted(entries, month, home, rates, zone)
        val total = converted.sumOf { it.second }
        val foreign = converted.filter { it.first.amount.currency != home }.sumOf { it.second }
        val foreignShare = if (total == 0L) 0.0 else foreign.toDouble() / total
        val currencies = entries.filter { YearMonth.from(date(it.time, zone)) == month }.map { it.amount.currency }.toSet()
        val busiest = summary.byDay.maxByOrNull { it.value }?.let { (day, minor) ->
            BusiestDay(day, minor, converted.count { date(it.first.time, zone) == day })
        }
        val persona = persona(summary.byCategory, foreignShare, currencies.size)
        val review = MonthReview(
            summary = summary,
            activeDays = summary.byDay.size,
            favoritePlace = favoritePlace(converted),
            busiestDay = busiest,
            currencies = currencies,
            foreignShare = foreignShare,
            persona = persona,
            cards = emptyList(),
        )
        return review.copy(cards = cards(review))
    }

    /** 默认回顾哪个月：上个月记过账就看上个月（已经过完的一个月），不然看这个月。 */
    fun defaultMonth(entries: List<Entry>, today: LocalDate, zone: ZoneId): YearMonth {
        val previous = YearMonth.from(today).minusMonths(1)
        return if (entries.any { YearMonth.from(date(it.time, zone)) == previous }) previous else YearMonth.from(today)
    }

    /**
     * 消费性格：每一类按「占比 ÷ 门槛」打分，取分最高、而且到了门槛的那个；「其他」不算。
     * 用了两种以上货币时，外币占比也一起比。都没到门槛就是「均衡」。
     */
    fun persona(byCategory: List<CategoryTotal>, foreignShare: Double, currencyCount: Int): Persona {
        val scores = PERSONA_THRESHOLDS.mapNotNull { (category, threshold, persona) ->
            val share = byCategory.firstOrNull { it.category == category }?.share ?: return@mapNotNull null
            persona to share / threshold
        }.toMutableList()
        if (currencyCount >= 2) scores += Persona.GLOBAL to foreignShare / GLOBAL_THRESHOLD
        return scores.filter { it.second >= 1.0 }.maxByOrNull { it.second }?.first ?: Persona.BALANCED
    }

    /** 卡片的顺序。没记账的月份只有封面；上个月没记账时，把「和上个月比」换成「第一个月」。 */
    fun cards(review: MonthReview): List<ReviewCard> {
        val summary = review.summary
        if (summary.entryCount == 0) return listOf(ReviewCard.COVER)
        return buildList {
            add(ReviewCard.COVER)
            if (summary.byCategory.isNotEmpty()) add(ReviewCard.TOP_CATEGORY)
            if (summary.biggest != null) add(ReviewCard.BIGGEST)
            if (review.favoritePlace != null) add(ReviewCard.FAVORITE_PLACE)
            if (review.busiestDay != null) add(ReviewCard.BUSIEST_DAY)
            add(if (summary.previousTotalMinor != null) ReviewCard.VERSUS_LAST_MONTH else ReviewCard.FIRST_MONTH)
            add(ReviewCard.PERSONA)
            add(ReviewCard.SUMMARY)
        }
    }

    /** 去得最多的店（次数一样时看金额）；每家都只去过一次时，取花钱最多的那家。商家名前后的空格不算。 */
    private fun favoritePlace(converted: List<Pair<Entry, Long>>): FavoritePlace? =
        converted.filter { it.first.merchant.isNotBlank() }
            .groupBy { it.first.merchant.trim() }
            .map { (name, list) -> FavoritePlace(name, list.sumOf { it.second }, list.size) }
            .maxWithOrNull(compareBy<FavoritePlace> { it.visits }.thenBy { it.minor })

    private fun date(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
}
