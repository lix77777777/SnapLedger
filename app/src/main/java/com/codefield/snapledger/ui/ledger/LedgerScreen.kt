package com.codefield.snapledger.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefield.snapledger.R
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.MonthSummary
import com.codefield.snapledger.model.Source
import com.codefield.snapledger.ui.common.CategoryBadge
import com.codefield.snapledger.ui.common.ScreenHeader
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.formatMoney
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 账本：顶部是本月支出，下面按天列出每一笔。点一笔可以修改。
 * 记一笔的按钮在外层 Scaffold 里（这样提示条出现时按钮会让开），
 * 所以对话框开没开由外面传进来：dialogFor 是 0 表示新记一笔，其他数字是正在修改的那一笔。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    entries: List<Entry>?,
    summary: MonthSummary?,
    homeCurrency: Currency,
    zone: ZoneId,
    dialogFor: Long?,
    onDialogFor: (Long?) -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onOpenSettings: () -> Unit,
    imageFor: (String) -> File,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.date_header_pattern)
    val dayFormat = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            // 底部留出两个按钮的高度，最后一笔不会被挡住。
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(title = stringResource(R.string.tab_ledger)) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
                }
            }
            item { MonthCard(summary) }
            when {
                entries == null -> Unit
                entries.isEmpty() -> item { EmptyState() }
                else -> entries.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }.forEach { (date, dayEntries) ->
                    stickyHeader(key = "day-$date") { DayHeader(date.format(dayFormat)) }
                    items(dayEntries, key = { it.id }) { entry -> EntryRow(entry, zone) { onDialogFor(entry.id) } }
                }
            }
        }
    }

    val editing = dialogFor?.takeIf { it != 0L }?.let { id -> entries?.firstOrNull { it.id == id } }
    if (dialogFor == 0L || editing != null) {
        EntryDialog(
            initial = editing,
            defaultCurrency = homeCurrency,
            zone = zone,
            onSave = {
                onSave(it)
                onDialogFor(null)
            },
            onDelete = editing?.let { entry -> { confirmDelete = entry.id } },
            onDismiss = { onDialogFor(null) },
            image = editing?.imagePath?.let(imageFor),
        )
    }

    val deleting = confirmDelete?.let { id -> entries?.firstOrNull { it.id == id } }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleting)
                    confirmDelete = null
                    onDialogFor(null)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MonthCard(summary: MonthSummary?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val total = summary?.let { formatMoney(Money(it.totalMinor, it.currency)) } ?: "—"
            Text(stringResource(R.string.ledger_month_total, total), style = MaterialTheme.typography.titleLarge)
            if (summary != null) {
                Text(stringResource(R.string.ledger_month_count, summary.entryCount), style = MaterialTheme.typography.bodyMedium)
                if (summary.unconvertedCount > 0) {
                    Text(stringResource(R.string.ledger_unconverted, summary.unconvertedCount), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun EntryRow(entry: Entry, zone: ZoneId, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(entry.category)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.merchant.ifBlank { stringResource(categoryName(entry.category)) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = Instant.ofEpochMilli(entry.time).atZone(zone).format(TIME_FORMAT)
                val source = if (entry.source == Source.SCREENSHOT) stringResource(R.string.source_screenshot) else ""
                Text(
                    text = listOf(time, source, entry.note).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatMoney(entry.amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🧾", fontSize = 48.sp)
        Text(stringResource(R.string.ledger_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.ledger_empty_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
