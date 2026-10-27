package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Entry
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * 找可能已经记过的同一笔：金额和货币都一样，而且时间差不到 10 分钟，或者是同一天、同一个商家。
 * 截图记账时常会把同一张截图分享两次，或者先手动记了又分享截图。只提醒，存不存由用户决定。
 */
object DuplicateFinder {

    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    fun find(entries: List<Entry>, draft: Entry, zone: ZoneId): Entry? = entries.firstOrNull { entry ->
        entry.id != draft.id && entry.amount == draft.amount &&
            (abs(entry.time - draft.time) <= WINDOW_MILLIS || (sameDay(entry, draft, zone) && sameMerchant(entry, draft)))
    }

    private fun sameDay(a: Entry, b: Entry, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(a.time).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b.time).atZone(zone).toLocalDate()

    private fun sameMerchant(a: Entry, b: Entry): Boolean =
        a.merchant.isNotBlank() && a.merchant.trim().equals(b.merchant.trim(), ignoreCase = true)
}
