package com.codefield.snapledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.codefield.snapledger.ui.ledger.ImportDialog
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

/** 一次最多从相册选几张截图。 */
private const val MAX_PICK = 20

@Composable
fun SnapLedgerApp(viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory)) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    val homeCurrency by viewModel.homeCurrency.collectAsStateWithLifecycle()
    val rateStatus by viewModel.rateStatus.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val summary = remember(entries, rates, homeCurrency) {
        entries?.let { MonthlyStats.summarize(it, YearMonth.now(zone), homeCurrency, rates, zone) }
    }

    // 每次回到前台（包括刚打开）都补一下汇率；断网时什么也不做，接着用存下的。
    LifecycleStartEffect(viewModel) {
        viewModel.refreshRates()
        onStopOrDispose { }
    }

    // 账本页的对话框：0 是新记一笔，其他是正在修改的那一笔。放在这里是因为按钮在 Scaffold 上。
    var ledgerDialog by rememberSaveable { mutableStateOf<Long?>(null) }
    val importQueue by viewModel.imports.collectAsStateWithLifecycle()
    // 系统的照片选择器：不用申请读取相册的权限，只能拿到用户选的那几张。
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        viewModel.importImages(uris)
    }

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
                    is LedgerMessage.SavedSeveral ->
                        snackbarHostState.showSnackbar(resources.getString(R.string.msg_saved_several, message.count))
                    is LedgerMessage.Deleted -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.msg_deleted),
                            actionLabel = resources.getString(R.string.undo),
                            // 带「撤销」的提示多留一会儿，来得及点。
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(message.entry)
                    }
                    LedgerMessage.ImageFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.msg_image_failed))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentRoute == Routes.LEDGER) {
                AddButtons(
                    onScan = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onAdd = { ledgerDialog = 0L },
                )
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
                    rates = rates,
                    zone = zone,
                    dialogFor = ledgerDialog,
                    onDialogFor = { ledgerDialog = it },
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    imageFor = viewModel::imageFile,
                )
            }
            composable(Routes.STATS) { StatsScreen(entries = entries, rates = rates, homeCurrency = homeCurrency, zone = zone) }
            composable(Routes.REVIEW) { ReviewScreen() }
            composable(Routes.MAP) { MapScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    homeCurrency = homeCurrency,
                    onHomeCurrency = viewModel::setHomeCurrency,
                    rates = rates,
                    rateStatus = rateStatus,
                    onUpdateRates = { viewModel.refreshRates(force = true) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // 截图记账的对话框放在最外层：从别的应用分享过来时，不管在哪个页面都会弹出来。
    ImportDialog(
        queue = importQueue,
        entries = entries.orEmpty(),
        defaultCurrency = homeCurrency,
        zone = zone,
        imageFor = viewModel::imageFile,
        onConfirm = viewModel::confirmImport,
        onConfirmItems = viewModel::confirmImportItems,
        onSelectItems = viewModel::selectImportItems,
        onEditItem = viewModel::editImportItem,
        onSkip = viewModel::skipImport,
    )
}

/** 账本页右下角：大按钮是识别截图（主要的记账方式），上面的小按钮是手动记一笔。 */
@Composable
private fun AddButtons(onScan: () -> Unit, onAdd: () -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val addLabel = stringResource(R.string.add_manually)
        SmallFloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clearAndSetSemantics { contentDescription = addLabel },
        ) {
            Text("+", fontSize = 22.sp)
        }
        ExtendedFloatingActionButton(onClick = onScan) {
            Text(stringResource(R.string.import_screenshot))
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
