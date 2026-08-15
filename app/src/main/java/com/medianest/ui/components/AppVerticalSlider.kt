package com.medianest.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Reusable vertical slider:
 * - Direct Canvas rendering for 100% dead-center circular head alignment.
 * - Solid/Glossy track and head without hollow cutout gaps.
 * - Responsive tap & vertical drag gesture detection.
 */
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
    customTrackHeight: Dp? = null,
    customThumbSize: DpSize? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val rangeSpan = (maxVal - minVal).coerceAtLeast(0.0001f)

    val trackWidthDp = customTrackHeight ?: if (thickness == AppSliderThickness.Thin) 3.5.dp else 6.dp
    val thumbSizeDp = customThumbSize ?: when (headStyle) {
        AppSliderHeadStyle.Bar -> DpSize(14.dp, 6.dp)
        AppSliderHeadStyle.Circular -> DpSize(13.dp, 13.dp)
    }

    Box(
        modifier = modifier
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val thumbRadius = thumbSizeDp.height.toPx() / 2f
                    val trackTop = thumbRadius
                    val trackBottom = size.height - thumbRadius
                    val usableHeight = (trackBottom - trackTop).coerceAtLeast(1f)

                    val clampedY = offset.y.coerceIn(trackTop, trackBottom)
                    val fraction = 1f - ((clampedY - trackTop) / usableHeight)
                    val newValue = minVal + fraction * rangeSpan
                    currentOnValueChange(newValue.coerceIn(minVal, maxVal))
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { currentOnValueChangeFinished?.invoke() },
                    onDragCancel = { currentOnValueChangeFinished?.invoke() }
                ) { change, _ ->
                    change.consume()
                    val thumbRadius = thumbSizeDp.height.toPx() / 2f
                    val trackTop = thumbRadius
                    val trackBottom = size.height - thumbRadius
                    val usableHeight = (trackBottom - trackTop).coerceAtLeast(1f)

                    val clampedY = change.position.y.coerceIn(trackTop, trackBottom)
                    val fraction = 1f - ((clampedY - trackTop) / usableHeight)
                    val newValue = minVal + fraction * rangeSpan
                    currentOnValueChange(newValue.coerceIn(minVal, maxVal))
                }
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f

            val thumbRadius = thumbSizeDp.height.toPx() / 2f
            val thumbRadiusX = thumbSizeDp.width.toPx() / 2f
            val trackTop = thumbRadius
            val trackBottom = height - thumbRadius
            val usableHeight = (trackBottom - trackTop).coerceAtLeast(1f)

            val trackWidthPx = trackWidthDp.toPx()
            val trackCornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)

            // 1. Draw Inactive Background Track (Dead Center)
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(centerX - trackWidthPx / 2f, trackTop),
                size = Size(trackWidthPx, usableHeight),
                cornerRadius = trackCornerRadius
            )

            // 2. Compute Thumb Center Y
            val clampedValue = value.coerceIn(minVal, maxVal)
            val fraction = ((clampedValue - minVal) / rangeSpan).coerceIn(0f, 1f)
            val thumbY = trackBottom - (fraction * usableHeight)

            // 3. Draw Active Fill
            if (minVal < 0f && maxVal > 0f) {
                // Centered 0dB mode (e.g. -10dB to +10dB EQ)
                val zeroFraction = ((0f - minVal) / rangeSpan).coerceIn(0f, 1f)
                val zeroY = trackBottom - (zeroFraction * usableHeight)

                val activeTop = minOf(thumbY, zeroY)
                val activeHeight = kotlin.math.abs(thumbY - zeroY).coerceAtLeast(0f)

                if (activeHeight > 0f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(centerX - trackWidthPx / 2f, activeTop),
                        size = Size(trackWidthPx, activeHeight),
                        cornerRadius = trackCornerRadius
                    )
                }

                // Subtle zero dB center tick
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(centerX - trackWidthPx - 1.5.dp.toPx(), zeroY),
                    end = Offset(centerX + trackWidthPx + 1.5.dp.toPx(), zeroY),
                    strokeWidth = 1.dp.toPx()
                )
            } else {
                // Bottom-to-thumb mode
                val activeHeight = (trackBottom - thumbY).coerceAtLeast(0f)
                if (activeHeight > 0f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(centerX - trackWidthPx / 2f, thumbY),
                        size = Size(trackWidthPx, activeHeight),
                        cornerRadius = trackCornerRadius
                    )
                }
            }

            // 4. Draw Thumb Head (Dead Center, No Hollow Artifacts)
            if (headStyle == AppSliderHeadStyle.Bar) {
                val barW = thumbSizeDp.width.toPx()
                val barH = thumbSizeDp.height.toPx()
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(centerX - barW / 2f, thumbY - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                if (style == AppSliderStyle.Glossy) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.8f),
                        topLeft = Offset(centerX - barW / 2f, thumbY - barH / 2f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = 0.75.dp.toPx())
                    )
                }
            } else {
                // Circular Solid/Glossy Head
                val radius = thumbRadius.coerceAtLeast(thumbRadiusX)
                drawCircle(
                    color = thumbColor,
                    radius = radius,
                    center = Offset(centerX, thumbY)
                )
                if (style == AppSliderStyle.Glossy) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = radius,
                        center = Offset(centerX, thumbY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }
}
