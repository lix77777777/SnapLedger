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

    companion object {
        val EMPTY = RateTable(emptyList())
    }
}

object CurrencyConverter {

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
