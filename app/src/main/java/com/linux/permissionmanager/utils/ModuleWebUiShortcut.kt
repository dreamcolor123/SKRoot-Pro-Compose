package com.linux.permissionmanager.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Base64
import com.linux.permissionmanager.AppSettings
import com.linux.permissionmanager.MainActivity
import com.linux.permissionmanager.R
import com.linux.permissionmanager.data.InstalledModule
import java.security.MessageDigest
import java.security.SecureRandom

internal data class ModuleWebUiShortcutSpec(
    val shortcutId: String,
    val shortLabel: String,
    val longLabel: String,
)

sealed interface PinModuleWebUiShortcutResult {
    val message: String

    data object Requested : PinModuleWebUiShortcutResult {
        override val message = "已请求创建桌面快捷方式，请在系统弹窗中确认"
    }

    data object Unsupported : PinModuleWebUiShortcutResult {
        override val message = "当前桌面启动器不支持固定快捷方式"
    }

    data class Failed(override val message: String) : PinModuleWebUiShortcutResult
}

object ModuleWebUiShortcut {
    const val ACTION_OPEN = "com.linux.permissionmanager.action.OPEN_MODULE_WEBUI"
    const val EXTRA_MODULE_ID = "module_webui_id"
    private const val EXTRA_TOKEN = "module_webui_shortcut_token"
    private const val SHORTCUT_ID_PREFIX = "skroot_webui_"

    fun requestPin(
        context: Context,
        module: InstalledModule,
    ): PinModuleWebUiShortcutResult {
        if (!module.hasWebUi) {
            return PinModuleWebUiShortcutResult.Failed("该模块没有 WebUI")
        }
        val moduleId = module.id.trim()
        if (moduleId.isBlank()) {
            return PinModuleWebUiShortcutResult.Failed("模块 ID 为空，快捷方式创建失败")
        }
        return runCatching {
            val manager = context.getSystemService(ShortcutManager::class.java)
                ?: return PinModuleWebUiShortcutResult.Unsupported
            if (!manager.isRequestPinShortcutSupported) {
                return PinModuleWebUiShortcutResult.Unsupported
            }
            val spec = shortcutSpec(module)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN
                putExtra(EXTRA_MODULE_ID, moduleId)
                putExtra(EXTRA_TOKEN, shortcutToken())
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val shortcut = ShortcutInfo.Builder(context, spec.shortcutId)
                .setShortLabel(spec.shortLabel)
                .setLongLabel(spec.longLabel)
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(launchIntent)
                .build()
            if (manager.requestPinShortcut(shortcut, null)) {
                PinModuleWebUiShortcutResult.Requested
            } else {
                PinModuleWebUiShortcutResult.Failed("桌面快捷方式创建请求未被启动")
            }
        }.getOrElse { error ->
            PinModuleWebUiShortcutResult.Failed(
                "创建桌面快捷方式失败：${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    fun moduleIdFrom(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN) return null
        val receivedToken = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (receivedToken.isBlank() || receivedToken != shortcutToken()) return null
        return intent.getStringExtra(EXTRA_MODULE_ID)?.trim()?.takeIf(String::isNotBlank)
    }

    internal fun shortcutSpec(module: InstalledModule): ModuleWebUiShortcutSpec {
        val moduleId = module.id.trim()
        val displayName = module.name
            .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { moduleId.ifBlank { "SKRoot" } }
        val fullLabel = "$displayName WebUI"
        return ModuleWebUiShortcutSpec(
            shortcutId = SHORTCUT_ID_PREFIX + sha256(moduleId).take(24),
            shortLabel = fullLabel.take(10),
            longLabel = fullLabel.take(25),
        )
    }

    private fun shortcutToken(): String = synchronized(this) {
        AppSettings.getString(AppSettings.KEY_MODULE_WEBUI_SHORTCUT_TOKEN, "")
            .takeIf(String::isNotBlank)
            ?: ByteArray(24).also(SecureRandom()::nextBytes)
                .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }
                .also { AppSettings.setString(AppSettings.KEY_MODULE_WEBUI_SHORTCUT_TOKEN, it) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
