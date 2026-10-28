package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.OcrLine
import com.codefield.snapledger.model.ParsedItem
import com.codefield.snapledger.model.ParsedReceipt
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 样例文字照着各付款页面的排版写（行的顺序、哪一行字最大），店名和金额是编的。 */
class ReceiptParserTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = at(2026, 10, 28, 20, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(seoul).toInstant().toEpochMilli()

    private fun parse(
        vararg lines: String,
        heights: List<Int> = emptyList(),
        fallback: Currency = Currency.KRW,
        now: Long = this.now,
    ): ParsedReceipt = ReceiptParser.parse(
        lines.mapIndexed { i, text -> OcrLine(text, top = i * 40, height = heights.getOrElse(i) { 0 }) },
        now, seoul, fallback,
    )

    @Test
    fun kakaoPayStyle() {
        val r = parse("결제 완료", "스타벅스 강남점", "6,500원", "2026.10.28 14:32", "결제수단 카카오페이머니", heights = listOf(30, 36, 64, 24, 24))
        assertEquals(Money(6500, Currency.KRW), r.amount)
        assertEquals("스타벅스 강남점", r.merchant)
        assertEquals(at(2026, 10, 28, 14, 32), r.time)
        assertEquals(Category.CAFE, r.category)
    }

    @Test
    fun tossStyleWithdrawal() {
        val r = parse("쿠팡", "-15,900원", "2026년 10월 27일 오후 9:15", "출금 계좌 토스뱅크", heights = listOf(36, 64, 24, 24))
        assertEquals(Money(15900, Currency.KRW), r.amount)
        assertEquals("쿠팡", r.merchant)
        assertEquals(at(2026, 10, 27, 21, 15), r.time)
        assertEquals(Category.SHOPPING, r.category)
    }

    @Test
    fun koreanCardMessage_skipsMaskedNameAndRunningTotal() {
        val r = parse("[Web발신]", "KB국민카드1234승인", "홍*동", "12,000원 일시불", "10/28 12:31", "GS25 역삼점", "누적1,234,560원")
        assertEquals(Money(12000, Currency.KRW), r.amount)
        assertEquals("GS25 역삼점", r.merchant)
        assertEquals(at(2026, 10, 28, 12, 31), r.time)
        assertEquals(Category.GROCERY, r.category)
    }

    @Test
    fun weChatPayStyle_prefersTheShortName() {
        val r = parse(
            "支付成功", "瑞幸咖啡", "-23.50", "当前状态 支付成功", "支付时间 2026年10月28日 09:41:05",
            "商户全称 瑞幸咖啡（北京）有限公司", "支付方式 零钱", "交易单号 4200001234202610281234567890",
            heights = listOf(28, 36, 72, 24, 24, 24, 24, 24),
        )
        assertEquals(Money(2350, Currency.CNY), r.amount)
        assertEquals("瑞幸咖啡", r.merchant)
        assertEquals(at(2026, 10, 28, 9, 41, 5), r.time)
        assertEquals(Category.CAFE, r.category)
        assertTrue("订单号不是金额", r.amountCandidates.none { it.minor > 1_000_000_000 })
    }

    @Test
    fun alipayStyle_usesTheItemDescription() {
        val r = parse(
            "交易成功", "-58.00", "创建时间 2026-10-26 19:20:11", "付款方式 余额宝",
            "商品说明 美团外卖订单", "收款方全称 北京三快在线科技有限公司",
            heights = listOf(30, 72, 24, 24, 24, 24),
        )
        assertEquals(Money(5800, Currency.CNY), r.amount)
        assertEquals("美团外卖订单", r.merchant)
        assertEquals(at(2026, 10, 26, 19, 20, 11), r.time)
        assertEquals(Category.FOOD, r.category)
    }

    @Test
    fun englishReceipt_takesTheTotalNotTheSubtotal() {
        val r = parse("Thank you!", "Subtotal $11.50", "Tax $1.49", "Total $12.99", fallback = Currency.USD)
        assertEquals(Money(1299, Currency.USD), r.amount)
    }

    @Test
    fun balanceIsNotThePayment() {
        assertEquals(Money(8000, Currency.KRW), parse("결제금액 8,000원", "잔액 52,300원").amount)
    }

    @Test
    fun fullWidthDigits_areUnderstood() {
        assertEquals(Money(12000, Currency.KRW), parse("결제금액 １２，０００원").amount)
    }

    @Test
    fun countsAndPercentages_areNotAmounts() {
        val r = parse("3개월 할부", "적립 5%", "결제금액 30,000원")
        assertEquals(listOf(Money(30000, Currency.KRW)), r.amountCandidates)
    }

    @Test
    fun yenSign_withJapaneseText_isYen() {
        assertEquals(Money(1200, Currency.JPY), parse("ありがとうございました", "合計 ¥1,200").amount)
    }

    @Test
    fun noHints_usesTheFallbackCurrency() {
        assertEquals(Money(1250, Currency.USD), parse("Amount 12.50", fallback = Currency.USD).amount)
    }

    @Test
    fun dateWithoutYear_inTheFuture_isLastYear() {
        val r = parse("12/30 18:00", "5,000원", now = at(2026, 1, 2, 9, 0))
        assertEquals(at(2025, 12, 30, 18, 0), r.time)
    }

    /** 两条银行卡短信截在一张图里：两条之间空得多，每条的店名、时间归那一条；打了码的人名不当商家。 */
    @Test
    fun twoCardMessages_areTwoItems() {
        val first = listOf("[Web발신]", "신한카드(1234)승인", "홍*동", "6,500원 일시불", "10/28 12:31", "스타벅스 강남점", "누적1,234,560원")
        val second = listOf("[Web발신]", "신한카드(1234)승인", "홍*동", "12,000원 일시불", "10/28 18:05", "GS25 역삼점", "누적1,246,560원")
        // 每行高 30、行距 10；两条短信之间空 130。
        val lines = first.mapIndexed { i, text -> OcrLine(text, top = 200 + i * 40, height = 30) } +
            second.mapIndexed { i, text -> OcrLine(text, top = 600 + i * 40, height = 30) }
        val r = ReceiptParser.parse(lines, now, seoul, Currency.KRW)
        assertEquals(
            listOf(
                ParsedItem(Money(6500, Currency.KRW), at(2026, 10, 28, 12, 31), "스타벅스 강남점", Category.CAFE),
                ParsedItem(Money(12000, Currency.KRW), at(2026, 10, 28, 18, 5), "GS25 역삼점", Category.GROCERY),
            ),
            r.items,
        )
    }

    /** 付款详情页：上面大字的金额，下面明细里又写了一遍（字小），不是两笔。 */
    @Test
    fun detailPageShowingTheAmountTwice_isNotAList() {
        val r = parse("GS25 역삼점", "-3,800원", "거래 일시 10월 28일 오후 12:34", "결제 금액 -3,800원", heights = listOf(44, 96, 36, 36))
        assertTrue(r.items.isEmpty())
        assertEquals(Money(3800, Currency.KRW), r.amount)
    }

    @Test
    fun nothingUseful_givesEmptyResult() {
        val r = parse("안녕하세요", "감사합니다")
        assertNull(r.amount)
        assertTrue(r.amountCandidates.isEmpty())
        assertNull(r.time)
        assertNull(r.merchant)
        assertNull(r.category)
    }

    @Test
    fun categoryGuesser_checksSpecificNamesFirst() {
        assertEquals(Category.FOOD, CategoryGuesser.guess("쿠팡이츠 주문"))
        assertEquals(Category.SHOPPING, CategoryGuesser.guess("쿠팡"))
        assertEquals(Category.GROCERY, CategoryGuesser.guess("CU 역삼점"))
        assertEquals(Category.CAFE, CategoryGuesser.guess("STARBUCKS COFFEE"))
        assertEquals(Category.TRANSPORT, CategoryGuesser.guess("滴滴出行"))
        assertNull("短英文词要整个词匹配", CategoryGuesser.guess("Cute Shop"))
    }
}
