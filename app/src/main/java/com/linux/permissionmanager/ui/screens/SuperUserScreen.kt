package com.linux.permissionmanager.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linux.permissionmanager.data.InstalledApp
import com.linux.permissionmanager.data.SuGrant
import com.linux.permissionmanager.ui.SuperUserUiState
import com.linux.permissionmanager.ui.components.*
import com.linux.permissionmanager.ui.theme.AppearanceTokens
import com.linux.permissionmanager.ui.theme.LocalChromeSurfaceAlpha
import com.linux.permissionmanager.ui.theme.LocalContentDrawsBehindNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperUserScreen(
    state: SuperUserUiState,
    bottomPadding: PaddingValues,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onShowPicker: () -> Unit,
    onHidePicker: () -> Unit,
    onPickerSearch: (String) -> Unit,
    onFilterSystem: (Boolean) -> Unit,
    onFilterThirdParty: (Boolean) -> Unit,
    onAdd: (InstalledApp) -> Unit,
    onAddAdb: () -> Unit,
    onRemove: (SuGrant) -> Unit,
    onClear: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<SuGrant?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val drawsBehindNavigation = LocalContentDrawsBehindNavigation.current
    val navigationClearance = bottomPadding.calculateBottomPadding()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("授权")
                        Text("${state.grants.size} 个应用已授权", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "刷新") }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.Add, "添加") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("添加 SU 授权") },
                                leadingIcon = { Icon(Icons.Outlined.Apps, null) },
                                onClick = { menuExpanded = false; onShowPicker() },
                            )
                            DropdownMenuItem(
                                text = { Text("添加 ADB 授权") },
                                leadingIcon = { Icon(Icons.Outlined.Terminal, null) },
                                onClick = { menuExpanded = false; onAddAdb() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清空授权", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; clearConfirm = true },
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
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AppearanceTokens.pageSurfaceAlpha),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = if (drawsBehindNavigation) 0.dp else navigationClearance,
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 8.dp,
                bottom = 20.dp + if (drawsBehindNavigation) navigationClearance else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (state.query.isNotBlank()) {
                        { IconButton(onClick = { onSearch("") }) { Icon(Icons.Outlined.Close, "清除") } }
                    } else null,
                    placeholder = { Text("搜索应用或包名") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            if (state.loading) item { LoadingState("正在读取授权列表…") }
            state.error?.let { item { ErrorState(it, onRefresh) } }
            if (!state.loading && state.filteredGrants.isEmpty()) {
                item { EmptyState("暂无 SU 授权", "点击右上角添加需要 Root 权限的应用", icon = Icons.Outlined.Shield) }
            }
            items(state.filteredGrants, key = { it.packageName }) { grant ->
                TonalCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppIcon(grant.icon, grant.label, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(grant.label.ifBlank { grant.packageName }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(grant.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { pendingRemove = grant }) { Icon(Icons.Outlined.DeleteOutline, "移除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (state.pickerVisible) {
        AppPickerDialog(
            state = state,
            onDismiss = onHidePicker,
            onSearch = onPickerSearch,
            onFilterSystem = onFilterSystem,
            onFilterThirdParty = onFilterThirdParty,
            onSelect = onAdd,
        )
    }
    pendingRemove?.let { grant ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除授权？") },
            text = { Text("确定移除 ${grant.label.ifBlank { grant.packageName }} 的 SU 授权吗？") },
            confirmButton = {
                Button(
                    onClick = { pendingRemove = null; onRemove(grant) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("取消") } },
        )
    }
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("清空所有授权？") },
            text = { Text("所有应用的 SU 授权都将被移除。") },
            confirmButton = {
                Button(
                    onClick = { clearConfirm = false; onClear() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AppPickerDialog(
    state: SuperUserUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onFilterSystem: (Boolean) -> Unit,
    onFilterThirdParty: (Boolean) -> Unit,
    onSelect: (InstalledApp) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.90f).widthIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选择应用", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.pickerQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("搜索应用或包名") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    FilterChip(
                        selected = state.showThirdPartyApps,
                        onClick = { onFilterThirdParty(!state.showThirdPartyApps) },
                        label = { Text("第三方应用") },
                        leadingIcon = if (state.showThirdPartyApps) {{ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }} else null,
                    )
                    FilterChip(
                        selected = state.showSystemApps,
                        onClick = { onFilterSystem(!state.showSystemApps) },
                        label = { Text("系统应用") },
                        leadingIcon = if (state.showSystemApps) {{ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }} else null,
                    )
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.filteredApps, key = { it.packageName }) { app ->
                        Surface(onClick = { onSelect(app) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                AppIcon(app.icon, app.label, Modifier.size(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
