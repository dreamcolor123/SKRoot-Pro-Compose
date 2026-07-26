package com.linux.permissionmanager

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.linux.permissionmanager.data.LogPayload
import com.linux.permissionmanager.data.UiEffect
import com.linux.permissionmanager.ui.*
import com.linux.permissionmanager.ui.screens.*
import com.linux.permissionmanager.ui.theme.SkpTheme
import com.linux.permissionmanager.utils.FileUtils
import com.linux.permissionmanager.utils.GetAppListPermissionHelper
import com.linux.permissionmanager.utils.UrlIntentUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SkpTheme { SkpApp() } }
    }
}

private data class NavigationItem(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val navigationItems = listOf(
    NavigationItem("主页", Icons.Filled.Home, Icons.Outlined.Home),
    NavigationItem("授权", Icons.Filled.Shield, Icons.Outlined.Shield),
    NavigationItem("模块", Icons.Filled.Extension, Icons.Outlined.Extension),
    NavigationItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
private fun SkpApp() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val application = activity.application as PermissionManagerApplication
    val factory = remember { AppViewModelFactory(application) }
    val mainViewModel: MainViewModel = viewModel(factory = factory)
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val superUserViewModel: SuperUserViewModel = viewModel(factory = factory)
    val moduleViewModel: ModuleViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val superUserState by superUserViewModel.state.collectAsStateWithLifecycle()
    val moduleState by moduleViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var logPayload by remember { mutableStateOf(LogPayload("日志", "")) }
    var pendingRunOnce by remember { mutableStateOf(false) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var missingAppListPermission by remember { mutableStateOf(!GetAppListPermissionHelper.getPermissions(activity)) }

    val modulePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) moduleViewModel.installUri(uri, pendingRunOnce)
    }
    val storageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasStorageAccess(context)) pendingStorageAction?.invoke()
        else scope.launch { snackbarHostState.showSnackbar("未授予存储访问权限") }
        pendingStorageAction = null
    }
    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pendingStorageAction?.invoke()
        else scope.launch { snackbarHostState.showSnackbar("未授予存储访问权限") }
        pendingStorageAction = null
    }

    fun withStorageAccess(action: () -> Unit) {
        if (hasStorageAccess(context)) {
            action()
            return
        }
        pendingStorageAction = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageSettings.launch(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } else {
            legacyStoragePermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    LaunchedEffect(mainState.activeRootKey) {
        homeViewModel.setRootKey(mainState.activeRootKey)
        superUserViewModel.setRootKey(mainState.activeRootKey)
        moduleViewModel.setRootKey(mainState.activeRootKey)
        settingsViewModel.setRootKey(mainState.activeRootKey)
    }

    LaunchedEffect(Unit) {
        application.container.events.events.collect { effect ->
            when (effect) {
                is UiEffect.Snackbar -> snackbarHostState.showSnackbar(effect.message)
                is UiEffect.OpenUrl -> UrlIntentUtils.openUrl(context, effect.url)
                is UiEffect.CopyText -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SKRoot", effect.text))
                    snackbarHostState.showSnackbar(effect.confirmation)
                }
                is UiEffect.ShowLog -> {
                    logPayload = LogPayload(effect.title, effect.content)
                    if (navController.currentDestination?.route != "log") navController.navigate("log")
                }
                is UiEffect.PickModule -> {
                    pendingRunOnce = effect.runOnce
                    modulePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                UiEffect.RequestStorageAccess -> withStorageAccess {}
                UiEffect.FinishActivity -> activity.finish()
            }
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            AdaptiveMainScreen(
                selectedPage = mainState.selectedPage,
                onPageSelected = mainViewModel::selectPage,
                snackbarHostState = snackbarHostState,
                home = {
                    HomeScreen(
                        state = homeState,
                        bottomPadding = it,
                        onConfigureRoot = mainViewModel::showRootConfig,
                        onRefresh = homeViewModel::refresh,
                        onInstall = homeViewModel::install,
                        onUninstall = homeViewModel::uninstall,
                        onTestRoot = homeViewModel::testRoot,
                        onRunCommand = homeViewModel::runCommand,
                        onCopyConsole = homeViewModel::copyConsole,
                        onClearConsole = homeViewModel::clearConsole,
                        onReboot = settingsViewModel::reboot,
                    )
                },
                superUser = {
                    SuperUserScreen(
                        state = superUserState,
                        bottomPadding = it,
                        onRefresh = superUserViewModel::refresh,
                        onSearch = superUserViewModel::setQuery,
                        onShowPicker = superUserViewModel::showPicker,
                        onHidePicker = superUserViewModel::hidePicker,
                        onPickerSearch = superUserViewModel::setPickerQuery,
                        onFilterSystem = { value -> superUserViewModel.setFilters(system = value) },
                        onFilterThirdParty = { value -> superUserViewModel.setFilters(thirdParty = value) },
                        onAdd = superUserViewModel::add,
                        onAddAdb = superUserViewModel::addAdb,
                        onRemove = superUserViewModel::remove,
                        onClear = superUserViewModel::clear,
                    )
                },
                modules = {
                    ModuleScreen(
                        state = moduleState,
                        bottomPadding = it,
                        onSelectTab = moduleViewModel::selectTab,
                        onRefreshInstalled = moduleViewModel::refreshInstalled,
                        onRefreshMarket = moduleViewModel::refreshMarket,
                        onPickModule = moduleViewModel::requestPick,
                        onOpenGuide = { application.container.events.emit(UiEffect.OpenUrl("https://abcz316.github.io/SKRoot-linuxKernelRoot/skroot_pro_app/module_developer_help.pdf")) },
                        onMarketQuery = moduleViewModel::setMarketQuery,
                        onRemove = moduleViewModel::remove,
                        onDetails = moduleViewModel::showDetails,
                        onWebUi = moduleViewModel::openWebUi,
                        onCheckUpdate = { moduleViewModel.checkUpdate(it) },
                        onChangelog = moduleViewModel::showChangelog,
                        onDownloadUpdate = moduleViewModel::downloadUpdate,
                        onDownloadMarket = moduleViewModel::downloadMarket,
                        onOpenUrl = { application.container.events.emit(UiEffect.OpenUrl(it)) },
                        onCancelDownload = moduleViewModel::cancelDownload,
                    )
                },
                settings = {
                    SettingsScreen(
                        state = settingsState,
                        bottomPadding = it,
                        onRefresh = settingsViewModel::refresh,
                        onBootFailChange = settingsViewModel::setBootFail,
                        onAdbChange = settingsViewModel::setAdbDisabled,
                        onLogChange = settingsViewModel::setLogEnabled,
                        onBasicTest = settingsViewModel::testBasic,
                        onModuleTest = settingsViewModel::testDefaultModule,
                        onShowLog = settingsViewModel::showLog,
                        onClearLog = settingsViewModel::clearLog,
                        onReboot = settingsViewModel::reboot,
                        onOpenUrl = settingsViewModel::openUrl,
                        onShowChangelog = settingsViewModel::showAppChangelog,
                    )
                },
            )
        }
        composable("log") {
            LogScreen(
                title = logPayload.title,
                content = logPayload.content,
                onBack = { navController.popBackStack() },
                onCopy = { application.container.events.emit(UiEffect.CopyText(logPayload.content)) },
                onExport = {
                    withStorageAccess {
                        val file = FileUtils.makeSdcardLogFile("skroot_log_", ".txt")
                        FileUtils.writeTextAsync(activity, file, logPayload.content, true) { ok, out, error ->
                            scope.launch { snackbarHostState.showSnackbar(if (ok) "已导出至 ${out.absolutePath}" else "导出失败：$error") }
                        }
                    }
                },
            )
        }
    }

    if (mainState.rootConfig.visible) {
        RootConfigDialog(
            state = mainState.rootConfig,
            onDismiss = mainViewModel::dismissRootConfig,
            onRootKeyChange = mainViewModel::updateRootKey,
            onModeChange = mainViewModel::updateMode,
            onImport = { withStorageAccess(mainViewModel::importHotloadFile) },
            onExport = { withStorageAccess(mainViewModel::exportHotloadFile) },
            onConfirm = mainViewModel::saveRootConfig,
        )
    } else if (mainState.rootConfig.busy) {
        BusyDialog("正在加载热启动补丁，预计需要 1 分钟…")
    }

    if (missingAppListPermission) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.Apps, null) },
            title = { Text("需要应用列表权限") },
            text = { Text("请授予读取应用列表权限，然后重新打开管理器。") },
            confirmButton = { Button(onClick = { missingAppListPermission = false; activity.finish() }) { Text("确定") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdaptiveMainScreen(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    home: @Composable (PaddingValues) -> Unit,
    superUser: @Composable (PaddingValues) -> Unit,
    modules: @Composable (PaddingValues) -> Unit,
    settings: @Composable (PaddingValues) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val latestSelectedPage by rememberUpdatedState(selectedPage)
    val latestOnPageSelected by rememberUpdatedState(onPageSelected)
    var navigationPage by remember { mutableIntStateOf(selectedPage) }
    var programmaticTarget by remember { mutableStateOf<Int?>(null) }
    var navigationJob by remember { mutableStateOf<Job?>(null) }

    fun navigateToPage(requestedPage: Int) {
        val page = requestedPage.coerceIn(0, 3)
        if (programmaticTarget == page) return

        // A new tab click supersedes the previous animation. Keeping an explicit target
        // prevents the cancelled animation's intermediate settledPage from feeding back
        // into MainViewModel and starting an animation in the opposite direction.
        navigationJob?.cancel()
        programmaticTarget = page
        navigationPage = page
        navigationJob = scope.launch {
            try {
                pagerState.animateScrollToPage(
                    page = page,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                )
            } finally {
                // A newer click owns the pager now; its job will perform the final sync.
                if (programmaticTarget == page) {
                    programmaticTarget = null
                    val settled = pagerState.settledPage
                    navigationPage = settled
                    if (latestSelectedPage != settled) latestOnPageSelected(settled)
                }
            }
        }
    }

    LaunchedEffect(selectedPage) {
        if (selectedPage != navigationPage && selectedPage != programmaticTarget) {
            navigateToPage(selectedPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                // User swipes update the selected navigation item only after settling.
                // Intermediate pages from a programmatic animation are deliberately ignored.
                if (programmaticTarget == null) {
                    navigationPage = page
                    if (latestSelectedPage != page) latestOnPageSelected(page)
                }
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            navigationItems.forEachIndexed { index, item ->
                                NavigationRailItem(
                                    selected = navigationPage == index,
                                    onClick = { navigateToPage(index) },
                                    icon = { Icon(if (navigationPage == index) item.selectedIcon else item.icon, item.label) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        beyondViewportPageCount = 3,
                    ) { page ->
                        MainPage(page, PaddingValues(0.dp), home, superUser, modules, settings)
                    }
                }
                // Keep the Snackbar in the content layer and anchor it explicitly. Without
                // this alignment a standalone SnackbarHost defaults to the top-left on tablets.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
                )
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        navigationItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = navigationPage == index,
                                onClick = { navigateToPage(index) },
                                icon = { Icon(if (navigationPage == index) item.selectedIcon else item.icon, item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) { outerPadding ->
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 3) { page ->
                    MainPage(page, outerPadding, home, superUser, modules, settings)
                }
            }
        }
    }
}

@Composable
private fun MainPage(
    page: Int,
    bottomPadding: PaddingValues,
    home: @Composable (PaddingValues) -> Unit,
    superUser: @Composable (PaddingValues) -> Unit,
    modules: @Composable (PaddingValues) -> Unit,
    settings: @Composable (PaddingValues) -> Unit,
) = when (page) {
    0 -> home(bottomPadding)
    1 -> superUser(bottomPadding)
    2 -> modules(bottomPadding)
    else -> settings(bottomPadding)
}

private fun hasStorageAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    else -> true
}
