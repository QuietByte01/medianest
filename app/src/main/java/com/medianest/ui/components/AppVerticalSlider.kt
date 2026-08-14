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

    val resolvedTrackHeight = customTrackHeight ?: 8.dp
    val resolvedThumbDpSize = customThumbSize ?: when (headStyle) {
        AppSliderHeadStyle.Bar -> DpSize(14.dp, 8.dp) // unrotated: physical height=14, physical width=8
        AppSliderHeadStyle.Circular -> DpSize(16.dp, 16.dp)
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
                    modifier = Modifier.fillMaxWidth(), // In Layout, this is the unrotated width (physical height)
                    enabled = enabled,
                    valueRange = valueRange,
                    steps = steps,
                    onValueChangeFinished = onValueChangeFinished,
                    interactionSource = interactionSource,
                    thumb = {
                        val shape = if (headStyle == AppSliderHeadStyle.Bar) RoundedCornerShape(1.5.dp) else CircleShape
                        Box(
                            modifier = Modifier
                                .size(resolvedThumbDpSize.width, resolvedThumbDpSize.height)
                                .clip(shape)
                                .background(thumbColor)
                                .then(
                                    if (style == AppSliderStyle.Glossy) {
                                        Modifier.border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.6f)), shape)
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
            // In a vertical slider rotated -90deg:
            // physical width = constraints.maxWidth
            // physical height = constraints.maxHeight
            
            // The unrotated slider's width should be the physical height.
            val sliderUnrotatedWidth = constraints.maxHeight
            // The unrotated slider's height should be the physical width.
            val sliderUnrotatedHeight = constraints.maxWidth.coerceAtLeast(1)
            
            val sliderConstraints = androidx.compose.ui.unit.Constraints.fixed(
                width = sliderUnrotatedWidth,
                height = sliderUnrotatedHeight
            )
            val placeable = measurables.first().measure(sliderConstraints)
            
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Rotated width is placeable.height
                // Rotated height is placeable.width
                val x = (constraints.maxWidth - placeable.width) / 2
                val y = (constraints.maxHeight - placeable.height) / 2
                
                placeable.placeWithLayer(
                    x = x,
                    y = y,
                    zIndex = 0f,
                    layerBlock = {
                        rotationZ = -90f
                        // By default transformOrigin is (0.5f, 0.5f)
                    }
                )
            }
        }
    }
}
