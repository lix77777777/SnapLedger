package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 金额显示成「₩6,500」「¥23.50」「$12.99」。千位分隔符统一用逗号。界面和通知都用这一份。 */
object MoneyFormat {

    fun format(money: Money): String {
        val digits = money.currency.fractionDigits
        val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
        val format = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        return symbol(money.currency) + format.format(BigDecimal(money.minor).movePointLeft(digits))
    }

    /** 日元和人民币都用 ¥，日元写成「JP¥」区分开。 */
    fun symbol(currency: Currency): String = when (currency) {
        Currency.KRW -> "₩"
        Currency.CNY -> "¥"
        Currency.USD -> "$"
        Currency.JPY -> "JP¥"
        Currency.EUR -> "€"
    }
}
