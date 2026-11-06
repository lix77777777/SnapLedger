package com.codefield.snapledger.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.DuplicateFinder
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.ui.PendingShot
import com.codefield.snapledger.ui.common.ScreenshotThumbnail
import com.codefield.snapledger.ui.common.ScreenshotViewer
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.formatMoney
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * 列表截图（账单、流水、付款通知）的确认对话框：列出识别出的每一笔，勾上的一次记下。
 * 可能已经记过的默认不勾。点一笔打开「修改这一笔」对话框，改完回到列表。
 * 勾选和修改存在 ViewModel 里，旋转屏幕不会丢。
 */
@Composable
internal fun ImportListDialog(
    shot: PendingShot,
    title: String,
    entries: List<Entry>,
    defaultCurrency: Currency,
    zone: ZoneId,
    image: File,
    onSelect: (Set<Int>) -> Unit,
    onEdit: (Int, Entry) -> Unit,
    onConfirm: (List<Entry>) -> Unit,
    onSkip: () -> Unit,
) {
    val items = shot.items
    val duplicates = remember(entries, items) { items.map { DuplicateFinder.find(entries, it, zone) != null } }
    val selected = shot.selected ?: items.indices.filterNot { duplicates[it] }.toSet()
    var editing by rememberSaveable { mutableIntStateOf(NONE) }
    var viewingImage by rememberSaveable { mutableStateOf(false) }

    val item = items.getOrNull(editing)
    if (item != null) {
        key(editing) {
            EntryDialog(
                initial = item,
                defaultCurrency = defaultCurrency,
                zone = zone,
                onSave = {
                    onEdit(editing, it)
                    editing = NONE
                },
                onDelete = null,
                onDismiss = { editing = NONE },
                image = image,
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    ScreenshotThumbnail(image, onClick = { viewingImage = true })
                    Text(
                        pluralStringResource(R.plurals.import_list_hint, items.size, items.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                items.forEachIndexed { i, entry ->
                    ItemRow(
                        entry = entry,
                        checked = i in selected,
                        duplicate = duplicates[i],
                        zone = zone,
                        onCheck = { checked -> onSelect(if (checked) selected + i else selected - i) },
                        onClick = { editing = i },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(items.filterIndexed { i, _ -> i in selected }) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.import_list_save, selected.size))
            }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.import_skip)) } },
    )

    if (viewingImage) ScreenshotViewer(image, onClose = { viewingImage = false })
}

/** 一笔：勾选框、商家（没有时写类别）、时间、可能重复的提醒、金额。点这一行修改。 */
@Composable
private fun ItemRow(entry: Entry, checked: Boolean, duplicate: Boolean, zone: ZoneId, onCheck: (Boolean) -> Unit, onClick: () -> Unit) {
    val format = rememberDateTimeFormat()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.merchant.ifBlank { stringResource(categoryName(entry.category)) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Instant.ofEpochMilli(entry.time).atZone(zone).format(format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (duplicate) {
                Text(
                    stringResource(R.string.import_list_duplicate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(formatMoney(entry.amount), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** 没在修改哪一笔。 */
private const val NONE = -1
