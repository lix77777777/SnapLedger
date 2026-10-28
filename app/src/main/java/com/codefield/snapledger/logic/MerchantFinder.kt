package com.codefield.snapledger.logic

/**
 * 找商家名。候选有两种：「가맹점」「商户全称」「商品说明」这类标签后面的字，以及其他像名字的行。
 * 加分：在付款金额上面最近的那个名字（付款页一般把商家名写在金额上面）、是认识的品牌。
 * 减分：带 *（「홍*동」这种是打了码的人名，不是商家）。
 */
internal object MerchantFinder {

    /** 长的放前面：「商户全称」要比「商户」先匹配。 */
    private val KEYS = listOf(
        "가맹점명", "가맹점", "사용처", "상호명", "상호", "결제처", "이용처",
        "商户全称", "商户名称", "商户", "收款方全称", "收款方", "商品说明", "商品",
        "merchant", "store",
    )

    /** 状态、付款方式、按钮这类行，不会是商家名。 */
    private val STATUS = listOf(
        "결제 완료", "결제완료", "결제 성공", "결제성공", "승인", "취소", "일시불", "할부", "입금", "출금", "송금", "web발신",
        "체크카드", "신용카드", "펌뱅킹", "상세", "내역", "설정",
        "支付成功", "交易成功", "付款成功", "已支付", "支付完成", "退款", "当前状态",
        "使用", "储蓄卡", "儲蓄卡", "信用卡", "借记卡", "详情", "明细", "设置",
        "payment", "paid", "success", "completed", "receipt",
    )
    private val AMOUNT_WITH_MARKER = Regex("""(₩|¥|\$|€)\s*\d|\d\s*(원|元|円)""")
    private const val MIN_SCORE = 2
    /** 往金额上面找商家名时，最多看几行。 */
    private const val LOOK_UP = 3
    /** 图标常被认成这些符号（「②腾讯云」里的 ②），名字两头的去掉。 */
    private const val JUNK = "|•·©®@<>〈〉《》‹›«»"

    private class Option(val text: String, val score: Int)

    fun find(lines: List<String>, amountLine: Int?): String? {
        val nearest = amountLine?.let { line -> (line - 1 downTo maxOf(0, line - LOOK_UP)).firstOrNull { isNameLike(clean(lines[it])) } }
        val options = mutableListOf<Option>()
        lines.forEachIndexed { i, line ->
            val trimmed = clean(line)
            val key = KEYS.firstOrNull { trimmed.lowercase().startsWith(it) }
            if (key != null) {
                val value = clean(trimmed.substring(key.length).trimStart(':', ' '))
                val text = value.ifEmpty { clean(lines.getOrNull(i + 1).orEmpty()) }
                if (isNameLike(text)) options += Option(text, score(text, labelled = true, aboveAmount = false))
            } else if (isNameLike(trimmed)) {
                options += Option(trimmed, score(trimmed, labelled = false, aboveAmount = i == nearest))
            }
        }
        // 同分时选短的：「瑞幸咖啡」比「瑞幸咖啡（北京）有限公司」好认。
        return options.filter { it.score >= MIN_SCORE }
            .sortedWith(compareByDescending<Option> { it.score }.thenBy { it.text.length })
            .firstOrNull()?.text
    }

    /** 列表里和金额写在同一行、金额左边的名字（「카카오페이  -48,000원」）。 */
    fun onLine(line: String, amountStart: Int): String? = clean(line.take(amountStart)).takeIf(::isNameLike)

    /** 列表里一笔的商家名：在这一笔的几行（block）里，先往金额上面找，再往下面找，取最近的名字（打了码的人名不算）。 */
    fun near(lines: List<String>, block: IntRange, amountLine: Int): String? =
        ((amountLine - 1 downTo block.first) + (amountLine + 1..block.last))
            .map { clean(lines[it]) }
            .firstOrNull { isNameLike(it) && '*' !in it }

    private fun score(text: String, labelled: Boolean, aboveAmount: Boolean): Int {
        var score = 0
        if (labelled) score += 3
        if (aboveAmount) score += 3
        if (CategoryGuesser.guess(text) != null) score += 4
        if ('*' in text) score -= 4
        return score
    }

    private fun clean(text: String): String = text.trim { it.isWhitespace() || it in JUNK || it in '①'..'⓿' }

    private fun isNameLike(text: String): Boolean {
        if (text.length !in 2..40 || text.none { it.isLetter() }) return false
        val lower = text.lowercase()
        if (STATUS.any { it in lower } || lower in KEYS) return false
        if (AMOUNT_WITH_MARKER.containsMatchIn(text) || DateTimeFinder.spans(text).isNotEmpty()) return false
        return text.count { it.isDigit() } * 2 <= text.length
    }
}
