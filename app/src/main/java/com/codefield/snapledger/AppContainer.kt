package com.codefield.snapledger

import android.content.Context
import android.content.pm.ApplicationInfo
import com.codefield.snapledger.data.LedgerRepository
import com.codefield.snapledger.data.SettingsStore
import com.codefield.snapledger.data.db.LedgerDatabase
import com.codefield.snapledger.remote.FrankfurterApi
import com.codefield.snapledger.system.ImageSharer
import com.codefield.snapledger.system.RateUpdater
import com.codefield.snapledger.system.ReviewReminder
import com.codefield.snapledger.system.ScreenshotReader
import com.codefield.snapledger.system.ScreenshotStore
import com.codefield.snapledger.system.TextScanner

/** 把各个文件夹的对象组装起来，整个 App 共用一份。界面通过它拿到需要的东西。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 调试版：识别时把读到的文字写进日志，设置里多一个「测试提醒」按钮。 */
    val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val database by lazy { LedgerDatabase.create(appContext) }
    val repository: LedgerRepository by lazy { LedgerRepository(database.entryDao(), database.rateDao()) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val rateUpdater: RateUpdater by lazy { RateUpdater(FrankfurterApi(), repository) }
    val screenshots: ScreenshotStore by lazy { ScreenshotStore(appContext) }
    val screenshotReader: ScreenshotReader by lazy { ScreenshotReader(screenshots, TextScanner(), debug = debuggable) }
    val reviewReminder: ReviewReminder by lazy { ReviewReminder(appContext) }
    val imageSharer: ImageSharer by lazy { ImageSharer(appContext) }
}
