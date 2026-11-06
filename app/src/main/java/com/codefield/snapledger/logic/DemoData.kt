package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.GeoPoint
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.Source
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * 演示用的账目（只在调试版的设置里能填入）：上个月整月和这个月到今天为止，
 * 一个在首尔上学的留学生的日常开销：咖啡、便利店、地铁、外卖、偶尔用人民币和日元。
 * 用固定的随机种子，每次生成的都一样，演示时心里有数。
 */
object DemoData {

    private data class Place(
        val merchant: String,
        val category: Category,
        val currency: Currency,
        /** 金额范围（最小单位）。 */
        val min: Long,
        val max: Long,
        val hours: IntRange,
        val at: GeoPoint?,
        /** 一个月大概几次。 */
        val perMonth: Int,
    )

    private val HANYANG = GeoPoint(37.5577, 127.0450)
    private val WANGSIMNI = GeoPoint(37.5612, 127.0371)
    private val SEONGSU = GeoPoint(37.5446, 127.0557)
    private val GANGNAM = GeoPoint(37.4979, 127.0276)
    private val HONGDAE = GeoPoint(37.5563, 126.9236)

    private val PLACES = listOf(
        Place("스타벅스 한양대점", Category.CAFE, Currency.KRW, 4_500, 6_800, 9..17, HANYANG, 8),
        Place("메가커피 왕십리역점", Category.CAFE, Currency.KRW, 2_000, 4_400, 8..16, WANGSIMNI, 7),
        Place("GS25 행당점", Category.GROCERY, Currency.KRW, 1_800, 9_500, 10..23, HANYANG, 6),
        Place("이마트 성수점", Category.GROCERY, Currency.KRW, 18_000, 62_000, 14..20, SEONGSU, 2),
        Place("김밥천국 왕십리점", Category.FOOD, Currency.KRW, 5_000, 9_000, 11..20, WANGSIMNI, 5),
        Place("학생식당", Category.FOOD, Currency.KRW, 4_500, 6_000, 11..13, HANYANG, 6),
        Place("교촌치킨", Category.FOOD, Currency.KRW, 19_000, 27_000, 18..22, HANYANG, 1),
        Place("지하철", Category.TRANSPORT, Currency.KRW, 1_400, 1_600, 8..21, WANGSIMNI, 6),
        Place("카카오T 택시", Category.TRANSPORT, Currency.KRW, 6_800, 18_000, 22..23, GANGNAM, 2),
        Place("올리브영 왕십리점", Category.SHOPPING, Currency.KRW, 9_000, 38_000, 15..20, WANGSIMNI, 1),
        Place("쿠팡", Category.SHOPPING, Currency.KRW, 12_000, 46_000, 21..23, null, 2),
        Place("CGV 왕십리", Category.ENTERTAINMENT, Currency.KRW, 14_000, 15_000, 19..21, WANGSIMNI, 1),
        Place("홍대 노래방", Category.ENTERTAINMENT, Currency.KRW, 15_000, 24_000, 21..23, HONGDAE, 1),
        Place("교보문고", Category.EDUCATION, Currency.KRW, 15_000, 32_000, 13..18, GANGNAM, 1),
        Place("SKT 휴대폰 요금", Category.HOUSING, Currency.KRW, 55_000, 55_000, 9..9, null, 1),
        // 用人民币付的：给家里买东西、外卖、视频会员。
        Place("瑞幸咖啡", Category.CAFE, Currency.CNY, 990, 1_990, 10..16, null, 2),
        Place("美团外卖", Category.FOOD, Currency.CNY, 2_500, 5_800, 12..20, null, 2),
        Place("淘宝", Category.SHOPPING, Currency.CNY, 3_900, 15_900, 21..23, null, 1),
        Place("爱奇艺会员", Category.ENTERTAINMENT, Currency.CNY, 2_500, 2_500, 9..9, null, 1),
    )

    /** 上个月整月 + 这个月 1 号到现在（今天还没到的时间不放，免得账本里出现比现在还晚的账）。 */
    fun entries(now: ZonedDateTime): List<Entry> {
        val random = Random(SEED)
        val zone = now.zone
        val thisMonth = YearMonth.from(now)
        val nowMillis = now.toInstant().toEpochMilli()
        return listOf(thisMonth.minusMonths(1) to thisMonth.minusMonths(1).lengthOfMonth(), thisMonth to now.dayOfMonth)
            .flatMap { (month, lastDay) -> monthEntries(month, lastDay, random, zone) }
            .filter { it.time <= nowMillis }
            .sortedBy { it.time }
    }

    private fun monthEntries(month: YearMonth, lastDay: Int, random: Random, zone: ZoneId): List<Entry> {
        val share = lastDay.toDouble() / month.lengthOfMonth()
        return PLACES.flatMap { place ->
            // 这个月还没过完时，按过了的天数少记几笔。
            val times = maxOf(if (place.perMonth >= 2) 1 else 0, (place.perMonth * share).toInt())
            List(times) {
                val day = random.nextInt(1, lastDay + 1)
                val time = LocalTime.of(random.nextInt(place.hours.first, place.hours.last + 1), random.nextInt(0, 60))
                val minor = roundAmount(random.nextLong(place.min, place.max + 1), place.currency)
                Entry(
                    amount = Money(minor, place.currency),
                    category = place.category,
                    merchant = place.merchant,
                    time = month.atDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli(),
                    location = place.at?.let { nearby(it, random) },
                    source = if (random.nextInt(3) == 0) Source.MANUAL else Source.SCREENSHOT,
                )
            }
        }
    }

    /** 韩元取整到 100 元，人民币取整到 1 角，看起来像真的价钱。 */
    private fun roundAmount(minor: Long, currency: Currency): Long = when (currency) {
        Currency.KRW -> (minor / 100) * 100
        Currency.CNY -> (minor / 10) * 10
        else -> minor
    }

    /** 同一家店的几次消费，地图上的点稍微错开一点（几十米），不会完全叠在一起。 */
    private fun nearby(point: GeoPoint, random: Random) = GeoPoint(
        point.latitude + random.nextDouble(-0.0006, 0.0006),
        point.longitude + random.nextDouble(-0.0006, 0.0006),
    )

    private const val SEED = 20261217
}
