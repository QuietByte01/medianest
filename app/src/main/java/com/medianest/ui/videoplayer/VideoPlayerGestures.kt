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

enum class SwipeEdge { NONE, LEFT, RIGHT }

fun Modifier.videoPlayerGestures(
    playerState: PlayerState,
    isControlsLocked: Boolean,
    isZoomed: Boolean,
    onToggleControls: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onPanDelta: (Offset) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onFeedback: (String) -> Unit,
    onDragStarted: () -> Unit,
    onDragEnded: () -> Unit,
    onEdgeSwipeProgress: (SwipeEdge, Float) -> Unit,
    coroutineScope: CoroutineScope
): Modifier = this.pointerInput(playerState.currentItem, isZoomed, isControlsLocked) {
    val edgeThresholdPx = 48.dp.toPx() 
    val maxEdgeSwipeDistancePx = 140.dp.toPx()
    val doubleTapTimeout = 300L
    val slop = 15f 

    var lastTapTime = 0L
    var singleTapJob: Job? = null

    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val initialX = down.position.x
            val screenWidth = size.width

            val initialEdge = when {
                initialX < edgeThresholdPx -> SwipeEdge.LEFT
                initialX > screenWidth - edgeThresholdPx -> SwipeEdge.RIGHT
                else -> SwipeEdge.NONE
            }

            var dragType = 0 // 0: None, 1: Vertical, 2: Horizontal Seek, 3: Edge Swipe, 4: Pan
            var totalDragOffset = Offset.Zero
            var isPinching = false

            while (true) {
                val event = awaitPointerEvent()
                val changes = event.changes

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

                    if (dragType == 2) onDragEnded()
                    if (dragType == 3) onEdgeSwipeProgress(SwipeEdge.NONE, 0f)

                    if (changes.all { !it.pressed }) {
                        isPinching = false
                        break
                    }
                }

                if (event.type == PointerEventType.Move) {
                    if (changes.size > 1) {
                        isPinching = true
                        dragType = -1
                        val zoom = event.calculateZoom()
                        if (!isControlsLocked) onScaleChange(zoom)
                        changes.forEach { it.consume() }
                    } else if (changes.size == 1 && dragType != -1) {
                        val change = changes[0]
                        val dragAmount = change.position - change.previousPosition
                        totalDragOffset += dragAmount

                        if (dragType == 0) {
                            if (abs(totalDragOffset.y) > slop && abs(totalDragOffset.y) > abs(totalDragOffset.x)) {
                                if (isZoomed) {
                                    dragType = 4 // Vertical Pan
                                } else {
                                    dragType = 1 // Vertical Lock (Brightness / Volume)
                                }
                                onDragStarted()
                            } else if (abs(totalDragOffset.x) > slop && abs(totalDragOffset.x) > abs(totalDragOffset.y)) {
                                if (initialEdge != SwipeEdge.NONE) {
                                    dragType = 3 // Edge Swipe Lock - EXCLUSIVE
                                } else if (isZoomed) {
                                    dragType = 4 // Horizontal Pan
                                    onDragStarted()
                                } else {
                                    dragType = 2 // Standard Horizontal Seek Drag
                                    onDragStarted()
                                }
                            }
                        }

                        when (dragType) {
                            3 -> {
                                val pullDistance = if (initialEdge == SwipeEdge.LEFT) totalDragOffset.x else -totalDragOffset.x
                                val progress = (pullDistance / maxEdgeSwipeDistancePx).coerceIn(0f, 1.2f)
                                onEdgeSwipeProgress(initialEdge, progress)
                            }
                            1 -> if (!isControlsLocked) {
                                val delta = -dragAmount.y / size.height.toFloat()
                                if (change.position.x < screenWidth / 2f) onBrightnessChange(delta) else onVolumeChange(delta)
                                change.consume()
                            }
                            2 -> if (!isControlsLocked) {
                                val deltaRatio = dragAmount.x / screenWidth.toFloat()
                                onSeekDelta((deltaRatio * 120_000).toLong())
                                change.consume()
                            }
                            4 -> if (!isControlsLocked) {
                                onPanDelta(dragAmount)
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
    }
}
