package com.linux.permissionmanager.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.linux.permissionmanager.customizer.CustomBuildStage
import com.linux.permissionmanager.customizer.PackageNameValidator
import com.linux.permissionmanager.ui.LocalCustomizerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun synchronizeImeTextFieldValue(
    current: TextFieldValue,
    externalText: String,
): TextFieldValue = if (current.text == externalText) {
    // Preserve the IME composition and selection that are not represented by the ViewModel String.
    current
} else {
    TextFieldValue(
        text = externalText,
        selection = TextRange(externalText.length),
    )
}

internal fun sanitizePackageTextFieldValue(value: TextFieldValue): TextFieldValue {
    val raw = value.text
    val normalized = PackageNameValidator.normalizeInput(raw)
    val contentStart = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length else it }
    val contentEnd = raw.indexOfLast { !it.isWhitespace() }.let { if (it < 0) contentStart else it + 1 }
    fun mapOffset(offset: Int): Int {
        if (contentStart >= contentEnd) return 0
        val bounded = offset.coerceIn(contentStart, contentEnd)
        return raw.substring(contentStart, bounded).count { it != '\r' && it != '\n' }
            .coerceIn(0, normalized.length)
    }
    return TextFieldValue(
        // Package names are plain identifiers. Rebuilding from String strips clipboard spans;
        // removing line breaks also prevents a leading newline from placing all glyphs outside
        // the visible line of a single-line text field.
        text = normalized,
        selection = TextRange(
            mapOffset(value.selection.start),
            mapOffset(value.selection.end),
        ),
        composition = null,
    )
}

@Composable
fun LocalCustomizerDialog(
    state: LocalCustomizerUiState,
    onDismiss: () -> Unit,
    onPackageNameChange: (String) -> Unit,
    onManagerNameChange: (String) -> Unit,
    onPickIcon: () -> Unit,
    onUseDefaultIcon: () -> Unit,
    onBuildAndInstall: () -> Unit,
    onExport: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Android, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("本地定制管理器", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            if (state.building && state.stage != CustomBuildStage.INSTALLING) "取消构建" else "关闭",
                        )
                    }
                }

                val packageSupport = state.packageError
                    ?: state.signatureConflict
                    ?: if (state.checkingPackage) "正在检查已安装应用…"
                    else if (state.packageName.isBlank()) "留空使用默认包名：${state.defaultPackageName}" else null
                // Keep the editable buffer local so IME composition, quick phrases and
                // clipboard commits are reflected in the same frame. Round-tripping the
                // String through StateFlow on every edit can make some release-build IMEs
                // discard their composing/selection state and render an apparently empty
                // field even though the ViewModel already received the pasted text.
                var packageField by remember {
                    mutableStateOf(
                        TextFieldValue(
                            text = state.packageName,
                            selection = TextRange(state.packageName.length),
                        ),
                    )
                }
                LaunchedEffect(state.packageName) {
                    packageField = synchronizeImeTextFieldValue(packageField, state.packageName)
                }
                OutlinedTextField(
                    value = packageField,
                    onValueChange = { value ->
                        val plainValue = sanitizePackageTextFieldValue(value)
                        packageField = plainValue
                        onPackageNameChange(plainValue.text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("包名") },
                    placeholder = { Text(state.defaultPackageName) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    enabled = !state.building,
                    isError = state.packageError != null || state.signatureConflict != null,
                    supportingText = packageSupport?.let { message -> { Text(message) } },
                )
                var managerNameField by remember {
                    mutableStateOf(
                        TextFieldValue(
                            text = state.managerName,
                            selection = TextRange(state.managerName.length),
                        ),
                    )
                }
                LaunchedEffect(state.managerName) {
                    managerNameField = synchronizeImeTextFieldValue(managerNameField, state.managerName)
                }
                OutlinedTextField(
                    value = managerNameField,
                    onValueChange = { value ->
                        managerNameField = value
                        onManagerNameChange(value.text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("管理器名称") },
                    placeholder = { Text(state.defaultManagerName) },
                    singleLine = true,
                    enabled = !state.building,
                    supportingText = { Text("留空使用默认名称：${state.defaultManagerName}") },
                )

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CustomIconPreview(state.iconUri, Modifier.size(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text("本地图标", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.iconUri == null) "未选择时沿用当前应用图标" else "已选择自定义图标",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = onPickIcon, enabled = !state.building) {
                            Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.iconUri == null) "选择" else "更换")
                        }
                    }
                }
                if (state.iconUri != null) {
                    TextButton(
                        onClick = onUseDefaultIcon,
                        enabled = !state.building,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("使用默认图标") }
                }

                if (state.stage != CustomBuildStage.IDLE || state.building || state.error != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.building) {
                            LinearProgressIndicator(
                                progress = { state.stage.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (state.stage == CustomBuildStage.COMPLETE) {
                            LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            state.error ?: state.stage.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = onExport,
                        enabled = state.canBuild,
                        modifier = Modifier.weight(1f),
                    ) { Text("仅导出 APK", maxLines = 1) }
                    Button(
                        onClick = onBuildAndInstall,
                        enabled = state.canInstall,
                        modifier = Modifier.weight(1f),
                    ) { Text("构建并安装", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun CustomIconPreview(uri: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val defaultBitmap = remember(context.packageName) {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256) }.getOrNull()
    }
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri == null) null else withContext(Dispatchers.IO) {
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
    val shape = RoundedCornerShape(18.dp)
    val preview = bitmap ?: defaultBitmap
    if (preview == null) {
        Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    } else {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = if (uri == null) "默认应用图标预览" else "定制图标预览",
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}
