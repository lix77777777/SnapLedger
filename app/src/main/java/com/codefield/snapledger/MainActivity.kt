package com.codefield.snapledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.codefield.snapledger.ui.LedgerViewModel
import com.codefield.snapledger.ui.SnapLedgerApp
import com.codefield.snapledger.ui.common.SnapLedgerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels { LedgerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 旋转屏幕重建时 intent 还是原来那个，只在第一次创建时收截图，不然会重复收。
        if (savedInstanceState == null) receiveShared(intent)
        setContent {
            SnapLedgerTheme {
                SnapLedgerApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShared(intent)
    }

    /** 别的应用分享过来的截图，一张或好几张。 */
    private fun receiveShared(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        viewModel.importImages(uris)
    }
}
