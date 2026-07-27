package com.linux.permissionmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.linux.permissionmanager.data.AppearanceSettings
import com.linux.permissionmanager.data.PaletteId

data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = Color(0xFF2E7D32), onSuccess = Color.White,
        successContainer = Color(0xFFD8F5D2), onSuccessContainer = Color(0xFF0D3B0F),
        warning = Color(0xFF8A5A00), warningContainer = Color(0xFFFFE8B2),
        onWarningContainer = Color(0xFF2A1800), infoContainer = Color(0xFFD9E2FF),
        onInfoContainer = Color(0xFF001A41),
    )
}

val LocalChromeSurfaceAlpha = staticCompositionLocalOf { 1f }
val LocalControlSurfaceAlpha = staticCompositionLocalOf { AppearanceTokens.defaultControlSurfaceAlpha }
val LocalContentDrawsBehindNavigation = staticCompositionLocalOf { false }

object AppearanceTokens {
    // Page surfaces must remain translucent enough for a user-selected image
    // to survive compositing. Cards and navigation stay more opaque so text
    // remains readable over detailed photographs.
    const val pageSurfaceAlpha = 0.38f
    const val defaultControlSurfaceAlpha = 0.76f
    const val dialogSurfaceAlpha = 0.98f
}

private val LightBackground = Color.White
private val LightSurfaceLow = Color(0xFFFAFAFA)
private val LightSurface = Color.White
private val LightSurfaceHigh = Color(0xFFFFFFFF)
private val LightSurfaceHighest = Color(0xFFF0F0F0)
private val LightSurfaceVariant = Color(0xFFE6E6E6)

private fun baseLight(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    background = LightBackground,
    surface = LightBackground,
    surfaceVariant = LightSurfaceVariant,
    surfaceContainer = LightSurface,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private fun paletteScheme(palette: PaletteId): ColorScheme = when (palette) {
    PaletteId.INDIGO -> baseLight(
        Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D),
        Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF1D192B), Color(0xFF7D5260),
    )
    PaletteId.SEA_SALT -> baseLight(
        Color(0xFF006874), Color.White, Color(0xFF97F0FF), Color(0xFF001F24),
        Color(0xFF4A6366), Color(0xFFCDE7EA), Color(0xFF051F22), Color(0xFF4F5F7D),
    )
    PaletteId.FOREST -> baseLight(
        Color(0xFF386A20), Color.White, Color(0xFFB9F397), Color(0xFF0B2002),
        Color(0xFF55624C), Color(0xFFD9E8CC), Color(0xFF131F0F), Color(0xFF3F665D),
    )
    PaletteId.CORAL -> baseLight(
        Color(0xFF984061), Color.White, Color(0xFFFFD9E2), Color(0xFF3F001D),
        Color(0xFF765660), Color(0xFFFFD9E2), Color(0xFF2D151F), Color(0xFF80543B),
    )
    PaletteId.SLATE -> baseLight(
        Color(0xFF485D92), Color.White, Color(0xFFD9E2FF), Color(0xFF001A41),
        Color(0xFF5A5F71), Color(0xFFDEE2F2), Color(0xFF171B2B), Color(0xFF76546F),
    )
}

private val LightSemanticColors = SemanticColors(
    success = Color(0xFF2E7D32), onSuccess = Color.White,
    successContainer = Color(0xFFD8F5D2), onSuccessContainer = Color(0xFF0D3B0F),
    warning = Color(0xFF8A5A00), warningContainer = Color(0xFFFFE8B2),
    onWarningContainer = Color(0xFF2A1800), infoContainer = Color(0xFFD9E2FF),
    onInfoContainer = Color(0xFF001A41),
)

@Composable
fun SkpTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = paletteScheme(appearance.palette)

    LaunchedEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    CompositionLocalProvider(
        LocalSemanticColors provides LightSemanticColors,
        LocalChromeSurfaceAlpha provides appearance.chromeSurfaceAlpha,
        LocalControlSurfaceAlpha provides appearance.controlSurfaceAlpha,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
