package com.codefield.snapledger.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.codefield.snapledger.R
import com.codefield.snapledger.ui.common.PlaceholderScreen

/** 设置：主货币、预算、导出。第 6 步起逐步补上。 */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.action_settings), modifier = modifier, onBack = onBack)
}
