package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** 存下来的各天汇率。周末和节假日没有新汇率，用之前最近一天的。 */
class RateTable(days: Collection<DailyRates>) {

    private val sorted = days.sortedBy { it.date }

    /** 那一天能用的汇率：当天或之前最近的一天；都没有就用之后最早的一天；一天都没有时是 null。 */
    fun ratesFor(date: LocalDate): DailyRates? = sorted.lastOrNull { !it.date.isAfter(date) } ?: sorted.firstOrNull()

    /** 存下来的最早、最新的一天；一天都没有时是 null。 */
    val first: DailyRates? get() = sorted.firstOrNull()
    val latest: DailyRates? get() = sorted.lastOrNull()

    companion object {
        val EMPTY = RateTable(emptyList())
    }
}

/** 显示用的汇率：units 个原货币值 value 个目标货币（保留两位小数）。 */
data class RateQuote(val units: Long, val value: BigDecimal)

object CurrencyConverter {

    /** 显示汇率时单位最多放大到这么多，免得汇率异常时一直放大下去。 */
    private const val MAX_QUOTE_UNITS = 1_000_000L

    /**
     * 显示用的汇率，比如「1 CNY = 201.08 KRW」「1,000 KRW = 4.97 CNY」：
     * 单位取 1、10、100…里最小的、换算后不小于 1 的那个。缺少汇率时返回 null。
     */
    fun quote(from: Currency, to: Currency, rates: DailyRates?): RateQuote? {
        val fromRate = rates?.perEuro?.get(from) ?: return null
        val toRate = rates.perEuro[to] ?: return null
        val perUnit = toRate.divide(fromRate, MathContext.DECIMAL64)
        var units = 1L
        while (perUnit.multiply(BigDecimal(units)) < BigDecimal.ONE && units < MAX_QUOTE_UNITS) units *= 10
        return RateQuote(units, perUnit.multiply(BigDecimal(units)).setScale(2, RoundingMode.HALF_EVEN))
    }

    /**
     * 把金额换算成另一种货币（四舍六入五成双，结果是目标货币的最小单位）。
     * 货币相同时原样返回；缺少需要的汇率时返回 null。
     */
    fun convert(money: Money, to: Currency, rates: DailyRates?): Money? {
        if (money.currency == to) return money
        val from = rates?.perEuro?.get(money.currency) ?: return null
        val target = rates.perEuro[to] ?: return null
        val major = BigDecimal(money.minor).movePointLeft(money.currency.fractionDigits)
        val converted = major.divide(from, MathContext.DECIMAL64).multiply(target)
        val minor = converted.movePointRight(to.fractionDigits).setScale(0, RoundingMode.HALF_EVEN)
        return Money(minor.longValueExact(), to)
    }
}
