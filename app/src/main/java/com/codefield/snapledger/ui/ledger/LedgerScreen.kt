package com.codefield.snapledger.ui.ledger

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.codefield.snapledger.R
import com.codefield.snapledger.ui.common.PlaceholderScreen

/** 账本：按天列出每一笔账。第 4 步实现。 */
@Composable
fun LedgerScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_ledger), modifier = modifier) {
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
    }
}
