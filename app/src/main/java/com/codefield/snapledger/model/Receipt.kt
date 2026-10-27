package com.codefield.snapledger.model

/**
 * 文字识别得到的一行字。top 和 height 是这一行在图上的位置和字的高度（像素），
 * 付款金额一般是整张截图里最大的数字，所以字的高度能帮忙判断。不知道时填 0。
 */
data class OcrLine(val text: String, val top: Int = 0, val height: Int = 0)

/**
 * 文字识别直接给出的一段字和它在图上的方框（像素）。同一行两头的字（比如「결제금액」和「6,500원」）
 * 常被识别成两段，要先按位置拼成 [OcrLine]。
 */
data class OcrBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/** 从截图里识别出的一笔账。找不到的部分是 null，由用户在确认对话框里补上。 */
data class ParsedReceipt(
    /** 最可能的付款金额。 */
    val amount: Money?,
    /** 其他可能的金额（包括第一个），识别错了可以在对话框里换。 */
    val amountCandidates: List<Money>,
    /** 付款时间（毫秒时间戳）。截图里没有日期时是 null，用当前时间。 */
    val time: Long?,
    val merchant: String?,
    val category: Category?,
)
