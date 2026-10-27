package com.codefield.snapledger.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.MonthlyStats
import com.codefield.snapledger.ui.ledger.LedgerScreen
import com.codefield.snapledger.ui.map.MapScreen
import com.codefield.snapledger.ui.review.ReviewScreen
import com.codefield.snapledger.ui.settings.SettingsScreen
import com.codefield.snapledger.ui.stats.StatsScreen
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.launch

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
fun SnapLedgerApp(viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory)) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    val homeCurrency by viewModel.homeCurrency.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val summary = remember(entries, rates, homeCurrency) {
        entries?.let { MonthlyStats.summarize(it, YearMonth.now(zone), homeCurrency, rates, zone) }
    }

    // 账本页的对话框：0 是新记一笔，其他是正在修改的那一笔。放在这里是因为 + 按钮在 Scaffold 上。
    var ledgerDialog by rememberSaveable { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // 保存、删除的提示。删除的提示可以撤销。
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                when (message) {
                    LedgerMessage.Saved -> snackbarHostState.showSnackbar(resources.getString(R.string.msg_saved))
                    is LedgerMessage.Deleted -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.msg_deleted),
                            actionLabel = resources.getString(R.string.undo),
                            // 带「撤销」的提示多留一会儿，来得及点。
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(message.entry)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentRoute == Routes.LEDGER) {
                FloatingActionButton(onClick = { ledgerDialog = 0L }) { Text("+", fontSize = 28.sp) }
            }
        },
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
            composable(Routes.LEDGER) {
                LedgerScreen(
                    entries = entries,
                    summary = summary,
                    homeCurrency = homeCurrency,
                    zone = zone,
                    dialogFor = ledgerDialog,
                    onDialogFor = { ledgerDialog = it },
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
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
