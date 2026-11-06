package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.CategoryTotal
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.Persona
import com.codefield.snapledger.model.ReviewCard
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyReviewTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val october = YearMonth.of(2026, 10)
    private val rates = RateTable(
        listOf(
            DailyRates(
                LocalDate.of(2026, 10, 1),
                mapOf(Currency.EUR to BigDecimal.ONE, Currency.KRW to BigDecimal("1500"), Currency.CNY to BigDecimal("7.5")),
            ),
        )
    )

    private fun at(month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(seoul).toInstant().toEpochMilli()

    private fun won(amount: Long, category: Category, merchant: String, month: Int, day: Int, hour: Int = 12) =
        Entry(amount = Money(amount, Currency.KRW), category = category, merchant = merchant, time = at(month, day, hour))

    private val entries = listOf(
        won(4500, Category.CAFE, "스타벅스", 10, 2),
        won(4500, Category.CAFE, "스타벅스 ", 10, 9),
        won(4500, Category.CAFE, "스타벅스", 10, 16),
        won(30000, Category.TRANSPORT, "Taxi", 10, 16, hour = 23),
        won(52000, Category.SHOPPING, "쿠팡", 10, 20),
        // 20 元 ÷ 7.5 × 1500 = 4000 韩元
        Entry(amount = Money(2000, Currency.CNY), category = Category.FOOD, merchant = "美团外卖", time = at(10, 21)),
        won(9000, Category.FOOD, "", 9, 30),
    )

    private fun review(list: List<Entry> = entries, month: YearMonth = october) =
        MonthlyReview.build(list, month, Currency.KRW, rates, seoul)

    @Test
    fun favoritePlace_isTheMostVisited_evenIfAnotherCostMore() {
        val place = review().favoritePlace!!
        assertEquals("名字前后的空格不算", "스타벅스", place.merchant)
        assertEquals(3, place.visits)
        assertEquals(13500L, place.minor)
    }

    @Test
    fun favoritePlace_withOnlySingleVisits_isTheMostExpensive() {
        val single = listOf(won(4500, Category.CAFE, "A", 10, 1), won(52000, Category.SHOPPING, "B", 10, 2))
        assertEquals("B", review(single).favoritePlace?.merchant)
        assertEquals(1, review(single).favoritePlace?.visits)
    }

    @Test
    fun busiestDay_countsTheEntriesOfThatDay() {
        val day = review().busiestDay!!
        assertEquals(LocalDate.of(2026, 10, 20), day.date)
        assertEquals(52000L, day.minor)
        assertEquals(1, day.count)
        assertEquals("10 月有消费的天数", 5, review().activeDays)
    }

    @Test
    fun foreignShare_andCurrencies() {
        val r = review()
        assertEquals(setOf(Currency.KRW, Currency.CNY), r.currencies)
        assertEquals(4000.0 / (13500 + 30000 + 52000 + 4000), r.foreignShare, 1e-9)
    }

    @Test
    fun persona_picksTheCategoryFurthestAboveItsThreshold() {
        fun share(category: Category, share: Double) = CategoryTotal(category, 0, share)
        // 咖啡 20% ÷ 15% = 1.33，餐饮 45% ÷ 40% = 1.13
        assertEquals(Persona.COFFEE, MonthlyReview.persona(listOf(share(Category.FOOD, 0.45), share(Category.CAFE, 0.20)), 0.0, 1))
        assertEquals(Persona.COMMUTER, MonthlyReview.persona(listOf(share(Category.TRANSPORT, 0.31), share(Category.CAFE, 0.14)), 0.0, 1))
        assertEquals("「其他」不算", Persona.BALANCED, MonthlyReview.persona(listOf(share(Category.OTHER, 0.9)), 0.0, 1))
        assertEquals("哪一类都没到门槛", Persona.BALANCED, MonthlyReview.persona(listOf(share(Category.FOOD, 0.3)), 0.0, 1))
        assertEquals("外币占一半", Persona.GLOBAL, MonthlyReview.persona(listOf(share(Category.FOOD, 0.45)), 0.5, 2))
        assertEquals("只有一种货币时不算跨境", Persona.FOODIE, MonthlyReview.persona(listOf(share(Category.FOOD, 0.45)), 0.5, 1))
    }

    @Test
    fun cards_followTheStoryOrder() {
        assertEquals(
            listOf(
                ReviewCard.COVER, ReviewCard.TOP_CATEGORY, ReviewCard.BIGGEST, ReviewCard.FAVORITE_PLACE,
                ReviewCard.BUSIEST_DAY, ReviewCard.VERSUS_LAST_MONTH, ReviewCard.PERSONA, ReviewCard.SUMMARY,
            ),
            review().cards,
        )
    }

    @Test
    fun cards_firstMonth_andEmptyMonth() {
        val september = review(month = YearMonth.of(2026, 9)).cards
        assertTrue("9 月之前没有记账", ReviewCard.FIRST_MONTH in september)
        assertFalse(ReviewCard.VERSUS_LAST_MONTH in september)
        assertFalse("没有商家名，不放常去的店", ReviewCard.FAVORITE_PLACE in september)
        val empty = review(month = YearMonth.of(2026, 12))
        assertEquals(listOf(ReviewCard.COVER), empty.cards)
        assertNull(empty.favoritePlace)
        assertNull(empty.busiestDay)
    }

    @Test
    fun defaultMonth_isLastMonthWhenItHasEntries() {
        assertEquals(october, MonthlyReview.defaultMonth(entries, LocalDate.of(2026, 11, 6), seoul))
        assertEquals("上个月（10 月）没记账就看这个月", YearMonth.of(2026, 12), MonthlyReview.defaultMonth(entries, LocalDate.of(2026, 12, 3), seoul))
    }
}
