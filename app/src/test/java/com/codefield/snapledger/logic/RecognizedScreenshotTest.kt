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

    // ---- 组员的截图（2026-11-19）：金额、日期、卡号、账号、小店的名字都改过，行的位置和认错的字保持原样 ----

    /** 银行 App 的「이체완료」页：收款人后面带着「님에게」，下面的账号是一长串数字。 */
    @Test
    fun transferDone_recipientWithoutParticle() {
        val r = parse(
            "top=57 h=30 | 13:42 ee 804",
            "top=187 h=37 | 이체완료",
            "top=580 h=53 | 신한은행 한양대학교(한양대학님에게",
            "top=651 h=52 | 45,000원을 보냈어요",
            "top=798 h=31 | 추가메모 자동이체",
            "top=981 h=33 | 입금계좌 신한은행",
            "top=1038 h=26 | 110123456789",
            "top=1118 h=34 | 출금계좌 하나은행",
            "top=1253 h=40 | 수수료 면제",
            "top=1342 h=34 | 받는분에게 표시",
            "top=1426 h=38 | 나에게 표시 한양대학교(한양대학",
            "top=1587 h=29 | 접기스",
            "top=1792 h=34 | 공유 확인",
        )
        assertTrue(r.items.isEmpty())
        assertEquals(Money(45000, Currency.KRW), r.amount)
        assertEquals("账号不是金额", listOf(Money(45000, Currency.KRW)), r.amountCandidates)
        assertEquals("「님에게」不是名字的一部分", "신한은행 한양대학교(한양대학", r.merchant)
        assertNull("这一页没写日期", r.time)
        assertEquals(Category.EDUCATION, r.category)
    }

    /** 银行 App 的「거래내역상세」页：商家名是上面的标题，金额上面那一行是「거래구분 공과금」；余额被涂掉了。 */
    @Test
    fun transactionDetail_titleIsMerchant_notTheFieldRows() {
        val r = parse(
            "top=58 h=30 | 15:17 el! LTE 66",
            "top=186 h=38 | 거래내역상세",
            "top=328 h=50 | 한국전력",
            "top=540 h=32 | 거래일시 2026.11.14 11:20:15",
            "top=618 h=32 | 거래구분 공과금",
            "top=695 h=33 | 거래금액 -132,580",
            "top=771 h=40 | 거래후잔액",
            "top=851 h=33 | 추가메모 메모 입력 >",
            "top=1000 h=30 | 한국전력' 검색",
            "top=1260 h=32 | 카카오톡 문자 내역복사",
            "top=1792 h=34 | 확인",
        )
        assertTrue(r.items.isEmpty())
        assertEquals(Money(132580, Currency.KRW), r.amount)
        assertEquals("「거래구분 공과금」是一栏说明，不是商家", "한국전력", r.merchant)
        assertEquals(at(2026, 11, 14, 11, 20, 15), r.time)
        assertEquals(Category.HOUSING, r.category)
    }

    /** 同一种明细页，商家是没见过的名字时：「거래구분 공과금」说明这是水电燃气费。 */
    @Test
    fun transactionDetail_utilityKindGivesCategory() {
        val r = parse(
            "top=58 h=30 | 15:17 el! LTE 66",
            "top=186 h=38 | 거래내역상세",
            "top=328 h=50 | 삼천리",
            "top=540 h=32 | 거래일시 2026.11.14 11:20:15",
            "top=618 h=32 | 거래구분 공과금",
            "top=695 h=33 | 거래금액 -38,400",
            "top=771 h=40 | 거래후잔액",
        )
        assertEquals("삼천리", r.merchant)
        assertEquals(Category.HOUSING, r.category)
    }

    /**
     * 信用卡 App 的「카드별 이용 내역」：一张里 9 笔，金额没有正负号，「원」不是没认出来就是认成了「l」。
     * 每笔下面一行是卡号和「국내일시불」，有的中间还夹着商标上的小字（emart24、GS25）；最下面是下一天的日期。
     */
    @Test
    fun cardUsageList_rowsWithoutSignOrCurrency() {
        val r = parse(
            "top=58 h=31 | 15:21 ! LTE 65",
            "top=180 h=47 | 카드별 이용 내역",
            "top=362 h=24 | 2026.11.05",
            "top=434 h=41 | 향미싸지루 11,000 l",
            "top=492 h=32 | 본인1234 | 국내일시불",
            "top=591 h=46 | 카페(cafe)달달 4,500",
            "top=653 h=31 | 본인1234 | 국내일시불",
            "top=751 h=44 | 이마트24 한빛대점 5,300",
            "top=788 h=19 | emart24",
            "top=813 h=31 | 본인1234 | 국내일시불",
            "top=908 h=47 | (주)한솔 13,000 l",
            "top=971 h=31 | 본인1234| 국내일시불",
            "top=1067 h=48 | 지에스25 중앙본점 2,700",
            "top=1109 h=19 | GS25",
            "top=1130 h=31 | 본인1234| 국내일시불",
            "top=1230 h=46 | 지에스25 중앙본점 1,200",
            "top=1268 h=19 | GS25",
            "top=1289 h=31 | 본인1234| 국내일시불",
            "top=1388 h=43 | 교통-버스002건 1,850",
            "top=1448 h=32 | 본인1234 | 국내일시불(후불교통)",
            "top=1547 h=44 | 교통-지하철O01건 1,650",
            "top=1608 h=32 | 본인1234 | 국내일시불(후불교통)",
            "top=1710 h=38 | 쿠팡이츠쿠팡이츠_KICC 21,000 l",
            "top=1769 h=25 | 본인1234 | 국내일시불",
            "top=1893 h=23 | 2026.11.06",
        )
        val noon = at(2026, 11, 5, 12, 0)
        assertEquals(
            "每行左边是名字、行尾是金额；日期是上面的「2026.11.05」，没写时间按中午；最下面的日期是下一天的，不算",
            listOf(
                ParsedItem(Money(11000, Currency.KRW), noon, "향미싸지루", null),
                ParsedItem(Money(4500, Currency.KRW), noon, "카페(cafe)달달", Category.CAFE),
                ParsedItem(Money(5300, Currency.KRW), noon, "이마트24 한빛대점", Category.GROCERY),
                ParsedItem(Money(13000, Currency.KRW), noon, "(주)한솔", null),
                ParsedItem(Money(2700, Currency.KRW), noon, "지에스25 중앙본점", Category.GROCERY),
                ParsedItem(Money(1200, Currency.KRW), noon, "지에스25 중앙본점", Category.GROCERY),
                ParsedItem(Money(1850, Currency.KRW), noon, "교통-버스002건", Category.TRANSPORT),
                ParsedItem(Money(1650, Currency.KRW), noon, "교통-지하철O01건", Category.TRANSPORT),
                ParsedItem(Money(21000, Currency.KRW), noon, "쿠팡이츠쿠팡이츠_KICC", Category.FOOD),
            ),
            r.items,
        )
    }

    /** 同一种明细的另一张：日期「2026.10.17」的点认成了逗号，只有最后一笔认出了「원」。 */
    @Test
    fun cardUsageList_dateWithMisreadDot() {
        val r = parse(
            "top=57 h=30 | 15:22 el! LTE 65",
            "top=179 h=49 | 카드별 이용 내역",
            "top=358 h=23 | 2026.10,17",
            "top=430 h=43 | 아침일곱시 12,500 l",
            "top=489 h=31 | 본인1234| 국내일시불",
            "top=588 h=44 | 아침일곱시 7,000",
            "top=650 h=26 | 본인1234 | 국내일시불",
            "top=747 h=46 | 세탁oro아이스크림 1,400",
            "top=807 h=32 | 본인1234 | 국내일시불",
            "top=910 h=38 | 이마트24 새봄초점 16,200",
            "top=946 h=17 | emart24",
            "top=966 h=32 | 본인1234 | 국내일시불",
            "top=1063 h=46 | 이마트24 4,500",
            "top=1105 h=17 | emart24",
            "top=1125 h=30 | 본인1234| 국내일시불",
            "top=1225 h=44 | 세탁or아이스크림 10,000",
            "top=1284 h=31 | 본인1234 | 국내일시불",
            "top=1383 h=48 | 쿠팡이츠 쿠팡이츠_KCC 25,000",
            "top=1446 h=25 | 본인1234 | 국내일시불",
            "top=1546 h=39 | 쿠팡이츠쿠팡이츠_KICC 19,500 l",
            "top=1603 h=30 | 본인1234| 국내일시불",
            "top=1702 h=44 | CJ올리브영 시청앞역점 21,940 원",
            "top=1761 h=31 | 본인1234| 국내일시불",
            "top=1889 h=23 | 2026.10.,18",
        )
        assertEquals(
            "只有一笔带「원」也是 9 笔",
            listOf(12500L, 7000L, 1400L, 16200L, 4500L, 10000L, 25000L, 19500L, 21940L),
            r.items.map { it.amount.minor },
        )
        assertEquals("「2026.10,17」是 10 月 17 日", List(9) { at(2026, 10, 17, 12, 0) }, r.items.map { it.time })
    }

    /** 明细里有两笔认出了「원」、其余没认出来时，不能只记那两笔。 */
    @Test
    fun cardUsageList_someRowsKeepWon() {
        val r = parse(
            "top=57 h=30 | 15:22 el! LTE 65",
            "top=179 h=49 | 카드별 이용 내역",
            "top=358 h=23 | 2026.10.17",
            "top=430 h=43 | 아침일곱시 12,500 원",
            "top=489 h=31 | 본인1234| 국내일시불",
            "top=588 h=44 | 아침일곱시 7,000",
            "top=650 h=26 | 본인1234 | 국내일시불",
            "top=747 h=46 | 세탁or아이스크림 1,400",
            "top=807 h=32 | 본인1234 | 국내일시불",
            "top=910 h=38 | CJ올리브영 시청앞역점 21,940 원",
            "top=966 h=32 | 본인1234 | 국내일시불",
        )
        assertEquals(listOf(12500L, 7000L, 1400L, 21940L), r.items.map { it.amount.minor })
    }

    /** 明细只截到一笔时按单笔处理：「지에스25」里的 25、卡号尾号都不是金额。 */
    @Test
    fun cardUsageRow_alone_brandDigitsAreNotAnAmount() {
        val r = parse(
            "top=58 h=31 | 15:21 ! LTE 65",
            "top=180 h=47 | 카드별 이용 내역",
            "top=362 h=24 | 2026.11.05",
            "top=1067 h=48 | 지에스25 중앙본점 2,700",
            "top=1109 h=19 | GS25",
            "top=1130 h=31 | 본인1234| 국내일시불",
        )
        assertTrue(r.items.isEmpty())
        assertEquals(Money(2700, Currency.KRW), r.amount)
        assertEquals(listOf(Money(2700, Currency.KRW)), r.amountCandidates)
    }

    // ---- 纸质小票的照片（2026-11-20）：维基共享资源上公开授权的 4 张，人名、电话、事业者号码改过，行的位置和认错的字保持原样 ----

    /**
     * 首尔一家超市的小票：说明的字是拉开印的（「합 계」），合计下面还有收的现金和找零，上面是税额；
     * 店名写在「상호:」后面，也是拉开印的；日期只在流水号里（「03-20130713-000386」）。
     */
    @Test
    fun paperReceipt_totalIsNotTaxCashOrChange() {
        val r = parse(
            "top=207 h=55 | 24시간 연중무휴",
            "top=268 h=40 | 상호:동 대 문마트",
            "top=300 h=61 | 대표:김 민수 사업자NO: 123-45-67890",
            "top=349 h=60 | 주소:서울시 종로구 창신동 123-4호 102호",
            "top=408 h=43 | 문의 :02-123-4567",
            "top=489 h=54 | 상품명 수량 단 가 액",
            "top=588 h=42 | 광천)해저파래도 1 2,950 2,950",
            "top=636 h=43 | 롯데)파스타스150 1,200 2,400",
            "top=684 h=44 | 농심)우육탕큰사 1 950 950",
            "top=734 h=49 | 빙)바나니나우유혼 1 4,300 4,300",
            "top=781 h=46 | 롯데)누드빼빼로1 1 800 800",
            "top=876 h=40 | 과세액 - 10,364",
            "top=924 h=41 | 부가세 - 1,036",
            "top=973 h=51 | -면세액 - 0",
            "top=1017 h=48 | 합 계 11,400",
            "top=1065 h=48 | 현 급 50, 000",
            "top=1116 h=44 | 거스름 38,600",
            "top=1212 h=62 | 03-20130713-000386 16:33 001 김민수",
            "top=1262 h=63 | 금액뒤 *표시품목은 부가세 면세상품입니다",
            "top=1319 h=60 | 알아두기",
            "top=1359 h=66 | 1,반품및 교환시 영수증을 필히 지참하세요",
            "top=1411 h=68 | 2, 현금영수증시 계산전 미리 말씀해주세요",
            "top=1465 h=63 | 3, 배송이 지연될시 조금만 더 기다려주세요",
            "top=1517 h=59 | 4,스티커50장을 모으시면 고급각티슈 증정",
        )
        assertTrue("一张小票是一笔，税额那几行不是一笔一笔的账", r.items.isEmpty())
        assertEquals("「합 계」是合计；现金 50,000 和找零 38,600 不是", Money(11400, Currency.KRW), r.amount)
        assertEquals("「상호:」后面是店名，拉开印的字连起来", "동대문마트", r.merchant)
        assertEquals("日期在流水号里", at(2013, 7, 13, 16, 33), r.time)
        assertEquals(
            "给用户挑的只有像钱的数字：条款的序号（1、2、3）、流水号（386）、找零都不算",
            listOf(11400L, 4300L, 1200L, 2400L, 2950L),
            r.amountCandidates.map { it.minor },
        )
    }

    /** 太白一家餐馆的小票：字印得淡，店名没读对（「이태리설 /」）；合计上面是菜品明细。 */
    @Test
    fun paperReceipt_itemRowsAreNotTheMerchant() {
        val r = parse(
            "top=146 h=54 | [영수]",
            "top=248 h=63 | 이태리설 /",
            "top=318 h=54 | 강원똑!! 사시' 니백시 ! 의 262 (시동)",
            "top=371 h=48 | 1층",
            "top=433 h=57 | 033-123-4567 / 20251004-ü1-0017",
            "top=489 h=55 | 2025-10-04 12:33:37",
            "top=614 h=48 | 상품냉 금액",
            "top=732 h=46 | 페퍼오l| 개 집스 29,000 29,000",
            "top=789 h=53 | 토마토 따스타 14,000 1 14,000",
            "top=843 h=54 | 수제 돈가스 13,000 1 13,000",
            "top=906 h=51 | 새우 실시치 14,000 1 14,000",
            "top=966 h=50 | 콜라 2,000 3 6,000",
            "top=1081 h=56 | 합계 액 76,000-",
            "top=1197 h=53 | 부기세 이세 4기책 69,094",
            "top=1261 h=48 | 부 6,906",
            "top=1374 h=50 | 헌l영수 ( ',.:시) [1] ++*",
            "top=1438 h=44 | 확인번: :",
            "top=1493 h=50 | 승인번2 : 76,000",
            "top=1544 h=58 | 송인일시 : 2025 -10-04 12:33:37",
        )
        assertTrue(r.items.isEmpty())
        assertEquals(Money(76000, Currency.KRW), r.amount)
        assertEquals(at(2025, 10, 4, 12, 33, 37), r.time)
        assertNull("「새우 실시치 14,000 1 14,000」是一道菜，不是店名；店名没读对时空着让用户填", r.merchant)
    }

    /** 中国麦当劳的小票：最大的数字是取餐号，店名在「欢迎光临」后面，金额没有货币符号。 */
    @Test
    fun paperReceipt_welcomeLineNamesTheStore() {
        val r = parse(
            "top=485 h=28 | 请留意店内取餐屏",
            "top=554 h=70 | 39581",
            "top=628 h=31 | 欢迎光临东莞天宝地铁站餐厅",
            "top=695 h=31 | 产品详情",
            "top=726 h=31 | 数量 产品 价格",
            "top=759 h=30 | 1 薄荷醒醒特浓比利时黑巧 9.90",
            "top=830 h=28 | 产晶小计 9.90",
            "top=867 h=65 | 实付全额 9.90",
            "top=954 h=40 | 您可以通过麦当劳PP打描二维码查看订单状态以及开",
            "top=1002 h=30 | 发票。",
            "top=1028 h=43 | 麦当劳喜欢您来,欢您再来!",
        )
        assertTrue(r.items.isEmpty())
        assertEquals("取餐号 39581 不是金额", Money(990, Currency.CNY), r.amount)
        assertEquals("取餐号、数量也不给用户挑", listOf(Money(990, Currency.CNY)), r.amountCandidates)
        assertEquals("东莞天宝地铁站餐厅", r.merchant)
        assertEquals(Category.FOOD, r.category)
    }

    /** 美国加油站的小票：全是英文，金额带「$」；金额上面一行是栏目名（「GALLONS FUEL TOTAL」），店名是认识的品牌。 */
    @Test
    fun paperReceipt_knownBrandBeatsTheColumnHeader() {
        val r = parse(
            "top=26 h=40 | SHELL V-POWER",
            "top=76 h=40 | ACTIVELY CLEANS",
            "top=120 h=50 | AS Y0U DRIVE",
            "top=175 h=40 | SALES RECEIPT",
            "top=276 h=40 | SHELL",
            "top=326 h=40 | 2029 S SAGINAW",
            "top=376 h=40 | MIDLAND",
            "top=426 h=42 | MI 48640",
            "top=522 h=50 | DATE06/21/06 4:01 PM",
            "top=578 h=38 | INVOICE#",
            "top=623 h=44 | AUTH#",
            "top=676 h=40 | MASTERCARD",
            "top=726 h=40 | ACCOUNT NUMBER",
            "top=876 h=41 | PUMP PRODUCT $/G",
            "top=925 h=45 | 04 UNLD $2.969",
            "top=1025 h=44 | GALLONS FUEL TOTAL",
            "top=1074 h=45 | 6.736 $ 20.00",
            "top=1228 h=38 | SHELL V-POWER",
            "top=1276 h=42 | 0UR MOST ADVANCED",
            "top=1322 h=48 | FUEL EVER.",
        )
        assertTrue(r.items.isEmpty())
        assertEquals(Money(2000, Currency.USD), r.amount)
        assertEquals("门牌号 2029、邮编 48640 不给用户挑", listOf(Money(2000, Currency.USD)), r.amountCandidates)
        assertEquals("SHELL", r.merchant)
        assertEquals(Category.TRANSPORT, r.category)
    }

    private companion object {
        val LOG = Regex("""top=(\d+) h=(\d+) \| (.*)""")
    }
}
