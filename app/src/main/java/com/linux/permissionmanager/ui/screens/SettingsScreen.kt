package com.linux.permissionmanager.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.linux.permissionmanager.data.AppearanceSettings
import com.linux.permissionmanager.data.PaletteId
import com.linux.permissionmanager.ui.SettingsUiState
import com.linux.permissionmanager.ui.components.*
import com.linux.permissionmanager.ui.theme.AppearanceTokens

private data class Choice(val title: String, val value: String)
private data class RebootChoice(val title: String, val command: String? = null, val soft: Boolean = false)

private val basicChoices = listOf(
    Choice("通道检查", "Channel"),
    Choice("内核起始地址检查", "KernelBase"),
    Choice("写入内存测试", "WriteTest"),
    Choice("读取跳板测试", "ReadTrampoline"),
    Choice("写入跳板测试", "WriteTrampoline"),
    Choice("物理地址计算测试", "PhysAddrCalc"),
)
private val moduleChoices = listOf(
    Choice("Root 权限模块（打印）", "RootBridgePrint"),
    Choice("Root 权限模块（执行）", "RootBridgeExec"),
    Choice("su 重定向模块（打印）", "SuRedirectPrint"),
    Choice("su 重定向模块（执行）", "SuRedirectExec"),
    Choice("系统目录净化模块（打印）", "TombstonesPurgePrint"),
)
private val rebootChoices = listOf(
    RebootChoice("普通重启", "setprop sys.powerctl reboot"),
    RebootChoice("软重启", soft = true),
    RebootChoice("重启到 Recovery", "setprop sys.powerctl reboot,recovery"),
    RebootChoice("重启到 Fastboot", "setprop sys.powerctl reboot,bootloader"),
    RebootChoice("重启到 FastbootD", "setprop sys.powerctl reboot,fastboot"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    appearance: AppearanceSettings,
    bottomPadding: PaddingValues,
    onRefresh: () -> Unit,
    onBootFailChange: (Boolean) -> Unit,
    onAdbChange: (Boolean) -> Unit,
    onLogChange: (Boolean) -> Unit,
    onBasicTest: (String) -> Unit,
    onModuleTest: (String) -> Unit,
    onShowLog: () -> Unit,
    onClearLog: () -> Unit,
    onReboot: (String?, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowChangelog: () -> Unit,
    onPaletteChange: (PaletteId) -> Unit,
    onPickBackground: () -> Unit,
    onBackgroundAlphaChange: (Float) -> Unit,
    onClearBackground: () -> Unit,
    onResetAppearance: () -> Unit,
    onOpenLocalCustomizer: () -> Unit,
) {
    var basicDialog by remember { mutableStateOf(false) }
    var moduleDialog by remember { mutableStateOf(false) }
    var rebootDialog by remember { mutableStateOf(false) }
    var pendingReboot by remember { mutableStateOf<RebootChoice?>(null) }
    var clearLogDialog by remember { mutableStateOf(false) }
    var resetAppearanceDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("设置")
                        Text("环境保护、诊断与管理器信息", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "刷新") } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppearanceTokens.navigationSurfaceAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppearanceTokens.navigationSurfaceAlpha),
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppearanceTokens.pageSurfaceAlpha),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding() + 8.dp, 16.dp, bottomPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.loading) item { LoadingState("正在读取设置…") }
            state.error?.let { item { ErrorState(it, onRefresh) } }
            if (!state.loading) {
                item {
                    SegmentedGroup("外观") {
                        Text(
                            "主题配色",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PaletteId.values().forEach { palette ->
                                PaletteOption(
                                    palette = palette,
                                    selected = appearance.palette == palette,
                                    onClick = { onPaletteChange(palette) },
                                )
                            }
                        }
                        SegmentedItem(
                            title = "背景图片",
                            summary = appearance.backgroundUri?.let { backgroundName(it) } ?: "使用纯色背景",
                            icon = Icons.Outlined.Image,
                            onClick = onPickBackground,
                            trailing = { Icon(Icons.Outlined.ChevronRight, null) },
                        )
                        if (appearance.backgroundEnabled) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("背景透明度", style = MaterialTheme.typography.bodyMedium)
                                    Text("${(appearance.backgroundAlpha * 100).toInt()}%", color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = appearance.backgroundAlpha,
                                    onValueChange = onBackgroundAlphaChange,
                                    valueRange = 0f..0.6f,
                                    steps = 11,
                                )
                                Text(
                                    "透明度越高，图片越明显；页面会保留浅色遮罩以保证可读性。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SegmentedItem(
                                title = "清除背景图片",
                                summary = "恢复纯色背景",
                                icon = Icons.Outlined.ImageNotSupported,
                                onClick = onClearBackground,
                                trailing = { Icon(Icons.Outlined.ChevronRight, null) },
                            )
                        }
                        SegmentedItem(
                            title = "恢复默认外观",
                            summary = "靛蓝紫、纯白背景、关闭自定义图片",
                            icon = Icons.Outlined.Restore,
                            onClick = { resetAppearanceDialog = true },
                            trailing = { Icon(Icons.Outlined.ChevronRight, null) },
                        )
                    }
                }
                item {
                    SegmentedGroup("环境保护") {
                        SegmentedSwitchItem(
                            title = "开机失败保护",
                            summary = "在环境异常时提供启动保护",
                            icon = Icons.Outlined.HealthAndSafety,
                            checked = state.bootFailProtect,
                            enabled = state.busyItem == null,
                            onCheckedChange = onBootFailChange,
                        )
                        SegmentedSwitchItem(
                            title = "强制关闭 ADB",
                            summary = "由 SKRoot 环境控制 ADB 状态",
                            icon = Icons.Outlined.DeveloperMode,
                            checked = state.adbForcedDisabled,
                            enabled = state.busyItem == null,
                            onCheckedChange = onAdbChange,
                        )
                    }
                }
                item {
                    SegmentedGroup("管理器") {
                        SegmentedItem(
                            title = "本地定制管理器",
                            summary = "在设备内定制包名、名称与应用图标",
                            icon = Icons.Outlined.Tune,
                            onClick = onOpenLocalCustomizer,
                            trailing = { Icon(Icons.Outlined.ChevronRight, null) },
                        )
                    }
                }
                item {
                    SegmentedGroup("日志") {
                        SegmentedSwitchItem(
                            title = "详细日志",
                            summary = "记录更多核心运行信息",
                            icon = Icons.Outlined.BugReport,
                            checked = state.logEnabled,
                            enabled = state.busyItem == null,
                            onCheckedChange = onLogChange,
                        )
                        SegmentedItem("查看日志", "打开可复制、可导出的日志查看器", Icons.Outlined.Article, onClick = onShowLog, trailing = { Icon(Icons.Outlined.ChevronRight, null) })
                        SegmentedItem("清理日志", "删除当前 SKRoot 日志", Icons.Outlined.DeleteSweep, onClick = { clearLogDialog = true }, trailing = { Icon(Icons.Outlined.ChevronRight, null) })
                    }
                }
                item {
                    SegmentedGroup("核心诊断") {
                        SegmentedItem("单项检查", "检查通道、内核地址与跳板", Icons.Outlined.FactCheck, onClick = { basicDialog = true }, trailing = { Icon(Icons.Outlined.ChevronRight, null) })
                        SegmentedItem("核心模块检查", "运行默认模块打印或执行测试", Icons.Outlined.Extension, onClick = { moduleDialog = true }, trailing = { Icon(Icons.Outlined.ChevronRight, null) })
                    }
                }
                item {
                    SegmentedGroup("快捷工具") {
                        SegmentedItem("重启选项", "普通重启、软重启、Recovery 与 Fastboot", Icons.Outlined.RestartAlt, onClick = { rebootDialog = true }, trailing = { Icon(Icons.Outlined.ChevronRight, null) })
                    }
                }
                state.update?.takeIf { it.hasNewVersion }?.let { update ->
                    item {
                        TonalCard(color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("发现新版本 ${update.latestVersion}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("可查看更新日志或前往下载页。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onShowChangelog) { Text("更新日志") }
                                    Button(onClick = { onOpenUrl(update.downloadUrl) }) { Text("下载") }
                                }
                            }
                        }
                    }
                }
                item {
                    SegmentedGroup("更新与关于") {
                        SegmentedItem("内置核心版本", state.sdkVersion, Icons.Outlined.Memory)
                        SegmentedItem("SKRoot 模块开发指南", "PDF 文档", Icons.Outlined.MenuBook, onClick = { onOpenUrl("https://abcz316.github.io/SKRoot-linuxKernelRoot/skroot_pro_app/module_developer_help.pdf") }, trailing = { Icon(Icons.Outlined.OpenInNew, null) })
                        SegmentedItem("GitHub", "github.com/abcz316/SKRoot-linuxKernelRoot", Icons.Outlined.Code, onClick = { onOpenUrl("https://github.com/abcz316/SKRoot-linuxKernelRoot") }, trailing = { Icon(Icons.Outlined.OpenInNew, null) })
                        SegmentedItem("Telegram", "t.me/skrootabc", Icons.Outlined.Send, onClick = { onOpenUrl("https://t.me/skrootabc") }, trailing = { Icon(Icons.Outlined.OpenInNew, null) })
                    }
                }
                item {
                    Text(
                        "管理器卸载后，已安装的 SKRoot 环境和模块仍然有效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (basicDialog) ChoiceDialog("选择单项检查", basicChoices, { basicDialog = false }) { basicDialog = false; onBasicTest(it.value) }
    if (moduleDialog) ChoiceDialog("选择核心模块检查", moduleChoices, { moduleDialog = false }) { moduleDialog = false; onModuleTest(it.value) }
    if (rebootDialog) {
        AlertDialog(
            onDismissRequest = { rebootDialog = false },
            icon = { Icon(Icons.Outlined.RestartAlt, null) },
            title = { Text("重启选项") },
            text = {
                Column { rebootChoices.forEach { choice -> TextButton(onClick = { rebootDialog = false; pendingReboot = choice }, modifier = Modifier.fillMaxWidth()) { Text(choice.title, modifier = Modifier.fillMaxWidth()) } } }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { rebootDialog = false }) { Text("取消") } },
        )
    }
    pendingReboot?.let { choice ->
        AlertDialog(
            onDismissRequest = { pendingReboot = null },
            title = { Text("确认重启？") },
            text = { Text("确定要${choice.title}吗？") },
            confirmButton = {
                Button(
                    onClick = { pendingReboot = null; onReboot(choice.command, choice.soft) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingReboot = null }) { Text("取消") } },
        )
    }
    if (clearLogDialog) {
        AlertDialog(
            onDismissRequest = { clearLogDialog = false },
            title = { Text("清理日志？") },
            text = { Text("当前 SKRoot 日志将被删除。") },
            confirmButton = { Button(onClick = { clearLogDialog = false; onClearLog() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("清理") } },
            dismissButton = { TextButton(onClick = { clearLogDialog = false }) { Text("取消") } },
        )
    }
    if (resetAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { resetAppearanceDialog = false },
            icon = { Icon(Icons.Outlined.Restore, null) },
            title = { Text("恢复默认外观？") },
            text = { Text("将清除主题选择和背景图片，不影响 Root Key、授权及模块数据。") },
            confirmButton = {
                Button(onClick = { resetAppearanceDialog = false; onResetAppearance() }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { resetAppearanceDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PaletteOption(
    palette: PaletteId,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(28.dp).background(Color(palette.previewPrimary)))
                Box(Modifier.size(28.dp).background(Color(palette.previewContainer)))
            }
            Text(palette.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(palette.summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (selected) Text("已选择", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun backgroundName(value: String): String {
    val path = runCatching { Uri.parse(value).lastPathSegment }.getOrNull().orEmpty()
    return if (path.isBlank()) "已选择背景图片" else "已选择：${Uri.decode(path).takeLast(48)}"
}

@Composable
private fun ChoiceDialog(title: String, choices: List<Choice>, onDismiss: () -> Unit, onSelect: (Choice) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column { choices.forEach { choice -> TextButton(onClick = { onSelect(choice) }, modifier = Modifier.fillMaxWidth()) { Text(choice.title, modifier = Modifier.fillMaxWidth()) } } }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
