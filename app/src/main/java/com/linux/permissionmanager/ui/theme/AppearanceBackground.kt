package com.linux.permissionmanager.ui.theme

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.linux.permissionmanager.data.AppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun AppearanceBackground(
    appearance: AppearanceSettings,
    backgroundModifier: Modifier = Modifier,
    onImageError: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val uri = appearance.backgroundUri?.let(Uri::parse)
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loaded by remember(uri) { mutableStateOf(uri == null) }
    LaunchedEffect(uri) {
        loaded = false
        bitmap = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmap(context, uri) }.getOrNull()
        }
        loaded = true
    }
    LaunchedEffect(uri, loaded, bitmap) {
        if (uri != null && loaded && bitmap == null) onImageError()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Keep the background in its own layer so effects such as Haze can
        // capture the actual wallpaper instead of only the translucent page
        // surfaces drawn above it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (bitmap != null && appearance.backgroundEnabled) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = appearance.backgroundAlpha,
                )
            }
            // A light scrim keeps text readable over bright photographs without
            // hiding the selected image.
            if (bitmap != null && appearance.backgroundEnabled) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
            }
        }
        content()
    }
}

private fun decodeSampledBitmap(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val display = context.resources.displayMetrics
    val target = max(display.widthPixels, display.heightPixels).coerceIn(1080, 4096)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}
