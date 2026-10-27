package com.codefield.snapledger.logic

/**
 * 找商家名。候选有两种：「가맹점」「商户全称」「商品说明」这类标签后面的字，以及其他像名字的行。
 * 加分：紧挨在付款金额上面（付款页一般把商家名写在金额上面）、是认识的品牌。
 * 减分：带 *（「홍*동」这种是打了码的人名，不是商家）。
 */
internal object MerchantFinder {

    /** 长的放前面：「商户全称」要比「商户」先匹配。 */
    private val KEYS = listOf(
        "가맹점명", "가맹점", "사용처", "상호명", "상호", "결제처", "이용처",
        "商户全称", "商户名称", "商户", "收款方全称", "收款方", "商品说明", "商品",
        "merchant", "store",
    )

    /** 状态、付款方式这类行，不会是商家名。 */
    private val STATUS = listOf(
        "결제 완료", "결제완료", "결제 성공", "결제성공", "승인", "취소", "일시불", "할부", "입금", "출금", "송금", "web발신",
        "支付成功", "交易成功", "付款成功", "已支付", "支付完成", "退款", "当前状态",
        "payment", "paid", "success", "completed", "receipt",
    )
    private val AMOUNT_WITH_MARKER = Regex("""(₩|¥|\$|€)\s*\d|\d\s*(원|元|円)""")
    private const val MIN_SCORE = 2

    private class Option(val text: String, val score: Int)

    fun find(lines: List<String>, amountLine: Int?): String? {
        val options = mutableListOf<Option>()
        lines.forEachIndexed { i, line ->
            val trimmed = line.trim()
            val key = KEYS.firstOrNull { trimmed.lowercase().startsWith(it) }
            if (key != null) {
                val value = trimmed.substring(key.length).trimStart(':', ' ').trim()
                val text = value.ifEmpty { lines.getOrNull(i + 1)?.trim().orEmpty() }
                if (isNameLike(text)) options += Option(text, score(text, labelled = true, aboveAmount = false))
            } else if (isNameLike(trimmed)) {
                options += Option(trimmed, score(trimmed, labelled = false, aboveAmount = amountLine != null && i == amountLine - 1))
            }
        }
        // 同分时选短的：「瑞幸咖啡」比「瑞幸咖啡（北京）有限公司」好认。
        return options.filter { it.score >= MIN_SCORE }
            .sortedWith(compareByDescending<Option> { it.score }.thenBy { it.text.length })
            .firstOrNull()?.text
    }

    private fun score(text: String, labelled: Boolean, aboveAmount: Boolean): Int {
        var score = 0
        if (labelled) score += 3
        if (aboveAmount) score += 3
        if (CategoryGuesser.guess(text) != null) score += 4
        if ('*' in text) score -= 4
        return score
    }

    private fun isNameLike(text: String): Boolean {
        if (text.length !in 2..40 || text.none { it.isLetter() }) return false
        val lower = text.lowercase()
        if (STATUS.any { it in lower } || lower in KEYS) return false
        if (AMOUNT_WITH_MARKER.containsMatchIn(text) || DateTimeFinder.spans(text).isNotEmpty()) return false
        return text.count { it.isDigit() } * 2 <= text.length
    }
}
