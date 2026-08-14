package com.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Reusable vertical slider:
 * - Defaults to THIN track (2.5dp) exclusively for vertical sliders (EQ, Gain, Pitch).
 * - Thumb head is dead-center aligned with the track axis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -10f..10f,
    steps: Int = 0,
    enabled: Boolean = true,
    style: AppSliderStyle = AppSliderStyle.Solid,
    headStyle: AppSliderHeadStyle = AppSliderHeadStyle.Circular,
    thickness: AppSliderThickness = AppSliderThickness.Thin,
    accentColor: Color = Color(0xFF818CF8),
    activeTrackColor: Color = if (style == AppSliderStyle.Solid) Color(0xFF6366F1) else accentColor,
    inactiveTrackColor: Color = if (style == AppSliderStyle.Solid) Color(0x336366F1) else Color.White.copy(alpha = 0.15f),
    thumbColor: Color = if (style == AppSliderStyle.Solid) Color(0xFF818CF8) else Color.White,
    activeTickColor: Color = Color.White,
    inactiveTickColor: Color = if (style == AppSliderStyle.Solid) Color(0xFF475569) else Color.White.copy(alpha = 0.3f),
    customTrackHeight: Dp? = null,
    customThumbSize: DpSize? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val resolvedTrackHeight = customTrackHeight ?: if (thickness == AppSliderThickness.Thin) 2.5.dp else 6.dp
    val resolvedThumbDpSize = customThumbSize ?: when (headStyle) {
        AppSliderHeadStyle.Bar -> DpSize(6.dp, 14.dp)
        AppSliderHeadStyle.Circular -> if (thickness == AppSliderThickness.Thin) DpSize(10.dp, 10.dp) else DpSize(14.dp, 14.dp)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Layout(
            content = {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxSize(),
                    enabled = enabled,
                    valueRange = valueRange,
                    steps = steps,
                    onValueChangeFinished = onValueChangeFinished,
                    interactionSource = interactionSource,
                    thumb = {
                        val shape = if (headStyle == AppSliderHeadStyle.Bar) RoundedCornerShape(2.dp) else CircleShape
                        Box(
                            modifier = Modifier
                                .size(resolvedThumbDpSize.width, resolvedThumbDpSize.height)
                                .clip(shape)
                                .background(thumbColor)
                                .then(
                                    if (style == AppSliderStyle.Glossy) {
                                        Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)), shape)
                                    } else Modifier
                                )
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(resolvedTrackHeight),
                            colors = SliderDefaults.colors(
                                activeTrackColor = activeTrackColor,
                                inactiveTrackColor = inactiveTrackColor,
                                activeTickColor = activeTickColor,
                                inactiveTickColor = inactiveTickColor
                            )
                        )
                    }
                )
            }
        ) { measurables, constraints ->
            val sliderHeight = constraints.maxWidth
            val sliderWidth = constraints.maxHeight.coerceAtLeast(1)
            val sliderConstraints = androidx.compose.ui.unit.Constraints(
                minWidth = sliderWidth,
                maxWidth = sliderWidth,
                minHeight = 0,
                maxHeight = sliderHeight
            )
            val placeable = measurables.first().measure(sliderConstraints)
            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = (constraints.maxWidth - placeable.width) / 2
                val y = (constraints.maxHeight - placeable.height) / 2
                placeable.placeWithLayer(
                    x = x,
                    y = y,
                    zIndex = 0f,
                    layerBlock = {
                        rotationZ = -90f
                    }
                )
            }
        }
    }
}
