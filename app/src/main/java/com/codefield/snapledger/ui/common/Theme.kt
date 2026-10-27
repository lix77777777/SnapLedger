package com.codefield.snapledger.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 临时配色：青绿（记账）+ 琥珀（提醒）。正式设计定下来以后只改这里。
// 卡片、+ 按钮、选中的小标签用的是 container 颜色，也要配成同一套，不然会用默认的紫色。
private val LightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFFB26A00),
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFF6A4BC4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFFFB74D),
    secondaryContainer = Color(0xFF5C3A00),
    onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = Color(0xFFB39DDB),
)

@Composable
fun SnapLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
