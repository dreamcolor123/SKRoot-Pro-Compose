package com.linux.permissionmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.linux.permissionmanager.BuildConfig
import com.linux.permissionmanager.data.EnvironmentState
import com.linux.permissionmanager.ui.HomeUiState
import com.linux.permissionmanager.ui.components.*
import com.linux.permissionmanager.ui.theme.LocalSemanticColors
import com.linux.permissionmanager.ui.theme.AppearanceTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    bottomPadding: PaddingValues,
    onConfigureRoot: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onTestRoot: () -> Unit,
    onRunCommand: (String) -> Unit,
    onCopyConsole: () -> Unit,
    onClearConsole: () -> Unit,
    onReboot: (String?, Boolean) -> Unit,
) {
    var commandDialog by remember { mutableStateOf(false) }
    var uninstallDialog by remember { mutableStateOf(false) }
    var rebootDialog by remember { mutableStateOf(false) }
    var pendingReboot by remember { mutableStateOf<RebootOption?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val semantic = LocalSemanticColors.current

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(BuildConfig.PUBLISH_APP_NAME)
                        Text("环境与系统状态", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                    actions = {
                        IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "刷新") }
                        IconButton(onClick = { rebootDialog = true }) { Icon(Icons.Outlined.PowerSettingsNew, "重启选项") }
                        IconButton(onClick = onConfigureRoot) { Icon(Icons.Outlined.Key, "Root Key") }
                    },
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
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (state.loading) item { LoadingState("正在检测 SKRoot 环境…") }
            state.error?.let { message -> item { ErrorState(message, onRefresh) } }
            if (!state.loading) {
                item {
                    val (icon, title, summary) = when (state.environment.state) {
                        EnvironmentState.RUNNING -> StatusVisual(
                            Icons.Outlined.CheckCircle, "正常运行",
                            "SKRoot 环境已就绪",
                        )
                        EnvironmentState.OUTDATED -> StatusVisual(
                            Icons.Outlined.Update, "核心版本过低",
                            "当前核心低于 SDK 版本，请更新环境",
                        )
                        EnvironmentState.PENDING_REBOOT -> StatusVisual(
                            Icons.Outlined.RestartAlt, "等待重启",
                            "环境更新已写入，重启设备后生效",
                        )
                        EnvironmentState.NOT_INSTALLED -> StatusVisual(
                            Icons.Outlined.WarningAmber, "环境未安装",
                            "安装后即可管理授权与模块",
                        )
                        EnvironmentState.FAULT -> StatusVisual(
                            Icons.Outlined.ErrorOutline, "运行异常",
                            "请检查日志或重新安装环境",
                        )
                        else -> StatusVisual(
                            Icons.Outlined.HelpOutline, "状态未知",
                            state.environment.rawState.ifBlank { "未返回有效环境状态" },
                        )
                    }
                    val showEnvironmentActionsInStatus = state.environment.state != EnvironmentState.RUNNING
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AppearanceTokens.cardSurfaceAlpha),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Icon(icon, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleLarge)
                                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusTag(
                                    if (state.environment.hotload) "热启动" else "Boot",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusTag(
                                    "核心 ${state.environment.installedVersion}",
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                StatusTag(
                                    "SDK ${state.environment.sdkVersion}",
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (showEnvironmentActionsInStatus) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onInstall,
                                        enabled = state.busyAction == null && state.environment.state != EnvironmentState.PENDING_REBOOT,
                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    ) {
                                        Icon(Icons.Outlined.SystemUpdateAlt, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            when (state.environment.state) {
                                                EnvironmentState.OUTDATED -> "更新环境"
                                                EnvironmentState.PENDING_REBOOT -> "等待重启"
                                                else -> "安装环境"
                                            },
                                            maxLines = 1,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { uninstallDialog = true },
                                        enabled = state.busyAction == null,
                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .55f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("卸载环境", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                item { SectionTitle("系统状态") }
                item {
                    val rows = listOf(
                        SystemRow(Icons.Outlined.Security, "SELinux", selinuxText(state.system.selinux), state.system.selinux != 0),
                        SystemRow(Icons.Outlined.FilterAlt, "Seccomp", seccompText(state.system.seccomp), state.system.seccomp == 2),
                        SystemRow(Icons.Outlined.DeveloperMode, "ADB", if (state.system.adbEnabled) "已开启" else "未开启", !state.system.adbEnabled),
                        SystemRow(Icons.Outlined.VerifiedUser, "OPlus 接口", if (state.system.oplusIntercepted) "已拦截" else "无需拦截", true),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        rows.forEach { row ->
                            SegmentedItem(
                                title = row.title,
                                summary = row.value,
                                icon = row.icon,
                                trailing = {
                                    Icon(
                                        if (row.good) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                        null,
                                        tint = if (row.good) MaterialTheme.colorScheme.primary else semantic.warning,
                                    )
                                },
                            )
                        }
                    }
                }

                item { SectionTitle("基础操作") }
                if (state.environment.state == EnvironmentState.RUNNING) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TonalCard(
                                modifier = Modifier.weight(1f),
                                enabled = state.busyAction == null,
                                onClick = onInstall,
                            ) {
                                QuickAction(Icons.Outlined.SystemUpdateAlt, "安装环境", "重新安装或更新")
                            }
                            TonalCard(
                                modifier = Modifier.weight(1f),
                                enabled = state.busyAction == null,
                                onClick = { uninstallDialog = true },
                            ) {
                                QuickAction(Icons.Outlined.DeleteOutline, "卸载环境", "移除环境与模块")
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TonalCard(Modifier.weight(1f), onClick = onTestRoot) {
                            QuickAction(Icons.Outlined.AdminPanelSettings, "测试 Root", "验证当前 Key")
                        }
                        TonalCard(Modifier.weight(1f), onClick = { commandDialog = true }) {
                            QuickAction(Icons.Outlined.Terminal, "Root 命令", "执行自定义命令")
                        }
                    }
                }
                item { SectionTitle("输出信息") }
                item { ConsoleCard(state.console, onCopyConsole, onClearConsole) }
            }
        }
    }

    if (commandDialog) {
        var command by remember { mutableStateOf("id") }
        AlertDialog(
            onDismissRequest = { commandDialog = false },
            icon = { Icon(Icons.Outlined.Terminal, null) },
            title = { Text("执行 Root 命令") },
            text = {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("命令") },
                    singleLine = true,
                )
            },
            confirmButton = { Button(onClick = { commandDialog = false; onRunCommand(command) }) { Text("执行") } },
            dismissButton = { TextButton(onClick = { commandDialog = false }) { Text("取消") } },
        )
    }
    if (uninstallDialog) {
        AlertDialog(
            onDismissRequest = { uninstallDialog = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text("卸载 SKRoot 环境？") },
            text = { Text("这会同时清空 SU 授权列表并删除已安装模块。") },
            confirmButton = {
                Button(
                    onClick = { uninstallDialog = false; onUninstall() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("确认卸载") }
            },
            dismissButton = { TextButton(onClick = { uninstallDialog = false }) { Text("取消") } },
        )
    }
    if (rebootDialog) {
        RebootOptionsDialog(
            onDismiss = { rebootDialog = false },
            onSelect = {
                rebootDialog = false
                pendingReboot = it
            },
        )
    }
    pendingReboot?.let { option ->
        RebootConfirmDialog(
            option = option,
            onDismiss = { pendingReboot = null },
            onConfirm = {
                pendingReboot = null
                onReboot(option.command, option.soft)
            },
        )
    }
}

private data class StatusVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val summary: String,
)
private data class SystemRow(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val value: String,
    val good: Boolean,
)

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 部分 ROM 会限制普通应用读取 /sys/fs/selinux/enforce，Native 此时返回 -1。
// Android 8+ 的生产设备默认处于 Enforcing；沿用旧版页面语义，仅明确的 0 显示宽容模式。
private fun selinuxText(value: Int) = if (value == 0) "宽容模式" else "严格模式"
private fun seccompText(value: Int) = when (value) { 0 -> "未开启"; 1 -> "严格模式"; 2 -> "过滤模式"; else -> "未知" }
