package com.medianest.ui.image.hybrid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

suspend fun PointerInputScope.detectInertialTransformGestures(
    canConsumePan: () -> Boolean,
    onGestureStart: () -> Unit = {},
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: (velocity: Offset) -> Unit,
    onSwipeUp: () -> Unit = {}
) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        var zoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        val down = awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid()
                val centroidSize = event.calculateCentroidSize(useCurrent = false)

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    val panDelta = panChange.getDistance()
                    val zoomDelta = (1f - zoom) * centroidSize
                    if (panDelta > touchSlop || abs(zoomDelta) > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (event.changes.size == 1) {
                        velocityTracker.addPosition(event.changes.first().uptimeMillis, event.changes.first().position)
                    } else {
                        velocityTracker.resetTracking() // Reset on multi-touch to avoid wild flings
                    }
                    onGesture(centroid, panChange, zoomChange)
                    
                    val isMultiTouch = event.changes.size > 1
                    if (isMultiTouch || canConsumePan()) {
                        if (event.changes.any { it.positionChanged() }) {
                            event.changes.forEach { it.consume() }
                        }
                    } else {
                        // Not consuming pan (e.g. scale == 1.0). Allow parent to intercept, 
                        // but if it's a strong upward swipe, fire onSwipeUp!
                        if (panChange.y < -10f && abs(panChange.y) > abs(panChange.x)) {
                            onSwipeUp()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        
        val velocity = if (pastTouchSlop) {
            try {
                velocityTracker.calculateVelocity()
            } catch (e: Exception) {
                androidx.compose.ui.unit.Velocity.Zero
            }
        } else {
            androidx.compose.ui.unit.Velocity.Zero
        }
        onGestureEnd(Offset(velocity.x, velocity.y))
    }
}
