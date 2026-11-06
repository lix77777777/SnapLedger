package com.codefield.snapledger.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCheckTest {

    private val budget = 500_000L

    @Test
    fun level_byShareOfTheBudget() {
        assertNull(BudgetCheck.level(399_999, budget))
        assertEquals(BudgetLevel.WARNING, BudgetCheck.level(400_000, budget))
        assertEquals(BudgetLevel.OVER, BudgetCheck.level(500_000, budget))
        assertNull("没设预算", BudgetCheck.level(900_000, 0))
    }

    @Test
    fun crossed_onlyWhenALineIsPassedGoingUp() {
        assertEquals(BudgetLevel.WARNING, BudgetCheck.crossed(390_000, 410_000, budget))
        assertEquals("一下子从七成跳到超支，只提醒超支", BudgetLevel.OVER, BudgetCheck.crossed(300_000, 520_000, budget))
        assertEquals(BudgetLevel.OVER, BudgetCheck.crossed(450_000, 510_000, budget))
        assertNull("已经过了八成，再记一笔不重复提醒", BudgetCheck.crossed(410_000, 420_000, budget))
        assertNull("改小、删掉不提醒", BudgetCheck.crossed(510_000, 450_000, budget))
        assertNull(BudgetCheck.crossed(100_000, 200_000, budget))
    }

    @Test
    fun location_onlyForEntriesRecordedSoonAfterPaying() {
        val now = 1_800_000_000_000L
        assertTrue(LocationPolicy.shouldAttach(now - 30 * 60 * 1000, now))
        assertTrue("时间稍微记在了将来也算", LocationPolicy.shouldAttach(now + 10 * 60 * 1000, now))
        assertFalse("昨天的截图", LocationPolicy.shouldAttach(now - 24 * 60 * 60 * 1000L, now))
    }
}
