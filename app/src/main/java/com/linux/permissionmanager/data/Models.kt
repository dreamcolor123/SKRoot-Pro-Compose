package com.linux.permissionmanager.data

import android.graphics.drawable.Drawable
import com.linux.permissionmanager.utils.ModuleWebUiShortcutRequest
import java.io.File

enum class EnvironmentState {
    CHECKING,
    RUNNING,
    OUTDATED,
    PENDING_REBOOT,
    NOT_INSTALLED,
    FAULT,
    UNKNOWN,
}

data class SystemStatus(
    val selinux: Int = -1,
    val seccomp: Int = -1,
    val adbEnabled: Boolean = false,
    val oplusIntercepted: Boolean = false,
)

data class EnvironmentInfo(
    val state: EnvironmentState = EnvironmentState.CHECKING,
    val rawState: String = "",
    val installedVersion: String = "-",
    val sdkVersion: String = "-",
    val hotload: Boolean = false,
    val hotloadMethod: String = "SHELL",
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val system: Boolean,
)

data class SuGrant(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

enum class ModuleRunState { NOT_RUNNING, RUNNING, ABNORMAL, REMOVED_PENDING_REBOOT }

data class ModuleUpdate(
    val hasNewVersion: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val changelogUrl: String,
)

data class InstalledModule(
    val name: String,
    val description: String,
    val version: String,
    val id: String,
    val author: String,
    val updateJson: String,
    val minSdk: String,
    val hasWebUi: Boolean,
    val runState: ModuleRunState,
    val update: ModuleUpdate? = null,
)

data class MarketModule(
    val chineseName: String,
    val englishName: String,
    val description: String,
    val version: String,
    val id: String,
    val author: String,
    val updateDate: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val chineseAlert: String,
    val englishAlert: String,
    val isInstalled: Boolean = false,
) {
    val displayName: String get() = chineseName.ifBlank { englishName.ifBlank { id } }
}

data class AppUpdate(
    val hasNewVersion: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val changelogUrl: String,
    val releaseNotes: String = "",
)

data class DownloadProgress(
    val title: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val cancellable: Boolean = true,
) {
    val fraction: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

data class LogPayload(val title: String, val content: String)

sealed interface UiEffect {
    data class Snackbar(val message: String) : UiEffect
    data class OpenUrl(val url: String) : UiEffect
    data class CopyText(val text: String, val confirmation: String = "已复制") : UiEffect
    data class ShowLog(val title: String, val content: String) : UiEffect
    data class PickModule(val runOnce: Boolean) : UiEffect
    data class PinModuleWebUiShortcut(val request: ModuleWebUiShortcutRequest) : UiEffect
    data object PickCustomizerIcon : UiEffect
    data class ExportCustomizedApk(val file: File, val suggestedName: String) : UiEffect
    data class InstallCustomizedApk(val file: File, val packageName: String) : UiEffect
    data object ShowRootConfig : UiEffect
    data object RequestStorageAccess : UiEffect
    data object FinishActivity : UiEffect
}
