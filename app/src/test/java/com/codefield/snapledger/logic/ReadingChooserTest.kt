package com.codefield.snapledger.logic

import com.codefield.snapledger.model.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「乱码」是模拟另一个模型读不认识的字时的样子：英文和数字读对了，别的字读成不相干的字。 */
class ReadingChooserTest {

    private fun reading(vararg lines: String) = lines.map { OcrLine(it) }

    private val koreanRead = reading("결제 완료", "스타벅스 강남점", "6,500원", "2026.10.28 14:32", "결제수단 카카오페이머니")
    private val koreanGarbled = reading("包利 妃显", "人目社人 社甘召", "6,500台", "2026.10.28 14:32", "包利十已 升升立叫")
    private val chineseRead = reading("支付成功", "瑞幸咖啡", "-23.50", "支付时间 2026年10月28日 09:41:05", "支付方式 零钱")
    private val chineseGarbled = reading("지시서공", "휴히어비", "-23.50", "지시이희 2026녀10무28디 09:41:05", "지시스시 퍼차")

    @Test
    fun koreanScreenshot_picksTheKoreanModel() {
        assertEquals(koreanRead, ReadingChooser.choose(listOf(koreanRead, koreanGarbled)))
    }

    @Test
    fun chineseScreenshot_picksTheChineseModel() {
        assertEquals(chineseRead, ReadingChooser.choose(listOf(chineseGarbled, chineseRead)))
    }

    @Test
    fun englishOnly_keepsTheFirstReading() {
        val first = reading("Starbucks", "Total", "\$5.40")
        val second = reading("Starbucks", "Total", "\$5.40 ")
        assertEquals(first, ReadingChooser.choose(listOf(first, second)))
    }

    @Test
    fun nothingRead_givesNoLines() {
        assertTrue(ReadingChooser.choose(emptyList()).isEmpty())
    }
}
