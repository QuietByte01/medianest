package com.medianest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
 * - Direct Canvas rendering for consistent solid/glossy look.
 * - Fixes interaction halos (hollow head) by using custom drawing.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    drawTicks: Boolean = steps > 0,
    enabled: Boolean = true,
    style: AppSliderStyle = AppSliderStyle.Solid,
    headStyle: AppSliderHeadStyle = AppSliderHeadStyle.Circular,
    thickness: AppSliderThickness = AppSliderThickness.Thick,
    accentColor: Color = Color(0xFF818CF8),
    activeTrackColor: Color = if (style == AppSliderStyle.Solid) accentColor else accentColor,
    inactiveTrackColor: Color = if (style == AppSliderStyle.Solid) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.15f),
    thumbColor: Color = if (style == AppSliderStyle.Solid) accentColor else Color.White,
    customTrackHeight: Dp? = null,
    customThumbSize: DpSize? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val rangeSpan = (maxVal - minVal).coerceAtLeast(0.0001f)

    var localValue by remember(value) { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        localValue = value
    }

    val trackHeightDp = customTrackHeight ?: if (thickness == AppSliderThickness.Thin) 4.dp else 10.dp
    val thumbSizeDp = customThumbSize ?: when (headStyle) {
        AppSliderHeadStyle.Bar -> DpSize(8.dp, 16.dp)
        AppSliderHeadStyle.Circular -> if (thickness == AppSliderThickness.Thin) DpSize(10.dp, 10.dp) else DpSize(14.dp, 14.dp)
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .fillMaxWidth()
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val thumbRadius = thumbSizeDp.width.toPx() / 2f
                    val trackStart = thumbRadius
                    val trackEnd = size.width - thumbRadius
                    val usableWidth = (trackEnd - trackStart).coerceAtLeast(1f)

                    val clampedX = offset.x.coerceIn(trackStart, trackEnd)
                    val fraction = (clampedX - trackStart) / usableWidth
                    val newValue = (minVal + fraction * rangeSpan).coerceIn(minVal, maxVal)
                    localValue = newValue
                    currentOnValueChange(newValue)
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { currentOnValueChangeFinished?.invoke() },
                    onDragCancel = { currentOnValueChangeFinished?.invoke() }
                ) { change, _ ->
                    change.consume()
                    val thumbRadius = thumbSizeDp.width.toPx() / 2f
                    val trackStart = thumbRadius
                    val trackEnd = size.width - thumbRadius
                    val usableWidth = (trackEnd - trackStart).coerceAtLeast(1f)

                    val newX = (change.position.x).coerceIn(trackStart, trackEnd)
                    val fraction = (newX - trackStart) / usableWidth
                    val newValue = (minVal + fraction * rangeSpan).coerceIn(minVal, maxVal)
                    localValue = newValue
                    currentOnValueChange(newValue)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            val thumbRadius = thumbSizeDp.width.toPx() / 2f
            val thumbRadiusY = thumbSizeDp.height.toPx() / 2f
            val trackStart = thumbRadius
            val trackEnd = width - thumbRadius
            val usableWidth = (trackEnd - trackStart).coerceAtLeast(1f)

            val trackHeightPx = trackHeightDp.toPx()
            val trackCornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            // 1. Draw Inactive Background Track (Full Width)
            if (style == AppSliderStyle.Glossy) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            inactiveTrackColor.copy(alpha = 0.25f),
                            inactiveTrackColor.copy(alpha = 0.1f)
                        )
                    ),
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(width, trackHeightPx),
                    cornerRadius = trackCornerRadius
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(width, trackHeightPx),
                    cornerRadius = trackCornerRadius,
                    style = Stroke(width = 0.5.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(width, trackHeightPx),
                    cornerRadius = trackCornerRadius
                )
            }

            // 1.5 Draw Ticks (Steps) - Only if drawTicks is true
            if (drawTicks) {
                val tickColor = if (style == AppSliderStyle.Glossy) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.65f)
                val totalPositions = steps + 2 // 0, intermediate steps, and max
                for (i in 0 until totalPositions) {
                    val tickFrac = i.toFloat() / (totalPositions - 1)
                    val tickX = trackStart + (tickFrac * usableWidth)
                    drawCircle(
                        color = tickColor,
                        radius = 2.0.dp.toPx(),
                        center = Offset(tickX, centerY)
                    )
                }
            }

            // 2. Compute Thumb Center X
            val clampedValue = localValue.coerceIn(minVal, maxVal)
            val fraction = ((clampedValue - minVal) / rangeSpan).coerceIn(0f, 1f)
            val thumbX = trackStart + (fraction * usableWidth)

            // 3. Draw Active Fill
            if (minVal < 0f && maxVal > 0f) {
                // Centered 0 mode
                val zeroFraction = ((0f - minVal) / rangeSpan).coerceIn(0f, 1f)
                val zeroX = trackStart + (zeroFraction * usableWidth)

                val activeLeft = minOf(thumbX, zeroX)
                val activeWidth = kotlin.math.abs(thumbX - zeroX).coerceAtLeast(0f)

                if (activeWidth > 0f) {
                    if (style == AppSliderStyle.Glossy) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    activeTrackColor,
                                    activeTrackColor.copy(alpha = 0.7f)
                                )
                            ),
                            topLeft = Offset(activeLeft, centerY - trackHeightPx / 2f),
                            size = Size(activeWidth, trackHeightPx),
                            cornerRadius = trackCornerRadius
                        )
                    } else {
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = Offset(activeLeft, centerY - trackHeightPx / 2f),
                            size = Size(activeWidth, trackHeightPx),
                            cornerRadius = trackCornerRadius
                        )
                    }
                }

                // Subtle zero center tick
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(zeroX, centerY - trackHeightPx - 1.5.dp.toPx()),
                    end = Offset(zeroX, centerY + trackHeightPx + 1.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            } else {
                // Start-to-thumb mode
                val activeWidth = thumbX.coerceAtLeast(0f)
                if (activeWidth > 0f) {
                    if (style == AppSliderStyle.Glossy) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    activeTrackColor,
                                    activeTrackColor.copy(alpha = 0.7f)
                                )
                            ),
                            topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                            size = Size(activeWidth, trackHeightPx),
                            cornerRadius = trackCornerRadius
                        )
                    } else {
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                            size = Size(activeWidth, trackHeightPx),
                            cornerRadius = trackCornerRadius
                        )
                    }
                }
            }

            // 4. Draw Thumb Head
            if (headStyle == AppSliderHeadStyle.Bar) {
                val barW = thumbSizeDp.width.toPx()
                val barH = thumbSizeDp.height.toPx()
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbX - barW / 2f, centerY - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                if (style == AppSliderStyle.Glossy) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.8f),
                        topLeft = Offset(thumbX - barW / 2f, centerY - barH / 2f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = 0.75.dp.toPx())
                    )
                }
            } else {
                val radius = thumbRadius.coerceAtLeast(thumbRadiusY)
                drawCircle(
                    color = thumbColor,
                    radius = radius,
                    center = Offset(thumbX, centerY)
                )
                if (style == AppSliderStyle.Glossy) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = radius,
                        center = Offset(thumbX, centerY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }
}
