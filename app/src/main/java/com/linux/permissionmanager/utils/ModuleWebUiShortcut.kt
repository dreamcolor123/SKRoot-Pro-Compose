package com.linux.permissionmanager.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import com.linux.permissionmanager.AppSettings
import com.linux.permissionmanager.ModuleWebUiShortcutRouterActivity
import com.linux.permissionmanager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

data class ModuleWebUiShortcutRequest(
    val moduleId: String,
    val moduleName: String,
    val shortcutName: String,
    val iconUri: Uri? = null,
)

internal data class ModuleWebUiShortcutSpec(
    val opaqueId: String,
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

/**
 * Creates pinned WebUI shortcuts without placing the module ID in the launcher-visible Intent.
 * The Intent carries one random opaque handle; its module mapping stays in private app settings.
 */
object ModuleWebUiShortcut {
    const val ACTION_OPEN = "com.linux.permissionmanager.action.OPEN_MODULE_WEBUI"
    const val EXTRA_OPAQUE_ID = "module_webui_shortcut_id"
    private const val SHORTCUT_ID_PREFIX = "skroot_webui_v2_"
    private const val OPAQUE_ID_BYTES = 24
    private const val ADAPTIVE_ICON_SIZE = 216
    private const val ADAPTIVE_ICON_CONTENT_SIZE = 144
    private val secureRandom = SecureRandom()

    suspend fun requestPin(
        context: Context,
        request: ModuleWebUiShortcutRequest,
    ): PinModuleWebUiShortcutResult {
        val moduleId = request.moduleId.trim()
        if (moduleId.isBlank()) {
            return PinModuleWebUiShortcutResult.Failed("模块 ID 为空，快捷方式创建失败")
        }
        val customBitmap = request.iconUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeAdaptiveShortcutIcon(context, uri) }
                ?: return PinModuleWebUiShortcutResult.Failed("无法读取选定的快捷方式图标")
        }
        return withContext(Dispatchers.Main.immediate) {
            runCatching {
                val manager = context.getSystemService(ShortcutManager::class.java)
                    ?: return@withContext PinModuleWebUiShortcutResult.Unsupported
                if (!manager.isRequestPinShortcutSupported) {
                    return@withContext PinModuleWebUiShortcutResult.Unsupported
                }
                val spec = shortcutSpec(request, newOpaqueId())
                val launchIntent = Intent(context, ModuleWebUiShortcutRouterActivity::class.java).apply {
                    action = ACTION_OPEN
                    putExtra(EXTRA_OPAQUE_ID, spec.opaqueId)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                }
                val icon = customBitmap?.let(Icon::createWithAdaptiveBitmap)
                    ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
                val shortcut = ShortcutInfo.Builder(context, spec.shortcutId)
                    .setShortLabel(spec.shortLabel)
                    .setLongLabel(spec.longLabel)
                    .setIcon(icon)
                    .setIntent(launchIntent)
                    .build()
                if (manager.requestPinShortcut(shortcut, null)) {
                    storeMapping(spec.opaqueId, moduleId)
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
    }

    fun opaqueIdFrom(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN) return null
        return intent.getStringExtra(EXTRA_OPAQUE_ID)
            ?.trim()
            ?.takeIf { it.length in 20..80 && it.all(::isOpaqueIdCharacter) }
    }

    fun resolveModuleId(intent: Intent?): String? = opaqueIdFrom(intent)?.let(::resolveModuleId)

    fun resolveModuleId(opaqueId: String): String? = synchronized(this) {
        readMappings()[opaqueId]?.trim()?.takeIf(String::isNotBlank)
    }

    internal fun shortcutSpec(
        request: ModuleWebUiShortcutRequest,
        opaqueId: String,
    ): ModuleWebUiShortcutSpec {
        val fallback = request.moduleName.ifBlank { request.moduleId.ifBlank { "WebUI" } }
        val displayName = sanitizeLabel(request.shortcutName).ifBlank { "${sanitizeLabel(fallback)} WebUI" }
        return ModuleWebUiShortcutSpec(
            opaqueId = opaqueId,
            shortcutId = SHORTCUT_ID_PREFIX + opaqueId,
            shortLabel = displayName.take(10),
            longLabel = displayName.take(25),
        )
    }

    internal fun opaqueIdFromBytes(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    internal fun encodeMappings(values: Map<String, String>): String = JSONObject().apply {
        values.forEach { (opaqueId, moduleId) -> put(opaqueId, moduleId) }
    }.toString()

    internal fun decodeMappings(raw: String): Map<String, String> = runCatching {
        val json = JSONObject(raw.ifBlank { "{}" })
        buildMap {
            json.keys().forEach { key ->
                val value = json.optString(key).trim()
                if (key.length in 20..80 && key.all(::isOpaqueIdCharacter) && value.isNotBlank()) {
                    put(key, value)
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun newOpaqueId(): String = ByteArray(OPAQUE_ID_BYTES)
        .also(secureRandom::nextBytes)
        .let(::opaqueIdFromBytes)

    private fun storeMapping(opaqueId: String, moduleId: String) = synchronized(this) {
        val mappings = readMappings().toMutableMap()
        mappings[opaqueId] = moduleId
        AppSettings.setString(AppSettings.KEY_MODULE_WEBUI_SHORTCUT_MAP, encodeMappings(mappings))
    }

    private fun readMappings(): Map<String, String> = decodeMappings(
        AppSettings.getString(AppSettings.KEY_MODULE_WEBUI_SHORTCUT_MAP, "{}"),
    )

    private fun sanitizeLabel(value: String): String = value
        .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isOpaqueIdCharacter(value: Char): Boolean =
        value in 'a'..'z' || value in 'A'..'Z' || value in '0'..'9' || value == '-' || value == '_'

    private fun decodeAdaptiveShortcutIcon(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        try {
            val output = Bitmap.createBitmap(ADAPTIVE_ICON_SIZE, ADAPTIVE_ICON_SIZE, Bitmap.Config.ARGB_8888)
            val inset = (ADAPTIVE_ICON_SIZE - ADAPTIVE_ICON_CONTENT_SIZE) / 2f
            val destination = RectF(
                inset,
                inset,
                inset + ADAPTIVE_ICON_CONTENT_SIZE,
                inset + ADAPTIVE_ICON_CONTENT_SIZE,
            )
            Canvas(output).drawBitmap(
                source,
                centerCropRect(source.width, source.height),
                destination,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            output
        } finally {
            source.recycle()
        }
    }.getOrNull()

    private fun centerCropRect(width: Int, height: Int): Rect {
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return Rect(left, top, left + side, top + side)
    }
}
