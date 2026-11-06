package com.codefield.snapledger.data

import android.content.Context
import androidx.core.content.edit
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Money

/** 用户设置，用 SharedPreferences 保存，和账目分开。 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 统计用的主货币，默认韩元。 */
    var homeCurrency: Currency
        get() = prefs.getString(KEY_HOME_CURRENCY, null)?.let { name -> Currency.entries.firstOrNull { it.name == name } }
            ?: Currency.KRW
        set(value) = prefs.edit { putString(KEY_HOME_CURRENCY, value.name) }

    /** 上次成功检查有没有新汇率的时间（毫秒），从没检查过是 0。 */
    var ratesCheckedAt: Long
        get() = prefs.getLong(KEY_RATES_CHECKED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_RATES_CHECKED_AT, value) }

    /** 每月 1 号提醒看回顾，默认开着。 */
    var reviewReminder: Boolean
        get() = prefs.getBoolean(KEY_REVIEW_REMINDER, true)
        set(value) = prefs.edit { putBoolean(KEY_REVIEW_REMINDER, value) }

    /** 新记的账（付款后 2 小时内）带上当前位置，默认开着。 */
    var recordLocation: Boolean
        get() = prefs.getBoolean(KEY_RECORD_LOCATION, true)
        set(value) = prefs.edit { putBoolean(KEY_RECORD_LOCATION, value) }

    /** 已经问过定位权限（第一次记账时问），之后不再自动弹窗，要开就去设置里开。 */
    var locationAsked: Boolean
        get() = prefs.getBoolean(KEY_LOCATION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_LOCATION_ASKED, value) }

    /** 每月预算，带货币（换了主货币时按汇率换算）；没设是 null。 */
    var budget: Money?
        get() {
            val minor = prefs.getLong(KEY_BUDGET_MINOR, 0L).takeIf { it > 0 } ?: return null
            val currency = prefs.getString(KEY_BUDGET_CURRENCY, null)?.let { name -> Currency.entries.firstOrNull { it.name == name } }
            return currency?.let { Money(minor, it) }
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_BUDGET_MINOR)
                remove(KEY_BUDGET_CURRENCY)
            } else {
                putLong(KEY_BUDGET_MINOR, value.minor)
                putString(KEY_BUDGET_CURRENCY, value.currency.name)
            }
        }

    private companion object {
        const val KEY_HOME_CURRENCY = "home_currency"
        const val KEY_RATES_CHECKED_AT = "rates_checked_at"
        const val KEY_REVIEW_REMINDER = "review_reminder"
        const val KEY_RECORD_LOCATION = "record_location"
        const val KEY_LOCATION_ASKED = "location_asked"
        const val KEY_BUDGET_MINOR = "budget_minor"
        const val KEY_BUDGET_CURRENCY = "budget_currency"
    }
}
