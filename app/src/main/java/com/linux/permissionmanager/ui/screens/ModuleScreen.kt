package com.linux.permissionmanager.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.linux.permissionmanager.data.DownloadProgress
import com.linux.permissionmanager.data.InstalledModule
import com.linux.permissionmanager.data.MarketModule
import com.linux.permissionmanager.data.ModuleRunState
import com.linux.permissionmanager.ui.ModuleUiState
import com.linux.permissionmanager.ui.components.*
import com.linux.permissionmanager.ui.theme.LocalSemanticColors
import com.linux.permissionmanager.ui.theme.AppearanceTokens
import com.linux.permissionmanager.ui.theme.LocalChromeSurfaceAlpha
import com.linux.permissionmanager.ui.theme.LocalContentDrawsBehindNavigation
import com.linux.permissionmanager.utils.ModuleWebUiShortcutRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ModuleActionIconSize = 18.dp
private val ModuleMenuIconSize = 20.dp
private val ModuleTabIconSize = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(
    state: ModuleUiState,
    bottomPadding: PaddingValues,
    onSelectTab: (Int) -> Unit,
    onRefreshInstalled: () -> Unit,
    onRefreshMarket: () -> Unit,
    onPickModule: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    onMarketQuery: (String) -> Unit,
    onRemove: (InstalledModule) -> Unit,
    onDetails: (InstalledModule) -> Unit,
    onWebUi: (InstalledModule) -> Unit,
    onCreateWebUiShortcut: (ModuleWebUiShortcutRequest) -> Unit,
    onCheckUpdate: (InstalledModule) -> Unit,
    onChangelog: (InstalledModule) -> Unit,
    onDownloadUpdate: (InstalledModule) -> Unit,
    onDownloadMarket: (MarketModule) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCancelDownload: () -> Unit,
) {
    var topMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InstalledModule?>(null) }
    var pendingUpdate by remember { mutableStateOf<InstalledModule?>(null) }
    var pendingMarket by remember { mutableStateOf<MarketModule?>(null) }
    var pendingShortcut by remember { mutableStateOf<InstalledModule?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text("模块")
                            Text("管理已安装模块并浏览模块市场", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (state.selectedTab == 0) onRefreshInstalled() else onRefreshMarket() }) {
                            Icon(Icons.Outlined.Refresh, "刷新")
                        }
                        Box {
                            IconButton(onClick = { topMenu = true }) { Icon(Icons.Outlined.Add, "安装模块") }
                            DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("从 ZIP 安装") },
                                    leadingIcon = { Icon(Icons.Outlined.FolderZip, null, Modifier.size(ModuleMenuIconSize)) },
                                    onClick = { topMenu = false; onPickModule(false) },
                                )
                                DropdownMenuItem(
                                    text = { Text("单次试运行") },
                                    leadingIcon = { Icon(Icons.Outlined.Science, null, Modifier.size(ModuleMenuIconSize)) },
                                    onClick = { topMenu = false; onPickModule(true) },
                                )
                                DropdownMenuItem(
                                    text = { Text("模块开发指南") },
                                    leadingIcon = { Icon(Icons.Outlined.MenuBook, null, Modifier.size(ModuleMenuIconSize)) },
                                    onClick = { topMenu = false; onOpenGuide() },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalChromeSurfaceAlpha.current),
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalChromeSurfaceAlpha.current),
                    ),
                )
                PrimaryTabRow(selectedTabIndex = state.selectedTab, containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalChromeSurfaceAlpha.current)) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { onSelectTab(0) },
                        text = { Text("已安装") },
                        icon = { Icon(Icons.Outlined.Extension, null, Modifier.size(ModuleTabIconSize)) },
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { onSelectTab(1) },
                        text = { Text("模块市场") },
                        icon = { Icon(Icons.Outlined.Storefront, null, Modifier.size(ModuleTabIconSize)) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppearanceTokens.pageSurfaceAlpha),
    ) { innerPadding ->
        if (state.selectedTab == 0) {
            InstalledModules(
                state, innerPadding, bottomPadding, scrollBehavior,
                onRefreshInstalled, { pendingDelete = it }, onDetails, onWebUi,
                { pendingShortcut = it }, onCheckUpdate, onChangelog, { pendingUpdate = it },
            )
        } else {
            MarketModules(
                state, innerPadding, bottomPadding, scrollBehavior,
                onRefreshMarket, onMarketQuery, { pendingMarket = it }, onOpenUrl,
            )
        }
    }

    pendingDelete?.let { module ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text("删除模块？") },
            text = { Text("确定删除 ${module.name} 吗？重启后生效。") },
            confirmButton = {
                Button(
                    onClick = { pendingDelete = null; onRemove(module) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
    pendingUpdate?.let { module ->
        val update = module.update
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            icon = { Icon(Icons.Outlined.SystemUpdate, null) },
            title = { Text("更新 ${module.name}？") },
            text = { Text("检测到新版本 ${update?.latestVersion.orEmpty()}，是否下载并安装？") },
            confirmButton = { Button(onClick = { pendingUpdate = null; onDownloadUpdate(module) }) { Text("立即更新") } },
            dismissButton = { TextButton(onClick = { pendingUpdate = null }) { Text("取消") } },
        )
    }
    pendingMarket?.let { module ->
        AlertDialog(
            onDismissRequest = { pendingMarket = null },
            icon = { Icon(Icons.Outlined.Download, null) },
            title = { Text("安装 ${module.displayName}？") },
            text = { Text(module.chineseAlert.ifBlank { "将下载模块 ZIP 并自动安装。" }) },
            confirmButton = { Button(onClick = { pendingMarket = null; onDownloadMarket(module) }) { Text("下载并安装") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingMarket = null }) { Text("取消") }
                    TextButton(onClick = { pendingMarket = null; onOpenUrl(module.downloadUrl) }) { Text("浏览器下载") }
                }
            },
        )
    }
    pendingShortcut?.let { module ->
        WebUiShortcutDialog(
            module = module,
            onDismiss = { pendingShortcut = null },
            onConfirm = { name, iconUri ->
                pendingShortcut = null
                onCreateWebUiShortcut(
                    ModuleWebUiShortcutRequest(
                        moduleId = module.id,
                        moduleName = module.name,
                        shortcutName = name,
                        iconUri = iconUri,
                    ),
                )
            },
        )
    }
    state.download?.let { DownloadProgressDialog(it, onCancelDownload) }
}

@Composable
private fun WebUiShortcutDialog(
    module: InstalledModule,
    onDismiss: () -> Unit,
    onConfirm: (String, Uri?) -> Unit,
) {
    var shortcutName by remember(module.id) {
        mutableStateOf("${module.name.ifBlank { module.id }} WebUI")
    }
    var iconUri by remember(module.id) { mutableStateOf<Uri?>(null) }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) iconUri = uri
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.AddToHomeScreen, null) },
        title = { Text("创建 WebUI 桌面快捷方式") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = shortcutName,
                    onValueChange = { shortcutName = it.take(25) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("快捷方式名称") },
                    singleLine = true,
                    supportingText = { Text("最多 25 个字符") },
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShortcutIconPreview(iconUri, Modifier.size(58.dp))
                        Column(Modifier.weight(1f)) {
                            Text("桌面图标", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (iconUri == null) "使用当前应用图标" else "已选择自定义图标",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { iconPicker.launch(arrayOf("image/*")) }) {
                            Text(if (iconUri == null) "选择" else "更换")
                        }
                    }
                }
                if (iconUri != null) {
                    TextButton(
                        onClick = { iconUri = null },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("使用默认图标") }
                }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Outlined.Visibility, null, Modifier.size(20.dp))
                        Text(
                            "快捷方式会在桌面和启动器记录中留下名称、图标及所属应用，可能降低管理器的隐藏性。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(shortcutName.trim(), iconUri) },
                enabled = shortcutName.isNotBlank(),
            ) { Text("继续创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ShortcutIconPreview(uri: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val defaultBitmap = remember(context.packageName) {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256) }.getOrNull()
    }
    var customBitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        customBitmap = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }
            }.getOrNull()
        }
    }
    val bitmap = customBitmap ?: defaultBitmap
    if (bitmap == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = if (uri == null) "默认快捷方式图标" else "自定义快捷方式图标",
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledModules(
    state: ModuleUiState,
    innerPadding: PaddingValues,
    bottomPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    onRetry: () -> Unit,
    onDelete: (InstalledModule) -> Unit,
    onDetails: (InstalledModule) -> Unit,
    onWebUi: (InstalledModule) -> Unit,
    onCreateWebUiShortcut: (InstalledModule) -> Unit,
    onCheckUpdate: (InstalledModule) -> Unit,
    onChangelog: (InstalledModule) -> Unit,
    onUpdate: (InstalledModule) -> Unit,
) {
    val drawsBehindNavigation = LocalContentDrawsBehindNavigation.current
    val navigationClearance = bottomPadding.calculateBottomPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding(),
                bottom = if (drawsBehindNavigation) 0.dp else navigationClearance,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 20.dp + if (drawsBehindNavigation) navigationClearance else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.installedLoading) item { LoadingState("正在读取已安装模块…") }
        state.installedError?.let { item { ErrorState(it, onRetry) } }
        if (!state.installedLoading && state.installed.isEmpty()) item {
            EmptyState("暂无已安装模块", "点击右上角从 ZIP 安装模块", icon = Icons.Outlined.ExtensionOff)
        }
        items(state.installed, key = { it.id }) { module ->
            InstalledModuleCard(module, onDelete, onDetails, onWebUi, onCreateWebUiShortcut, onCheckUpdate, onChangelog, onUpdate)
        }
    }
}

@Composable
private fun InstalledModuleCard(
    module: InstalledModule,
    onDelete: (InstalledModule) -> Unit,
    onDetails: (InstalledModule) -> Unit,
    onWebUi: (InstalledModule) -> Unit,
    onCreateWebUiShortcut: (InstalledModule) -> Unit,
    onCheckUpdate: (InstalledModule) -> Unit,
    onChangelog: (InstalledModule) -> Unit,
    onUpdate: (InstalledModule) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current
    val (label, color, onColor) = when (module.runState) {
        ModuleRunState.RUNNING -> Triple("运行中", semantic.successContainer, semantic.onSuccessContainer)
        ModuleRunState.ABNORMAL -> Triple("运行异常", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        ModuleRunState.REMOVED_PENDING_REBOOT -> Triple("待重启", semantic.infoContainer, semantic.onInfoContainer)
        ModuleRunState.NOT_RUNNING -> Triple("未启动", semantic.warningContainer, semantic.onWarningContainer)
    }
    TonalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(module.name.ifBlank { module.id }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("版本 ${module.version}  ·  ${module.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusTag(label, color, onColor)
            }
            if (module.description.isNotBlank()) {
                Text(module.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (module.hasWebUi) {
                    TextButton(onClick = { onWebUi(module) }) {
                        Icon(Icons.Outlined.Language, null, Modifier.size(ModuleActionIconSize))
                        Spacer(Modifier.width(6.dp))
                        Text("WebUI")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (module.update?.hasNewVersion == true) FilledTonalButton(onClick = { onUpdate(module) }) { Text("有新版") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("详情") }, leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(ModuleMenuIconSize)) }, onClick = { menu = false; onDetails(module) })
                        if (module.hasWebUi) {
                            DropdownMenuItem(
                                text = { Text("创建 WebUI 桌面快捷方式") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.AddToHomeScreen, null, Modifier.size(ModuleMenuIconSize)) },
                                onClick = { menu = false; onCreateWebUiShortcut(module) },
                            )
                        }
                        DropdownMenuItem(text = { Text("检查更新") }, leadingIcon = { Icon(Icons.Outlined.Update, null, Modifier.size(ModuleMenuIconSize)) }, onClick = { menu = false; onCheckUpdate(module) })
                        if (!module.update?.changelogUrl.isNullOrBlank()) DropdownMenuItem(text = { Text("更新日志") }, leadingIcon = { Icon(Icons.Outlined.Article, null, Modifier.size(ModuleMenuIconSize)) }, onClick = { menu = false; onChangelog(module) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(ModuleMenuIconSize), tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete(module) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketModules(
    state: ModuleUiState,
    innerPadding: PaddingValues,
    bottomPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    onRetry: () -> Unit,
    onQuery: (String) -> Unit,
    onDownload: (MarketModule) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val drawsBehindNavigation = LocalContentDrawsBehindNavigation.current
    val navigationClearance = bottomPadding.calculateBottomPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding(),
                bottom = if (drawsBehindNavigation) 0.dp else navigationClearance,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 20.dp + if (drawsBehindNavigation) navigationClearance else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.marketQuery,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (state.marketQuery.isNotBlank()) {{ IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "清除") } }} else null,
                placeholder = { Text("搜索模块、作者或关键词") },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
        if (state.marketLoading) item { LoadingState("正在加载模块市场…") }
        state.marketError?.let { item { ErrorState(it, onRetry) } }
        if (!state.marketLoading && state.filteredMarket.isEmpty()) item { EmptyState("没有匹配的模块", "尝试更换搜索关键词", icon = Icons.Outlined.SearchOff) }
        items(state.filteredMarket, key = { it.id }) { module ->
            TonalCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(module.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("版本 ${module.version}  ·  ${module.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (module.updateDate.isNotBlank()) StatusTag(module.updateDate, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(module.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (module.sourceUrl.isNotBlank()) TextButton(onClick = { onOpenUrl(module.sourceUrl) }) { Text("源代码") }
                        Spacer(Modifier.weight(1f))
                        if (module.isInstalled) {
                            StatusTag("已安装", LocalSemanticColors.current.successContainer, LocalSemanticColors.current.onSuccessContainer)
                        } else Button(onClick = { onDownload(module) }) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(ModuleActionIconSize))
                            Spacer(Modifier.width(6.dp))
                            Text("安装")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressDialog(progress: DownloadProgress, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.Downloading, null) },
        title = { Text(progress.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress.fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (progress.totalBytes > 0) "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}"
                    else "${formatBytes(progress.downloadedBytes)} · 大小未知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCancel, enabled = progress.cancellable) { Text("取消") } },
    )
}
