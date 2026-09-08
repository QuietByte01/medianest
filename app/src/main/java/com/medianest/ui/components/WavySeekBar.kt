package com.medianest.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.PI
import kotlin.math.sin

/**
 * Samsung One UI Media Notification style Dual-Wavy Seekbar.
 * Features fast liquid movement, adaptive wave dimensions across phones & tablets,
 * and graceful tapering on both ends.
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
    inactiveColor: Color = Color.White.copy(alpha = 0.10f),
    thumbColor: Color = Color.White,
    // Tablet-aware adaptive defaults: Larger waves and amplitude for tablets and wide screens
    waveAmplitudeDp: Dp = Dp.Unspecified,
    waveLengthDp: Dp = Dp.Unspecified,
    activeTrackHeightDp: Dp = 6.dp,
    inactiveTrackHeightDp: Dp = 4.dp,
    heightDp: Dp = 48.dp,
    showThumb: Boolean = true,
    fullTrackBackground: Boolean = false,
    customThumbRadiusDp: Dp = Dp.Unspecified
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600 || configuration.screenWidthDp >= 600

    // Adaptive wave sizing: On tablets / wide screens, use longer wavelengths and slightly taller waves
    // to prevent dense, repetitive tiny waves and present larger, smoother fluid waves.
    val actualWaveAmplitudeDp = if (waveAmplitudeDp.isSpecified) waveAmplitudeDp else if (isTablet) 8.5.dp else 6.5.dp
    val actualWaveLengthDp = if (waveLengthDp.isSpecified) waveLengthDp else if (isTablet) 96.dp else 56.dp

    val density = LocalDensity.current
    val waveAmplitudePx = with(density) { actualWaveAmplitudeDp.toPx() }
    val activeBaseRadiusPx = with(density) { activeTrackHeightDp.toPx() } / 2f
    val inactiveHeightPx = with(density) { inactiveTrackHeightDp.toPx() }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)

    // TWEAKED: Much faster animation speed (cut duration from 3500ms to 1600ms)
    val infiniteTransition = rememberInfiniteTransition(label = "samsung_dual_wave")
    val basePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (4 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth transition between wavy (playing) and flat (paused)
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "amplitudeFactor"
    )

    var isDragging by remember { mutableStateOf(false) }
    var internalValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (!isDragging) internalValue = value
    }

    val currentFrac = ((internalValue - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val defaultThumbRadius = if (isDragging) (if (isTablet) 11.dp else 10.dp) else (if (isTablet) 8.dp else 7.dp)
    val targetThumbRadius = if (customThumbRadiusDp.isSpecified) (if (isDragging) customThumbRadiusDp * 1.3f else customThumbRadiusDp) else defaultThumbRadius

    val thumbRadius by animateDpAsState(
        targetValue = targetThumbRadius,
        label = "ThumbRadius"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    down.consume()

                    val updateValue = { x: Float, trackPaddingPx: Float ->
                        val trackWidth = size.width - (trackPaddingPx * 2)
                        val newFrac = ((x - trackPaddingPx) / trackWidth).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFrac * rangeSpan
                        internalValue = newValue
                        onValueChange(newValue)
                    }

                    val padding = if (showThumb) thumbRadius.toPx() else 0f
                    updateValue(down.position.x, padding)

                    drag(down.id) { change ->
                        change.consume()
                        updateValue(change.position.x, padding)
                    }

                    isDragging = false
                    onValueChangeFinished?.invoke()
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val centerY = size.height / 2f

            val trackPadding = if (showThumb) thumbRadius.toPx() else 0f
            val trackWidth = width - trackPadding * 2
            val activeX = trackPadding + (trackWidth * currentFrac)

            // 1. INACTIVE TRACK
            val inactiveRadius = inactiveHeightPx / 2f
            val (startX, trackWidthVal) = if (fullTrackBackground) {
                trackPadding to (width - trackPadding * 2)
            } else {
                activeX to (width - trackPadding - activeX)
            }
            
            if (trackWidthVal > 0) {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(startX, centerY - inactiveRadius),
                    size = Size(trackWidthVal, inactiveHeightPx),
                    cornerRadius = CornerRadius(inactiveRadius, inactiveRadius)
                )
            }

            // 2. HELPER FUNCTION TO DRAW FILLED WAVES
            fun drawLiquidWave(
                wavelengthDp: Dp,
                amplitudeMultiplier: Float,
                phaseShift: Float,
                waveColor: Color
            ) {
                val path = Path()
                val wavelengthPx = wavelengthDp.toPx()
                val effectiveAmp = waveAmplitudePx * amplitudeFactor * amplitudeMultiplier

                path.moveTo(activeX, centerY + activeBaseRadiusPx)
                path.lineTo(trackPadding, centerY + activeBaseRadiusPx)

                path.arcTo(
                    rect = Rect(
                        left = trackPadding - activeBaseRadiusPx,
                        top = centerY - activeBaseRadiusPx,
                        right = trackPadding + activeBaseRadiusPx,
                        bottom = centerY + activeBaseRadiusPx
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )

                val step = 2.dp.toPx()
                var x = trackPadding

                // Scaled taper zones for phone vs tablet
                val rightTaperZone = if (isTablet) 56.dp.toPx() else 40.dp.toPx()
                val leftTaperZone = if (isTablet) 64.dp.toPx() else 48.dp.toPx()

                while (x <= activeX) {
                    val rightDist = activeX - x
                    val leftDist = x - trackPadding

                    val rightTaperRaw = (rightDist / rightTaperZone).coerceIn(0f, 1f)
                    val leftTaperRaw = (leftDist / leftTaperZone).coerceIn(0f, 1f)

                    // Smoothstep equation
                    val rightTaper = rightTaperRaw * rightTaperRaw * (3 - 2 * rightTaperRaw)
                    val leftTaper = leftTaperRaw * leftTaperRaw * (3 - 2 * leftTaperRaw)
                    val overallTaper = rightTaper * leftTaper

                    val angle = (x / wavelengthPx) * (2 * PI) - phaseShift

                    val hillMultiplier = (sin(angle).toFloat() + 1f) / 2f
                    val topY = centerY - activeBaseRadiusPx - (hillMultiplier * effectiveAmp * overallTaper)

                    path.lineTo(x, topY)
                    x += step
                }

                path.lineTo(activeX, centerY - activeBaseRadiusPx)
                path.close()

                drawPath(path = path, color = waveColor)
            }

            if (activeX > trackPadding) {
                // 3. DRAW BACK WAVE (Translucent, faster)
                drawLiquidWave(
                    wavelengthDp = actualWaveLengthDp * 0.85f,
                    amplitudeMultiplier = 0.85f,
                    phaseShift = basePhase * 1.4f,
                    waveColor = activeColor.copy(alpha = 0.30f)
                )

                // 4. DRAW FRONT WAVE (Solid @ 50% opacity)
                drawLiquidWave(
                    wavelengthDp = actualWaveLengthDp,
                    amplitudeMultiplier = 1.0f,
                    phaseShift = basePhase,
                    waveColor = activeColor.copy(alpha = 0.50f)
                )
            }

            // 5. THUMB (Classic Circular Thumb)
            if (showThumb) {
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius.toPx(),
                    center = Offset(activeX, centerY)
                )
            }
        }
    }
}