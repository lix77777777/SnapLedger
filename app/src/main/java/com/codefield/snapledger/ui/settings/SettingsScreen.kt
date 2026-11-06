package com.codefield.snapledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.AmountInput
import com.codefield.snapledger.logic.CurrencyConverter
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.ui.RateStatus
import com.codefield.snapledger.ui.common.ScreenHeader
import com.codefield.snapledger.ui.common.currencyName
import com.codefield.snapledger.ui.common.currencySymbol
import com.codefield.snapledger.ui.common.formatMoney
import com.codefield.snapledger.ui.common.formatQuote
import com.codefield.snapledger.ui.common.rememberLocationAccess
import com.codefield.snapledger.ui.common.rememberNotificationAccess
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 设置：主货币、汇率、每月回顾提醒。预算以后加。 */
@Composable
fun SettingsScreen(
    homeCurrency: Currency,
    onHomeCurrency: (Currency) -> Unit,
    rates: RateTable,
    rateStatus: RateStatus,
    onUpdateRates: () -> Unit,
    reviewReminder: Boolean,
    onReviewReminder: (Boolean) -> Unit,
    debug: Boolean,
    onTestReminder: () -> Unit,
    recordLocation: Boolean,
    onRecordLocation: (Boolean) -> Unit,
    budget: Money?,
    onBudget: (Money?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.action_settings), onBack = onBack)
        Section(stringResource(R.string.settings_home_currency)) {
            Hint(stringResource(R.string.settings_home_currency_hint))
            Column(modifier = Modifier.selectableGroup()) {
                Currency.entries.forEach { currency ->
                    CurrencyOption(currency, selected = currency == homeCurrency, onSelect = { onHomeCurrency(currency) })
                }
            }
        }
        Section(stringResource(R.string.settings_rates)) {
            RatesInfo(rates, homeCurrency)
            when (rateStatus) {
                RateStatus.UPDATING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rates_updating), style = MaterialTheme.typography.bodyMedium)
                }
                RateStatus.FAILED -> Text(
                    stringResource(R.string.rates_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                RateStatus.IDLE -> Unit
            }
            OutlinedButton(onClick = onUpdateRates, enabled = rateStatus != RateStatus.UPDATING) {
                Text(stringResource(R.string.rates_update_now))
            }
            Hint(stringResource(R.string.rates_rule))
            Hint(stringResource(R.string.rates_source))
        }
        Section(stringResource(R.string.settings_budget)) {
            BudgetOptions(budget, homeCurrency, onBudget)
        }
        Section(stringResource(R.string.field_location)) {
            LocationOptions(recordLocation, onRecordLocation)
        }
        Section(stringResource(R.string.settings_reminders)) {
            ReminderOptions(reviewReminder, onReviewReminder, debug, onTestReminder)
        }
    }
}

/** 每月预算：显示现在的预算，点按钮打开输入框（用主货币）。 */
@Composable
private fun BudgetOptions(budget: Money?, homeCurrency: Currency, onBudget: (Money?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Text(
        if (budget == null) stringResource(R.string.settings_budget_none)
        else stringResource(R.string.settings_budget_value, formatMoney(budget)),
        style = MaterialTheme.typography.bodyLarge,
    )
    Hint(stringResource(R.string.settings_budget_hint))
    OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.settings_budget_set)) }
    if (editing) {
        // 原来的预算是别的货币时，从空白开始填（单位不一样，直接改数字容易弄错）。
        var text by rememberSaveable { mutableStateOf(budget?.takeIf { it.currency == homeCurrency }?.let(AmountInput::format).orEmpty()) }
        val amount = AmountInput.parse(text, homeCurrency)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.settings_budget)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    prefix = { Text(currencySymbol(homeCurrency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(enabled = amount != null, onClick = {
                    onBudget(amount)
                    editing = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onBudget(null)
                    editing = false
                }) { Text(stringResource(R.string.budget_clear)) }
            },
        )
    }
}

/** 记账时记下位置的开关；打开时没有权限就顺便问。 */
@Composable
private fun LocationOptions(recordLocation: Boolean, onRecordLocation: (Boolean) -> Unit) {
    val location = rememberLocationAccess()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_location), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = recordLocation,
            onCheckedChange = { on ->
                onRecordLocation(on)
                if (on && !location.granted) location.request { }
            },
        )
    }
    Hint(stringResource(R.string.settings_location_hint))
    if (recordLocation && !location.granted) {
        Text(stringResource(R.string.settings_location_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { if (location.denied) location.openSettings() else location.request { } }) {
            Text(stringResource(if (location.denied) R.string.review_notify_settings else R.string.settings_location_on))
        }
    }
}

/** 每月回顾提醒的开关。打开时如果还没有通知权限，顺便申请。调试版多一个马上发测试提醒的按钮。 */
@Composable
private fun ReminderOptions(reviewReminder: Boolean, onReviewReminder: (Boolean) -> Unit, debug: Boolean, onTestReminder: () -> Unit) {
    val notifications = rememberNotificationAccess()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.settings_review_reminder),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = reviewReminder,
            onCheckedChange = { on ->
                onReviewReminder(on)
                if (on && !notifications.granted) notifications.request()
            },
        )
    }
    if (reviewReminder && !notifications.granted) {
        Text(stringResource(R.string.settings_notifications_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = notifications.request) {
            Text(stringResource(if (notifications.denied) R.string.review_notify_settings else R.string.review_notify_on))
        }
    }
    if (debug) {
        OutlinedButton(onClick = onTestReminder) { Text(stringResource(R.string.settings_test_reminder)) }
    }
}

/** 最新汇率的日期，以及其他几种货币各值多少主货币。 */
@Composable
private fun RatesInfo(rates: RateTable, homeCurrency: Currency) {
    val latest = rates.latest
    if (latest == null) {
        Text(stringResource(R.string.rates_none), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Text(stringResource(R.string.rates_date, latest.date.format(dateFormat)), style = MaterialTheme.typography.bodyMedium)
    Currency.entries.filter { it != homeCurrency }.forEach { currency ->
        val quote = CurrencyConverter.quote(currency, homeCurrency, latest) ?: return@forEach
        Text(formatQuote(quote, currency, homeCurrency), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CurrencyOption(currency: Currency, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 整行可以点，单选按钮本身不用再处理点击。
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text("${currencySymbol(currency)}  ${stringResource(currencyName(currency))} (${currency.name})")
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
