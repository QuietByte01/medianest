package com.medianest.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Transparent Glassmorphic Surface component for cards, dialogs, and UI items.
 *
 * Automatically connects to [LocalBackdropState] to render real-time hardware
 * backdrop blur of the screen content behind the surface when available.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 0.5.dp,
    backgroundImage: Any? = null,
    enableBlur: Boolean = false,
    blurRadius: Dp = 12.dp,
    backgroundImageAlpha: Float = 1f,
    backgroundBrush: Brush? = null,
    backdropState: BackdropBlurState? = LocalBackdropState.current,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val context = LocalContext.current

    // Dark → Obsidian tint; Light → Frosted #E3E3E3
    val effectiveBg = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else {
        if (enableBlur && backdropState != null && backgroundImage == null) {
            Color(0x330F1015)
        } else if (isDark) {
            Color(0x221C1F2B)
        } else {
            Color(0xBFFFFFFF)
        }
    }

    val effectiveBorder = if (borderColor != Color.Unspecified) {
        borderColor
    } else {
        if (isDark) Color(0x28FFFFFF) else Color(0x28000000)
    }

    val finalBgBrush = backgroundBrush ?: Brush.verticalGradient(
        colors = listOf(
            effectiveBg,
            effectiveBg.copy(alpha = (effectiveBg.alpha * 0.7f).coerceIn(0f, 1f))
        )
    )

    val surfaceModifier = modifier
        .clip(shape)
        .then(
            if (enableBlur && backdropState != null && backgroundImage == null) {
                Modifier.backdropReceiver(
                    state = backdropState,
                    blurRadius = blurRadius,
                    tint = Color(0x660A0C10),
                    baseColor = Color.Transparent
                )
            } else Modifier
        )
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

    Box(modifier = surfaceModifier) {
        // 1. Background Image (Lower layer) - Only if backgroundImage != null
        if (backgroundImage != null) {
            val imageModifier = Modifier
                .matchParentSize()
                .clip(shape)
                .alpha(backgroundImageAlpha)
                .let { m ->
                    if (enableBlur && blurRadius > 0.dp) {
                        m.blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    } else {
                        m
                    }
                }

            if (backgroundImage is Int) {
                Image(
                    painter = painterResource(id = backgroundImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backgroundImage)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier
                )
            }
        }

        // 2. Background Color Tint (Overlay layer)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush = finalBgBrush, shape = shape)
        )

        content()
    }
}
