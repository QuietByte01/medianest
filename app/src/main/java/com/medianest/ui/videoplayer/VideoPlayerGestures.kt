package com.medianest.ui.videoplayer

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import com.medianest.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SwipeEdge { NONE, LEFT }

fun Modifier.videoPlayerGestures(
    playerState: PlayerState,
    isControlsLocked: Boolean,
    onToggleControls: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onFeedback: (String) -> Unit,
    onDragStarted: () -> Unit,
    onDragEnded: () -> Unit,
    onEdgeSwipeProgress: (SwipeEdge, Float) -> Unit,
    coroutineScope: CoroutineScope
): Modifier = this.pointerInput(playerState.currentItem) {
    val edgeThresholdPx = 32.dp.toPx()
    val maxEdgeSwipeDistancePx = 140.dp.toPx()
    val doubleTapTimeout = 300L
    val slop = 10f

    var lastTapTime = 0L
    var singleTapJob: Job? = null

    awaitPointerEventScope {
        while (true) {
            // 1. Wait for initial pointer down event
            val down = awaitFirstDown(requireUnconsumed = false)
            val initialX = down.position.x

            val isTouchNearLeftEdge = initialX < edgeThresholdPx
            val initialEdge = when {
                isTouchNearLeftEdge -> SwipeEdge.LEFT
                else -> SwipeEdge.NONE
            }

            var isPinching = false
            var dragType = 0 // 0: None, 1: Vertical, 2: Horizontal Seek, 3: Edge Swipe
            var totalDragOffset = Offset.Zero

            // 2. Track pointer event loop
            while (true) {
                val event = awaitPointerEvent()
                val changes = event.changes

                // --- Handle Pointer Release ---
                if (event.type == PointerEventType.Release) {
                    if (changes.size == 1 && !isPinching && dragType == 0) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < doubleTapTimeout) {
                            singleTapJob?.cancel()
                            singleTapJob = null

                            if (!isControlsLocked) {
                                val x = changes[0].position.x
                                val width = size.width
                                when {
                                    x < width / 3f -> onSeekBackward()
                                    x > width * 2f / 3f -> onSeekForward()
                                    else -> onTogglePlayPause()
                                }
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = currentTime
                            singleTapJob = coroutineScope.launch {
                                delay(doubleTapTimeout)
                                if (lastTapTime != 0L) {
                                    onToggleControls()
                                    lastTapTime = 0L
                                }
                            }
                        }
                    }

                    if (dragType == 2) {
                        onDragEnded() // Trigger final seek
                    } else if (dragType == 1) {
                        onDragEnded() // Clean up vertical drag state
                    }

                    if (dragType == 3) {
                        onEdgeSwipeProgress(SwipeEdge.NONE, 0f) // Dismiss edge overlay on release
                    }

                    if (changes.all { !it.pressed }) break
                }

                // --- Handle Pointer Movement ---
                if (event.type == PointerEventType.Move) {
                    if (changes.size > 1) {
                        // Pinch Zoom (Multi-touch)
                        isPinching = true
                        dragType = -1
                        val zoom = event.calculateZoom()
                        if (!isControlsLocked) onScaleChange(zoom)
                        changes.forEach { it.consume() }
                    } else if (changes.size == 1 && !isPinching && dragType != -1) {
                        val change = changes[0]
                        val dragAmount = change.position - change.previousPosition
                        totalDragOffset += dragAmount

                        // Lock gesture orientation once passing slop threshold
                        if (dragType == 0) {
                            if (abs(totalDragOffset.y) > slop && abs(totalDragOffset.y) > abs(totalDragOffset.x)) {
                                dragType = 1 // Vertical Lock (Brightness / Volume)
                                onDragStarted()
                            } else if (abs(totalDragOffset.x) > slop && abs(totalDragOffset.x) > abs(totalDragOffset.y)) {
                                if (initialEdge != SwipeEdge.NONE) {
                                    dragType = 3 // Edge Swipe Lock (Back / Next Edge)
                                } else {
                                    dragType = 2 // Standard Horizontal Seek Drag
                                    onDragStarted()
                                }
                            }
                        }

                        // --- Gesture Action Execution ---
                        if (dragType == 3) {
                            // Track left edge swipe progress without consuming events (allows system back swipe)
                            val pullDistance = totalDragOffset.x
                            val progress = (pullDistance / maxEdgeSwipeDistancePx).coerceIn(0f, 1.2f)
                            onEdgeSwipeProgress(initialEdge, progress)
                        } else if (dragType == 1 && !isControlsLocked) {
                            // Vertical Brightness & Volume control
                            val delta = -dragAmount.y / size.height.toFloat()
                            if (change.position.x < size.width / 2f) {
                                onBrightnessChange(delta)
                            } else {
                                onVolumeChange(delta)
                            }
                            change.consume()
                        } else if (dragType == 2 && !isControlsLocked) {
                            // Horizontal Seek control
                            val deltaRatio = dragAmount.x / size.width.toFloat()
                            onSeekDelta((deltaRatio * 120_000).toLong())
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}