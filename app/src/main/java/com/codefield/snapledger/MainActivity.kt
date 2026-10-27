package com.codefield.snapledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.codefield.snapledger.ui.SnapLedgerApp
import com.codefield.snapledger.ui.common.SnapLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapLedgerTheme {
                SnapLedgerApp()
            }
        }
    }
}
