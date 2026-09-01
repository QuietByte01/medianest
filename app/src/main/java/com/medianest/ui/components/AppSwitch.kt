package com.medianest.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppSwitchStyle {
    Solid,
    Glossy
}

/**
 * Reusable switch/toggle component supporting both Solid and Glossy visual styles.
 * When set to [AppSwitchStyle.Glossy], renders a custom 3D frosted glassmorphic switch with "ON" / "OFF" labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Solid,
    showLabels: Boolean = true,
    accentColor: Color = Color(0xFF6366F1),
    checkedThumbColor: Color = Color.White,
    checkedTrackColor: Color = if (style == AppSwitchStyle.Glossy) accentColor.copy(alpha = 0.4f) else accentColor,
    uncheckedThumbColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.6f) else Color(0xFF717D96),
    uncheckedTrackColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.08f) else Color(0x3D2D3748),
    checkedBorderColor: Color = if (style == AppSwitchStyle.Glossy) accentColor.copy(alpha = 0.5f) else Color.Transparent,
    uncheckedBorderColor: Color = if (style == AppSwitchStyle.Glossy) Color.White.copy(alpha = 0.15f) else Color.Transparent
) {
    if (style == AppSwitchStyle.Glossy) {
        GlossySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            showLabels = showLabels,
            accentColor = accentColor
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
 * A glossy, 3D frosted glass-like toggle switch component with inner "ON" / "OFF" labels,
 * glass reflection highlights, dynamic glass border outlines, and smooth spring thumb physics.
 */
@Composable
fun GlossySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 76.dp,
    height: Dp = 34.dp,
    thumbSize: Dp = 26.dp,
    showLabels: Boolean = true,
    accentColor: Color = Color(0xFF6366F1)
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
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "glossy_switch_scale"
    )

    val offLabelAlpha by animateFloatAsState(
        targetValue = if (!checked) 1f else 0f,
        animationSpec = tween(150),
        label = "glossy_switch_off_alpha"
    )

    val onLabelAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(150),
        label = "glossy_switch_on_alpha"
    )

    val padding = 4.dp

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
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
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
        // 1. Frosted Glass Capsule Track Background & Border Outline
        val trackGradient = if (checked && accentColor != Color.Unspecified && accentColor != Color(0xFF6366F1)) {
            Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.40f),
                    accentColor.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.12f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.38f),
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.12f)
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackGradient, shape = CircleShape)
                .drawWithContent {
                    drawContent()

                    // Top Glass Specular Reflection Streak
                    val glossHeight = size.height * 0.45f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = glossHeight
                        ),
                        size = Size(size.width, glossHeight)
                    )

                    // Bright Glass Outer Rim / Border Stroke
                    val strokeWidth = 1.5.dp.toPx()
                    val borderBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.20f)
                        )
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        style = Stroke(width = strokeWidth),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
        )

        // 2. "ON" / "OFF" Text Labels Inside Track
        if (showLabels) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                // "ON" Label (Visible on left when checked)
                Text(
                    text = "ON",
                    color = Color.White.copy(alpha = onLabelAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                // "OFF" Label (Visible on right when unchecked)
                Text(
                    text = "OFF",
                    color = Color.White.copy(alpha = offLabelAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        // 3. 3D Glass Sphere / Disc Thumb Knob
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
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF1F5F9),
                                Color(0xFFCBD5E1)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.40f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .drawWithContent {
                        drawContent()

                        // Specular Glare Ellipse on top-left of the Thumb knob
                        val glareRadius = size.minDimension * 0.32f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.95f),
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
