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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.AmountInput
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Source
import com.codefield.snapledger.ui.common.categoryEmoji
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.currencySymbol
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 记一笔或修改一笔。输入的内容用 rememberSaveable 保存，旋转屏幕也不会丢。
 * initial 为 null 时是新记一笔；onDelete 为 null 时不显示删除按钮。
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
) {
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.let(AmountInput::format).orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(initial?.amount?.currency ?: defaultCurrency) }
    var merchant by rememberSaveable { mutableStateOf(initial?.merchant.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: Category.OTHER) }
    var time by rememberSaveable { mutableLongStateOf(initial?.time ?: System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(Picker.NONE) }

    val amount = AmountInput.parse(amountText, currency)
    val locale = LocalConfiguration.current.locales[0]
    val dateTime = Instant.ofEpochMilli(time).atZone(zone)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_entry else R.string.edit_entry)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            location = initial?.location,
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

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
        Picker.NONE -> Unit
    }
}

private enum class Picker { NONE, DATE, TIME }

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
