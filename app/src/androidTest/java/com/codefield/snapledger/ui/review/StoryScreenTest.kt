package com.codefield.snapledger.ui.review

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.DemoData
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.DailyRates
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 回顾卡片：读屏软件的「下一张」「上一张」能翻页（用演示数据的 10 月）。 */
@RunWith(AndroidJUnit4::class)
class StoryScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val seoul = ZoneId.of("Asia/Seoul")
    private val rates = RateTable(
        listOf(
            DailyRates(
                LocalDate.of(2026, 9, 1),
                mapOf(Currency.EUR to BigDecimal.ONE, Currency.KRW to BigDecimal("1500"), Currency.CNY to BigDecimal("7.5")),
            ),
        )
    )

    private fun text(id: Int) = rule.activity.getString(id)

    // 按标签执行读屏自定义操作的测试接口还标着「实验性」。
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun screenReaderActionsTurnThePages() {
        // 时钟手动走：每张卡 5.5 秒后会自动翻页，测试里只走 1 秒，等翻页动画放完。
        rule.mainClock.autoAdvance = false
        val entries = DemoData.entries(ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, seoul))
        rule.setContent {
            StoryScreen(
                month = YearMonth.of(2026, 10),
                entries = entries,
                rates = rates,
                homeCurrency = Currency.KRW,
                zone = seoul,
                onClose = {},
                onShare = {},
            )
        }
        val story = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        rule.onNodeWithText(text(R.string.review_spent)).assertExists()

        story.performCustomAccessibilityActionWithLabel(text(R.string.review_next))
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText(text(R.string.review_top_category)).assertExists()
        rule.onNodeWithText(text(R.string.review_spent)).assertDoesNotExist()

        story.performCustomAccessibilityActionWithLabel(text(R.string.review_previous))
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText(text(R.string.review_spent)).assertExists()
    }
}
