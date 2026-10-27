package com.codefield.snapledger.logic

/** 把全角数字和符号换成半角、统一减号，后面的正则只需要处理一种写法。 */
internal object TextNormalizer {

    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '０'..'９' -> '0' + (c - '０')
                    '，' -> ','
                    '．' -> '.'
                    '：' -> ':'
                    '－', '−', '–', '—' -> '-'
                    '＋' -> '+'
                    '￥' -> '¥'
                    '＄' -> '$'
                    '￦' -> '₩'
                    ' ' -> ' '
                    else -> c
                }
            )
        }
    }
}
