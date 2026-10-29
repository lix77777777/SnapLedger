package com.codefield.snapledger.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.RateQuote
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 金额显示成「₩6,500」「¥23.50」「$12.99」。千位分隔符统一用逗号。 */
fun formatMoney(money: Money): String {
    val digits = money.currency.fractionDigits
    val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
    val format = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
    return currencySymbol(money.currency) + format.format(BigDecimal(money.minor).movePointLeft(digits))
}

/** 日元和人民币都用 ¥，日元写成「JP¥」区分开。 */
fun currencySymbol(currency: Currency): String = when (currency) {
    Currency.KRW -> "₩"
    Currency.CNY -> "¥"
    Currency.USD -> "$"
    Currency.JPY -> "JP¥"
    Currency.EUR -> "€"
}

/** 汇率显示成「1 CNY = ₩201.08」「1,000 KRW = ¥4.97」。 */
fun formatQuote(quote: RateQuote, from: Currency, to: Currency): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    val units = DecimalFormat("#,##0", symbols).format(quote.units)
    return "$units ${from.name} = ${currencySymbol(to)}${DecimalFormat("#,##0.00", symbols).format(quote.value)}"
}

@StringRes
fun currencyName(currency: Currency): Int = when (currency) {
    Currency.KRW -> R.string.currency_krw
    Currency.CNY -> R.string.currency_cny
    Currency.USD -> R.string.currency_usd
    Currency.JPY -> R.string.currency_jpy
    Currency.EUR -> R.string.currency_eur
}

@StringRes
fun categoryName(category: Category): Int = when (category) {
    Category.FOOD -> R.string.category_food
    Category.CAFE -> R.string.category_cafe
    Category.GROCERY -> R.string.category_grocery
    Category.TRANSPORT -> R.string.category_transport
    Category.SHOPPING -> R.string.category_shopping
    Category.HOUSING -> R.string.category_housing
    Category.ENTERTAINMENT -> R.string.category_entertainment
    Category.HEALTH -> R.string.category_health
    Category.EDUCATION -> R.string.category_education
    Category.TRAVEL -> R.string.category_travel
    Category.OTHER -> R.string.category_other
}

/** 先用表情符号当类别图标，第 10 步统一换成正式图标。 */
fun categoryEmoji(category: Category): String = when (category) {
    Category.FOOD -> "🍜"
    Category.CAFE -> "☕"
    Category.GROCERY -> "🛒"
    Category.TRANSPORT -> "🚌"
    Category.SHOPPING -> "🛍️"
    Category.HOUSING -> "🏠"
    Category.ENTERTAINMENT -> "🎬"
    Category.HEALTH -> "💊"
    Category.EDUCATION -> "📚"
    Category.TRAVEL -> "✈️"
    Category.OTHER -> "📦"
}

/** 每个类别一个颜色，统计图表也用这一套。 */
fun categoryColor(category: Category): Color = when (category) {
    Category.FOOD -> Color(0xFFEF6C00)
    Category.CAFE -> Color(0xFF8D6E63)
    Category.GROCERY -> Color(0xFF43A047)
    Category.TRANSPORT -> Color(0xFF1E88E5)
    Category.SHOPPING -> Color(0xFFD81B60)
    Category.HOUSING -> Color(0xFF5E35B1)
    Category.ENTERTAINMENT -> Color(0xFFFDD835)
    Category.HEALTH -> Color(0xFFE53935)
    Category.EDUCATION -> Color(0xFF00897B)
    Category.TRAVEL -> Color(0xFF039BE5)
    Category.OTHER -> Color(0xFF757575)
}
