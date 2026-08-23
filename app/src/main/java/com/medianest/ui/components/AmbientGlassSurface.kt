package com.medianest.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Universal Ambient Glass Surface component matching the signature Info Sheet / Ambient background system.
 *
 * Layers:
 * 1. Base Layer: Obsidian (#0C0E14) in dark mode, Frosted White (#F8FAFC) in light mode.
 * 2. Blurred Artwork Layer: 64dp blurred thumbnail/art at alpha = 0.32f (when [backgroundImage] is provided).
 * 3. Dynamic Glow Orbs: Top-left and bottom-right radial gradient glow orbs dynamically extracted from [hue] or [backgroundImage].
 * 4. Glass Sheen Layer: Vertical highlight sheen.
 * 5. Glass Border: 0.5dp faded vertical gradient border.
 */
@Composable
fun AmbientGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundImage: Any? = null,
    hue: Float? = null,
    borderWidth: Dp = 0.5.dp,
    containerColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    var activeHue by remember(backgroundImage, hue) { mutableStateOf(hue) }

    LaunchedEffect(backgroundImage, hue) {
        if (hue != null) {
            activeHue = hue
        } else if (backgroundImage is Uri) {
            activeHue = extractBaseHueFromArt(context, backgroundImage)
        } else if (backgroundImage is String && backgroundImage.isNotBlank()) {
            activeHue = runCatching { extractBaseHueFromArt(context, Uri.parse(backgroundImage)) }.getOrNull()
        } else {
            activeHue = null
        }
    }

    val ambientTopColor = remember(activeHue, isDark) {
        if (activeHue != null) Color.hsv(activeHue!!, 0.65f, 0.40f, 0.35f)
        else if (isDark) Color(0x354A3B2C)
        else Color(0x35E2E8F0)
    }

    val ambientBottomColor = remember(activeHue, isDark) {
        if (activeHue != null) Color.hsv((activeHue!! + 25f) % 360f, 0.55f, 0.28f, 0.30f)
        else if (isDark) Color(0x301E2838)
        else Color(0x30CBD5E1)
    }

    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)

    val baseBg = when {
        containerColor != Color.Unspecified && containerColor != Color.Transparent -> containerColor
        isDark -> Color(0xFF0C0E14)
        else -> Color(0xFFF8FAFC)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .background(baseBg)
    ) {
        // 1. Dynamic Blurred Background Thumbnail/Art Layer
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
                    .blur(64.dp)
                    .graphicsLayer { alpha = 0.32f }
            )
        }

        // 2. Ambient Radial Glow Orbs (Top-Left & Bottom-Right)
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
                        radius = 280f
                    ),
                    shape = shape
                )
        )

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
                        radius = 320f
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
                            listOf(Color(0x18FFFFFF), Color(0x35000000))
                        } else {
                            listOf(Color(0x30FFFFFF), Color(0x10000000))
                        }
                    ),
                    shape = shape
                )
        )

        content()
    }
}
