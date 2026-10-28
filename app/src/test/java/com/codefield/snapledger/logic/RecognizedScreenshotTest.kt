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

/**
 * 真实的识别结果：在模拟器上用 ML Kit 识别截图后，从调试日志里复制的文字（包括认错的字）。
 * 前五个是照着各付款页排版画的假页面，店名和金额是编的；后面是用户自己的截图，
 * 金额、余额、时间、卡号都改过，行的位置和认错的字保持原样。
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

    /**
     * 用户的截图：银行 App 的交易明细（深色模式），一张里 6 笔，其中一笔是退款。
     * 「원」大多没认出来，有的认成「l」「2」「2A」；最上面的大数字是账户余额。
     */
    @Test
    fun bankHistory_eachExpenseBecomesAnItem() {
        val r = parse(
            "top=61 h=45 | 21:35 72",
            "top=234 h=52 | 계좌관리 G",
            "top=417 h=76 | 52,640",
            "top=522 h=37 | 출금가능금액 52,640원",
            "top=662 h=52 | 이체",
            "top=926 h=40 | 3개월. 전체. 최신순v",
            "top=1049 h=35 | 잔액",
            "top=1162 h=40 | 10월 28일",
            "top=1289 h=58 | 카카오페이 -35,000",
            "top=1387 h=38 | 20:12:47 펌뱅킹 이체 52,640",
            "top=1527 h=48 | 카카오페이 -12,000",
            "top=1617 h=41 | 20:05:31.펌뱅킹 이체 87,640",
            "top=1864 h=34 | 10월 27일",
            "top=1991 h=48 | 카카오페이 -18,700l",
            "top=2086 h=35 | 19:41:09 펌뱅킹 이체 99,6402",
            "top=2221 h=51 | 쿠팡 +15,200",
            "top=2313 h=41 | 18:22:16 펌뱅킹 이체 118,340",
            "top=2450 h=59 | 지에스더프레시 -6,300",
            "top=2546 h=42 | 14:26:58. 체크카드 103,140",
            "top=2680 h=60 | 카카오페이 -20,0002A",
        )
        assertEquals(
            "余额和退款都不算；日期从上面的「10월 28일」「10월 27일」来；最后一笔的时间被截掉了，按那天中午",
            listOf(
                ParsedItem(Money(35000, Currency.KRW), at(2026, 10, 28, 20, 12, 47), "카카오페이", null),
                ParsedItem(Money(12000, Currency.KRW), at(2026, 10, 28, 20, 5, 31), "카카오페이", null),
                ParsedItem(Money(18700, Currency.KRW), at(2026, 10, 27, 19, 41, 9), "카카오페이", null),
                ParsedItem(Money(6300, Currency.KRW), at(2026, 10, 27, 14, 26, 58), "지에스더프레시", Category.GROCERY),
                ParsedItem(Money(20000, Currency.KRW), at(2026, 10, 27, 12, 0), "카카오페이", null),
            ),
            r.items,
        )
    }

    /**
     * 用户的截图：微信「微信支付」里的两条付款通知（深色模式）。
     * 返回箭头认成了日文「く」，图标认成了「②」，第二条的「费」认成了「责」。
     */
    @Test
    fun weChatPayNotifications_twoPayments() {
        val r = parse(
            "top=65 h=42 | 21:34 0! 73",
            "top=194 h=62 | く 微信支付 Q您",
            "top=377 h=37 | 查看明细",
            "top=506 h=39 | 日撮设置",
            "top=675 h=33 | 00:48",
            "top=818 h=51 | ②腾讯云费用账户-移动支付",
            "top=1052 h=52 | 使用工商银行储蓄卡(1234) 支付",
            "top=1164 h=103 | ¥30.00",
            "top=1342 h=41 | 账单详情〉",
            "top=1600 h=30 | 10:57",
            "top=1741 h=53 | ②腾讯云责用账户-移动支付",
            "top=1974 h=49 | |使用工商銀行儲蓄卡(1234) 支付",
            "top=2089 h=98 | ¥25.00",
            "top=2262 h=47 | |账单译情〉",
            "top=2568 h=41 | 我的账单 支付服务 优惠",
        )
        assertEquals(
            "一个「く」不算日文，还是人民币；只写了时间的算截图那天",
            listOf(
                ParsedItem(Money(3000, Currency.CNY), at(2026, 10, 28, 0, 48), "腾讯云费用账户-移动支付", null),
                ParsedItem(Money(2500, Currency.CNY), at(2026, 10, 28, 10, 57), "腾讯云责用账户-移动支付", null),
            ),
            r.items,
        )
    }

    /** 同一张截图只截到一条通知时，按单笔处理。 */
    @Test
    fun weChatPayNotification_alone_skipsCardLineAndBattery() {
        val r = parse(
            "top=65 h=42 | 21:34 0! 73",
            "top=194 h=62 | く 微信支付 Q您",
            "top=675 h=33 | 00:48",
            "top=818 h=51 | ②腾讯云费用账户-移动支付",
            "top=1052 h=52 | 使用工商银行储蓄卡(1234) 支付",
            "top=1164 h=103 | ¥30.00",
            "top=1342 h=41 | 账单详情〉",
        )
        assertTrue(r.items.isEmpty())
        assertEquals(Money(3000, Currency.CNY), r.amount)
        assertEquals("电量 73、卡号尾号 1234 都不是金额", listOf(Money(3000, Currency.CNY)), r.amountCandidates)
        assertEquals("付款方式那一行不是商家", "腾讯云费用账户-移动支付", r.merchant)
    }

    private companion object {
        val LOG = Regex("""top=(\d+) h=(\d+) \| (.*)""")
    }
}
