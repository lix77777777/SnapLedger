package com.codefield.snapledger.model

import java.time.LocalDate

/** 消费性格：看这个月的钱主要花在哪里挑一个。名字和说明在界面的资源里，这里只有编号。 */
enum class Persona {
    COFFEE,
    FOODIE,
    COMMUTER,
    SHOPPER,
    HOME_COOK,
    FUN,
    TRAVELER,
    LEARNER,
    HEALTHY,
    HOMEBODY,
    /** 好几种货币，外币花得不少。 */
    GLOBAL,
    /** 哪一类都不突出。 */
    BALANCED,
}

/** 常去的店：visits 是去了几次。只有一次的话，就是花钱最多的那家。金额已换算成主货币。 */
data class FavoritePlace(val merchant: String, val minor: Long, val visits: Int)

/** 花钱最多的一天。 */
data class BusiestDay(val date: LocalDate, val minor: Long, val count: Int)

/** 回顾的一张卡片。没有内容的卡片（比如上个月没记账，就没有「和上个月比」）不放进去。 */
enum class ReviewCard {
    COVER,
    TOP_CATEGORY,
    BIGGEST,
    FAVORITE_PLACE,
    BUSIEST_DAY,
    VERSUS_LAST_MONTH,
    /** 上个月没有记账：这是第一个月。 */
    FIRST_MONTH,
    PERSONA,
    /** 最后一张，可以分享成图片。 */
    SUMMARY,
}

/** 一个月的回顾。金额都已经按付款那天的汇率换算成主货币。 */
data class MonthReview(
    val summary: MonthSummary,
    /** 有消费的天数。 */
    val activeDays: Int,
    val favoritePlace: FavoritePlace?,
    val busiestDay: BusiestDay?,
    /** 这个月用过的货币。 */
    val currencies: Set<Currency>,
    /** 外币占这个月支出的比例（0～1）。 */
    val foreignShare: Double,
    val persona: Persona,
    val cards: List<ReviewCard>,
)
