package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.OcrLine
import com.codefield.snapledger.model.ParsedReceipt
import java.time.ZoneId

/**
 * 把截图上识别出的文字变成一笔账：金额、时间、商家、类别。
 * 不认识是哪个付款应用也能用（通用规则），结果交给确认对话框，由用户改对。
 */
object ReceiptParser {

    private const val MAX_CANDIDATES = 5

    /**
     * @param now 现在的时间，用来推算截图里没写的年份。
     * @param fallbackCurrency 截图里看不出货币时用的货币（比如全是英文和数字）。
     */
    fun parse(lines: List<OcrLine>, now: Long, zone: ZoneId, fallbackCurrency: Currency): ParsedReceipt {
        val texts = lines.map { TextNormalizer.normalize(it.text) }
        val all = texts.joinToString("\n")
        val hasKana = all.any { it in '぀'..'ヿ' }
        val candidates = AmountFinder.find(texts, lines.map { it.height }, documentCurrency(all, hasKana, fallbackCurrency), hasKana)
        val best = candidates.firstOrNull()
        val merchant = MerchantFinder.find(texts, best?.lineIndex)
        return ParsedReceipt(
            amount = best?.money,
            amountCandidates = candidates.map { it.money }.distinct().take(MAX_CANDIDATES),
            time = DateTimeFinder.find(texts, now, zone),
            merchant = merchant,
            category = merchant?.let(CategoryGuesser::guess) ?: CategoryGuesser.guess(all),
        )
    }

    /** 没有货币符号时，看截图里是什么文字：有韩文按韩元，有日文假名按日元，只有汉字按人民币。 */
    private fun documentCurrency(text: String, hasKana: Boolean, fallback: Currency): Currency = when {
        text.any { it in '가'..'힣' } -> Currency.KRW
        hasKana -> Currency.JPY
        text.any { it in '一'..'鿿' } -> Currency.CNY
        else -> fallback
    }
}
