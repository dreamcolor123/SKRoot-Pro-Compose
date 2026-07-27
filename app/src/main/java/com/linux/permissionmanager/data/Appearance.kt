package com.linux.permissionmanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.linux.permissionmanager.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PaletteId(
    val key: String,
    val title: String,
    val summary: String,
    val previewPrimary: Long,
    val previewContainer: Long,
) {
    INDIGO(
        "indigo", "靛蓝紫", "默认 · 纯白背景", 0xFF6750A4, 0xFFEADDFF,
    ),
    SEA_SALT(
        "sea_salt", "海盐青绿", "清爽低饱和", 0xFF006874, 0xFF97F0FF,
    ),
    FOREST(
        "forest", "森林翡翠", "舒适耐看", 0xFF386A20, 0xFFB9F397,
    ),
    CORAL(
        "coral", "日落珊瑚", "温暖活跃", 0xFF984061, 0xFFFFD9E2,
    ),
    SLATE(
        "slate", "石板蓝灰", "克制工具风", 0xFF485D92, 0xFFD9E2FF,
    ),
    ;

    companion object {
        fun fromKey(value: String): PaletteId = values().firstOrNull { it.key == value } ?: INDIGO
    }
}

data class AppearanceSettings(
    val palette: PaletteId = PaletteId.INDIGO,
    val backgroundUri: String? = null,
    val backgroundAlpha: Float = 0.28f,
    val chromeTransparency: Float = 0f,
    val controlTransparency: Float = 0.24f,
    val glassNavigationEnabled: Boolean = true,
    val glassNavigationTransparency: Float = 0.5f,
) {
    val backgroundEnabled: Boolean get() = !backgroundUri.isNullOrBlank()
    val chromeSurfaceAlpha: Float get() = (1f - chromeTransparency).coerceIn(0f, 1f)
    val controlSurfaceAlpha: Float get() = (1f - controlTransparency).coerceIn(0f, 1f)
    val glassNavigationOpacity: Float get() = (1f - glassNavigationTransparency).coerceIn(0f, 1f)
}

class AppearanceStore(private val context: Context) {
    private val mutableState = MutableStateFlow(load())
    val state: StateFlow<AppearanceSettings> = mutableState.asStateFlow()

    fun setPalette(palette: PaletteId) {
        update(mutableState.value.copy(palette = palette))
    }

    fun setBackground(uri: Uri?) {
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        update(mutableState.value.copy(backgroundUri = uri?.toString()))
    }

    fun clearBackground() {
        val old = mutableState.value.backgroundUri
        if (!old.isNullOrBlank()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(old),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        update(mutableState.value.copy(backgroundUri = null))
    }

    fun setBackgroundAlpha(value: Float) {
        update(mutableState.value.copy(backgroundAlpha = value.coerceIn(0f, 1f)))
    }

    fun setChromeTransparency(value: Float) {
        update(mutableState.value.copy(chromeTransparency = value.coerceIn(0f, 1f)))
    }

    fun setControlTransparency(value: Float) {
        update(mutableState.value.copy(controlTransparency = value.coerceIn(0f, 1f)))
    }

    fun setGlassNavigationEnabled(value: Boolean) {
        update(mutableState.value.copy(glassNavigationEnabled = value))
    }

    fun setGlassNavigationTransparency(value: Float) {
        update(mutableState.value.copy(glassNavigationTransparency = value.coerceIn(0f, 1f)))
    }

    fun reset() {
        clearBackground()
        update(AppearanceSettings())
    }

    private fun load(): AppearanceSettings = AppearanceSettings(
        palette = PaletteId.fromKey(AppSettings.getString(AppSettings.KEY_APPEARANCE_PALETTE, PaletteId.INDIGO.key)),
        backgroundUri = AppSettings.getString(AppSettings.KEY_APPEARANCE_BACKGROUND_URI, "").ifBlank { null },
        backgroundAlpha = AppSettings.getString(AppSettings.KEY_APPEARANCE_BACKGROUND_ALPHA, "0.28")
            .toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.28f,
        chromeTransparency = AppSettings.getString(AppSettings.KEY_APPEARANCE_CHROME_TRANSPARENCY, "0")
            .toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
        controlTransparency = AppSettings.getString(AppSettings.KEY_APPEARANCE_CONTROL_TRANSPARENCY, "0.24")
            .toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.24f,
        glassNavigationEnabled = AppSettings.getBoolean(AppSettings.KEY_APPEARANCE_GLASS_NAVIGATION_ENABLED, true),
        glassNavigationTransparency = AppSettings
            .getString(AppSettings.KEY_APPEARANCE_GLASS_NAVIGATION_TRANSPARENCY, "0.5")
            .toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
    )

    private fun update(value: AppearanceSettings) {
        mutableState.value = value
        AppSettings.setString(AppSettings.KEY_APPEARANCE_PALETTE, value.palette.key)
        AppSettings.setString(AppSettings.KEY_APPEARANCE_BACKGROUND_URI, value.backgroundUri.orEmpty())
        AppSettings.setString(AppSettings.KEY_APPEARANCE_BACKGROUND_ALPHA, value.backgroundAlpha.toString())
        AppSettings.setString(AppSettings.KEY_APPEARANCE_CHROME_TRANSPARENCY, value.chromeTransparency.toString())
        AppSettings.setString(AppSettings.KEY_APPEARANCE_CONTROL_TRANSPARENCY, value.controlTransparency.toString())
        AppSettings.setBoolean(AppSettings.KEY_APPEARANCE_GLASS_NAVIGATION_ENABLED, value.glassNavigationEnabled)
        AppSettings.setString(
            AppSettings.KEY_APPEARANCE_GLASS_NAVIGATION_TRANSPARENCY,
            value.glassNavigationTransparency.toString(),
        )
    }
}
