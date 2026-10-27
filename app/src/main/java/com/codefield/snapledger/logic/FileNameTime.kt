package com.codefield.snapledger.logic

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 从截图的文件名里读截图时间。截图里没写日期时，用它当付款时间（一般付完就截图）。
 * 支持「Screenshot_20261028_143205_KakaoPay.jpg」「Screenshot_20261028-143205.png」
 * 「Screenshot_2026-10-28-14-32-05-123_com.tencent.mm.jpg」这类写法。
 */
object FileNameTime {

    private val PATTERN = Regex("""(?<!\d)(20\d{2})[-_.]?(\d{2})[-_.]?(\d{2})[-_. ]?(\d{2})[-_.]?(\d{2})[-_.]?(\d{2})""")

    fun parse(name: String, zone: ZoneId): Long? {
        for (m in PATTERN.findAll(name)) {
            val (year, month, day, hour, minute, second) = m.destructured
            val time = runCatching {
                LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
            }.getOrNull() ?: continue
            return time.atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }
}
