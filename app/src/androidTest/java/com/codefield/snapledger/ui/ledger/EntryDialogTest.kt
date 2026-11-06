package com.codefield.snapledger.ui.ledger

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codefield.snapledger.R
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.Source
import com.codefield.snapledger.ui.common.categoryName
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 「记一笔」对话框：填好金额、商家、类别能保存；没填金额时提示，不保存。 */
@RunWith(AndroidJUnit4::class)
class EntryDialogTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val seoul = ZoneId.of("Asia/Seoul")
    private var saved: Entry? = null

    private fun text(id: Int) = rule.activity.getString(id)

    private fun showDialog() = rule.setContent {
        EntryDialog(
            initial = null,
            defaultCurrency = Currency.KRW,
            zone = seoul,
            onSave = { saved = it },
            onDelete = null,
            onDismiss = {},
        )
    }

    @Test
    fun saveWithoutAmount_showsAnErrorAndSavesNothing() {
        showDialog()
        rule.onNodeWithText(text(R.string.save)).performClick()

        rule.onNodeWithText(text(R.string.error_amount)).assertExists()
        assertNull(saved)
    }

    @Test
    fun filledIn_savesAManualEntry() {
        showDialog()
        rule.onNodeWithText(text(R.string.field_amount)).performClick().performTextInput("6500")
        rule.onNodeWithText(text(R.string.field_merchant)).performClick().performTextInput("스타벅스")
        rule.onNodeWithText(text(categoryName(Category.CAFE)), substring = true).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.save)).performClick()

        val entry = saved
        assertNotNull(entry)
        entry!!
        assertEquals(Money(6500, Currency.KRW), entry.amount)
        assertEquals("스타벅스", entry.merchant)
        assertEquals(Category.CAFE, entry.category)
        assertEquals(Source.MANUAL, entry.source)
        assertNull("对话框自己不取位置，保存时由 ViewModel 决定", entry.location)
    }
}
