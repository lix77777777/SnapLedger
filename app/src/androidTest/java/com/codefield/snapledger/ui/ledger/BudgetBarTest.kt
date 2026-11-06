package com.codefield.snapledger.ui.ledger

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.MonthlyStats
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.ui.common.formatMoney
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 账本的本月卡片：设了预算时显示用了几成，超出时写超了多少。 */
@RunWith(AndroidJUnit4::class)
class BudgetBarTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val budget = Money(260_000, Currency.KRW)

    private fun showLedger(spent: Long) {
        val entries = listOf(Entry(1, Money(spent, Currency.KRW), Category.GROCERY, "이마트", System.currentTimeMillis()))
        val summary = MonthlyStats.summarize(entries, YearMonth.now(zone), Currency.KRW, RateTable.EMPTY, zone)
        rule.setContent {
            LedgerScreen(
                entries = entries,
                summary = summary,
                homeCurrency = Currency.KRW,
                rates = RateTable.EMPTY,
                budget = budget,
                zone = zone,
                dialogFor = null,
                onDialogFor = {},
                onSave = {},
                onDelete = {},
                onOpenSettings = {},
                imageFor = { File(it) },
            )
        }
    }

    @Test
    fun underBudget_showsHowMuchIsUsed() {
        showLedger(spent = 104_000)
        rule.onNodeWithText(rule.activity.getString(R.string.budget_progress, formatMoney(budget), 40)).assertExists()
    }

    @Test
    fun overBudget_showsByHowMuch() {
        showLedger(spent = 270_000)
        rule.onNodeWithText(rule.activity.getString(R.string.budget_over, formatMoney(Money(10_000, Currency.KRW)))).assertExists()
    }
}
