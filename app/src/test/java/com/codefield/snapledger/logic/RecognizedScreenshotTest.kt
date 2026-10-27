package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.OcrLine
import com.codefield.snapledger.model.ParsedReceipt
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实的识别结果：在模拟器上用 ML Kit 识别截图后，从调试日志里原样复制的文字（包括认错的字）。
 * 截图是照着各付款页排版画的假页面，店名和金额是编的。用户自己的截图测过以后也加在这里。
 */
class RecognizedScreenshotTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = at(2026, 10, 28, 20, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(seoul).toInstant().toEpochMilli()

    /** 每行的格式和 ScreenshotReader 的调试日志一样：「top=43 h=25 | 7:30 LTE 85%」。 */
    private fun parse(vararg log: String): ParsedReceipt {
        val lines = log.map { line ->
            val (top, height, text) = LOG.matchEntire(line)!!.destructured
            OcrLine(text, top.toInt(), height.toInt())
        }
        return ReceiptParser.parse(lines, now, seoul, Currency.KRW)
    }

    @Test
    fun kakaoPay_wonSignNotRecognized() {
        val r = parse(
            "top=43 h=25 | 7:30 LTE 85%",
            "top=308 h=48 | 결제 완료",
            "top=430 h=40 | 스타벅스 강남R점",
            "top=523 h=106 | 6,500",
            "top=828 h=34 | 결제일시 2026.10.27 19:30",
            "top=903 h=42 | 결제수단 카카오페이머니",
            "top=984 h=41 | 카카오페이머니 잔액 123,400",
            "top=2107 h=44 | 확인",
        )
        assertEquals(Money(6500, Currency.KRW), r.amount)
        assertEquals("余额不给用户挑", listOf(Money(6500, Currency.KRW)), r.amountCandidates)
        assertEquals("스타벅스 강남R점", r.merchant)
        assertEquals(at(2026, 10, 27, 19, 30), r.time)
        assertEquals(Category.CAFE, r.category)
    }

    @Test
    fun weChatPay_statusBarNetworkIsNotAnAmount() {
        val r = parse(
            "top=39 h=28 | 9:15 5G 72%",
            "top=264 h=52 | 支付成功",
            "top=387 h=39 | 瑞幸咖啡",
            "top=493 h=87 | 23.50",
            "top=767 h=32 | 当前状态 支付成功",
            "top=847 h=34 | 支付时间 2026年10月28日 09:14:58",
            "top=927 h=35 | 商户全称 瑞幸咖啡(北京)有限公司",
            "top=1006 h=34 | 支付方式 零钱",
            "top=1086 h=35 | 交易单号 4200001234202610281234567890",
        )
        assertEquals(Money(2350, Currency.CNY), r.amount)
        assertEquals("「5G」不是 5 元", listOf(Money(2350, Currency.CNY)), r.amountCandidates)
        assertEquals("瑞幸咖啡", r.merchant)
        assertEquals(at(2026, 10, 28, 9, 14, 58), r.time)
        assertEquals(Category.CAFE, r.category)
    }

    @Test
    fun toss_wonReadAsL_andStoreNumberIsNotAnAmount() {
        val r = parse(
            "top=43 h=25 | 12:34 LTE 64%",
            "top=263 h=49 | GS25 역삼점",
            "top=361 h=98 | -3,800l",
            "top=645 h=37 | 거래 일시 10월 28일 오후 12:34",
            "top=725 h=37 | 출금 계좌. 토스뱅크 통장",
            "top=801 h=45 | 거래 후 잔액 245,300",
        )
        assertEquals(Money(3800, Currency.KRW), r.amount)
        assertEquals("「GS25」里的 25 不是金额", listOf(Money(3800, Currency.KRW)), r.amountCandidates)
        assertEquals("GS25 역삼점", r.merchant)
        assertEquals(at(2026, 10, 28, 12, 34), r.time)
        assertEquals(Category.GROCERY, r.category)
    }

    @Test
    fun alipay_tableBordersAreIgnored() {
        val r = parse(
            "top=118 h=40 | 账单详情",
            "top=306 h=41 | 美团外卖",
            "top=408 h=82 | -58.00",
            "top=548 h=35 | 交易成功",
            "top=727 h=33 | 支付时间 2026-10-26 19:20:11",
            "top=805 h=35 | 付款方式 |余额宝",
            "top=885 h=35 | 商品说明 美团外卖订单",
            "top=964 h=36 | |收款方全称 北京三快在线科技有限公司",
        )
        assertEquals(Money(5800, Currency.CNY), r.amount)
        assertEquals("美团外卖", r.merchant)
        assertEquals(at(2026, 10, 26, 19, 20, 11), r.time)
        assertEquals(Category.FOOD, r.category)
    }

    @Test
    fun chatScreenshot_hasNoAmount() {
        val r = parse(
            "top=42 h=25 | 8:01",
            "top=609 h=39 | 오늘 저녁 같이 먹을래?",
            "top=730 h=38 | 좋아! 7시에 학교 앞에서 보자",
        )
        assertNull(r.amount)
        assertTrue(r.amountCandidates.isEmpty())
    }

    private companion object {
        val LOG = Regex("""top=(\d+) h=(\d+) \| (.*)""")
    }
}
