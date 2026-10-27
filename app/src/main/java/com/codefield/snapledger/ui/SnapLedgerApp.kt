package com.codefield.snapledger.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codefield.snapledger.R
import com.codefield.snapledger.ui.ledger.LedgerScreen
import com.codefield.snapledger.ui.map.MapScreen
import com.codefield.snapledger.ui.review.ReviewScreen
import com.codefield.snapledger.ui.settings.SettingsScreen
import com.codefield.snapledger.ui.stats.StatsScreen

object Routes {
    const val LEDGER = "ledger"
    const val STATS = "stats"
    const val REVIEW = "review"
    const val MAP = "map"
    const val SETTINGS = "settings"
}

/** 底部菜单里的四个主页面。设置从账本页右上角进入，不放在底部菜单。 */
enum class TopLevel(val route: String, @param:StringRes val label: Int) {
    Ledger(Routes.LEDGER, R.string.tab_ledger),
    Stats(Routes.STATS, R.string.tab_stats),
    Review(Routes.REVIEW, R.string.tab_review),
    Map(Routes.MAP, R.string.tab_map),
}

@Composable
fun SnapLedgerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(currentRoute = currentRoute, navController = navController)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LEDGER,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LEDGER) { LedgerScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) }) }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.REVIEW) { ReviewScreen() }
            composable(Routes.MAP) { MapScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar {
        TopLevel.entries.forEach { destination ->
            val label = stringResource(destination.label)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // 图标最后统一换成正式素材，现在先用文字的第一个字占位。
                icon = { Text(label.take(1)) },
                label = { Text(label) },
            )
        }
    }
}
