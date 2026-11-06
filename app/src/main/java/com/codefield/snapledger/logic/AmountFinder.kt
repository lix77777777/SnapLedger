package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import kotlin.math.roundToInt

/**
 * 一个可能是付款金额的数字。
 * @param sign 前面的正负号：'-' 是付出去的钱，'+' 是收到的钱，没写是 null。
 * @param marked 带货币符号或单位（₩、원、¥、元……）。
 * @param balance 旁边写着「잔액」「余额」这类词：是余额、积分，不是这次付的钱。
 * @param labelled 旁边写着「결제금액」「实付」「합계」这类词。
 * @param start 金额（连同正负号、货币符号）在这一行里从第几个字开始。列表里前面的字常常是商家名。
 */
internal data class AmountCandidate(
    val money: Money,
    val lineIndex: Int,
    val score: Int,
    val height: Int,
    val sign: Char? = null,
    val marked: Boolean = false,
    val balance: Boolean = false,
    val labelled: Boolean = false,
    val start: Int = 0,
)

/**
 * 找付款金额：把每一行里像金额的数字都找出来打分，分数最高的最可能是这次付的钱。
 * 加分：带货币符号、前面有减号（付款）、旁边有「결제금액」「实付」这类词、字比别的大。
 * 减分：旁边有「잔액」「余额」「누적」这类词（余额、累计都不是这次付的钱）。
 */
internal object AmountFinder {

    /** 带千位逗号的数字后面可以多一个数字：「원」常被认成「2」（「137,1892」）。 */
    private val NUMBER = Regex("""(₩|¥|\$|€|KRW|CNY|RMB|USD|EUR|JPY|人民币)?\s*(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?(\d)?(?!\d)\s*(원|元|円|块|KRW|CNY|RMB|USD|EUR|JPY)?""")
    private val MARKERS = Regex("""₩|¥|\$|€|KRW|CNY|RMB|USD|EUR|JPY|人民币|원|元|円|块""")

    private val STRONG = listOf(
        "결제금액", "결제 금액", "승인금액", "이용금액", "총 결제", "총결제", "합계", "총액",
        "实付", "支付金额", "付款金额", "交易金额", "合计", "总计", "total", "amount", "paid",
    )
    private val WEAK = listOf("결제", "금액", "支付", "付款", "金额", "price")
    /** 「subtotal」里也有「total」，所以小计、税额要单独减分。「출금가능금액」是账户里还能取的钱。 */
    private val NEGATIVE = listOf(
        "잔액", "누적", "한도", "포인트", "적립", "캐시백", "할인", "부가세", "출금가능", "가능금액",
        "余额", "可用", "积分", "返现", "优惠", "立减", "税额",
        "balance", "limit", "point", "discount", "subtotal",
    )

    /** 数字后面紧跟这些字时是数量、日期或百分比，不是金额。 */
    private const val COUNT_SUFFIXES = "개명건회일시분초월년个次件人天期号번%"

    fun find(lines: List<String>, heights: List<Int>, documentCurrency: Currency, hasKana: Boolean): List<AmountCandidate> {
        val maxHeight = heights.maxOrNull() ?: 0
        val result = mutableListOf<AmountCandidate>()
        lines.forEachIndexed { index, rawLine ->
            if (DateTimeFinder.isStatusBar(rawLine, index)) return@forEachIndexed
            val line = blank(rawLine, DateTimeFinder.spans(rawLine))
            // 金额单独一行时，说明文字（比如「결제금액」）一般在上一行。
            val label = if (isBareAmount(rawLine)) lines.getOrNull(index - 1).orEmpty() + " " + rawLine else rawLine
            val context = label.lowercase()
            for (m in NUMBER.findAll(line)) {
                val height = heights.getOrElse(index) { 0 }
                candidate(line, m, index, context, height, maxHeight, documentCurrency, hasKana)?.let { result += it }
            }
        }
        return result.sortedWith(
            compareByDescending<AmountCandidate> { it.score }.thenByDescending { it.height }.thenBy { it.lineIndex }
        )
    }

    private fun candidate(
        line: String,
        m: MatchResult,
        index: Int,
        context: String,
        height: Int,
        maxHeight: Int,
        documentCurrency: Currency,
        hasKana: Boolean,
    ): AmountCandidate? {
        val prefix = m.groupValues[1]
        val integer = m.groupValues[2]
        val decimals = m.groupValues[3]
        val stray = m.groupValues[4]
        val suffix = m.groupValues[5]
        val numberStart = m.groups[2]!!.range.first
        val numberEnd = (m.groups[4] ?: m.groups[3] ?: m.groups[2])!!.range.last + 1
        // 前面紧挨着数字、小数点或逗号，或者后面还连着「,数字」：只是一个更长数字的一部分。
        if (numberStart > 0 && line[numberStart - 1].let { it.isDigit() || it == '.' || it == ',' }) return null
        if (line.getOrNull(numberEnd) == ',' && line.getOrNull(numberEnd + 1)?.isDigit() == true) return null
        val grouped = ',' in integer
        if (stray.isNotEmpty() && (!grouped || decimals.isNotEmpty())) return null

        val hasMarker = prefix.isNotEmpty() || suffix.isNotEmpty()
        if (!hasMarker) {
            // 和英文连在一起的数字是名字、型号的一部分：「GS25」「5G」。前面只有一个 W 时可能是没认出来的 ₩，不算。
            // 带千位逗号的不会是型号，后面的字母多半是认错的「원」（「10,0002A」）。
            if (!grouped && (wordBefore(line, numberStart) || line.getOrNull(numberEnd)?.let { it in 'A'..'Z' } == true)) return null
            val next = line.drop(numberEnd).trimStart().firstOrNull()
            if (next != null && next in COUNT_SUFFIXES) return null
            // 没有逗号的长数字是订单号、卡号、电话号码；括号里的是卡号尾号「(1234)」。
            if (integer.length >= 7 && !grouped) return null
            if (line.getOrNull(numberStart - 1) == '(' && line.getOrNull(numberEnd) == ')') return null
        }
        if (integer.count { it.isDigit() } >= 13) return null

        val currency = currencyOf(prefix.ifEmpty { suffix }, documentCurrency, hasKana)
        val minor = toMinor(integer, decimals, currency) ?: return null
        val markerStart = if (prefix.isNotEmpty()) m.groups[1]!!.range.first else numberStart
        val signAt = signIndex(line, markerStart)
        val sign = signAt?.let { line[it] }
        val labelled = STRONG.any { it in context }
        val balance = NEGATIVE.any { it in context }

        var score = 0
        if (hasMarker) score += 3
        if (sign == '-') score += 2
        when {
            labelled -> score += 4
            WEAK.any { it in context } -> score += 2
        }
        if (balance) score -= 6
        if (minor == 0L) score -= 5
        if (maxHeight > 0) score += (3.0 * height / maxHeight).roundToInt()
        return AmountCandidate(Money(minor, currency), index, score, height, sign, hasMarker, balance, labelled, signAt ?: markerStart)
    }

    /** 数字（或货币符号）前面的正负号在第几个字。卡号「****-5678」里的减号不算，减号前面得是行首、空格或冒号。 */
    private fun signIndex(line: String, start: Int): Int? {
        var i = start - 1
        while (i >= 0 && line[i] == ' ') i--
        if (i < 0 || line[i] != '-' && line[i] != '+') return null
        val before = line.getOrNull(i - 1)
        return if (before == null || before.isWhitespace() || before == ':') i else null
    }

    private fun currencyOf(marker: String, fallback: Currency, hasKana: Boolean): Currency = when (marker) {
        "₩", "원", "KRW" -> Currency.KRW
        "元", "块", "CNY", "RMB", "人民币" -> Currency.CNY
        "円", "JPY" -> Currency.JPY
        "¥" -> if (hasKana) Currency.JPY else Currency.CNY
        "$", "USD" -> Currency.USD
        "€", "EUR" -> Currency.EUR
        else -> fallback
    }

    private fun toMinor(integer: String, decimals: String, currency: Currency): Long? {
        val whole = integer.replace(",", "").toLongOrNull() ?: return null
        val digits = currency.fractionDigits
        if (digits == 0) return whole
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val fraction = decimals.padEnd(digits, '0').take(digits).toLong()
        return whole * factor + fraction
    }

    private fun wordBefore(line: String, start: Int): Boolean =
        line.getOrNull(start - 1)?.isAsciiLetter() == true && line.getOrNull(start - 2)?.isAsciiLetter() == true

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun isBareAmount(line: String): Boolean = line.replace(MARKERS, "").none { it.isLetter() }

    private fun blank(line: String, spans: List<IntRange>): String {
        if (spans.isEmpty()) return line
        val chars = line.toCharArray()
        for (range in spans) for (i in range) chars[i] = ' '
        return String(chars)
    }
}
