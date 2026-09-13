package com.medianest.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

data class HeartBubbleParticle(
    val id: Int,
    val initialXOffset: Float,     // Horizontal starting spread
    val maxUpwardDistance: Float,  // Total vertical rise
    val swayAmplitude: Float,      // Left-right horizontal sway amount
    val swayFrequency: Float,      // Sway speed / cycle count
    val size: Dp,
    val color: Color,
    val initialRotation: Float,
    val delayFraction: Float       // Staggered release (0.0 to 0.25 of total duration)
)

/**
 * Generates an artistic dynamic harmonic palette based on the extracted album art hue.
 * When hue is null, falls back to Samsung Music's signature rose/coral/pink palette.
 */
fun generateHarmonicHeartPalette(hue: Float?): List<Color> {
    if (hue == null) {
        return listOf(
            Color(0xFFFF2D55),
            Color(0xFFFF4081),
            Color(0xFFFF5252),
            Color(0xFFFF79B0),
            Color(0xFFE056FD),
            Color(0xFFFF6B81),
            Color(0xFFFFAAA6)
        )
    }
    val h = (hue % 360f + 360f) % 360f
    return listOf(
        Color.hsv(h, 0.92f, 1.0f),                             // 1. Primary vivid dynamic hue
        Color.hsv((h + 18f) % 360f, 0.85f, 0.98f),             // 2. Warm analog dynamic hue
        Color.hsv((h - 18f + 360f) % 360f, 0.86f, 0.98f),      // 3. Cool analog dynamic hue
        Color.hsv(h, 0.52f, 1.0f),                             // 4. Pastel luminous tint
        Color.hsv((h + 38f) % 360f, 0.90f, 0.96f),             // 5. Harmonic dynamic accent
        Color.hsv((h + 325f) % 360f, 0.84f, 1.0f),             // 6. Radiant companion hue
        Color.hsv(h, 0.72f, 0.95f),                             // 7. Rich glow tone
        Color.hsv((h + 55f) % 360f, 0.78f, 1.0f)               // 8. Spark dynamic hue
    )
}

private val baseHeartPath = Path().apply {
    // Exact symmetric ♡ heart path matching Material / Unicode ♡
    moveTo(50f, 24f)
    cubicTo(42f, 10f, 22f, 10f, 11f, 23f)
    cubicTo(0f, 36f, 2f, 58f, 20f, 72f)
    cubicTo(32f, 82f, 44f, 92f, 50f, 96f)
    cubicTo(56f, 92f, 68f, 82f, 80f, 72f)
    cubicTo(98f, 58f, 100f, 36f, 89f, 23f)
    cubicTo(78f, 10f, 58f, 10f, 50f, 24f)
    close()
}

private fun DrawScope.drawHeartParticle(
    centerX: Float,
    centerY: Float,
    sizePx: Float,
    color: Color,
    alpha: Float,
    scale: Float,
    rotationDeg: Float
) {
    if (alpha <= 0.01f || scale <= 0.01f) return
    val totalScale = (sizePx / 100f) * scale
    withTransform({
        translate(left = centerX, top = centerY)
        rotate(degrees = rotationDeg, pivot = Offset.Zero)
        scale(scaleX = totalScale, scaleY = totalScale, pivot = Offset.Zero)
        translate(left = -50f, top = -50f)
    }) {
        drawPath(baseHeartPath, color = color.copy(alpha = alpha))
    }
}

/**
 * BubblingHeartButton — Samsung Music-style favorite heart button.
 *
 * Features:
 * 1. Radiant fountain of bubbling hearts with dynamic hue extracted from album art.
 * 2. Instant 0ms optimistic response on touch.
 * 3. Pure Compose Canvas particle rendering.
 * 4. Elastic bouncy spring on main heart.
 * 5. Soft radial halo glow pulse.
 */
@Composable
fun BubblingHeartButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    hue: Float? = null,
    activeColor: Color? = null,
    inactiveColor: Color = Color.White.copy(alpha = 0.85f),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    var optimisticFavorite by remember(isFavorite) { mutableStateOf(isFavorite) }
    val resolvedActiveColor = activeColor ?: hue?.let { Color.hsv(it, 0.90f, 1.0f) } ?: Color(0xFFFF2D55)
    val scope = rememberCoroutineScope()

    val mainHeartScale = remember { Animatable(1f) }
    val haloScale = remember { Animatable(0.5f) }
    val haloAlpha = remember { Animatable(0f) }

    var particles by remember { mutableStateOf<List<HeartBubbleParticle>>(emptyList()) }
    val burstProgress = remember { Animatable(0f) }

    fun triggerBurst() {
        val palette = generateHarmonicHeartPalette(hue)
        val list = ArrayList<HeartBubbleParticle>(14)
        for (i in 0 until 14) {
            val spreadFactor = ((i - 7) / 7f) * 32f
            val initX = spreadFactor + (Random.nextFloat() * 12f - 6f)
            val riseDist = Random.nextFloat() * 60f + 85f // 85dp to 145dp upward
            val swayAmp = Random.nextFloat() * 12f + 4f
            val swayFreq = Random.nextFloat() * 1.5f + 0.8f
            val pSize = (Random.nextInt(16, 24)).dp
            val col = palette[i % palette.size]
            val rot = (spreadFactor * 0.6f) + (Random.nextFloat() * 20f - 10f)
            val delayFrac = (Random.nextFloat() * 0.16f)

            list.add(
                HeartBubbleParticle(
                    id = i,
                    initialXOffset = initX,
                    maxUpwardDistance = riseDist,
                    swayAmplitude = swayAmp,
                    swayFrequency = swayFreq,
                    size = pSize,
                    color = col,
                    initialRotation = rot,
                    delayFraction = delayFrac
                )
            )
        }

        scope.launch {
            burstProgress.snapTo(0f)
            particles = list

            // 1. Pop bounce on main heart
            launch {
                mainHeartScale.snapTo(0.75f)
                mainHeartScale.animateTo(
                    targetValue = 1.25f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                )
                mainHeartScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

            // 2. Halo ring expand & fade
            launch {
                haloScale.snapTo(0.5f)
                haloAlpha.snapTo(0.85f)
                launch {
                    haloScale.animateTo(2.8f, tween(700, easing = FastOutSlowInEasing))
                }
                launch {
                    haloAlpha.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
                }
            }

            // 3. Bubbling Hearts Rise
            launch {
                burstProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(1600, easing = LinearOutSlowInEasing)
                )
                particles = emptyList()
            }
        }
    }

    Box(
        modifier = modifier
            .size(size + 20.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                val willBeFavorite = !optimisticFavorite
                optimisticFavorite = willBeFavorite
                if (willBeFavorite) {
                    triggerBurst()
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Canvas Bubbling Hearts Fountain inside 320dp Popup Window with VSYNC frame sync
        if (particles.isNotEmpty()) {
            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                var frameProgress by remember { mutableStateOf(0f) }

                LaunchedEffect(Unit) {
                    val durationNanos = 1_600_000_000L
                    val startTime = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val elapsed = now - startTime
                        val p = (elapsed.toFloat() / durationNanos).coerceIn(0f, 1f)
                        frameProgress = p
                        if (p >= 1f) {
                            particles = emptyList()
                            break
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .size(320.dp, 320.dp)
                        .zIndex(999f)
                ) {
                    val animVal = frameProgress
                    val centerX = this.size.width / 2f
                    val centerY = this.size.height / 2f

                    particles.forEach { particle ->
                        val p = ((animVal - particle.delayFraction) / (1f - particle.delayFraction)).coerceIn(0f, 1f)
                        if (p > 0.001f && p < 0.999f) {
                            val riseProgress = sin((p * Math.PI.toFloat()) / 2f)
                            val swayOffset = sin(p * Math.PI.toFloat() * particle.swayFrequency) * particle.swayAmplitude
                            val px = centerX + (particle.initialXOffset + swayOffset) * density
                            val py = centerY - (riseProgress * particle.maxUpwardDistance) * density

                            val scale = when {
                                p < 0.20f -> (p / 0.20f) * 1.25f
                                p < 0.65f -> 1.25f - ((p - 0.20f) / 0.45f) * 0.20f
                                else -> (1.05f - ((p - 0.65f) / 0.35f) * 0.75f).coerceAtLeast(0f)
                            }

                            val alpha = when {
                                p < 0.10f -> p / 0.10f
                                p < 0.60f -> 1.0f
                                else -> (1.0f - ((p - 0.60f) / 0.40f)).coerceIn(0f, 1f)
                            }

                            val rot = particle.initialRotation + (swayOffset * 1.5f)

                            drawHeartParticle(
                                centerX = px,
                                centerY = py,
                                sizePx = particle.size.toPx(),
                                color = particle.color,
                                alpha = alpha,
                                scale = scale,
                                rotationDeg = rot
                            )
                        }
                    }
                }
            }
        }

        // 2. Halo Radial Glow Ring
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val a = haloAlpha.value
                    alpha = if (a > 0.01f) a else 0f
                    scaleX = haloScale.value
                    scaleY = haloScale.value
                }
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = resolvedActiveColor.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Main Heart Icon
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = mainHeartScale.value
                scaleY = mainHeartScale.value
            }
        ) {
            Icon(
                imageVector = if (optimisticFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (optimisticFavorite) resolvedActiveColor else inactiveColor,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Reusable bubbling floating hearts burst effect.
 * Snappy, GPU-accelerated on pure Canvas.
 */
@Composable
fun BubblingHeartBurstEffect(
    triggerKey: Int,
    modifier: Modifier = Modifier,
    hue: Float? = null,
    particleCount: Int = 10,
    durationMs: Int = 1000,
    minUpwardDistance: Float = 80f,
    maxUpwardDistance: Float = 160f,
    customPalette: List<Color>? = null
) {
    val palette = remember(hue, customPalette) {
        customPalette ?: generateHarmonicHeartPalette(hue)
    }

    var currentParticles by remember { mutableStateOf<List<HeartBubbleParticle>>(emptyList()) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0) {
            val list = ArrayList<HeartBubbleParticle>(particleCount)
            for (i in 0 until particleCount) {
                val initX = (Random.nextFloat() * 40f - 20f)
                val riseDist = Random.nextFloat() * (maxUpwardDistance - minUpwardDistance) + minUpwardDistance
                val swayAmp = Random.nextFloat() * 16f + 6f
                val swayFreq = Random.nextFloat() * 1.6f + 0.8f
                val pSize = (Random.nextInt(14, 22)).dp
                val col = palette[i % palette.size]
                val rot = Random.nextFloat() * 40f - 20f
                val delayFrac = (i.toFloat() / particleCount.toFloat()) * 0.22f

                list.add(
                    HeartBubbleParticle(
                        id = i,
                        initialXOffset = initX,
                        maxUpwardDistance = riseDist,
                        swayAmplitude = swayAmp,
                        swayFrequency = swayFreq,
                        size = pSize,
                        color = col,
                        initialRotation = rot,
                        delayFraction = delayFrac
                    )
                )
            }
            currentParticles = list

            try {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMs, easing = LinearOutSlowInEasing)
                )
            } finally {
                currentParticles = emptyList()
                progress.snapTo(0f)
            }
        } else {
            currentParticles = emptyList()
            progress.snapTo(0f)
        }
    }

    if (currentParticles.isNotEmpty()) {
        Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
                usePlatformDefaultWidth = false
            )
        ) {
            var frameProgress by remember { mutableStateOf(0f) }

            LaunchedEffect(Unit) {
                val durationNanos = durationMs * 1_000_000L
                val startTime = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val elapsed = now - startTime
                    val p = (elapsed.toFloat() / durationNanos).coerceIn(0f, 1f)
                    frameProgress = p
                    if (p >= 1f) {
                        currentParticles = emptyList()
                        break
                    }
                }
            }

            Canvas(
                modifier = modifier.size(320.dp, 320.dp)
            ) {
                val animVal = frameProgress
                val centerX = this.size.width / 2f
                val centerY = this.size.height / 2f

                currentParticles.forEach { particle ->
                    val p = ((animVal - particle.delayFraction) / (1f - particle.delayFraction)).coerceIn(0f, 1f)
                    if (p > 0.001f && p < 0.999f) {
                        val riseProgress = sin((p * Math.PI.toFloat()) / 2f)
                        val swayOffset = sin(p * Math.PI.toFloat() * particle.swayFrequency) * particle.swayAmplitude
                        val px = centerX + (particle.initialXOffset + swayOffset) * density
                        val py = centerY - (riseProgress * particle.maxUpwardDistance) * density

                        val scale = when {
                            p < 0.20f -> (p / 0.20f) * 1.25f
                            p < 0.65f -> 1.25f - ((p - 0.20f) / 0.45f) * 0.20f
                            else -> (1.05f - ((p - 0.65f) / 0.35f) * 0.75f).coerceAtLeast(0f)
                        }

                        val alpha = when {
                            p < 0.10f -> p / 0.10f
                            p < 0.60f -> 1.0f
                            else -> (1.0f - ((p - 0.60f) / 0.40f)).coerceIn(0f, 1f)
                        }

                        val rot = particle.initialRotation + (swayOffset * 1.5f)

                        drawHeartParticle(
                            centerX = px,
                            centerY = py,
                            sizePx = particle.size.toPx(),
                            color = particle.color,
                            alpha = alpha,
                            scale = scale,
                            rotationDeg = rot
                        )
                    }
                }
            }
        }
    }
}
