package com.codefield.snapledger.system

import com.codefield.snapledger.data.LedgerRepository
import com.codefield.snapledger.logic.RatePlanner
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.remote.RateSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把缺少的汇率下载下来存进数据库：第一次打开时取最近一周的，记了更早的账时补上那段，隔一段时间看看有没有新的。
 * 断网时什么也不存，统计接着用已经存下的汇率。
 */
class RateUpdater(private val source: RateSource, private val repository: LedgerRepository) {

    // 启动和记账可能同时触发，一次只让一个在下载。
    private val mutex = Mutex()

    /**
     * entries 用来找最早一笔账的日期；checkNewer 为 true 时也看看有没有新汇率。
     * 返回这次存了几天的汇率。网络出错时抛 IOException，已经存下的不受影响。
     */
    suspend fun update(entries: List<Entry>, today: LocalDate, zone: ZoneId, checkNewer: Boolean): Int = mutex.withLock {
        val cached = RateTable(repository.observeRates().first())
        val earliest = entries.minOfOrNull { it.time }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val spans = RatePlanner.missing(cached.first?.date, cached.latest?.date, earliest, today, checkNewer)
        var saved = 0
        for (span in spans) {
            val days = source.fetch(span.from, span.to)
            if (days.isNotEmpty()) repository.saveRates(days)
            saved += days.size
        }
        saved
    }
}
