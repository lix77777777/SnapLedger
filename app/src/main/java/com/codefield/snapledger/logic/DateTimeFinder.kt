package com.codefield.snapledger.logic

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 在识别出的文字里找付款的日期和时间。
 * 支持「2026.10.28 14:32」「2026년 10월 27일 오후 9:15」「2026年10月28日 09:41:05」「10/28 12:31」这类写法。
 */
internal object DateTimeFinder {

    private val FULL_DATE = Regex("""(20\d{2})\s*[.\-/년年]\s*(\d{1,2})\s*[.\-/월月]\s*(\d{1,2})\s*[일日号]?""")
    private val SHORT_DATE = Regex("""(?<![\d.])(\d{1,2})\s*(?:[월月]\s*(\d{1,2})\s*[일日号]?|/(\d{1,2}))(?![\d/])""")
    private val TIME = Regex("""(오전|오후|上午|下午|AM|PM|am|pm)?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*(AM|PM|am|pm))?""")
    private val PHONE = Regex("""\d{2,4}-\d{3,4}-\d{4}""")

    /** 有这些词的行优先当作付款时间（截图里可能还有别的日期）。 */
    private val DATE_KEYS = listOf("일시", "거래일", "결제일", "승인일", "이용일", "时间", "日期", "date", "time")
    private val AFTERNOON = setOf("오후", "下午", "pm")
    private val MORNING = setOf("오전", "上午", "am")

    /** 这一行里日期、时间、电话号码所在的位置。找金额时要跳过这些地方。 */
    fun spans(line: String): List<IntRange> =
        listOf(FULL_DATE, SHORT_DATE, TIME, PHONE).flatMap { regex -> regex.findAll(line).map { it.range } }

    /** 付款时间（毫秒时间戳）。找不到日期时返回 null；只有日期没有时间时按中午 12 点算。 */
    fun find(lines: List<String>, now: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val order = lines.indices.sortedBy { i -> if (DATE_KEYS.any { lines[i].contains(it, ignoreCase = true) }) 0 else 1 }
        for (i in order) {
            val line = lines[i]
            val (date, end) = fullDate(line) ?: shortDate(line, today) ?: continue
            val time = timeIn(line.substring(end)) ?: lines.getOrNull(i + 1)?.let(::timeIn) ?: LocalTime.NOON
            return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private fun fullDate(line: String): Pair<LocalDate, Int>? {
        for (m in FULL_DATE.findAll(line)) {
            val (year, month, day) = m.destructured
            val date = dateOrNull(year.toInt(), month.toInt(), day.toInt()) ?: continue
            return date to m.range.last + 1
        }
        return null
    }

    /** 没写年份时按今年算；算出来比明天还晚，就是去年的。 */
    private fun shortDate(line: String, today: LocalDate): Pair<LocalDate, Int>? {
        for (m in SHORT_DATE.findAll(line)) {
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].ifEmpty { m.groupValues[3] }.toIntOrNull() ?: continue
            var date = dateOrNull(today.year, month, day) ?: continue
            if (date.isAfter(today.plusDays(1))) date = dateOrNull(today.year - 1, month, day) ?: continue
            return date to m.range.last + 1
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun timeIn(text: String): LocalTime? {
        val m = TIME.find(text) ?: return null
        var hour = m.groupValues[2].toInt()
        val minute = m.groupValues[3].toInt()
        val second = m.groupValues[4].toIntOrNull() ?: 0
        val marker = m.groupValues[1].ifEmpty { m.groupValues[5] }.lowercase()
        if (marker in AFTERNOON && hour < 12) hour += 12
        if (marker in MORNING && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
    }
}
