package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Dedicated Solid Glossy Surface component for Dialogs, BottomSheets, and Menus.
 * Leaves standard GlassSurface transparent for cards and list items.
 */
@Composable
fun SolidGlossySurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current

    // Obsidian translucent glossy colors (0xEE08090E top, 0xCC08090E bottom in dark; 0xF5FFFFFF top, 0xE6F1F5F9 bottom in light)
    val defaultBgTop = if (isDark) Color(0xEE08090E) else Color(0xF5FFFFFF)
    val defaultBgBottom = if (isDark) Color(0xCC08090E) else Color(0xBFFFFFFF)

    val defaultBorderTop = if (isDark) Color(0x40FFFFFF) else Color(0x60FFFFFF)
    val defaultBorderBottom = if (isDark) Color(0x15FFFFFF) else Color(0x15000000)

    val hasCustomBg = backgroundColor != Color.Unspecified

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = if (hasCustomBg) {
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = (backgroundColor.alpha * 0.85f).coerceIn(0f, 1f))
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            defaultBgTop,
                            defaultBgBottom
                        )
                    )
                },
                shape = shape
            )
            .border(
                width = borderWidth,
                brush = if (borderColor != Color.Unspecified) {
                    Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = 0.3f)))
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            defaultBorderTop,
                            defaultBorderBottom
                        )
                    )
                },
                shape = shape
            )
    ) {
        // Specular Top Sheen Highlight for authentic glossy feel
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 120f
                    ),
                    shape = shape
                )
        )

        content()
    }
}
