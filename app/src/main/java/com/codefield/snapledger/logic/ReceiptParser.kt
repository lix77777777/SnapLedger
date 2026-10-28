package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.OcrLine
import com.codefield.snapledger.model.ParsedItem
import com.codefield.snapledger.model.ParsedReceipt
import java.time.ZoneId

/**
 * 把截图上识别出的文字变成一笔账：金额、时间、商家、类别。
 * 不认识是哪个付款应用也能用（通用规则），结果交给确认对话框，由用户改对。
 */
object ReceiptParser {

    private const val MAX_CANDIDATES = 5

    /**
     * @param now 截图的时间（不知道时用现在），用来推算截图里没写的年份、只写了时间的那一天。
     * @param fallbackCurrency 截图里看不出货币时用的货币（比如全是英文和数字）。
     */
    fun parse(lines: List<OcrLine>, now: Long, zone: ZoneId, fallbackCurrency: Currency): ParsedReceipt {
        // 识别结果的行首行尾常带着竖线（表格的边框），先去掉。
        val texts = lines.map { TextNormalizer.normalize(it.text).trim(' ', '|') }
        val all = texts.joinToString("\n")
        val japanese = isJapanese(all)
        val candidates = AmountFinder.find(texts, lines.map { it.height }, documentCurrency(all, japanese, fallbackCurrency), japanese)

        // 列表截图：每一笔支出分开记。列表里只有一笔支出时（其余是收入），就按那一笔记。
        val items = ListFinder.find(texts, lines, candidates, now, zone)
            .filterNot { it.income }
            .map { ParsedItem(it.amount, it.time, it.merchant, it.merchant?.let(CategoryGuesser::guess)) }
        if (items.isNotEmpty()) {
            val first = items.first()
            return ParsedReceipt(
                amount = first.amount,
                amountCandidates = items.map { it.amount }.distinct().take(MAX_CANDIDATES),
                time = first.time,
                merchant = first.merchant,
                category = first.category,
                items = if (items.size >= 2) items else emptyList(),
            )
        }

        val best = candidates.firstOrNull()
        val merchant = MerchantFinder.find(texts, best?.lineIndex)
        return ParsedReceipt(
            amount = best?.money,
            // 余额、积分这类扣了分的数字不给用户挑，免得看着乱。
            amountCandidates = candidates.filter { it === best || it.score >= 0 }.map { it.money }.distinct().take(MAX_CANDIDATES),
            time = DateTimeFinder.find(texts, now, zone),
            merchant = merchant,
            category = merchant?.let(CategoryGuesser::guess) ?: CategoryGuesser.guess(all),
        )
    }

    /** 没有货币符号时，看截图里是什么文字：有韩文按韩元，是日文按日元，只有汉字按人民币。 */
    private fun documentCurrency(text: String, japanese: Boolean, fallback: Currency): Currency = when {
        text.any { it in '가'..'힣' } -> Currency.KRW
        japanese -> Currency.JPY
        text.any { it in '一'..'鿿' } -> Currency.CNY
        else -> fallback
    }

    /**
     * 有两个以上的假名、而且假名不比汉字少太多时算日文。中文模型偶尔把返回箭头「<」认成「く」，
     * 一个假名就把人民币当成日元了。「・」中文里也用，不算假名。
     */
    private fun isJapanese(text: String): Boolean {
        val kana = text.count { it in '぀'..'ヿ' && it != '・' }
        val han = text.count { it in '一'..'鿿' }
        return kana >= 2 && kana * 4 >= han
    }
}
