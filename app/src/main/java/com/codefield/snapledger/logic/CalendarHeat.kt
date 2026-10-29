package com.codefield.snapledger.logic

import java.time.DayOfWeek
import java.time.YearMonth

/**
 * 消费日历（热力图）的排版：一行 7 天，从 firstDay（按界面语言：中文从周一，韩文、英文从周日）开始。
 * 格子按行从左到右编号，从 0 开始；这个月 1 号之前的是空格。
 */
object CalendarHeat {

    /** 颜色深浅分几级（不算没花钱的 0 级）。 */
    const val LEVELS = 4

    /** 1 号前面空几格。 */
    fun leadingBlanks(month: YearMonth, firstDay: DayOfWeek): Int = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7

    fun rows(month: YearMonth, firstDay: DayOfWeek): Int = (leadingBlanks(month, firstDay) + month.lengthOfMonth() + 6) / 7

    /** 第 index 格是几号；空格（1 号之前、月底之后）是 null。 */
    fun dayAt(month: YearMonth, firstDay: DayOfWeek, index: Int): Int? =
        (index - leadingBlanks(month, firstDay) + 1).takeIf { it in 1..month.lengthOfMonth() }

    /** 颜色深浅：没花钱是 0；花了钱的按占最多那天的比例分成 1 到 [LEVELS] 级（向上取整，花得再少也看得见）。 */
    fun level(minor: Long, maxMinor: Long): Int {
        if (minor <= 0 || maxMinor <= 0) return 0
        return ((LEVELS * minor + maxMinor - 1) / maxMinor).toInt().coerceIn(1, LEVELS)
    }
}
