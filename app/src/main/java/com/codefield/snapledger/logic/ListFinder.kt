package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.OcrLine
import java.time.ZoneId

/** 列表里的一笔。income 是带「+」的收入（退款、转入），账本不记。 */
internal data class ListRow(val amount: Money, val income: Boolean, val merchant: String?, val time: Long?)

/**
 * 认出账单、流水、付款通知这类一张图里有好几笔的截图，把每一笔分开。
 * 列表的特点是同一种格式重复好几次：每一笔都有一个差不多大的金额，流水带正负号，付款通知带货币符号。
 * 两个金额之间的那几行按上下的空隙分给两边：同一笔里的行挨得近，两笔之间空得多。
 */
internal object ListFinder {

    /** 字的高度至少是最大的那个金额的这么多，才算「差不多大」。 */
    private const val SIMILAR_HEIGHT = 0.7

    /** 不是列表时返回空的。 */
    fun find(texts: List<String>, lines: List<OcrLine>, candidates: List<AmountCandidate>, now: Long, zone: ZoneId): List<ListRow> {
        val usable = candidates.filterNot { it.balance }
        // 流水看正负号（深色背景上「원」常常认不出来，正负号认得出）；付款通知没有正负号，看货币符号。
        val signed = similarSize(usable.filter { it.sign != null })
        val amounts = (if (signed.size >= 2) signed else similarSize(usable.filter { it.marked }))
            .groupBy { it.lineIndex }.values.map { same -> same.maxBy { it.score } }
            .sortedBy { it.lineIndex }
        if (amounts.size < 2) return emptyList()
        // 有的写着「결제금액」「合计」、有的没写：是一张付款页上的实付金额和明细，不是列表。
        if (amounts.any { it.labelled } && !amounts.all { it.labelled }) return emptyList()

        val names = amounts.map { MerchantFinder.onLine(texts[it.lineIndex], it.start) }
        val blocks = blocks(amounts.map { it.lineIndex }, lines, names.map { it != null })
        return amounts.mapIndexed { i, amount ->
            ListRow(
                amount = amount.money,
                income = amount.sign == '+',
                merchant = names[i] ?: MerchantFinder.near(texts, blocks[i], amount.lineIndex),
                time = DateTimeFinder.findInBlock(texts, blocks[i], now, zone),
            )
        }
    }

    private fun similarSize(amounts: List<AmountCandidate>): List<AmountCandidate> {
        val max = amounts.maxOfOrNull { it.height } ?: return emptyList()
        return amounts.filter { it.height >= max * SIMILAR_HEIGHT }
    }

    /**
     * 每一笔占哪几行。相邻两笔之间，在上下空得最多的地方分开；空得一样多时，金额和名字在同一行的（明细写在下面），
     * 中间的行归上一笔，否则（明细写在金额上面）归下一笔。第一笔往上、最后一笔往下取几行，照第二笔、倒数第二笔的样子。
     */
    private fun blocks(amountLines: List<Int>, lines: List<OcrLine>, named: List<Boolean>): List<IntRange> {
        val cuts = amountLines.zipWithNext().mapIndexed { i, (upper, lower) ->
            val order = if (named[i]) (lower - 1 downTo upper).toList() else (upper until lower).toList()
            order.maxBy { gapBelow(lines, it) } + 1
        }
        val above = amountLines[1] - cuts.first()
        val below = cuts.last() - 1 - amountLines[amountLines.size - 2]
        return amountLines.indices.map { i ->
            val first = if (i == 0) maxOf(0, amountLines[0] - above) else cuts[i - 1]
            val last = if (i == amountLines.lastIndex) minOf(lines.lastIndex, amountLines[i] + below) else cuts[i] - 1
            first..last
        }
    }

    /** 第 i 行和下一行之间空了多少像素。 */
    private fun gapBelow(lines: List<OcrLine>, i: Int): Int = lines[i + 1].top - (lines[i].top + lines[i].height)
}
