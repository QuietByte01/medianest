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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ThinSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = Color.White,
    inactiveTrackColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color.White,
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 6.dp
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbRadius * 2 + 16.dp)
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val newFrac = (offset.x / width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFrac * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val newFrac = (offset.x / width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFrac * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val newFrac = (change.position.x / width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFrac * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            val rangeLen = valueRange.endInclusive - valueRange.start
            val frac = if (rangeLen > 0) ((value - valueRange.start) / rangeLen).coerceIn(0f, 1f) else 0f
            val activeX = (width * frac).coerceIn(0f, width)

            val trackHeightPx = trackHeight.toPx()
            val thumbRadiusPx = thumbRadius.toPx()

            // 1. Inactive Track line (Centered vertically at centerY)
            drawLine(
                color = inactiveTrackColor,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )

            // 2. Active Track line with REAL DIFFUSED GLOW effect
            if (activeX > 0f) {
                drawIntoCanvas { canvas ->
                    // Soft diffused glow paint along active track
                    val glowPaint = android.graphics.Paint().apply {
                        color = activeTrackColor.copy(alpha = 0.35f).toArgb()
                        strokeWidth = trackHeightPx * 2.5f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        isAntiAlias = true
                        maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawLine(
                        0f, centerY,
                        activeX, centerY,
                        glowPaint
                    )

                    // Soft diffused glow around thumb
                    val thumbGlowPaint = android.graphics.Paint().apply {
                        color = activeTrackColor.copy(alpha = 0.45f).toArgb()
                        isAntiAlias = true
                        maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(
                        activeX, centerY,
                        thumbRadiusPx * 1.8f,
                        thumbGlowPaint
                    )
                }

                // Crisp core active line
                drawLine(
                    color = activeTrackColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeX, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )
            }

            // 3. Circle Thumb
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx + if (isDragging) 2f else 0f,
                center = Offset(activeX, centerY)
            )
        }
    }
}

