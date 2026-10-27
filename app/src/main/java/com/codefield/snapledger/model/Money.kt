package com.codefield.snapledger.model

/**
 * 支持的货币。fractionDigits 是小数位数：韩元、日元没有小数，人民币、美元、欧元有两位。
 * 汇率接口（Frankfurter）里这几种都有。
 */
enum class Currency(val fractionDigits: Int) {
    KRW(0),
    CNY(2),
    USD(2),
    JPY(0),
    EUR(2),
}

/**
 * 金额。用最小单位的整数保存，避免小数误差：韩元按「元」，人民币按「分」。
 * 比如 12,000 韩元是 Money(12000, KRW)，23.50 元人民币是 Money(2350, CNY)。
 */
data class Money(val minor: Long, val currency: Currency)
