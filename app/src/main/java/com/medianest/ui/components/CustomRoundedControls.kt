package com.medianest.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Android 13/14 Material You style custom rounded control buttons.
 * Eliminates sharp edges with soft pill-shaped geometry, rounded triangle vertices,
 * and fluid spring micro-interactions.
 */

// ============================================================================
// 1. Core Geometric Rounded Shape Painters
// ============================================================================

/**
 * Draws a polygon with smooth rounded corners at every vertex using quadratic Béziers.
 */
private fun DrawScope.drawRoundedPolygon(
    vertices: List<Offset>,
    cornerRadius: Float,
    color: Color
) {
    if (vertices.size < 3) return
    val path = Path()
    val n = vertices.size

    for (i in 0 until n) {
        val curr = vertices[i]
        val prev = vertices[(i - 1 + n) % n]
        val next = vertices[(i + 1) % n]

        val vPrev = prev - curr
        val lenPrev = sqrt(vPrev.x * vPrev.x + vPrev.y * vPrev.y).coerceAtLeast(0.001f)
        val uPrev = Offset(vPrev.x / lenPrev, vPrev.y / lenPrev)

        val vNext = next - curr
        val lenNext = sqrt(vNext.x * vNext.x + vNext.y * vNext.y).coerceAtLeast(0.001f)
        val uNext = Offset(vNext.x / lenNext, vNext.y / lenNext)

        val d = cornerRadius.coerceAtMost(lenPrev * 0.45f).coerceAtMost(lenNext * 0.45f)

        val tangentIn = curr + uPrev * d
        val tangentOut = curr + uNext * d

        if (i == 0) {
            path.moveTo(tangentIn.x, tangentIn.y)
        } else {
            path.lineTo(tangentIn.x, tangentIn.y)
        }
        path.quadraticTo(curr.x, curr.y, tangentOut.x, tangentOut.y)
    }
    path.close()
    drawPath(path, color = color, style = Fill)
}

// ============================================================================
// 2. Custom Rounded Vector Icons
// ============================================================================

/**
 * Custom smooth right-pointing Play Arrow with soft, rounded vertices.
 */
@Composable
fun RoundedPlayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = w * 0.16f

        // Center the play triangle nicely inside the bounding box
        val vertices = listOf(
            Offset(w * 0.24f, h * 0.16f), // Top-left
            Offset(w * 0.84f, h * 0.50f), // Right tip
            Offset(w * 0.24f, h * 0.84f)  // Bottom-left
        )
        drawRoundedPolygon(vertices, radius, tint)
    }
}

/**
 * Custom smooth twin-pill Pause Icon with 100% rounded capsule ends.
 */
@Composable
fun RoundedPauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pillWidth = w * 0.22f
        val pillHeight = h * 0.68f
        val gap = w * 0.18f
        val totalWidth = 2 * pillWidth + gap
        val startX = (w - totalWidth) / 2f
        val startY = (h - pillHeight) / 2f
        val capRadius = pillWidth / 2f

        // Left rounded pill bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX, startY),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(capRadius, capRadius)
        )
        // Right rounded pill bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX + pillWidth + gap, startY),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(capRadius, capRadius)
        )
    }
}

/**
 * Custom smooth Skip Previous Icon: Vertical rounded pill + left-pointing rounded triangle.
 */
@Composable
fun RoundedSkipPreviousIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pillWidth = w * 0.10f
        val pillHeight = h * 0.60f
        val capRadius = pillWidth / 2f
        val startX = w * 0.10f
        val startY = (h - pillHeight) / 2f

        // 1. Left vertical rounded pill bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX, startY),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(capRadius, capRadius)
        )

        // 2. Left-pointing rounded triangle
        val triRadius = w * 0.16f
        val triVertices = listOf(
            Offset(w * 0.88f, h * 0.20f), // Top-right
            Offset(w * 0.32f, h * 0.50f), // Left tip
            Offset(w * 0.88f, h * 0.20f + h * 0.60f)  // Bottom-right
        )
        drawRoundedPolygon(triVertices, triRadius, tint)
    }
}

/**
 * Custom smooth Skip Next Icon: Right-pointing rounded triangle + vertical rounded pill.
 */
@Composable
fun RoundedSkipNextIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pillWidth = w * 0.10f
        val pillHeight = h * 0.60f
        val capRadius = pillWidth / 2f
        val pillX = w * 0.80f
        val pillY = (h - pillHeight) / 2f

        // 1. Right-pointing rounded triangle
        val triRadius = w * 0.16f
        val triVertices = listOf(
            Offset(w * 0.12f, h * 0.20f), // Top-left
            Offset(w * 0.68f, h * 0.50f), // Right tip
            Offset(w * 0.12f, h * 0.80f)  // Bottom-left
        )
        drawRoundedPolygon(triVertices, triRadius, tint)

        // 2. Right vertical rounded pill bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(pillX, pillY),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(capRadius, capRadius)
        )
    }
}

/**
 * Redesigned Skip Previous Icon: Vertical pill + double left-pointing triangles (|<<)
 */
@Composable
fun RoundedDoubleSkipPreviousIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // 1. Double Triangles (Height matches Play Icon: 0.16 to 0.84)
        val triRadius = w * 0.10f
        
        // Outer triangle
        val tri1Vertices = listOf(
            Offset(w * 0.58f, h * 0.16f),
            Offset(w * 0.30f, h * 0.50f),
            Offset(w * 0.58f, h * 0.84f)
        )
        drawRoundedPolygon(tri1Vertices, triRadius, tint)
        
        // Inner triangle
        val tri2Vertices = listOf(
            Offset(w * 0.86f, h * 0.16f),
            Offset(w * 0.58f, h * 0.50f),
            Offset(w * 0.86f, h * 0.84f)
        )
        drawRoundedPolygon(tri2Vertices, triRadius, tint)

        // 2. Left vertical rounded pill bar
        val pillHeight = h * 0.50f
        val pillWidth = w * 0.10f
        val capRadius = pillWidth / 2f
        val startX = w * 0.10f
        val startY = (h - pillHeight) / 2f
        
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX, startY),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(capRadius, capRadius)
        )
    }
}

/**
 * Redesigned Skip Next Icon: Double right-pointing triangles + vertical pill (>>|)
 */
@Composable
fun RoundedDoubleSkipNextIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Two Right-pointing rounded triangles (Height matches Play Icon: 0.16 to 0.84)
        val triRadius = w * 0.10f
        
        // Inner triangle
        val tri1Vertices = listOf(
            Offset(w * 0.14f, h * 0.16f),
            Offset(w * 0.42f, h * 0.50f),
            Offset(w * 0.14f, h * 0.84f)
        )
        drawRoundedPolygon(tri1Vertices, triRadius, tint)
        
        // Outer triangle
        val tri2Vertices = listOf(
            Offset(w * 0.42f, h * 0.16f),
            Offset(w * 0.70f, h * 0.50f),
            Offset(w * 0.42f, h * 0.84f)
        )
        drawRoundedPolygon(tri2Vertices, triRadius, tint)

        // 2. Right vertical rounded pill bar
        val pillHeight = h * 0.50f
        val pillWidth = w * 0.10f
        val capRadius = pillWidth / 2f
        val pillX = w * 0.80f
        val pillY = (h - pillHeight) / 2f

        drawRoundRect(
            color = tint,
            topLeft = Offset(pillX, pillY),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(capRadius, capRadius)
        )
    }
}

// ============================================================================
// 3. Interactive Custom Control Buttons
// ============================================================================

enum class ControlButtonStyle {
    TRANSPARENT_MINIMAL, // Pure rounded icons with touch ripples (as in Material You notification widget)
    GLASS_SQUIRCLE,      // Frosted rounded squircle container
    PILL_ACCENT          // Solid / Gradient pill container
}

/**
 * Custom animated Play/Pause button with smooth spring bounce & morphing icons.
 */
@Composable
fun CustomRoundedPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
    iconSize: Dp = 32.dp,
    style: ControlButtonStyle = ControlButtonStyle.TRANSPARENT_MINIMAL,
    tint: Color = Color.White,
    containerColor: Color = Color.White.copy(alpha = 0.22f),
    borderColor: Color? = Color.White.copy(alpha = 0.35f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "play_pause_scale"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .then(
                when (style) {
                    ControlButtonStyle.TRANSPARENT_MINIMAL -> Modifier.clip(CircleShape)
                    ControlButtonStyle.GLASS_SQUIRCLE -> Modifier
                        .clip(RoundedCornerShape(buttonSize * 0.35f))
                        /*
                        .background(containerColor)
                        .then(
                            if (borderColor != null) {
                                Modifier.border(1.dp, borderColor, RoundedCornerShape(buttonSize * 0.35f))
                            } else Modifier
                        )
                        */
                    ControlButtonStyle.PILL_ACCENT -> Modifier
                        .clip(CircleShape)
                        /*
                        .background(containerColor)
                        .then(
                            if (borderColor != null) {
                                Modifier.border(1.dp, borderColor, CircleShape)
                            } else Modifier
                        )
                        */
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isPlaying,
            animationSpec = tween(durationMillis = 220),
            label = "play_pause_crossfade"
        ) { playing ->
            if (playing) {
                RoundedPauseIcon(
                    modifier = Modifier.size(iconSize),
                    tint = tint
                )
            } else {
                RoundedPlayIcon(
                    modifier = Modifier.size(iconSize),
                    tint = tint
                )
            }
        }
    }
}

/**
 * Custom animated Skip Previous Button with rounded geometry.
 */
@Composable
fun CustomRoundedPreviousButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 26.dp,
    enabled: Boolean = true,
    style: ControlButtonStyle = ControlButtonStyle.TRANSPARENT_MINIMAL,
    tint: Color = Color.White,
    containerColor: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color? = null,
    useDoubleIcon: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "prev_scale"
    )

    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .then(
                when (style) {
                    ControlButtonStyle.TRANSPARENT_MINIMAL -> Modifier.clip(CircleShape)
                    ControlButtonStyle.GLASS_SQUIRCLE -> Modifier
                        .clip(RoundedCornerShape(buttonSize * 0.35f))
                        /*
                        .background(containerColor)
                        .then(
                            if (borderColor != null) {
                                Modifier.border(1.dp, borderColor, RoundedCornerShape(buttonSize * 0.35f))
                            } else Modifier
                        )
                        */
                    ControlButtonStyle.PILL_ACCENT -> Modifier
                        .clip(CircleShape)
                        /*
                        .background(containerColor)
                        .then(
                            if (borderColor != null) {
                                Modifier.border(1.dp, borderColor, CircleShape)
                            } else Modifier
                        )
                        */
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (useDoubleIcon) {
            RoundedDoubleSkipPreviousIcon(
                modifier = Modifier.size(iconSize),
                tint = effectiveTint
            )
        } else {
            RoundedSkipPreviousIcon(
                modifier = Modifier.size(iconSize),
                tint = effectiveTint
            )
        }
    }
}

/**
 * Custom animated Skip Next Button with rounded geometry.
 */
@Composable
fun CustomRoundedNextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 26.dp,
    enabled: Boolean = true,
    style: ControlButtonStyle = ControlButtonStyle.TRANSPARENT_MINIMAL,
    tint: Color = Color.White,
    containerColor: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color? = null,
    useDoubleIcon: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "next_scale"
    )

    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .then(
                when (style) {
                    ControlButtonStyle.TRANSPARENT_MINIMAL -> Modifier.clip(CircleShape)
                    ControlButtonStyle.GLASS_SQUIRCLE -> Modifier
                        .clip(RoundedCornerShape(buttonSize * 0.35f))
                        /*
                        .background(containerColor)
                        .then(
                            if (borderColor != null) {
                                Modifier.border(1.dp, borderColor, RoundedCornerShape(buttonSize * 0.35f))
                            } else Modifier
                        )
                        */
                    ControlButtonStyle.PILL_ACCENT -> Modifier
                        .clip(CircleShape)
                        /*
                        .background(containerColor)
                        .then(
                            if (borderColor != null) {
                                Modifier.border(1.dp, borderColor, CircleShape)
                            } else Modifier
                        )
                        */
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (useDoubleIcon) {
            RoundedDoubleSkipNextIcon(
                modifier = Modifier.size(iconSize),
                tint = effectiveTint
            )
        } else {
            RoundedSkipNextIcon(
                modifier = Modifier.size(iconSize),
                tint = effectiveTint
            )
        }
    }
}

// ============================================================================
// 4. Complete Transport Control Bar (Android 13/14 Material You Aesthetic)
// ============================================================================

/**
 * Full modern transport control row matching the user's custom design.
 * Features 3-button (Prev, Play/Pause, Next) or 5-button (with Shuffle & Repeat) layouts.
 */
@Composable
fun MaterialYouPlayerControlBar(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isShuffle: Boolean = false,
    onShuffleToggle: (() -> Unit)? = null,
    repeatMode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL
    onRepeatToggle: (() -> Unit)? = null,
    hasPrevious: Boolean = true,
    hasNext: Boolean = true,
    style: ControlButtonStyle = ControlButtonStyle.GLASS_SQUIRCLE,
    tint: Color = Color.White,
    accentTint: Color = Color(0xFFA8C7FA),
    playButtonSize: Dp = 68.dp,
    secondaryButtonSize: Dp = 48.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optional Shuffle
        if (onShuffleToggle != null) {
            val shuffleInteraction = remember { MutableInteractionSource() }
            val shufflePressed by shuffleInteraction.collectIsPressedAsState()
            val shuffleScale by animateFloatAsState(
                targetValue = if (shufflePressed) 0.85f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "shuffle_scale"
            )

            Surface(
                onClick = onShuffleToggle,
                shape = RoundedCornerShape(secondaryButtonSize * 0.35f),
                color = Color.Transparent, // if (isShuffle) accentTint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                border = null, // if (isShuffle) BorderStroke(1.dp, accentTint.copy(alpha = 0.6f)) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .size(secondaryButtonSize)
                    .scale(shuffleScale)
                    .offset(x = (-12).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CustomShuffleIcon(
                        modifier = Modifier.size(secondaryButtonSize * 0.48f),
                        tint = if (isShuffle) accentTint else tint.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Previous Button
        CustomRoundedPreviousButton(
            onClick = onPrevious,
            buttonSize = secondaryButtonSize,
            iconSize = secondaryButtonSize * 0.54f,
            enabled = hasPrevious,
            style = style,
            tint = tint,
            containerColor = Color.White.copy(alpha = 0.12f),
            borderColor = Color.White.copy(alpha = 0.22f),
            useDoubleIcon = true
        )

        // Main Center Play/Pause Button
        CustomRoundedPlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseToggle,
            buttonSize = playButtonSize,
            iconSize = playButtonSize * 0.50f,
            style = style,
            tint = tint,
            containerColor = Color.White.copy(alpha = 0.24f),
            borderColor = Color.White.copy(alpha = 0.40f)
        )

        // Next Button
        CustomRoundedNextButton(
            onClick = onNext,
            buttonSize = secondaryButtonSize,
            iconSize = secondaryButtonSize * 0.54f,
            enabled = hasNext,
            style = style,
            tint = tint,
            containerColor = Color.White.copy(alpha = 0.12f),
            borderColor = Color.White.copy(alpha = 0.22f),
            useDoubleIcon = true
        )

        // Optional Repeat
        if (onRepeatToggle != null) {
            val repeatInteraction = remember { MutableInteractionSource() }
            val repeatPressed by repeatInteraction.collectIsPressedAsState()
            val repeatScale by animateFloatAsState(
                targetValue = if (repeatPressed) 0.85f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "repeat_scale"
            )
            val isRepeatActive = repeatMode > 0

            Surface(
                onClick = onRepeatToggle,
                shape = RoundedCornerShape(secondaryButtonSize * 0.35f),
                color = Color.Transparent, // if (isRepeatActive) accentTint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                border = null, // if (isRepeatActive) BorderStroke(1.dp, accentTint.copy(alpha = 0.6f)) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .size(secondaryButtonSize)
                    .scale(repeatScale)
                    .offset(x = 12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CustomABRepeatIcon(
                        mode = repeatMode,
                        modifier = Modifier.size(secondaryButtonSize * 0.48f),
                        tint = if (isRepeatActive) accentTint else tint.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// 4. Custom Suite Icons (Matching UI Reference Design)
// ============================================================================

/**
 * Custom Playlist Icon: 3 horizontal list bars with a music note on top-right.
 */
@Composable
fun CustomPlaylistIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = h * 0.08f

        val xStart = w * 0.15f
        val y1 = h * 0.30f
        val y2 = h * 0.52f
        val y3 = h * 0.74f

        // 3 horizontal lines (cascading lengths: 75%, 45%, 25%)
        drawLine(color = tint, start = Offset(xStart, y1), end = Offset(w * 0.75f, y1), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(xStart, y2), end = Offset(w * 0.45f, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(xStart, y3), end = Offset(w * 0.25f, y3), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        // Musical note pulled left
        val noteHeadCenter = Offset(w * 0.53f, h * 0.72f)
        val noteHeadRadius = w * 0.08f
        val stemX = w * 0.61f

        // Solid Note Head
        drawCircle(color = tint, center = noteHeadCenter, radius = noteHeadRadius)
        // Vertical Stem
        drawLine(color = tint, start = Offset(stemX, h * 0.72f), end = Offset(stemX, h * 0.40f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        // Upper Beam Flag
        val flagPath = Path().apply {
            moveTo(stemX, h * 0.40f)
            quadraticTo(w * 0.70f, h * 0.45f, w * 0.75f, h * 0.58f)
        }
        drawPath(path = flagPath, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

/**
 * Custom Smooth Heart Icon (Outlined or Filled).
 */
@Composable
fun CustomHeartIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    isFilled: Boolean = false
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.82f)
            cubicTo(w * 0.12f, h * 0.58f, w * 0.05f, h * 0.32f, w * 0.25f, h * 0.18f)
            cubicTo(w * 0.38f, h * 0.08f, w * 0.48f, h * 0.20f, w * 0.5f, h * 0.28f)
            cubicTo(w * 0.52f, h * 0.20f, w * 0.62f, h * 0.08f, w * 0.75f, h * 0.18f)
            cubicTo(w * 0.95f, h * 0.32f, w * 0.88f, h * 0.58f, w * 0.5f, h * 0.82f)
            close()
        }
        if (isFilled) {
            drawPath(path = path, color = tint, style = Fill)
        } else {
            drawPath(path = path, color = tint, style = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Custom Clean Plus Icon (+).
 */
@Composable
fun CustomPlusIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.085f
        drawLine(color = tint, start = Offset(w * 0.20f, h * 0.5f), end = Offset(w * 0.80f, h * 0.5f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.20f), end = Offset(w * 0.5f, h * 0.80f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

/**
 * Custom Shuffle Icon: Intersecting smooth curves with arrow tips.
 */
@Composable
fun CustomShuffleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.075f

        val p1 = Path().apply {
            moveTo(w * 0.18f, h * 0.32f)
            cubicTo(w * 0.42f, h * 0.32f, w * 0.58f, h * 0.68f, w * 0.82f, h * 0.68f)
        }
        drawPath(p1, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        val p2 = Path().apply {
            moveTo(w * 0.18f, h * 0.68f)
            cubicTo(w * 0.42f, h * 0.68f, w * 0.58f, h * 0.32f, w * 0.82f, h * 0.32f)
        }
        drawPath(p2, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        val topArrow = listOf(
            Offset(w * 0.72f, h * 0.20f),
            Offset(w * 0.86f, h * 0.32f),
            Offset(w * 0.72f, h * 0.44f)
        )
        drawLine(color = tint, start = topArrow[0], end = topArrow[1], strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = topArrow[2], end = topArrow[1], strokeWidth = stroke, cap = StrokeCap.Round)

        val botArrow = listOf(
            Offset(w * 0.72f, h * 0.56f),
            Offset(w * 0.86f, h * 0.68f),
            Offset(w * 0.72f, h * 0.80f)
        )
        drawLine(color = tint, start = botArrow[0], end = botArrow[1], strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = botArrow[2], end = botArrow[1], strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * Custom Auto Repeat Icon: Sleek looping arrows.
 */
@Composable
fun CustomABRepeatIcon(
    mode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f

        val rx = w * 0.15f
        val ry = h * 0.25f
        val rw = w * 0.70f
        val rh = h * 0.50f
        val corner = rh / 2f

        // Loop body
        drawRoundRect(
            color = tint,
            topLeft = Offset(rx, ry),
            size = Size(rw, rh),
            cornerRadius = CornerRadius(corner, corner),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        // Top right arrow head (pointing right)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.12f), end = Offset(w * 0.80f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.38f), end = Offset(w * 0.80f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Bottom left arrow head (pointing left)
        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.62f), end = Offset(w * 0.20f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.88f), end = Offset(w * 0.20f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)

        if (mode == 1) {
            // Draw a stylized "1" in the center
            val s1 = w * 0.08f
            // Vertical bar
            drawLine(color = tint, start = Offset(w * 0.52f, h * 0.40f), end = Offset(w * 0.52f, h * 0.60f), strokeWidth = s1, cap = StrokeCap.Round)
            // Tip
            drawLine(color = tint, start = Offset(w * 0.44f, h * 0.45f), end = Offset(w * 0.52f, h * 0.40f), strokeWidth = s1, cap = StrokeCap.Round)
        } else if (mode == 0) {
            // Draw a diagonal slash
            drawLine(
                color = tint.copy(alpha = 0.5f),
                start = Offset(w * 0.28f, h * 0.28f),
                end = Offset(w * 0.72f, h * 0.72f),
                strokeWidth = stroke * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Custom Replay Circle Icon: Enclosing circular loop with play triangle inside.
 */
@Composable
fun CustomReplayCircleIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.075f

        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.70f, h * 0.70f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val arrowTip = Offset(w * 0.50f, h * 0.15f)
        drawLine(color = tint, start = Offset(w * 0.40f, h * 0.04f), end = arrowTip, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.40f, h * 0.26f), end = arrowTip, strokeWidth = stroke, cap = StrokeCap.Round)

        val triRadius = w * 0.10f
        val triVertices = listOf(
            Offset(w * 0.44f, h * 0.38f),
            Offset(w * 0.64f, h * 0.50f),
            Offset(w * 0.44f, h * 0.62f)
        )
        drawRoundedPolygon(triVertices, triRadius, tint)
    }
}

/**
 * Custom Volume Icon: Speaker cone body + dual outward sound waves.
 */
@Composable
fun CustomVolumeIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.075f

        val conePath = Path().apply {
            moveTo(w * 0.15f, h * 0.38f)
            lineTo(w * 0.28f, h * 0.38f)
            lineTo(w * 0.48f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.80f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.15f, h * 0.62f)
            close()
        }
        drawPath(conePath, color = tint, style = Fill)

        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.32f),
            size = Size(w * 0.32f, h * 0.36f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        drawArc(
            color = tint,
            startAngle = -50f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.48f, h * 0.20f),
            size = Size(w * 0.44f, h * 0.60f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * Custom Equalizer Spectrum Icon: 4 vertical rounded spectrum bars.
 */
@Composable
fun CustomEqualizerIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.12f
        val capRadius = barWidth / 2f
        val gap = w * 0.11f
        val startX = w * 0.15f

        val heights = listOf(h * 0.55f, h * 0.80f, h * 0.35f, h * 0.65f)

        heights.forEachIndexed { i, barH ->
            val x = startX + i * (barWidth + gap)
            val y = (h - barH) / 2f + h * 0.05f
            drawRoundRect(
                color = tint,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(capRadius, capRadius)
            )
        }
    }
}

/**
 * Custom Vertical More Menu Icon (3 stacked dots).
 */
@Composable
fun CustomMoreVertIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dotRadius = w * 0.085f
        val centerX = w * 0.5f

        drawCircle(color = tint, center = Offset(centerX, h * 0.22f), radius = dotRadius)
        drawCircle(color = tint, center = Offset(centerX, h * 0.50f), radius = dotRadius)
        drawCircle(color = tint, center = Offset(centerX, h * 0.78f), radius = dotRadius)
    }
}
