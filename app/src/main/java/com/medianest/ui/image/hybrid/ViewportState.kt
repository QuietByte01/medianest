package com.medianest.ui.image.hybrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

@Stable
class ViewportState(
    private val scope: CoroutineScope
) {
    var scale by mutableStateOf(1f)
        private set
        
    var offset by mutableStateOf(Offset.Zero)
        private set

    var viewportSize by mutableStateOf(Size.Zero)
    var contentSize by mutableStateOf(Size.Zero)

    val isZoomed: Boolean get() = scale > 1.01f

    private val scaleAnim = Animatable(1f)
    private val offsetXAnim = Animatable(0f)
    private val offsetYAnim = Animatable(0f)

    fun onGesture(centroid: Offset, pan: Offset, zoom: Float) {
        if (zoom.isNaN() || zoom <= 0f) return
        val oldScale = scale
        val targetScale = (scale * zoom).coerceIn(0.5f, 10f)
        
        val effectiveScale = if (targetScale < 1f) {
            1f - (1f - targetScale).pow(0.5f) * 0.5f
        } else {
            targetScale
        }
        
        scale = effectiveScale
        
        // Exact formula for scaling around a centroid with pan when TransformOrigin is Center
        val origin = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val newOffset = offset * (effectiveScale / oldScale) + (centroid - origin) * (1f - effectiveScale / oldScale) + pan
        
        // Boundaries
        val maxX = max(0f, (contentSize.width * effectiveScale - viewportSize.width) / 2f)
        val maxY = max(0f, (contentSize.height * effectiveScale - viewportSize.height) / 2f)
        
        val boundedX = newOffset.x.coerceIn(-maxX, maxX)
        val boundedY = newOffset.y.coerceIn(-maxY, maxY)
        
        val dx = newOffset.x - boundedX
        val dy = newOffset.y - boundedY
        
        val rubberX = boundedX + sign(dx) * (kotlin.math.abs(dx).pow(0.8f))
        val rubberY = boundedY + sign(dy) * (kotlin.math.abs(dy).pow(0.8f))
        
        offset = Offset(rubberX, rubberY)
        
        scope.launch {
            scaleAnim.snapTo(scale)
            offsetXAnim.snapTo(offset.x)
            offsetYAnim.snapTo(offset.y)
        }
    }

    fun onGestureEnd(velocity: Offset) {
        scope.launch {
            val targetScale = scale.coerceIn(1f, 10f)
            
            val maxX = max(0f, (contentSize.width * targetScale - viewportSize.width) / 2f)
            val maxY = max(0f, (contentSize.height * targetScale - viewportSize.height) / 2f)
            
            val targetX = (offset.x + velocity.x * 0.1f).coerceIn(-maxX, maxX)
            val targetY = (offset.y + velocity.y * 0.1f).coerceIn(-maxY, maxY)

            launch {
                scaleAnim.animateTo(targetScale, spring(stiffness = Spring.StiffnessLow)) {
                    scale = value
                }
            }
            launch {
                offsetXAnim.animateTo(targetX, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                    offset = offset.copy(x = value)
                }
            }
            launch {
                offsetYAnim.animateTo(targetY, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)) {
                    offset = offset.copy(y = value)
                }
            }
        }
    }

    fun toggleZoom(centroid: Offset) {
        scope.launch {
            val targetScale = if (isZoomed) 1f else 2.5f
            
            val maxX = max(0f, (contentSize.width * targetScale - viewportSize.width) / 2f)
            val maxY = max(0f, (contentSize.height * targetScale - viewportSize.height) / 2f)
            
            // Calculate target offset to center the tap point
            val targetOffsetX = if (targetScale > 1f) {
                ((viewportSize.width / 2f - centroid.x) * targetScale).coerceIn(-maxX, maxX)
            } else 0f
            
            val targetOffsetY = if (targetScale > 1f) {
                ((viewportSize.height / 2f - centroid.y) * targetScale).coerceIn(-maxY, maxY)
            } else 0f

            launch {
                scaleAnim.animateTo(targetScale, spring(stiffness = Spring.StiffnessLow)) {
                    scale = value
                }
            }
            launch {
                offsetXAnim.animateTo(targetOffsetX, spring(stiffness = Spring.StiffnessLow)) {
                    offset = offset.copy(x = value)
                }
            }
            launch {
                offsetYAnim.animateTo(targetOffsetY, spring(stiffness = Spring.StiffnessLow)) {
                    offset = offset.copy(y = value)
                }
            }
        }
    }
}

@Composable
fun rememberViewportState(): ViewportState {
    val scope = rememberCoroutineScope()
    return remember { ViewportState(scope) }
}
