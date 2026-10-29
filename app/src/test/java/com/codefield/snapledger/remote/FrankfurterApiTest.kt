package com.codefield.snapledger.remote

import com.codefield.snapledger.model.Currency
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 用接口真实返回过的 JSON 测试解析（2026-10-29 用 curl 取的，只留了两天）。 */
class FrankfurterApiTest {

    @Test
    fun series_eachDayWithExactRates() {
        val json = """
            {"amount":1.0,"base":"EUR","start_date":"2026-10-23","end_date":"2026-10-28",
             "rates":{"2026-10-28":{"CNY":7.4217,"JPY":175.86,"KRW":1492.35,"USD":1.1133},
                      "2026-10-23":{"CNY":7.466,"JPY":175.51,"KRW":1482.46,"USD":1.1193}}}
        """.trimIndent()
        val days = FrankfurterApi.parse(json)
        assertEquals("按日期排好", listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 28)), days.map { it.date })
        val last = days.last().perEuro
        assertEquals("文字原样转成 BigDecimal，不经过 Double", BigDecimal("1492.35"), last[Currency.KRW])
        assertEquals(BigDecimal("7.4217"), last[Currency.CNY])
        assertEquals(BigDecimal("175.86"), last[Currency.JPY])
        assertEquals(BigDecimal.ONE, last[Currency.EUR])
    }

    @Test
    fun singleDay() {
        val days = FrankfurterApi.parse("""{"amount":1.0,"base":"EUR","date":"2026-10-28","rates":{"KRW":1492.35,"CNY":7.4217}}""")
        assertEquals(LocalDate.of(2026, 10, 28), days.single().date)
        assertEquals(BigDecimal("1492.35"), days.single().perEuro[Currency.KRW])
    }

    @Test
    fun unknownCurrency_isSkipped() {
        val day = FrankfurterApi.parse("""{"date":"2026-10-28","rates":{"GBP":0.85,"KRW":1492.35}}""").single()
        assertEquals(setOf(Currency.KRW, Currency.EUR), day.perEuro.keys)
    }

    @Test(expected = IOException::class)
    fun notJson_isAnIoError() {
        FrankfurterApi.parse("<html>502 Bad Gateway</html>")
    }

    @Test(expected = IOException::class)
    fun noRates_isAnIoError() {
        FrankfurterApi.parse("""{"message":"not found"}""")
    }
}
