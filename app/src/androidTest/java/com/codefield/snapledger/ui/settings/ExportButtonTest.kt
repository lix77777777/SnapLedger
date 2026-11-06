package com.codefield.snapledger.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.ui.RateStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 设置里的「导出 CSV」：账本是空的时候按钮是灰的，记了账才能点。 */
@RunWith(AndroidJUnit4::class)
class ExportButtonTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exportIsOnlyPossibleWithEntries() {
        var entryCount by mutableIntStateOf(0)
        rule.setContent {
            SettingsScreen(
                homeCurrency = Currency.KRW,
                onHomeCurrency = {},
                rates = RateTable.EMPTY,
                rateStatus = RateStatus.IDLE,
                onUpdateRates = {},
                reviewReminder = false,
                onReviewReminder = {},
                debug = false,
                onTestReminder = {},
                recordLocation = false,
                onRecordLocation = {},
                budget = null,
                onBudget = {},
                entryCount = entryCount,
                onExport = { _, _ -> },
                onFillDemo = {},
                onBack = {},
            )
        }
        val export = rule.onNodeWithText(rule.activity.getString(R.string.settings_export))

        export.assertIsNotEnabled()
        entryCount = 3
        export.assertIsEnabled()
    }
}
