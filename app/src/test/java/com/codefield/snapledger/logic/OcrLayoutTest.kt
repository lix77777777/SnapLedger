package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.OcrBox
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLayoutTest {

    @Test
    fun labelAndValueOnOneRow_areJoinedLeftToRight() {
        val lines = OcrLayout.lines(
            listOf(
                OcrBox("6,500원", left = 700, top = 495, right = 1000, bottom = 535),
                OcrBox("결제금액", left = 40, top = 500, right = 200, bottom = 530),
            )
        )
        assertEquals(listOf("결제금액 6,500원"), lines.map { it.text })
        assertEquals(495, lines[0].top)
        assertEquals(40, lines[0].height)
    }

    @Test
    fun stackedSmallLinesNextToABigAmount_stayTwoRows() {
        val lines = OcrLayout.lines(
            listOf(
                OcrBox("결제금액", left = 40, top = 500, right = 200, bottom = 530),
                OcrBox("(부가세 포함)", left = 40, top = 540, right = 240, bottom = 570),
                OcrBox("6,500원", left = 700, top = 495, right = 1000, bottom = 575),
            )
        )
        assertEquals(listOf("결제금액 6,500원", "(부가세 포함)"), lines.map { it.text })
    }

    @Test
    fun rowsComeOutTopToBottom_andBlankBoxesAreDropped() {
        val lines = OcrLayout.lines(
            listOf(
                OcrBox("2026.10.28 14:32", left = 40, top = 900, right = 400, bottom = 930),
                OcrBox(" ", left = 40, top = 100, right = 60, bottom = 130),
                OcrBox("스타벅스 강남점", left = 40, top = 300, right = 500, bottom = 340),
            )
        )
        assertEquals(listOf("스타벅스 강남점", "2026.10.28 14:32"), lines.map { it.text })
    }

    /** 明细页：左边是说明，右边是值。不拼行的话，余额那个数字前面就没有「잔액」，会被当成付款金额。 */
    @Test
    fun detailPage_balanceIsNotTakenAsThePayment() {
        val rows = listOf("가맹점" to "스타벅스 강남점", "잔액" to "120,000원", "결제금액" to "6,500원", "결제일시" to "2026.10.28 14:32")
        // 识别结果常常先给左边一列，再给右边一列。
        val boxes = rows.mapIndexed { i, (label, _) -> OcrBox(label, 40, 300 + i * 80, 240, 340 + i * 80) } +
            rows.mapIndexed { i, (_, value) -> OcrBox(value, 600, 298 + i * 80, 1000, 342 + i * 80) }
        val parsed = ReceiptParser.parse(OcrLayout.lines(boxes), now = 0, zone = ZoneId.of("Asia/Seoul"), fallbackCurrency = Currency.KRW)
        assertEquals(Money(6500, Currency.KRW), parsed.amount)
        assertEquals("스타벅스 강남점", parsed.merchant)
    }
}
