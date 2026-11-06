package com.codefield.snapledger.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codefield.snapledger.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 账本页右下角的两个按钮：读屏软件要能念出名字、能点。第 10 步加图标时「识别截图」的名字丢过一次。 */
@RunWith(AndroidJUnit4::class)
class AddButtonsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bothButtonsHaveNamesForScreenReaders() {
        var scans = 0
        var adds = 0
        rule.setContent { AddButtons(onScan = { scans++ }, onAdd = { adds++ }) }

        rule.onNodeWithContentDescription(rule.activity.getString(R.string.import_screenshot))
            .assertHasClickAction()
            .performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.add_manually))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, scans)
        assertEquals(1, adds)
    }
}
