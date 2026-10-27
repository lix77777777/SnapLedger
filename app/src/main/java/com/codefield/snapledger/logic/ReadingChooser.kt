package com.codefield.snapledger.logic

import com.codefield.snapledger.model.OcrLine

/**
 * 同一张截图用韩文模型和中文模型各读一遍，挑读得懂的那一份。
 * 两个模型都认识英文和数字，但不认识对方的字，遇到了会读成乱码；乱码里几乎不会出现完整的付款常用词，
 * 所以数一数常用词（长的词算的分多）就能分出来。一样多时用前面那份。
 */
object ReadingChooser {

    private val WORDS = listOf(
        "결제", "금액", "승인", "완료", "카드", "일시불", "할부", "합계", "잔액", "가맹점", "사용처", "주문", "상품",
        "송금", "보냈어요", "이용", "일시", "거래", "포인트", "할인", "영수증", "매장", "페이", "원",
        "支付", "付款", "金额", "交易", "成功", "完成", "商户", "商家", "订单", "时间", "方式", "收款", "转账",
        "余额", "合计", "账单", "详情", "优惠", "微信", "支付宝", "零钱", "银行", "元",
    )

    fun choose(readings: List<List<OcrLine>>): List<OcrLine> = readings.maxByOrNull(::score).orEmpty()

    internal fun score(lines: List<OcrLine>): Int = lines.sumOf { line ->
        WORDS.sumOf { word -> occurrences(line.text, word) * word.length }
    }

    private fun occurrences(text: String, word: String): Int {
        var count = 0
        var from = text.indexOf(word)
        while (from >= 0) {
            count++
            from = text.indexOf(word, from + word.length)
        }
        return count
    }
}
