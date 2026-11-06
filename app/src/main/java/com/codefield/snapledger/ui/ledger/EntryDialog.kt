package com.codefield.snapledger.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.AmountInput
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.GeoPoint
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.Source
import com.codefield.snapledger.ui.map.MapPickerDialog
import com.codefield.snapledger.ui.common.ScreenshotThumbnail
import com.codefield.snapledger.ui.common.ScreenshotViewer
import com.codefield.snapledger.ui.common.categoryEmoji
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.currencySymbol
import com.codefield.snapledger.ui.common.formatMoney
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 记一笔、修改一笔，或者确认截图识别出的一笔。输入的内容用 rememberSaveable 保存，旋转屏幕也不会丢。
 * initial 为 null 时是新记一笔；截图识别的结果也用 initial 传进来（编号是 0，金额是 0 表示没识别出金额）。
 * onDelete 为 null 时不显示删除按钮。
 * @param image 这笔账的截图，显示成小图，点了看大图。
 * @param candidates 截图里找到的几个金额，识别错了可以点别的。
 * @param notices 显示在最上面的提醒，比如没找到金额、可能记过了。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDialog(
    initial: Entry?,
    defaultCurrency: Currency,
    zone: ZoneId,
    onSave: (Entry) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    title: String = stringResource(if (initial == null) R.string.add_entry else R.string.edit_entry),
    image: File? = null,
    candidates: List<Money> = emptyList(),
    notices: List<String> = emptyList(),
    dismissLabel: String = stringResource(R.string.cancel),
    dismissOnClickOutside: Boolean = true,
) {
    var amountText by rememberSaveable {
        mutableStateOf(initial?.amount?.takeIf { it.minor > 0 }?.let(AmountInput::format).orEmpty())
    }
    var currency by rememberSaveable { mutableStateOf(initial?.amount?.currency ?: defaultCurrency) }
    var merchant by rememberSaveable { mutableStateOf(initial?.merchant.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: Category.OTHER) }
    var time by rememberSaveable { mutableLongStateOf(initial?.time ?: System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    // 位置分成经纬度两个数存，旋转屏幕也不丢；没有位置时都是 null。
    var latitude by rememberSaveable { mutableStateOf(initial?.location?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(initial?.location?.longitude) }
    var showError by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(Picker.NONE) }
    var viewingImage by rememberSaveable { mutableStateOf(false) }

    val amount = AmountInput.parse(amountText, currency)
    val locale = LocalConfiguration.current.locales[0]
    val dateTime = Instant.ofEpochMilli(time).atZone(zone)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside),
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (image != null || notices.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (image != null) ScreenshotThumbnail(image, onClick = { viewingImage = true })
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            notices.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
                        }
                    }
                }
                if (candidates.size > 1) {
                    Text(stringResource(R.string.import_candidates), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidates.forEach { option ->
                            FilterChip(
                                selected = option == amount,
                                onClick = {
                                    amountText = AmountInput.format(option)
                                    currency = option.currency
                                    showError = false
                                },
                                label = { Text(formatMoney(option)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.field_amount)) },
                    prefix = { Text(currencySymbol(currency)) },
                    singleLine = true,
                    isError = showError && amount == null,
                    supportingText = if (showError && amount == null) {
                        { Text(stringResource(R.string.error_amount)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Currency.entries.forEach { option ->
                        FilterChip(selected = option == currency, onClick = { currency = option }, label = { Text(option.name) })
                    }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.field_merchant)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { option ->
                        FilterChip(
                            selected = option == category,
                            onClick = { category = option },
                            label = { Text(categoryEmoji(option) + " " + stringResource(categoryName(option))) },
                        )
                    }
                }
                Text(stringResource(R.string.field_date), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker = Picker.DATE }) {
                        Text(dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)))
                    }
                    OutlinedButton(onClick = { picker = Picker.TIME }) { Text(dateTime.format(TIME_FORMAT)) }
                }
                Text(stringResource(R.string.field_location), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hasPlace = latitude != null && longitude != null
                    Text(
                        stringResource(if (hasPlace) R.string.location_set else R.string.location_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { picker = Picker.MAP }) {
                        Text(stringResource(if (hasPlace) R.string.location_change else R.string.location_pick))
                    }
                    if (hasPlace) {
                        TextButton(onClick = {
                            latitude = null
                            longitude = null
                        }) { Text(stringResource(R.string.location_clear)) }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val money = amount
                val lat = latitude
                val lng = longitude
                if (money == null) {
                    showError = true
                } else {
                    onSave(
                        Entry(
                            id = initial?.id ?: 0,
                            amount = money,
                            category = category,
                            merchant = merchant.trim(),
                            time = time,
                            note = note.trim(),
                            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
                            source = initial?.source ?: Source.MANUAL,
                            imagePath = initial?.imagePath,
                        )
                    )
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        },
    )

    if (viewingImage && image != null) ScreenshotViewer(image, onClose = { viewingImage = false })

    when (picker) {
        Picker.DATE -> {
            // 日期选择器按 UTC 的零点表示选中的那一天。
            val state = rememberDatePickerState(
                initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { picker = Picker.NONE },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utc ->
                            val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                            time = LocalDateTime.of(date, dateTime.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        }
                        picker = Picker.NONE
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = Picker.NONE }) { Text(stringResource(R.string.cancel)) } },
            ) {
                DatePicker(state = state)
            }
        }
        Picker.TIME -> {
            val state = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picker = Picker.NONE },
                title = { Text(stringResource(R.string.field_time)) },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        time = dateTime.withHour(state.hour).withMinute(state.minute).withSecond(0).toInstant().toEpochMilli()
                        picker = Picker.NONE
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = Picker.NONE }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        Picker.MAP -> {
            val lat = latitude
            val lng = longitude
            MapPickerDialog(
                initial = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
                onPick = { place ->
                    latitude = place.latitude
                    longitude = place.longitude
                    picker = Picker.NONE
                },
                onDismiss = { picker = Picker.NONE },
            )
        }
        Picker.NONE -> Unit
    }
}

private enum class Picker { NONE, DATE, TIME, MAP }

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
