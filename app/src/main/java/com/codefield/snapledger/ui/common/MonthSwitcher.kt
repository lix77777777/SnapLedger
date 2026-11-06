package com.codefield.snapledger.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.codefield.snapledger.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 「‹ 2026年10月 ›」：统计页和回顾页翻月份用。 */
@Composable
fun MonthSwitcher(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onMonth: (YearMonth) -> Unit) {
    val format = rememberMonthFormat()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val previous = stringResource(R.string.stats_prev_month)
        val next = stringResource(R.string.stats_next_month)
        TextButton(
            onClick = { onMonth(month.minusMonths(1)) },
            enabled = canGoBack,
            modifier = Modifier.semantics { contentDescription = previous },
        ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
        Text(
            month.format(format),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onMonth(month.plusMonths(1)) },
            enabled = canGoForward,
            modifier = Modifier.semantics { contentDescription = next },
        ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
    }
}

/** 按界面语言显示年月：「2026年10月」「2026년 10월」「October 2026」。 */
@Composable
fun rememberMonthFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.stats_month_pattern)
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
