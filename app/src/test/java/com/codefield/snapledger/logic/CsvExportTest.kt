package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.GeoPoint
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val rates = RateTable(
        listOf(
            DailyRates(
                LocalDate.of(2026, 10, 1),
                mapOf(Currency.EUR to BigDecimal.ONE, Currency.KRW to BigDecimal("1500"), Currency.CNY to BigDecimal("7.5")),
            ),
        )
    )
    private val labels = CsvLabels(
        date = "日期", time = "时间", merchant = "商家", category = "类别", amount = "金额",
        currency = "货币", inHome = "折合 KRW", note = "备注", latitude = "纬度", longitude = "经度",
        categories = mapOf(Category.CAFE to "咖啡", Category.FOOD to "餐饮"),
    )

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(seoul).toInstant().toEpochMilli()

    private fun lines(entries: List<Entry>): List<String> =
        CsvExport.csv(entries, Currency.KRW, rates, seoul, labels).removePrefix("\uFEFF").split("\r\n")

    @Test
    fun csv_hasBomHeaderAndOneLinePerEntryOldestFirst() {
        val text = CsvExport.csv(
            listOf(
                Entry(2, Money(2350, Currency.CNY), Category.CAFE, "瑞幸咖啡", at(10, 8, 5), note = "和朋友, 两杯"),
                Entry(1, Money(6500, Currency.KRW), Category.FOOD, "국밥집", at(3, 12, 30), location = GeoPoint(37.5559, 127.0444)),
            ),
            Currency.KRW, rates, seoul, labels,
        )
        assertTrue("Excel 要靠 BOM 认出 UTF-8", text.startsWith("\uFEFF"))
        assertEquals(
            listOf(
                "日期,时间,商家,类别,金额,货币,折合 KRW,备注,纬度,经度",
                "2026-10-03,12:30,국밥집,餐饮,6500,KRW,6500,,37.555900,127.044400",
                // 23.50 元 ÷ 7.5 × 1500 = 4700 韩元；备注里有逗号，整格加引号
                "2026-10-10,08:05,瑞幸咖啡,咖啡,23.50,CNY,4700,\"和朋友, 两杯\",,",
                "",
            ),
            text.removePrefix("\uFEFF").split("\r\n"),
        )
    }

    @Test
    fun csv_leavesTheHomeAmountEmptyWithoutARate() {
        val row = lines(listOf(Entry(1, Money(999, Currency.USD), Category.OTHER, "Steam", at(21, 22, 0))))[1]
        assertEquals("没给名字的类别用编号", "2026-10-21,22:00,Steam,OTHER,9.99,USD,,,,", row)
    }

    @Test
    fun quote_wrapsCommasQuotesAndLineBreaks() {
        assertEquals("plain", CsvExport.quote("plain"))
        assertEquals("\"Coffee, Tea\"", CsvExport.quote("Coffee, Tea"))
        assertEquals("\"说 \"\"好吃\"\"\"", CsvExport.quote("说 \"好吃\""))
        assertEquals("\"第一行\n第二行\"", CsvExport.quote("第一行\n第二行"))
    }

    @Test
    fun text_keepsSpreadsheetsFromRunningFormulas() {
        assertEquals("'=HYPERLINK(\"x\")", CsvExport.text("=HYPERLINK(\"x\")"))
        assertEquals("'+82 10", CsvExport.text("+82 10"))
        assertEquals("'@home", CsvExport.text("@home"))
        assertEquals("스타벅스", CsvExport.text("스타벅스"))
        assertEquals("", CsvExport.text(""))
    }

    @Test
    fun plain_usesTheCurrencyDecimals() {
        assertEquals("6500", CsvExport.plain(Money(6500, Currency.KRW)))
        assertEquals("23.50", CsvExport.plain(Money(2350, Currency.CNY)))
        assertEquals("0.05", CsvExport.plain(Money(5, Currency.USD)))
        assertEquals("1200", CsvExport.plain(Money(1200, Currency.JPY)))
    }
}
