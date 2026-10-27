package com.codefield.snapledger

import android.content.Context
import com.codefield.snapledger.data.LedgerRepository
import com.codefield.snapledger.data.SettingsStore
import com.codefield.snapledger.data.db.LedgerDatabase

/** 把各个文件夹的对象组装起来，整个 App 共用一份。界面通过它拿到需要的东西。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database by lazy { LedgerDatabase.create(appContext) }
    val repository: LedgerRepository by lazy { LedgerRepository(database.entryDao(), database.rateDao()) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
}
