package com.codefield.snapledger.data

import android.content.Context
import androidx.core.content.edit
import com.codefield.snapledger.model.Currency

/** 玩家设置，用 SharedPreferences 保存，和账目分开。 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 统计用的主货币，默认韩元。 */
    var homeCurrency: Currency
        get() = prefs.getString(KEY_HOME_CURRENCY, null)?.let { name -> Currency.entries.firstOrNull { it.name == name } }
            ?: Currency.KRW
        set(value) = prefs.edit { putString(KEY_HOME_CURRENCY, value.name) }

    private companion object {
        const val KEY_HOME_CURRENCY = "home_currency"
    }
}
