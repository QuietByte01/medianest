package com.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class AppSliderStyle {
    Solid,
    Glossy
}

enum class AppSliderHeadStyle {
    Circular,
    Bar
}

enum class AppSliderThickness {
    Thin,
    Thick
}

/**
 * Reusable horizontal slider:
 * - Defaults to THICK track (6dp) for all horizontal sliders.
 * - Thumb head sits centered on track bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    style: AppSliderStyle = AppSliderStyle.Solid,
    headStyle: AppSliderHeadStyle = AppSliderHeadStyle.Circular,
    thickness: AppSliderThickness = AppSliderThickness.Thick,
    accentColor: Color = Color(0xFF818CF8),
    activeTrackColor: Color = if (style == AppSliderStyle.Solid) accentColor.copy(alpha = 0.95f) else accentColor,
    inactiveTrackColor: Color = if (style == AppSliderStyle.Solid) accentColor.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.15f),
    thumbColor: Color = if (style == AppSliderStyle.Solid) accentColor else Color.White,
    activeTickColor: Color = Color.White,
    inactiveTickColor: Color = if (style == AppSliderStyle.Solid) Color(0xFF475569) else Color.White.copy(alpha = 0.3f),
    customTrackHeight: Dp? = null,
    customThumbSize: DpSize? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveModifier = if (modifier == Modifier) Modifier.height(24.dp) else modifier

    val resolvedTrackHeight = customTrackHeight ?: if (thickness == AppSliderThickness.Thin) 3.dp else 6.dp
    val resolvedThumbDpSize = customThumbSize ?: when (headStyle) {
        AppSliderHeadStyle.Bar -> DpSize(8.dp, 18.dp)
        AppSliderHeadStyle.Circular -> if (thickness == AppSliderThickness.Thin) DpSize(10.dp, 10.dp) else DpSize(16.dp, 16.dp)
    }

    if (style == AppSliderStyle.Glossy || headStyle == AppSliderHeadStyle.Bar || thickness == AppSliderThickness.Thin || customThumbSize != null || customTrackHeight != null) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = effectiveModifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            thumb = {
                val shape = if (headStyle == AppSliderHeadStyle.Bar) RoundedCornerShape(2.5.dp) else CircleShape
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
                    modifier = Modifier.height(resolvedTrackHeight)
                        .then(
                            if (style == AppSliderStyle.Glossy) {
                                Modifier.border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(resolvedTrackHeight / 2))
                            } else Modifier
                        ),
                    colors = SliderDefaults.colors(
                        activeTrackColor = if (style == AppSliderStyle.Glossy) {
                            accentColor.copy(alpha = 0.85f)
                        } else activeTrackColor,
                        inactiveTrackColor = if (style == AppSliderStyle.Glossy) {
                            Color.White.copy(alpha = 0.12f)
                        } else inactiveTrackColor,
                        activeTickColor = activeTickColor,
                        inactiveTickColor = inactiveTickColor
                    )
                )
            }
        )
    } else {
        // Standard thick solid native slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = effectiveModifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor,
                activeTickColor = activeTickColor,
                inactiveTickColor = inactiveTickColor
            )
        )
    }
}
