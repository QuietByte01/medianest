package com.medianest.ui.image.hybrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Manages pinch-zoom, pan, and double-tap gestures.
 * Calculates viewport state and determines handoff thresholds with hysteresis.
 */
class ZoomController(
    val config: HybridImageViewerConfig = HybridImageViewerConfig()
) {
    private val animScale = Animatable(1f)
    private val animOffset = Animatable(Offset.Zero, Offset.VectorConverter)
    
    private var containerSize by mutableStateOf(Size.Zero)
    private var contentSize by mutableStateOf(Size.Zero)

    /**
     * Unified viewport state, automatically updated during animations.
     */
    val viewport by derivedStateOf {
        ImageViewport(
            scale = animScale.value,
            offsetX = animOffset.value.x,
            offsetY = animOffset.value.y,
            containerSize = containerSize,
            contentSize = contentSize
        )
    }

    /**
     * Determines if the tiled renderer should be active based on current scale and hysteresis.
     */
    var useTiledRenderer by mutableStateOf(false)
        private set

    private var isOriginallyGiant = false

    fun updateContainerSize(size: Size) {
        if (containerSize != size) {
            containerSize = size
        }
    }

    fun updateContentSize(size: Size) {
        if (contentSize != size) {
            contentSize = size
        }
    }

    fun setInitialGiantState(isGiant: Boolean) {
        isOriginallyGiant = isGiant
        if (isGiant) {
            useTiledRenderer = true
        }
    }

    suspend fun handleZoom(zoomFactor: Float, centroid: Offset) = coroutineScope {
        val oldScale = animScale.value
        val newScale = (oldScale * zoomFactor).coerceIn(config.minZoomScale, config.maxZoomScale)
        
        val centerX = containerSize.width / 2f
        val centerY = containerSize.height / 2f
        
        val scaleChange = newScale / oldScale
        val currentOffset = animOffset.value
        
        val newOffsetX = (currentOffset.x + centroid.x - centerX) * scaleChange - (centroid.x - centerX)
        val newOffsetY = (currentOffset.y + centroid.y - centerY) * scaleChange - (centroid.y - centerY)
        
        val clamped = viewport.copy(scale = newScale).clampOffset(newOffsetX, newOffsetY)
        
        launch { animScale.snapTo(newScale) }
        launch { animOffset.snapTo(clamped) }
        
        checkHandoff(newScale)
    }

    suspend fun handlePan(dragAmount: Offset) {
        val newX = animOffset.value.x + dragAmount.x
        val newY = animOffset.value.y + dragAmount.y
        val clamped = viewport.clampOffset(newX, newY)
        
        animOffset.snapTo(clamped)
    }

    suspend fun handleDoubleTap(centroid: Offset) = coroutineScope {
        if (animScale.value > 1.1f) {
            // Release tiled renderer BEFORE animating back to 1× so tiles
            // are not decoded at 1× scale during the animation.
            checkHandoff(1f)
            launch { animScale.animateTo(1f, tween(350)) }
            launch { animOffset.animateTo(Offset.Zero, tween(350)) }
        } else {
            val targetScale = 3f
            val centerX = containerSize.width / 2f
            val centerY = containerSize.height / 2f

            val targetX = (centerX - centroid.x) * (targetScale - 1f)
            val targetY = (centerY - centroid.y) * (targetScale - 1f)

            val clamped = viewport.copy(scale = targetScale).clampOffset(targetX, targetY)

            // Animate first; the tiled renderer will activate naturally via handleZoom
            // once the pinch gesture crosses zoomHandoffScale, or via the final
            // checkHandoff with the real animated scale value below.
            launch { animScale.animateTo(targetScale, tween(400)) }
            launch { animOffset.animateTo(clamped, tween(400)) }
            checkHandoff(animScale.value)
        }
    }

    suspend fun setScale(targetScale: Float) = coroutineScope {
        val newScale = targetScale.coerceIn(config.minZoomScale, config.maxZoomScale)
        val clamped = viewport.copy(scale = newScale).clampOffset(animOffset.value.x, animOffset.value.y)

        // Evaluate handoff against the target so zooming out releases the tiled renderer
        // before the animation starts (avoids tiles flickering at mid-animation scale).
        checkHandoff(newScale)
        launch { animScale.animateTo(newScale, tween(300)) }
        launch { animOffset.animateTo(clamped, tween(300)) }
    }

    private fun checkHandoff(scale: Float) {
        if (isOriginallyGiant) {
            useTiledRenderer = true
            return
        }
        if (!useTiledRenderer && scale >= config.zoomHandoffScale) {
            useTiledRenderer = true
        } else if (useTiledRenderer && scale <= config.zoomReleaseScale) {
            useTiledRenderer = false
        }
    }

    /**
     * Syncs animatables if viewport is changed externally (e.g. initial load).
     */
    suspend fun sync(scale: Float, offset: Offset) = coroutineScope {
        launch { animScale.snapTo(scale) }
        launch { animOffset.snapTo(offset) }
        checkHandoff(scale)
    }
}

@Composable
fun rememberZoomController(config: HybridImageViewerConfig): ZoomController {
    return remember(config) { ZoomController(config) }
}
