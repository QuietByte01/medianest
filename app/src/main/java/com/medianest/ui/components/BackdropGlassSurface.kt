package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Lightweight, dedicated Frosted Glass Surface for overlays, dialogs, dropdown menus,
 * bottom sheets, and side panels where hardware backdrop blur is applied.
 *
 * Neutralized obsidian glass base prevents ambient background hue orbs from bleeding through.
 */
@Composable
fun BackdropGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 0.5.dp,
    enableBlur: Boolean = true,
    blurRadius: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    baseColor: Color = Color.Transparent,
    backdropState: BackdropBlurState? = LocalBackdropState.current,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current

    val effectiveTint = if (tint != Color.Unspecified) {
        tint
    } else {
        Color(0x660A0C10)
    }

    val effectiveBg = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else {
        if (isDark) Color(0x330F1015) else Color(0x33FFFFFF)
    }

    val effectiveBorder = if (borderColor != Color.Unspecified) {
        borderColor
    } else {
        if (isDark) Color(0x38FFFFFF) else Color(0x28000000)
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            effectiveBg,
            effectiveBg.copy(alpha = (effectiveBg.alpha * 0.7f).coerceIn(0f, 1f))
        )
    )

    val surfaceModifier = Modifier
        .clip(shape)
        .then(
            if (enableBlur && backdropState != null) {
                Modifier.backdropReceiver(
                    state = backdropState,
                    blurRadius = blurRadius,
                    tint = effectiveTint,
                    baseColor = baseColor
                )
            } else {
                // When backdropState is null (such as in platform Dialog windows where the OS
                // window blur applies blur to the screen behind), draw translucent tint over
                // baseColor without an opaque gradient background occluding the blurred window.
                Modifier
                    .background(baseColor, shape = shape)
                    .drawBehind {
                        drawRect(color = effectiveTint)
                    }
            }
        )
        .then(modifier)
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
//        .background(brush = bgBrush, shape = shape)

    Box(
        modifier = surfaceModifier,
        content = content
    )
}
