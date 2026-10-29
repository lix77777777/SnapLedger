package com.codefield.snapledger.logic

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RatePlannerTest {

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private val today = date(10, 29)

    @Test
    fun nothingSaved_downloadsFromAWeekBeforeTheFirstEntry() {
        assertEquals(
            listOf(DateSpan(date(9, 24), null)),
            RatePlanner.missing(null, null, earliest = date(10, 1), today = today, checkNewer = false),
        )
    }

    @Test
    fun nothingSaved_andNoEntries_downloadsTheLastWeek() {
        assertEquals(listOf(DateSpan(date(10, 22), null)), RatePlanner.missing(null, null, null, today, checkNewer = false))
    }

    @Test
    fun entryOlderThanTheSavedRates_downloadsTheMissingStart() {
        assertEquals(
            listOf(DateSpan(date(9, 28), date(10, 20))),
            RatePlanner.missing(date(10, 20), date(10, 28), earliest = date(10, 5), today = today, checkNewer = false),
        )
    }

    @Test
    fun savedRatesCoverEveryEntry_nothingToDownload() {
        assertEquals(emptyList<DateSpan>(), RatePlanner.missing(date(10, 1), date(10, 28), date(10, 3), today, checkNewer = false))
    }

    @Test
    fun saturdayEntry_isCoveredByTheFridayBefore() {
        // 10 月 23 日是周五，周六的账用周五的汇率，不用再下载。
        assertEquals(emptyList<DateSpan>(), RatePlanner.missing(date(10, 23), date(10, 28), date(10, 24), today, checkNewer = false))
    }

    @Test
    fun checkNewer_startsFromTheLastSavedDay() {
        assertEquals(
            listOf(DateSpan(date(10, 27), null)),
            RatePlanner.missing(date(10, 1), date(10, 27), date(10, 3), today, checkNewer = true),
        )
    }

    @Test
    fun checkNewer_andOlderEntry_downloadsBothEnds() {
        assertEquals(
            listOf(DateSpan(date(9, 23), date(10, 1)), DateSpan(date(10, 27), null)),
            RatePlanner.missing(date(10, 1), date(10, 27), date(9, 30), today, checkNewer = true),
        )
    }

    @Test
    fun checkNewer_whenTodayIsSaved_nothingToDownload() {
        assertEquals(emptyList<DateSpan>(), RatePlanner.missing(date(10, 1), today, date(10, 3), today, checkNewer = true))
    }
}
