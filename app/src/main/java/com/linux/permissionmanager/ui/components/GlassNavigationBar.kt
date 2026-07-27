@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
)

package com.linux.permissionmanager.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import io.github.elyesmansour.floatingTabBar.FloatingTabBar
import io.github.elyesmansour.floatingTabBar.FloatingTabBarDefaults

@Immutable
data class GlassNavigationItem(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

/**
 * Floating navigation based on elyesmansour/compose-floating-tab-bar.
 * Haze renders in a stable sibling layer behind the library's animated content.
 */
@Composable
fun GlassFloatingNavigationBar(
    items: List<GlassNavigationItem>,
    selectedIndex: Int,
    hazeState: HazeState,
    transparency: Float,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val glassOpacity = (1f - transparency).coerceIn(0f, 1f)
    val solidSurfaceAlpha = glassOpacity * glassOpacity
    val glassShape = RoundedCornerShape(32.dp)
    val barWidth = (items.size.coerceAtLeast(1) * 62 + 8).dp
    val surfaceColor = colorScheme.surface
    // Keep the V3 strength at the opaque end, then reduce it gently as the
    // glass becomes more transparent: 30dp -> 20dp. The 100% endpoint skips
    // the effect layer entirely below.
    val blurRadius = (20f + 10f * glassOpacity).dp
    // Keep the real blurred page visible. The previous tint reached 70% and
    // the pre-Android-12 fallback reached 94%, which visually became a white bar.
    // Backdrop blur has to stay fully composited. Fading the complete Haze
    // result blends the original sharp page back over the blurred copy, which
    // makes text behind the capsule look unblurred (especially above 50%).
    // Only the glass tint/fill follows the transparency slider.
    val tintAlpha = 0.18f * glassOpacity
    val fallbackAlpha = 0.40f * glassOpacity
    val glassStyle = remember(
        surfaceColor,
        colorScheme.surfaceContainerHigh,
        colorScheme.primary,
        tintAlpha,
        fallbackAlpha,
        blurRadius,
    ) {
        HazeStyle(
            backgroundColor = surfaceColor,
            tints = listOf(
                HazeTint(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = tintAlpha),
                            colorScheme.surfaceContainerHigh.copy(alpha = tintAlpha),
                            colorScheme.primary.copy(alpha = tintAlpha * 0.28f),
                        )
                    )
                )
            ),
            blurRadius = blurRadius,
            // The repeated noise texture can shimmer against slowly moving
            // high-frequency content such as text. Pure blur remains stable.
            noiseFactor = 0f,
            fallbackTint = HazeTint(surfaceColor.copy(alpha = fallbackAlpha)),
        )
    }
    val edgeBrush = remember(colorScheme.outlineVariant, glassOpacity) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.72f * glassOpacity),
                colorScheme.outlineVariant.copy(alpha = 0.42f * glassOpacity),
                Color.White.copy(alpha = 0.18f * glassOpacity),
            )
        )
    }

    // The glass capsule is a stable sibling behind FloatingTabBar. Keeping it
    // outside the library's SharedTransitionLayout prevents the Haze layer from
    // being stretched into a horizontal band during selection transitions.
    Box(
        modifier = modifier.width(barWidth),
        contentAlignment = Alignment.Center,
    ) {
        if (glassOpacity > 0f) {
            // Keep the captured backdrop at full alpha so it replaces the
            // sharp pixels behind the capsule with their blurred equivalent.
            // Tint, solid fill and border remain independently transparent.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(glassShape)
                    .hazeEffect(state = hazeState, style = glassStyle) {
                        blurEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        // Explicit full-resolution input avoids resampling
                        // artifacts if the library default changes later.
                        inputScale = HazeInputScale.None
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f * glassOpacity),
                                Color.Transparent,
                                colorScheme.primary.copy(alpha = 0.035f * glassOpacity),
                            )
                        ),
                        shape = glassShape,
                    ),
            )

            // The final solid alpha is opacity², while the edge follows the
            // slider linearly. At 0% transparency this surface is fully opaque.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = surfaceColor.copy(alpha = solidSurfaceAlpha),
                        shape = glassShape,
                    )
                    .border(width = 1.dp, brush = edgeBrush, shape = glassShape),
            )
        }

        FloatingTabBar(
            isInline = false,
            selectedTabKey = selectedIndex,
            modifier = Modifier.fillMaxWidth(),
            colors = FloatingTabBarDefaults.colors(
                backgroundColor = Color.Transparent,
                accessoryBackgroundColor = Color.Transparent,
            ),
            shapes = FloatingTabBarDefaults.shapes(
                tabBarShape = glassShape,
                tabShape = RoundedCornerShape(24.dp),
            ),
            sizes = FloatingTabBarDefaults.sizes(
                tabBarContentPadding = PaddingValues(4.dp),
                tabExpandedContentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
                tabSpacing = 0.dp,
            ),
            elevations = FloatingTabBarDefaults.elevations(
                inlineElevation = 0.dp,
                expandedElevation = 0.dp,
            ),
            // FloatingTabBar caches its tab scope. Re-key it so icon and label
            // lambdas observe the latest selection instead of the initial page.
            contentKey = selectedIndex,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                tab(
                    key = index,
                    title = {
                        Text(
                            text = item.label,
                            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    },
                    icon = {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selected) colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(20.dp),
                                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onItemSelected(index) },
                )
            }
        }
    }
}
