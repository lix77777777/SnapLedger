package com.codefield.snapledger.logic

import com.codefield.snapledger.model.OcrBox
import com.codefield.snapledger.model.OcrLine

/**
 * 把识别出的一段段字按位置拼成行。付款页常把说明和金额放在同一行的两头（「결제금액 …… 6,500원」），
 * 识别结果却是分开的两段；拼起来以后，找金额时才知道这个数字旁边写的是「결제금액」。
 * 上下重叠超过矮的那段的一半、左右又不重叠的，算同一行。
 */
object OcrLayout {

    fun lines(boxes: List<OcrBox>): List<OcrLine> {
        val rows = mutableListOf<MutableList<OcrBox>>()
        // 从上往下放，所以每一行的第一段就是最上面的，行的顺序也是从上往下。
        for (box in boxes.filter { it.text.isNotBlank() }.sortedBy { it.top }) {
            val row = rows.lastOrNull { row -> row.any { sameRow(it, box) } && row.none { sideBySideOverlap(it, box) } }
            if (row != null) row += box else rows += mutableListOf(box)
        }
        return rows.map { row ->
            OcrLine(
                text = row.sortedBy { it.left }.joinToString(" ") { it.text.trim() },
                top = row.minOf { it.top },
                height = row.maxOf { it.height },
            )
        }
    }

    private fun sameRow(a: OcrBox, b: OcrBox): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap * 2 >= minOf(a.height, b.height)
    }

    /** 左右有重叠：是上下叠着的两行（比如左边的两行小字），不是同一行。 */
    private fun sideBySideOverlap(a: OcrBox, b: OcrBox): Boolean = a.left < b.right && b.left < a.right
}
