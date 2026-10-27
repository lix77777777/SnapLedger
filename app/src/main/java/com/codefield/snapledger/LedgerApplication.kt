package com.codefield.snapledger

import android.app.Application

class LedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
