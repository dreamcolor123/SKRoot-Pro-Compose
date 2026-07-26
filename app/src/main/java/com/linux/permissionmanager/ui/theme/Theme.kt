package com.linux.permissionmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

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

private val MilkBackground = Color(0xFFFFFFFF)
private val MilkSurfaceLow = Color(0xFFFFF8F0)
private val MilkSurface = Color(0xFFF8F2EA)
private val MilkSurfaceHigh = Color(0xFFF1EBE3)
private val MilkSurfaceHighest = Color(0xFFEAE4DC)
private val MilkSurfaceVariant = Color(0xFFE9E2D9)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    background = MilkBackground,
    surface = MilkBackground,
    surfaceVariant = MilkSurfaceVariant,
    surfaceContainer = MilkSurface,
    surfaceContainerLow = MilkSurfaceLow,
    surfaceContainerHigh = MilkSurfaceHigh,
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private fun ColorScheme.withMilkSurfaces(): ColorScheme = copy(
    background = MilkBackground,
    surface = MilkBackground,
    surfaceVariant = MilkSurfaceVariant,
    surfaceDim = MilkSurfaceHighest,
    surfaceBright = MilkBackground,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = MilkSurfaceLow,
    surfaceContainer = MilkSurface,
    surfaceContainerHigh = MilkSurfaceHigh,
    surfaceContainerHighest = MilkSurfaceHighest,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightSemanticColors = SemanticColors(
    success = Color(0xFF2E7D32), onSuccess = Color.White,
    successContainer = Color(0xFFD8F5D2), onSuccessContainer = Color(0xFF0D3B0F),
    warning = Color(0xFF8A5A00), warningContainer = Color(0xFFFFE8B2),
    onWarningContainer = Color(0xFF2A1800), infoContainer = Color(0xFFD9E2FF),
    onInfoContainer = Color(0xFF001A41),
)

private fun semanticColors(dark: Boolean) = if (dark) {
    SemanticColors(
        success = Color(0xFFA5D6A7), onSuccess = Color(0xFF103A16),
        successContainer = Color(0xFF214E27), onSuccessContainer = Color(0xFFC0F0C3),
        warning = Color(0xFFFFCA7A), warningContainer = Color(0xFF5B3B00),
        onWarningContainer = Color(0xFFFFE1AC), infoContainer = Color(0xFF294369),
        onInfoContainer = Color(0xFFD9E2FF),
    )
} else LightSemanticColors

@Composable
fun SkpTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context).withMilkSurfaces()
        dark -> DarkColors
        else -> LightColors
    }

    LaunchedEffect(dark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalSemanticColors provides semanticColors(dark)) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
