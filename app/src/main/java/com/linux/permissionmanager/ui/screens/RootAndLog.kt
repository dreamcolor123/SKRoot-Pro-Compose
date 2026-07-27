package com.linux.permissionmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.linux.permissionmanager.ui.RootConfigUiState
import com.linux.permissionmanager.ui.theme.AppearanceTokens
import com.linux.permissionmanager.ui.theme.LocalChromeSurfaceAlpha

data class RebootOption(
    val title: String,
    val command: String? = null,
    val soft: Boolean = false,
)

val RebootOptions = listOf(
    RebootOption("普通重启", "setprop sys.powerctl reboot"),
    RebootOption("软重启", soft = true),
    RebootOption("重启到 Recovery", "setprop sys.powerctl reboot,recovery"),
    RebootOption("重启到 Fastboot", "setprop sys.powerctl reboot,bootloader"),
    RebootOption("重启到 FastbootD", "setprop sys.powerctl reboot,fastboot"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootConfigDialog(
    state: RootConfigUiState,
    onDismiss: () -> Unit,
    onRootKeyChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text("Root 密钥配置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("运行模式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Boot", true to "热启动").forEachIndexed { index, (hotload, label) ->
                        SegmentedButton(
                            selected = state.hotload == hotload,
                            onClick = { onModeChange(hotload) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            icon = { SegmentedButtonDefaults.Icon(state.hotload == hotload) },
                        ) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = state.rootKey,
                    onValueChange = onRootKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Root Key") },
                    supportingText = {
                        Text(if (state.hotload) "可手动输入，或从 /sdcard/1.h 导入" else "请输入当前 Boot 环境的 Root Key")
                    },
                    enabled = !state.busy && state.hotloadCommand.isBlank(),
                    singleLine = true,
                )
                if (state.hotload) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onImport, enabled = !state.busy) {
                            Icon(Icons.Outlined.FileOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("从 1.h 导入")
                        }
                        OutlinedButton(onClick = onExport, enabled = !state.busy && state.hotloadCommand.isNotBlank()) {
                            Icon(Icons.Outlined.SaveAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("导出")
                        }
                    }
                    if (state.hotloadCommand.isNotBlank()) {
                        Text(
                            "已加载热启动脚本 · ${state.method}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !state.busy) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("取消") } },
    )
}

@Composable
fun BusyDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        icon = { CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp) },
        title = { Text("正在处理") },
        text = { Text(message) },
        confirmButton = {},
    )
}

@Composable
fun RebootOptionsDialog(
    onDismiss: () -> Unit,
    onSelect: (RebootOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PowerSettingsNew, null) },
        title = { Text("重启选项") },
        text = {
            Column {
                RebootOptions.forEach { option ->
                    TextButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(option.title, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun RebootConfirmDialog(
    option: RebootOption,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PowerSettingsNew, null) },
        title = { Text("确认重启？") },
        text = { Text("确定要${option.title}吗？") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    title: String,
    content: String,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "复制") }
                    IconButton(onClick = onExport) { Icon(Icons.Outlined.SaveAlt, "导出") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalChromeSurfaceAlpha.current),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalChromeSurfaceAlpha.current),
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppearanceTokens.pageSurfaceAlpha),
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            color = androidx.compose.ui.graphics.Color(0xFF17151A),
            contentColor = androidx.compose.ui.graphics.Color(0xFFE7E1E8),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = content.ifBlank { "(空)" },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
