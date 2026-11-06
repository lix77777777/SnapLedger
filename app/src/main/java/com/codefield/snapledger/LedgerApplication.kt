package com.codefield.snapledger

import android.app.Application

class LedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 每月 1 号的回顾提醒：已经排好的不动，被系统清掉了（比如重装）就重新排上。
        if (container.settings.reviewReminder) container.reviewReminder.schedule()
    }
}
