package com.medianest.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

data class HeartBubbleParticle(
    val id: Int,
    val initialXOffset: Float,     // Horizontal starting spread (-24dp to +24dp)
    val maxUpwardDistance: Float,  // Total vertical rise (80dp to 140dp)
    val swayAmplitude: Float,      // Left-right horizontal sway amount
    val swayFrequency: Float,      // Sway speed / cycle count
    val size: Dp,
    val color: Color,
    val initialRotation: Float,
    val delayFraction: Float       // Staggered release (0.0 to 0.25 of total duration)
)

/**
 * Generates an artistic harmonic palette based on the extracted album art hue.
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
        Color.hsv(h, 0.88f, 1.0f),                            // 1. Primary vivid hue
        Color.hsv((h + 16f) % 360f, 0.78f, 0.98f),             // 2. Analogous warm
        Color.hsv((h - 16f + 360f) % 360f, 0.80f, 0.98f),      // 3. Analogous cool
        Color.hsv(h, 0.45f, 1.0f),                            // 4. Pastel luminous tint
        Color.hsv((h + 32f) % 360f, 0.85f, 0.95f),             // 5. Harmonic accent
        Color.hsv((h + 335f) % 360f, 0.80f, 1.0f),            // 6. Soft warm hue
        Color.hsv(h, 0.65f, 0.94f)                            // 7. Soft glowing tone
    )
}

/**
 * BubblingHeartButton — Samsung Music-style favorite heart button.
 *
 * Features:
 * 1. Slow, serene bubbling hearts that float straight upwards, sway gently, and slowly fade out into nothingness.
 * 2. Gentle spring scale-up pop on the main heart.
 * 3. Soft expanding halo pulse.
 * 4. Colors dynamically inherit the album art hue.
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
    val resolvedActiveColor = activeColor ?: hue?.let { Color.hsv(it, 0.90f, 1.0f) } ?: Color(0xFFFF2D55)
    var burstTrigger by remember { mutableIntStateOf(0) }
    val mainHeartScale = remember { Animatable(1f) }
    val haloScale = remember { Animatable(0.5f) }
    val haloAlpha = remember { Animatable(0f) }

    LaunchedEffect(burstTrigger) {
        if (burstTrigger > 0) {
            coroutineScope {
                // 1. Pop bounce on main heart (Gentle spring)
                launch {
                    mainHeartScale.snapTo(0.75f)
                    mainHeartScale.animateTo(
                        targetValue = 1.30f,
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    )
                    mainHeartScale.animateTo(
                        targetValue = 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }

                // 2. Halo ring expand & fade
                launch {
                    haloScale.snapTo(0.6f)
                    haloAlpha.snapTo(0.65f)
                    launch {
                        haloScale.animateTo(2.4f, tween(900, easing = FastOutSlowInEasing))
                    }
                    launch {
                        haloAlpha.animateTo(0f, tween(900, easing = LinearEasing))
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(size + 36.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (!isFavorite) {
                    burstTrigger += 1
                } else {
                    burstTrigger = 0
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Halo Radial Glow Ring
        if (haloAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = haloScale.value
                        scaleY = haloScale.value
                        alpha = haloAlpha.value
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = resolvedActiveColor.copy(alpha = haloAlpha.value * 0.45f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Bubbling Mini Hearts Burst (Floating upward and slowly vanishing)
        BubblingHeartBurstEffect(
            triggerKey = burstTrigger,
            hue = hue,
            particleCount = 8,
            durationMs = 1900,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Main Heart Icon
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = mainHeartScale.value
                scaleY = mainHeartScale.value
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) resolvedActiveColor else inactiveColor,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Reusable bubbling floating hearts burst effect.
 *
 * Hearts spawn near the bottom/center, slowly rise upwards (80dp - 130dp),
 * sway gently from side to side, and slowly fade out until vanishing completely.
 */
@Composable
fun BubblingHeartBurstEffect(
    triggerKey: Int,
    modifier: Modifier = Modifier,
    hue: Float? = null,
    particleCount: Int = 8,
    durationMs: Int = 1900,
    minUpwardDistance: Float = 75f,
    maxUpwardDistance: Float = 135f,
    customPalette: List<Color>? = null
) {
    val palette = remember(hue, customPalette) {
        customPalette ?: generateHarmonicHeartPalette(hue)
    }

    val particleList = remember { mutableStateListOf<HeartBubbleParticle>() }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0) {
            particleList.clear()
            for (i in 0 until particleCount) {
                val initX = (Random.nextFloat() * 44f - 22f) // Initial spread around center (-22dp to +22dp)
                val riseDist = Random.nextFloat() * (maxUpwardDistance - minUpwardDistance) + minUpwardDistance
                val swayAmp = Random.nextFloat() * 12f + 6f   // 6dp to 18dp horizontal sway
                val swayFreq = Random.nextFloat() * 1.5f + 1.2f // Sine frequency
                val pSize = (Random.nextInt(12, 19)).dp
                val col = palette[i % palette.size]
                val rot = Random.nextFloat() * 36f - 18f
                val delayFrac = (i.toFloat() / particleCount.toFloat()) * 0.22f // Staggered release

                particleList.add(
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

            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMs, easing = LinearEasing)
            )
            particleList.clear()
        }
    }

    if (particleList.isNotEmpty()) {
        val animValue = progress.value
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            particleList.forEach { particle ->
                // Effective progress for this particle after its delay
                val p = ((animValue - particle.delayFraction) / (1f - particle.delayFraction)).coerceIn(0f, 1f)

                if (p > 0f && p < 1f) {
                    // Gentle rising ease-out motion
                    val riseProgress = easeOutSine(p)
                    val offsetY = -(riseProgress * particle.maxUpwardDistance).dp

                    // Left-right organic horizontal sway
                    val swayOffset = sin(p * Math.PI.toFloat() * particle.swayFrequency) * particle.swayAmplitude
                    val offsetX = (particle.initialXOffset + swayOffset).dp

                    // Scale progression: Pops in quickly, stays full size, gently shrinks at top
                    val scale = when {
                        p < 0.15f -> (p / 0.15f) * 1.15f
                        p < 0.65f -> 1.15f - ((p - 0.15f) / 0.50f) * 0.15f
                        else -> (1f - ((p - 0.65f) / 0.35f) * 0.5f)
                    }

                    // Opacity (Alpha): Fades in, stays visible, slowly and smoothly dissolves into nothingness
                    val alpha = when {
                        p < 0.12f -> p / 0.12f
                        p < 0.45f -> 1.0f
                        else -> (1.0f - ((p - 0.45f) / 0.55f)).coerceIn(0f, 1f)
                    }

                    val rotation = particle.initialRotation + (swayOffset * 1.5f)

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                rotationZ = rotation
                                this.alpha = alpha
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = particle.color,
                            modifier = Modifier.size(particle.size)
                        )
                    }
                }
            }
        }
    }
}

private fun easeOutSine(t: Float): Float {
    return sin((t * Math.PI.toFloat()) / 2f)
}
