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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class HeartParticle(
    val id: Int,
    val angleRad: Double,
    val distance: Float,
    val size: Dp,
    val color: Color,
    val rotation: Float,
    val delayMs: Int
)

/**
 * BubblingHeartButton — Samsung Music-style favorite heart button.
 *
 * Features:
 * 1. Bouncy spring pop scale animation on the main heart.
 * 2. Expanding radial glow halo ring.
 * 3. Cascade burst of 10-12 bubbling, floating mini-hearts with staggered upward drift,
 *    color gradients (pink/rose/coral/magenta), rotation, and fade-out.
 */
@Composable
fun BubblingHeartButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    activeColor: Color = Color(0xFFFF2D55),
    inactiveColor: Color = Color.White.copy(alpha = 0.85f),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    var burstTrigger by remember { mutableIntStateOf(0) }
    val mainHeartScale = remember { Animatable(1f) }
    val haloScale = remember { Animatable(0.5f) }
    val haloAlpha = remember { Animatable(0f) }

    LaunchedEffect(burstTrigger) {
        if (burstTrigger > 0) {
            coroutineScope {
                // 1. Pop bounce on main heart
                launch {
                    mainHeartScale.snapTo(0.7f)
                    mainHeartScale.animateTo(
                        targetValue = 1.38f,
                        animationSpec = tween(140, easing = FastOutSlowInEasing)
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
                        haloScale.animateTo(2.4f, tween(420, easing = FastOutSlowInEasing))
                    }
                    launch {
                        haloAlpha.animateTo(0f, tween(420, easing = LinearEasing))
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(size + 32.dp)
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
                    tint = activeColor.copy(alpha = haloAlpha.value * 0.45f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Bubbling Mini Hearts Burst
        BubblingHeartBurstEffect(
            triggerKey = burstTrigger,
            particleCount = 10,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Main Heart Icon (With pop bounce and color transition)
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = mainHeartScale.value
                scaleY = mainHeartScale.value
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) activeColor else inactiveColor,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Reusable bubbling floating hearts burst effect triggered whenever [triggerKey] changes (> 0).
 */
@Composable
fun BubblingHeartBurstEffect(
    triggerKey: Int,
    modifier: Modifier = Modifier,
    particleCount: Int = 12,
    spreadAngleMinDeg: Double = -165.0,
    spreadAngleMaxDeg: Double = -15.0,
    minDistance: Float = 36f,
    maxDistance: Float = 72f,
    palette: List<Color> = listOf(
        Color(0xFFFF2D55),
        Color(0xFFFF4081),
        Color(0xFFFF5252),
        Color(0xFFFF79B0),
        Color(0xFFE056FD),
        Color(0xFFFF6B81),
        Color(0xFFFFAAA6)
    )
) {
    val particleList = remember { mutableStateListOf<HeartParticle>() }
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0) {
            particleList.clear()
            val span = spreadAngleMaxDeg - spreadAngleMinDeg
            for (i in 0 until particleCount) {
                val baseAngle = spreadAngleMinDeg + (span / (particleCount.coerceAtLeast(2) - 1)) * i
                val jitter = Random.nextDouble(-12.0, 12.0)
                val angleRad = Math.toRadians(baseAngle + jitter)
                val dist = Random.nextFloat() * (maxDistance - minDistance) + minDistance
                val pSize = (Random.nextInt(10, 18)).dp
                val col = palette[i % palette.size]
                val rot = Random.nextFloat() * 50f - 25f
                val delay = Random.nextInt(0, 90)

                particleList.add(
                    HeartParticle(
                        id = i,
                        angleRad = angleRad,
                        distance = dist,
                        size = pSize,
                        color = col,
                        rotation = rot,
                        delayMs = delay
                    )
                )
            }

            particleProgress.snapTo(0f)
            particleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
            particleList.clear()
        }
    }

    if (particleList.isNotEmpty()) {
        val progress = particleProgress.value
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            particleList.forEach { particle ->
                val effectiveProgress = ((progress * 650 - particle.delayMs) / (650 - particle.delayMs)).coerceIn(0f, 1f)

                if (effectiveProgress > 0f) {
                    val currentDistance = particle.distance * easeOutQuad(effectiveProgress)
                    val offsetX = (currentDistance * cos(particle.angleRad)).dp
                    val offsetY = (currentDistance * sin(particle.angleRad) - (effectiveProgress * 14f)).dp

                    val pScale = when {
                        effectiveProgress < 0.25f -> effectiveProgress / 0.25f * 1.15f
                        effectiveProgress < 0.7f -> 1.15f - (effectiveProgress - 0.25f) / 0.45f * 0.35f
                        else -> (1f - (effectiveProgress - 0.7f) / 0.3f) * 0.8f
                    }

                    val pAlpha = when {
                        effectiveProgress < 0.15f -> effectiveProgress / 0.15f
                        effectiveProgress > 0.60f -> 1f - ((effectiveProgress - 0.60f) / 0.40f)
                        else -> 1f
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .graphicsLayer {
                                scaleX = pScale
                                scaleY = pScale
                                rotationZ = particle.rotation + (effectiveProgress * 15f)
                                alpha = pAlpha
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

private fun easeOutQuad(t: Float): Float {
    return 1f - (1f - t) * (1f - t)
}
