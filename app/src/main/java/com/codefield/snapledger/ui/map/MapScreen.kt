package com.codefield.snapledger.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.codefield.snapledger.R
import com.codefield.snapledger.ui.common.PlaceholderScreen

/** 消费地图：这个月在哪里花了钱。第 9 步实现。 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_map), modifier = modifier)
}
