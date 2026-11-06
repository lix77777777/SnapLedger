package com.codefield.snapledger.logic

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** 每月回顾提醒的时间：每月 1 号上午 10 点。 */
object ReminderSchedule {

    const val HOUR = 10

    /** 下一次提醒：今天是 1 号而且还没到 10 点就是今天，不然是下个月 1 号。 */
    fun next(now: ZonedDateTime): ZonedDateTime {
        val thisMonth = now.withDayOfMonth(1).withHour(HOUR).truncatedTo(ChronoUnit.HOURS)
        return if (now.isBefore(thisMonth)) thisMonth else thisMonth.plusMonths(1)
    }
}
