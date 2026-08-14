package com.medianest.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Android 13/14 Media Notification style Wavy Seekbar.
 * The active track oscillates smoothly when [isPlaying] is true,
 * reacting animatedly to music playback, and flattens out when paused.
 */
@Composable
fun WavySeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    activeColor: Color = Color(0xFFA8C7FA),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color.White,
    waveAmplitudeDp: Dp = 6.dp,
    waveLengthDp: Dp = 30.dp,
    strokeWidthDp: Dp = 9.dp,
    heightDp: Dp = 36.dp
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidthDp.toPx() }
    val waveAmplitudePx = with(density) { waveAmplitudeDp.toPx() }
    val waveLengthPx = with(density) { waveLengthDp.toPx() }
    val thumbRadiusPx = with(density) { 7.dp.toPx() }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val currentFrac = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    // Phase animation for wave movement when music plays
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_seek_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth transition between active wavy state and paused flat state
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "amplitudeFactor"
    )

    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val newFrac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFrac * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
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
                        val newFrac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFrac * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val activeX = (width * currentFrac).coerceIn(0f, width)

            val pillWidthPx = with(density) { (if (isDragging) 8.dp else 6.dp).toPx() }
            val pillHeightPx = with(density) { (if (isDragging) 24.dp else 20.dp).toPx() }

            // 1. Draw Inactive Track
            val inactiveStartX = (activeX + pillWidthPx / 2f).coerceAtMost(width)
            if (inactiveStartX < width) {
                val inactiveHeight = strokeWidthPx * 0.5f
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(inactiveStartX, centerY - inactiveHeight / 2f),
                    size = Size(width - inactiveStartX, inactiveHeight),
                    cornerRadius = CornerRadius(inactiveHeight / 2f, inactiveHeight / 2f)
                )
            }

            // 2. Draw Active Wavy Track
            if (activeX > 0f) {
                val path = Path()
                val step = 1.5f
                var x = 0f
                val effectiveAmp = waveAmplitudePx * amplitudeFactor

                path.moveTo(0f, centerY)

                while (x <= activeX) {
                    // Taper wave height near activeX so it joins the thumb center smoothly
                    val endDist = activeX - x
                    val taper = if (endDist < waveLengthPx * 0.8f) (endDist / (waveLengthPx * 0.8f)).coerceIn(0f, 1f) else 1f

                    val angle = (x / waveLengthPx) * (2 * PI) + phase
                    val y = centerY + (sin(angle).toFloat() * effectiveAmp * taper)

                    path.lineTo(x, y)
                    x += step
                }

                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Draw Iconic Android 13/14 System Media Notification Pill Thumb
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset((activeX - pillWidthPx / 2f).coerceIn(0f, width - pillWidthPx), centerY - pillHeightPx / 2f),
                size = Size(pillWidthPx, pillHeightPx),
                cornerRadius = CornerRadius(pillWidthPx / 2f, pillWidthPx / 2f)
            )
        }
    }
}
