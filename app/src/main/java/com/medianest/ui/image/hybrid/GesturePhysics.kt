package com.medianest.ui.image.hybrid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Google Photos gesture coordinator:
 * - Clean Single Tap with 260ms double-tap disambiguation.
 * - Instant Double Tap centered precisely on tap coordinate (no pre-pan or pre-dismiss).
 * - Multi-touch Pinch-to-Zoom up to 25.0x.
 * - Single-finger Pan when scale > 1.0f.
 * - Physical Drag-to-Dismiss on vertical drag down when scale == 1.0f.
 * - Swipe-up for info bottom sheet.
 */
suspend fun PointerInputScope.detectGooglePhotosGestures(
    isZoomed: () -> Boolean,
    onGestureStart: () -> Unit = {},
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: (velocity: Offset) -> Unit,
    onDragDismiss: (dragAmount: Offset) -> Unit = {},
    onDragDismissEnd: (velocity: Offset) -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onTap: () -> Unit = {},
    onDoubleTap: (centroid: Offset) -> Unit = {}
) = coroutineScope {
    var singleTapJob: Job? = null
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var isDismissing = false
        val touchSlop = viewConfiguration.touchSlop

        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        onGestureStart()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        val downTime = down.uptimeMillis
        val downPos = down.position

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (canceled) break

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val centroid = event.calculateCentroid()
            val centroidSize = event.calculateCentroidSize(useCurrent = false)
            val pointerCount = event.changes.size

            if (!pastTouchSlop) {
                zoom *= zoomChange
                pan += panChange
                val panDist = pan.getDistance()
                val zoomDist = abs(1f - zoom) * centroidSize

                // Only start translating/dismissing once touch motion passes slop threshold
                if (panDist > touchSlop * 1.5f || zoomDist > touchSlop) {
                    pastTouchSlop = true
                    singleTapJob?.cancel()
                    singleTapJob = null

                    // If NOT zoomed, only vertical downward drag is dismiss
                    if (!isZoomed() && pointerCount == 1 && pan.y > 0 && abs(pan.y) > abs(pan.x) * 2.0f) {
                        isDismissing = true
                    }
                }
            }

            if (pastTouchSlop) {
                if (pointerCount == 1) {
                    velocityTracker.addPosition(event.changes.first().uptimeMillis, event.changes.first().position)
                } else {
                    velocityTracker.resetTracking()
                    isDismissing = false
                }

                if (isDismissing) {
                    onDragDismiss(panChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else {
                    // When not dismissing:
                    // If pinch-zooming OR already zoomed, handle zoom & 2D pan
                    if (pointerCount > 1 || isZoomed()) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            onGesture(centroid, panChange, zoomChange)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } else {
                        // At 1x scale single-finger: do NOT move/pan the image!
                        // Only detect intentional swipe up for info bottom sheet
                        if (pan.y < 0 && abs(pan.y) > touchSlop * 2f && abs(pan.y) > abs(pan.x) * 2.0f) {
                            onSwipeUp()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        if (!pastTouchSlop) {
            val timeSinceLastTap = downTime - lastTapTime
            val distSinceLastTap = (downPos - lastTapPosition).getDistance()

            if (timeSinceLastTap < 280L && distSinceLastTap < touchSlop * 4) {
                // Cancel pending single tap and trigger instant Double Tap
                singleTapJob?.cancel()
                singleTapJob = null
                lastTapTime = 0L
                onDoubleTap(downPos)
            } else {
                lastTapTime = downTime
                lastTapPosition = downPos
                // Disambiguate single tap vs double tap
                singleTapJob?.cancel()
                singleTapJob = launch {
                    delay(280L)
                    onTap()
                }
            }
        } else {
            val velocity = try {
                velocityTracker.calculateVelocity()
            } catch (e: Exception) {
                Velocity.Zero
            }

            if (isDismissing) {
                onDragDismissEnd(Offset(velocity.x, velocity.y))
            } else {
                onGestureEnd(Offset(velocity.x, velocity.y))
            }
        }
    }
}
