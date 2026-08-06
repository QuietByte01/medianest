package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.MediaNestApp
import com.example.ui.theme.LocalDarkTheme

/**
 * Glassmorphic Surface component using standard Compose Modifier.blur().
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp), // Max 16dp constraint
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 0.5.dp,
    backgroundImage: Any? = null,
    enableBlur: Boolean = true,
    blurRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val settingsManager = MediaNestApp.instance.settingsManager
    val glassEnabled by settingsManager.glassmorphismEnabled.collectAsState(initial = true)

    // Dark / Light Glass Colors
    val defaultBg = if (isDark) Color(0x1A141722) else Color(0x73FFFFFF)
    val defaultBorder = if (isDark) Color(0x18FFFFFF) else Color(0x18000000)

    val effectiveBg = if (backgroundColor != Color.Unspecified) backgroundColor else defaultBg
    val effectiveBorder = if (borderColor != Color.Unspecified) borderColor else defaultBorder

    val context = LocalContext.current
    val shouldApplyBlur = enableBlur && glassEnabled

    // Smooth transition when toggling glass/blur settings
    val animatedBlurRadius by animateFloatAsState(
        targetValue = if (shouldApplyBlur) blurRadius.value else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "GlassBlurAnimation"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        effectiveBorder,
                        effectiveBorder.copy(alpha = (effectiveBorder.alpha * 0.4f).coerceIn(0f, 1f))
                    )
                ),
                shape = shape
            )
    ) {
        // 1. BACKGROUND IMAGE WITH MODIFIER.BLUR
        if (backgroundImage != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backgroundImage)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = true } // Forces layer bounds so blur doesn't bleed or crash
                    .then(
                        if (animatedBlurRadius > 0f) {
                            Modifier.blur(animatedBlurRadius.dp)
                        } else {
                            Modifier
                        }
                    )
            )
        }

        // 2. FROSTED GLASS TINT OVERLAY
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            effectiveBg,
                            effectiveBg.copy(alpha = (effectiveBg.alpha * 0.7f).coerceIn(0f, 1f))
                        )
                    ),
                    shape = shape
                )
        )

        // 3. UI CONTENT LAYER
        Box(
            modifier = Modifier,
            content = content
        )
    }
}
