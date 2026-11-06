package com.codefield.snapledger.data

import android.content.Context
import androidx.core.content.edit
import com.codefield.snapledger.model.Currency

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

    private companion object {
        const val KEY_HOME_CURRENCY = "home_currency"
        const val KEY_RATES_CHECKED_AT = "rates_checked_at"
        const val KEY_REVIEW_REMINDER = "review_reminder"
    }
}
