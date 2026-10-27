package com.codefield.snapledger.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 临时配色：青绿（记账）+ 琥珀（提醒）。正式设计定下来以后只改这里。
private val LightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFFB26A00),
    tertiary = Color(0xFF6A4BC4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    secondary = Color(0xFFFFB74D),
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
