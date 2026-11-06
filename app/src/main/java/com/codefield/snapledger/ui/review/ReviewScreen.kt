package com.codefield.snapledger.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.MonthlyReview
import com.codefield.snapledger.logic.MonthlyStats
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.MonthReview
import com.codefield.snapledger.ui.common.MonthSwitcher
import com.codefield.snapledger.ui.common.ScreenHeader
import com.codefield.snapledger.ui.common.formatMoney
import com.codefield.snapledger.ui.common.rememberMonthFormat
import com.codefield.snapledger.ui.common.rememberNotificationAccess
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 回顾页：选一个月，看封面，点「开始回顾」进入一张张的故事卡片。
 * 默认是上个月（已经过完的一个月）；上个月没记账就是这个月。
 */
@Composable
fun ReviewScreen(
    entries: List<Entry>?,
    rates: RateTable,
    homeCurrency: Currency,
    zone: ZoneId,
    reminderOn: Boolean,
    onOpenStory: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember(zone) { LocalDate.now(zone) }
    val thisMonth = YearMonth.from(today)
    // 用户翻过月份才记下来；没翻过时跟着账目算默认月份（账目是读完数据库才有的）。
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val month = chosen?.let(YearMonth::parse) ?: entries?.let { MonthlyReview.defaultMonth(it, today, zone) } ?: thisMonth
    val firstMonth = entries?.let { MonthlyStats.firstMonth(it, zone) }?.coerceAtMost(thisMonth) ?: thisMonth
    val review = remember(entries, rates, homeCurrency, month) {
        entries?.let { MonthlyReview.build(it, month, homeCurrency, rates, zone) }
    }
    val notifications = rememberNotificationAccess()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.tab_review))
        MonthSwitcher(month, canGoBack = month > firstMonth, canGoForward = month < thisMonth, onMonth = { chosen = it.toString() })
        if (review != null) {
            CoverPreview(review, onStart = { onOpenStory(month) })
            if (month == thisMonth && review.summary.entryCount > 0) Hint(stringResource(R.string.review_in_progress))
        }
        if (reminderOn && !notifications.granted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.review_notify_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = notifications.request) {
                        Text(stringResource(if (notifications.denied) R.string.review_notify_settings else R.string.review_notify_on))
                    }
                }
            }
        }
    }
}

/** 封面的预览：月份、花了多少、几天有消费、消费性格，以及「开始回顾」。 */
@Composable
private fun CoverPreview(review: MonthReview, onStart: () -> Unit) {
    val summary = review.summary
    val monthText = summary.month.format(rememberMonthFormat())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(COVER_COLORS))
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.review_title, monthText), color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (summary.entryCount == 0) {
                Text(stringResource(R.string.review_empty), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(stringResource(R.string.review_spent), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
            Text(
                formatMoney(Money(summary.totalMinor, summary.currency)),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                countAndDays(summary.entryCount, review.activeDays),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                personaEmoji(review.persona) + " " + stringResource(personaName(review.persona)),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = COVER_COLORS.first()),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.review_start)) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
