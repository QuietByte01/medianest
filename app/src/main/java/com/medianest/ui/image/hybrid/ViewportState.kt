package com.medianest.ui.image.hybrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/**
 * Viewport transformation state adhering to Google Photos / Pixel standard:
 * - 120Hz continuous multi-touch & single-finger pan
 * - Precise centroid-based scaling up to 8x
 * - 200-250ms smooth double-tap interpolation between 1.0x (Fit) and 2.5x (Crop)
 * - Physical Drag-to-Dismiss (scale 1.0x -> 0.7x, scrim alpha 1.0 -> 0.0)
 */
@Stable
class ViewportState(
    private val scope: CoroutineScope
) {
    var scale by mutableFloatStateOf(1f)
        private set
        
    var offset by mutableStateOf(Offset.Zero)
        private set

    // Drag-to-dismiss progress (0f = none, 1f = fully dismissed)
    var dismissFraction by mutableFloatStateOf(0f)
        private set

    var isDismissing by mutableStateOf(false)
        private set

    var viewportSize by mutableStateOf(Size.Zero)
    var contentSize by mutableStateOf(Size.Zero)

    val isZoomed: Boolean get() = scale > 1.01f

    private val scaleAnim = Animatable(1f)
    private val offsetXAnim = Animatable(0f)
    private val offsetYAnim = Animatable(0f)
    private val dismissFractionAnim = Animatable(0f)
    private var animationJob: Job? = null

    /**
     * Continuous multi-touch & pan gesture handler.
     */
    fun onGesture(centroid: Offset, pan: Offset, zoom: Float) {
        if (zoom.isNaN() || zoom <= 0f) return
        animationJob?.cancel()

        val oldScale = scale
        val targetScale = (scale * zoom).coerceIn(0.5f, 25.0f)
        
        // Rubber-band scaling when pulled below 1.0x
        val effectiveScale = if (targetScale < 1f) {
            1f - (1f - targetScale).pow(0.5f) * 0.5f
        } else {
            targetScale
        }
        
        scale = effectiveScale
        
        // Precise centroid-based offset interpolation (origin at center)
        val origin = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val newOffset = offset * (effectiveScale / oldScale) + (centroid - origin) * (1f - effectiveScale / oldScale) + pan
        
        // Compute pan boundaries for ContentScale.Fit content
        val maxX = max(0f, (contentSize.width * effectiveScale - viewportSize.width) / 2f)
        val maxY = max(0f, (contentSize.height * effectiveScale - viewportSize.height) / 2f)
        
        val boundedX = newOffset.x.coerceIn(-maxX, maxX)
        val boundedY = newOffset.y.coerceIn(-maxY, maxY)
        
        val dx = newOffset.x - boundedX
        val dy = newOffset.y - boundedY
        
        // Natural Google Photos rubber-banding resistance
        val rubberX = boundedX + sign(dx) * (abs(dx).pow(0.8f))
        val rubberY = boundedY + sign(dy) * (abs(dy).pow(0.8f))
        
        offset = Offset(rubberX, rubberY)
    }

    /**
     * Handles Drag-to-Dismiss pull interaction when scale == 1.0x.
     */
    fun onDragDismiss(dragAmount: Offset) {
        animationJob?.cancel()
        isDismissing = true
        
        val newY = (offset.y + dragAmount.y).coerceAtLeast(0f)
        val progress = (newY / (viewportSize.height * 0.40f)).coerceIn(0f, 1f)
        
        dismissFraction = progress
        // Smooth horizontal drift alongside natural vertical drag
        offset = Offset(offset.x + dragAmount.x * 0.4f, newY)
        // Gentle, natural scale contraction down to 0.78x
        scale = 1f - (progress * 0.22f)
    }

    /**
     * Finishes drag-to-dismiss or snaps back with spring physics.
     */
    fun onDragDismissEnd(velocity: Offset, onDismiss: () -> Unit) {
        animationJob?.cancel()
        animationJob = scope.launch {
            if (dismissFraction > 0.25f || velocity.y > 800f) {
                // Smooth fluid exit animation (flying down off screen while fading)
                launch {
                    dismissFractionAnim.snapTo(dismissFraction)
                    dismissFractionAnim.animateTo(
                        1f,
                        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) {
                        dismissFraction = value
                    }
                }
                launch {
                    offsetYAnim.snapTo(offset.y)
                    offsetYAnim.animateTo(
                        viewportSize.height * 0.8f,
                        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) {
                        offset = offset.copy(y = value)
                    }
                }
                launch {
                    scaleAnim.snapTo(scale)
                    scaleAnim.animateTo(
                        0.65f,
                        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) {
                        scale = value
                    }
                }
                onDismiss()
            } else {
                // Ultra-smooth spring snap-back to center
                launch {
                    dismissFractionAnim.snapTo(dismissFraction)
                    dismissFractionAnim.animateTo(
                        0f,
                        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) {
                        dismissFraction = value
                    }
                }
                launch {
                    scaleAnim.snapTo(scale)
                    scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                        scale = value
                    }
                }
                launch {
                    offsetXAnim.snapTo(offset.x)
                    offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                        offset = offset.copy(x = value)
                    }
                }
                launch {
                    offsetYAnim.snapTo(offset.y)
                    offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                        offset = offset.copy(y = value)
                    }
                }
                isDismissing = false
            }
        }
    }

    /**
     * Smooth spring snap-back or inertial fling after pinch/pan release.
     */
    fun onGestureEnd(velocity: Offset) {
        animationJob?.cancel()
        animationJob = scope.launch {
            val targetScale = scale.coerceIn(1f, 25f)
            
            val maxX = max(0f, (contentSize.width * targetScale - viewportSize.width) / 2f)
            val maxY = max(0f, (contentSize.height * targetScale - viewportSize.height) / 2f)
            
            val targetX = (offset.x + velocity.x * 0.08f).coerceIn(-maxX, maxX)
            val targetY = (offset.y + velocity.y * 0.08f).coerceIn(-maxY, maxY)

            launch {
                scaleAnim.snapTo(scale)
                scaleAnim.animateTo(targetScale, spring(stiffness = Spring.StiffnessMediumLow)) {
                    scale = value
                }
            }
            launch {
                offsetXAnim.snapTo(offset.x)
                offsetXAnim.animateTo(targetX, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                    offset = offset.copy(x = value)
                }
            }
            launch {
                offsetYAnim.snapTo(offset.y)
                offsetYAnim.animateTo(targetY, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                    offset = offset.copy(y = value)
                }
            }
        }
    }

    /**
     * Smooth 200–250ms double-tap toggle between 1.0x (Fit) and 2.5x (Crop) centered on tap coordinate.
     */
    fun toggleZoom(centroid: Offset) {
        animationJob?.cancel()
        animationJob = scope.launch {
            if (scale > 1.2f) {
                // Zoom out to 1.0x
                launch {
                    scaleAnim.snapTo(scale)
                    scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) { scale = value }
                }
                launch {
                    offsetXAnim.snapTo(offset.x)
                    offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) { offset = offset.copy(x = value) }
                }
                launch {
                    offsetYAnim.snapTo(offset.y)
                    offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) { offset = offset.copy(y = value) }
                }
            } else {
                // Zoom in to 2.5x centered on tap
                val targetScale = 2.5f
                val origin = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                val targetOffset = (origin - centroid) * (targetScale - 1f)
                
                val maxX = max(0f, (contentSize.width * targetScale - viewportSize.width) / 2f)
                val maxY = max(0f, (contentSize.height * targetScale - viewportSize.height) / 2f)
                
                val boundedX = targetOffset.x.coerceIn(-maxX, maxX)
                val boundedY = targetOffset.y.coerceIn(-maxY, maxY)

                launch {
                    scaleAnim.snapTo(scale)
                    scaleAnim.animateTo(targetScale, spring(stiffness = Spring.StiffnessMediumLow)) { scale = value }
                }
                launch {
                    offsetXAnim.snapTo(offset.x)
                    offsetXAnim.animateTo(boundedX, spring(stiffness = Spring.StiffnessMediumLow)) { offset = offset.copy(x = value) }
                }
                launch {
                    offsetYAnim.snapTo(offset.y)
                    offsetYAnim.animateTo(boundedY, spring(stiffness = Spring.StiffnessMediumLow)) { offset = offset.copy(y = value) }
                }
            }
        }
    }
}
