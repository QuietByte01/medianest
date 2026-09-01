package com.medianest.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppSwitchStyle {
    Solid,
    Glossy
}

/**
 * Reusable switch/toggle component supporting both Solid and Glossy visual styles.
 * When set to [AppSwitchStyle.Glossy], renders a transparent frosted glassmorphic switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Solid,
    accentColor: Color = Color.Unspecified,
    checkedThumbColor: Color = Color.White,
    checkedTrackColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.4f) else accentColor,
    uncheckedThumbColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.6f) else Color(0xFF717D96),
    uncheckedTrackColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.08f) else Color(0x3D2D3748),
    checkedBorderColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.6f) else Color.Transparent,
    uncheckedBorderColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.2f) else Color.Transparent
) {
    if (style == AppSwitchStyle.Glossy) {
        GlossySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = checkedThumbColor,
                checkedTrackColor = checkedTrackColor,
                checkedBorderColor = checkedBorderColor,
                uncheckedThumbColor = uncheckedThumbColor,
                uncheckedTrackColor = uncheckedTrackColor,
                uncheckedBorderColor = uncheckedBorderColor,
                disabledCheckedThumbColor = checkedThumbColor.copy(alpha = 0.4f),
                disabledCheckedTrackColor = checkedTrackColor.copy(alpha = 0.2f),
                disabledUncheckedThumbColor = uncheckedThumbColor.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = uncheckedTrackColor.copy(alpha = 0.2f)
            )
        )
    }
}

/**
 * A transparent, frosted glassmorphic toggle switch component.
 * Features a frosted glass capsule track with soft white stroke highlights,
 * pure white frosted tint when selected (no colors or labels), and a 3D frosted glass thumb knob.
 */
@Composable
fun GlossySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 52.dp,
    height: Dp = 28.dp,
    thumbSize: Dp = 22.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val thumbPosition by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glossy_switch_thumb_pos"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "glossy_switch_scale"
    )

    val padding = 3.dp

    Box(
        modifier = modifier
            .semantics { role = Role.Switch }
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = if (enabled) 1f else 0.4f
            }
            .shadow(
                elevation = if (checked) 6.dp else 3.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                onCheckedChange?.invoke(!checked)
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Transparent Frosted Glass Capsule Track Background & Border Highlight
        val trackGradient = if (checked) {
            // Selected state: Brighter frosted white tint
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.48f),
                    Color.White.copy(alpha = 0.32f),
                    Color.White.copy(alpha = 0.22f)
                )
            )
        } else {
            // Unselected state: Subtle clear frosted glass
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.25f),
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackGradient, shape = CircleShape)
                .drawWithContent {
                    drawContent()

                    // Top Glass Specular Streak Highlight
                    val glossHeight = size.height * 0.48f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (checked) 0.60f else 0.38f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = glossHeight
                        ),
                        size = Size(size.width, glossHeight)
                    )

                    // Soft White Glass Rim / Border Stroke
                    val strokeWidth = 1.2.dp.toPx()
                    val borderBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (checked) 0.85f else 0.55f),
                            Color.White.copy(alpha = if (checked) 0.45f else 0.25f),
                            Color.White.copy(alpha = if (checked) 0.25f else 0.10f)
                        )
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        style = Stroke(width = strokeWidth),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
        )

        // 2. 3D Frosted Glass Sphere / Disc Thumb Knob
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val maxTravel = maxWidth - thumbSize
            val currentOffset = maxTravel * thumbPosition

            Box(
                modifier = Modifier
                    .offset(x = currentOffset)
                    .size(thumbSize)
                    .shadow(
                        elevation = if (checked) 5.dp else 2.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.20f),
                        spotColor = Color.Black.copy(alpha = 0.25f)
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF8FAFC),
                                Color(0xFFE2E8F0),
                                Color(0xFFCBD5E1)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.45f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .drawWithContent {
                        drawContent()

                        // Specular Glare Ellipse on top-left of the Thumb knob
                        val glareRadius = size.minDimension * 0.30f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.90f),
                                    Color.White.copy(alpha = 0.0f)
                                ),
                                center = Offset(size.width * 0.35f, size.height * 0.32f),
                                radius = glareRadius
                            ),
                            radius = glareRadius,
                            center = Offset(size.width * 0.35f, size.height * 0.32f)
                        )
                    }
            )
        }
    }
}
