package com.codefield.snapledger.logic

/** 本月支出和预算比：花到八成是 WARNING，到了或超过预算是 OVER。 */
enum class BudgetLevel { WARNING, OVER }

/** 超支提醒：记了一笔以后，本月支出是不是跨过了预算的 80% 或 100%。 */
object BudgetCheck {

    /** 花到预算的这个比例就提醒一次。 */
    const val WARNING_SHARE = 0.8

    fun level(spent: Long, budget: Long): BudgetLevel? = when {
        budget <= 0 -> null
        spent >= budget -> BudgetLevel.OVER
        spent >= budget * WARNING_SHARE -> BudgetLevel.WARNING
        else -> null
    }

    /** 从 before 变成 after 时跨过的那条线；没跨过、或者是改小了（比如删了一笔）时是 null，免得重复提醒。 */
    fun crossed(before: Long, after: Long, budget: Long): BudgetLevel? {
        if (after <= before) return null
        val now = level(after, budget) ?: return null
        return if (level(before, budget) == now) null else now
    }
}

/** 记账时要不要记下手机当前的位置：只有刚付完款（2 小时内）记的才记，补记以前的账时手机早就不在那里了。 */
object LocationPolicy {

    const val RECENT_MILLIS = 2 * 60 * 60 * 1000L

    fun shouldAttach(paidAt: Long, now: Long): Boolean = now - paidAt in -RECENT_MILLIS..RECENT_MILLIS
}
