package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money

/** 记账对话框里金额输入框的文字和金额之间的转换。 */
object AmountInput {

    private val PATTERN = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?|\.\d+""")

    /**
     * 「6,500」「6500」「23.5」「23.50」都可以；小数位比这种货币允许的多（比如韩元带小数）、
     * 不是正数、格式不对时返回 null。
     */
    fun parse(text: String, currency: Currency): Money? {
        val cleaned = TextNormalizer.normalize(text).trim().replace(" ", "")
        if (!PATTERN.matches(cleaned)) return null
        val parts = cleaned.replace(",", "").split('.')
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()
        val digits = currency.fractionDigits
        if (fraction.length > digits) return null
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val minor = whole * factor + (fraction.padEnd(digits, '0').ifEmpty { "0" }).toLong()
        return if (minor > 0) Money(minor, currency) else null
    }

    /** 编辑已有的一笔时，输入框里显示的文字：韩元「6500」，人民币「23.50」。 */
    fun format(money: Money): String {
        val digits = money.currency.fractionDigits
        if (digits == 0) return money.minor.toString()
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val fraction = (money.minor % factor).toString().padStart(digits, '0')
        return "${money.minor / factor}.$fraction"
    }
}
