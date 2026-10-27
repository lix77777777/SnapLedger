package com.codefield.snapledger.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.DuplicateFinder
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.ui.ImportQueue
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.formatMoney
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 截图记账的对话框，一次显示队列里的第一张：还在识别时显示进度，识别好了显示填好的「记一笔」对话框。
 * 「跳过」和返回键都是不记这一张；点对话框外面不会关，免得一不小心把识别结果丢了。
 */
@Composable
fun ImportDialog(
    queue: ImportQueue,
    entries: List<Entry>,
    defaultCurrency: Currency,
    zone: ZoneId,
    imageFor: (String) -> File,
    onConfirm: (Entry) -> Unit,
    onSkip: () -> Unit,
) {
    val shot = queue.current ?: return
    val draft = shot.draft
    val several = queue.total > 1

    if (draft == null) {
        AlertDialog(
            onDismissRequest = onSkip,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Text(
                    if (several) stringResource(R.string.import_reading_count, queue.position, queue.total)
                    else stringResource(R.string.import_reading)
                )
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.import_reading_hint))
                }
            },
            confirmButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.import_skip)) } },
        )
        return
    }

    val duplicate = remember(entries, draft) { DuplicateFinder.find(entries, draft, zone) }
    val noAmount = stringResource(R.string.import_no_amount).takeIf { draft.amount.minor == 0L }
    val similar = duplicate?.let { stringResource(R.string.import_duplicate, describe(it, zone)) }

    // 换到下一张时整个对话框重新开始，不然上一张输入的内容会留在框里。
    key(shot.image) {
        EntryDialog(
            initial = draft,
            defaultCurrency = defaultCurrency,
            zone = zone,
            onSave = onConfirm,
            onDelete = null,
            onDismiss = onSkip,
            title = if (several) stringResource(R.string.import_title_count, queue.position, queue.total)
            else stringResource(R.string.import_title),
            image = imageFor(shot.image),
            candidates = shot.candidates,
            notices = listOfNotNull(noAmount, similar),
            dismissLabel = stringResource(R.string.import_skip),
            dismissOnClickOutside = false,
        )
    }
}

/** 「2026年10月28日 14:32 · Starbucks · ₩6,500」，用来说明可能重复的是哪一笔。 */
@Composable
private fun describe(entry: Entry, zone: ZoneId): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val name = entry.merchant.ifBlank { stringResource(categoryName(entry.category)) }
    return listOf(Instant.ofEpochMilli(entry.time).atZone(zone).format(format), name, formatMoney(entry.amount)).joinToString(" · ")
}
