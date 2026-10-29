package com.codefield.snapledger.remote

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 从网上取汇率。测试时换成假的。 */
fun interface RateSource {
    /** from 到 to（null 表示到最新）每个工作日的汇率。网络或数据出错时抛 IOException。 */
    suspend fun fetch(from: LocalDate, to: LocalDate?): List<DailyRates>
}

/**
 * Frankfurter 汇率接口：欧洲央行每个工作日公布的参考汇率，免费、不需要密钥，以欧元为基准。
 * 起始日是周末时，接口会从之前最近的工作日开始返回。
 */
class FrankfurterApi(private val baseUrl: String = "https://api.frankfurter.dev/v1") : RateSource {

    override suspend fun fetch(from: LocalDate, to: LocalDate?): List<DailyRates> = withContext(Dispatchers.IO) {
        val connection = URL("$baseUrl/$from..${to ?: ""}?symbols=$SYMBOLS").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                // 这段时间还没有公布汇率（比如只查今天，而今天的还没出来）。
                HttpURLConnection.HTTP_NOT_FOUND -> emptyList()
                else -> throw IOException("Frankfurter HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** 要哪几种货币（欧元是基准，不用要）。 */
        private val SYMBOLS = Currency.entries.filter { it != Currency.EUR }.joinToString(",")

        /**
         * 解析接口返回的 JSON。一段时间的：{"rates": {"2026-10-27": {"KRW": 1480.44, …}, …}}；
         * 只有一天的：{"date": "2026-10-28", "rates": {"KRW": 1492.35, …}}。
         * 汇率按原样的文字转成 BigDecimal，不经过 Double，避免小数误差。不认识的货币跳过。
         */
        internal fun parse(json: String): List<DailyRates> {
            val root = try {
                Json.parseToJsonElement(json).jsonObject
            } catch (e: IllegalArgumentException) {
                throw IOException("Frankfurter: not JSON", e)
            }
            val rates = root["rates"] as? JsonObject ?: throw IOException("Frankfurter: no rates")
            val days = if (rates.values.all { it is JsonObject }) {
                rates.mapValues { (_, value) -> value.jsonObject }
            } else {
                val date = root["date"]?.jsonPrimitive?.content ?: throw IOException("Frankfurter: no date")
                mapOf(date to rates)
            }
            return days.mapNotNull { (date, values) -> day(date, values) }.sortedBy { it.date }
        }

        private fun day(date: String, values: JsonObject): DailyRates? {
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
            val perEuro = values.mapNotNull { (code, value) ->
                val currency = Currency.entries.firstOrNull { it.name == code } ?: return@mapNotNull null
                val rate = value.jsonPrimitive.content.toBigDecimalOrNull() ?: return@mapNotNull null
                currency to rate
            }.toMap()
            return DailyRates(day, perEuro + (Currency.EUR to BigDecimal.ONE))
        }
    }
}
