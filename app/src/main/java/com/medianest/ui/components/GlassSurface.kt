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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Transparent Glassmorphic Surface component for cards and UI items.
 *
 * Dark mode  → Obsidian tint (0xCC08090E)
 * Light mode → Frosted white tint (0xCCE3E3E3)
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
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val context = LocalContext.current

    // Dark → Obsidian tint; Light → Frosted #E3E3E3
    val effectiveBg = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else {
        if (isDark) Color(0xCC08090E) else Color(0xBFFFFFFF)
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
        // 1. Background Image (Lower layer) - Instant synchronous rendering for drawable resources
        val imageModifier = Modifier
            .matchParentSize()
            .alpha(backgroundImageAlpha)
            .let { m ->
                if (enableBlur && blurRadius > 0.dp) {
                    m.blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
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
        } else if (backgroundImage != null) {
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

        // 2. Background Color Tint (Overlay layer)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush = finalBgBrush, shape = shape)
        )

        content()
    }
}

/**
 * Ambient Glass Surface component matching the exact rich luminous ambient background of the Info Sheet & Library.
 * Uses dynamic radial glow orbs (electric indigo and violet), an obsidian glass base, vertical sheen, and gradient border.
 */
@Composable
fun AmbientGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 0.5.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current

    // Signature vibrant ambient glow colors matching the Library & Info Sheet
    val ambientTopColor = if (isDark) Color(0x456366F1) else Color(0x35818CF8) // Vibrant Indigo Glow
    val ambientBottomColor = if (isDark) Color(0x358B5CF6) else Color(0x28A78BFA) // Soft Purple Glow
    val baseBackground = if (isDark) Color(0xF20F121C) else Color(0xF2F8FAFC)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
            .background(baseBackground)
    ) {
        // 1. Top-Left Vibrant Ambient Radial Glow
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientTopColor,
                            Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = 260f
                    ),
                    shape = shape
                )
        )

        // 2. Bottom-Right Ambient Radial Glow
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientBottomColor,
                            Color.Transparent
                        ),
                        center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        radius = 280f
                    ),
                    shape = shape
                )
        )

        // 3. Subtle Glass Sheen
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0x1CFFFFFF), Color(0x05FFFFFF), Color(0x28000000))
                        } else {
                            listOf(Color(0x35FFFFFF), Color(0x08FFFFFF), Color(0x10000000))
                        }
                    ),
                    shape = shape
                )
        )

        content()
    }
}
