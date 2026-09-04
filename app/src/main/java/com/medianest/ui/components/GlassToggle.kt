package com.medianest.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Frosted Glass Toggle Switch.
 *
 * Faithfully mirrors the HTML/CSS glass switch specification:
 * - OFF State:
 *   - Track: Fully transparent / crystal glass with subtle 1px border `rgba(255, 255, 255, 0.55)`
 *     and dual inner highlight / soft shadow.
 *   - Knob: Flat translucent glass `rgba(245, 248, 252, 0.38)` with `rgba(255, 255, 255, 0.62)` border
 *     and subtle inner sheen.
 * - ON State:
 *   - Track: Frosted glass gradient (180deg vertical `rgba(255,255,255,0.30)` to `rgba(255,255,255,0.12)`),
 *     enhanced inset specular highlights, and optional hardware backdrop blur.
 *   - Knob: Smooth transition using `cubic-bezier(0.4, 0, 0.2, 1)`.
 * - Press Animation:
 *   - Interactive scale down to 0.96 on touch/active state.
 *
 * Dimensions:
 * Default standard size is 52.dp x 27.dp (matching standard mobile switch proportions),
 * or customizable with [width] and [height].
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 52.dp,
    height: Dp = 27.dp,
    backdropState: BackdropBlurState? = LocalBackdropState.current,
    enableBackdropBlur: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animation curve: cubic-bezier(0.4, 0, 0.2, 1)
    val standardCubicBezier = remember { CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) }

    // Progress: 0f (OFF) -> 1f (ON)
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = standardCubicBezier),
        label = "glassToggleProgress"
    )

    // Press scale: 0.96 when active/pressed
    val knobScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150, easing = standardCubicBezier),
        label = "glassToggleScale"
    )

    val shape = RoundedCornerShape(percent = 50)

    // Track gradient: OFF is transparent; ON is vertical gradient 30% -> 12% white
    val onGradientTop = Color(0x4DFFFFFF)    // rgba(255, 255, 255, 0.30)
    val onGradientBottom = Color(0x1FFFFFFF) // rgba(255, 255, 255, 0.12)

    val trackBorderColor = Color(0x8CFFFFFF) // rgba(255, 255, 255, 0.55)

    // Inner shadow & sheen colors
    val insetTopOff = Color(0x73FFFFFF)      // rgba(255, 255, 255, 0.45)
    val insetTopOn = Color(0xBFFFFFFF)       // rgba(255, 255, 255, 0.75)
    val insetBottomColor = Color(0x1F7887A0) // rgba(120, 135, 160, 0.12)
    val dropShadowColor = Color(0x296E7D96)  // rgba(110, 125, 150, 0.16)

    val effectiveAlpha = if (enabled) 1f else 0.45f

    val clickableModifier = if (onCheckedChange != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null, // Custom glass click feel without material ripple
            enabled = enabled,
            role = Role.Switch,
            onClick = { onCheckedChange(!checked) }
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .then(clickableModifier)
            .clip(shape)
            .then(
                if (enableBackdropBlur && backdropState != null && checkProgress > 0.05f) {
                    Modifier.backdropReceiver(
                        state = backdropState,
                        blurRadius = (10.dp * checkProgress),
                        tint = Color.Transparent,
                        baseColor = Color.Transparent
                    )
                } else Modifier
            )
            .drawBehind {
                val cornerRadius = size.height / 2f

                // 1. Drop shadow / ambient glow underneath
                drawRoundRect(
                    color = dropShadowColor.copy(alpha = dropShadowColor.alpha * effectiveAlpha),
                    topLeft = Offset(0f, 2f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                )

                // 2. Track Background (ON: Frosted Glass Gradient, OFF: Transparent)
                if (checkProgress > 0f) {
                    val currentTop = onGradientTop.copy(alpha = onGradientTop.alpha * checkProgress * effectiveAlpha)
                    val currentBottom = onGradientBottom.copy(alpha = onGradientBottom.alpha * checkProgress * effectiveAlpha)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(currentTop, currentBottom),
                            startY = 0f,
                            endY = size.height
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                    )
                }

                // 3. Inset Highlights & Inner Rim
                val topHighlightAlpha = (insetTopOff.alpha + (insetTopOn.alpha - insetTopOff.alpha) * checkProgress) * effectiveAlpha
                // Top inner highlight stroke
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = topHighlightAlpha),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.45f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 1.2f)
                )

                // Bottom subtle inner shade stroke
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            insetBottomColor.copy(alpha = insetBottomColor.alpha * effectiveAlpha)
                        ),
                        startY = size.height * 0.55f,
                        endY = size.height
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 1.0f)
                )
            }
            .border(
                width = 1.dp,
                color = trackBorderColor.copy(alpha = trackBorderColor.alpha * effectiveAlpha),
                shape = shape
            )
    ) {
        // Track interior padding & knob sizing based on the 170x88 / 72px knob spec
        val paddingY = height * (7f / 88f)
        val paddingX = width * (7f / 170f)
        val knobSize = height - (paddingY * 2)

        val totalTravel = width - (paddingX * 2) - knobSize

        // Horizontal displacement
        val knobOffsetX = paddingX + (totalTravel * checkProgress)

        // Knob Glass Styling:
        // Background: rgba(245, 248, 252, 0.38)
        val knobBgColor = Color(0x61F5F8FC)
        val knobBorderColor = Color(0x9EFFFFFF) // rgba(255, 255, 255, 0.62)
        val knobHighlightTop = Color(0x8CFFFFFF) // rgba(255, 255, 255, 0.55)
        val knobInnerShade = Color(0x1496A5B9)   // rgba(150, 165, 185, 0.08)

        Box(
            modifier = Modifier
                .offset(x = knobOffsetX, y = paddingY)
                .requiredSize(knobSize)
                .scale(knobScale)
                .clip(CircleShape)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Base translucent flat glass fill
                    drawCircle(
                        color = knobBgColor.copy(alpha = knobBgColor.alpha * effectiveAlpha),
                        radius = radius,
                        center = center
                    )

                    // Subtle inner sheen: top-left specular highlight
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                knobHighlightTop.copy(alpha = knobHighlightTop.alpha * effectiveAlpha),
                                Color.Transparent,
                                knobInnerShade.copy(alpha = knobInnerShade.alpha * effectiveAlpha)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        radius = radius - 0.5f,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }
                .border(
                    width = 1.dp,
                    color = knobBorderColor.copy(alpha = knobBorderColor.alpha * effectiveAlpha),
                    shape = CircleShape
                )
        )
    }
}
