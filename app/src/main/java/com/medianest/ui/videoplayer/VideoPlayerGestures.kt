package com.medianest.ui.videoplayer

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SwipeEdge { NONE, LEFT, RIGHT }

/**
 * Attaches video player gesture handling.
 *
 * All callbacks are wrapped in rememberUpdatedState so they always reflect the latest
 * lambda without cancelling the active pointerInput gesture. The pointerInput block is
 * keyed only on [currentItemId] — it restarts only when the media item changes, NOT on
 * every playerState recomposition (e.g. isPlaying changes during scrubbing).
 *
 * The [coroutineScope] is used for single-tap delay jobs launched from within the
 * restricted awaitPointerEventScope.
 */
@Composable
fun Modifier.videoPlayerGestures(
    currentItemId: Long?,
    isControlsLocked: Boolean,
    isZoomed: Boolean,
    coroutineScope: CoroutineScope,
    onToggleControls: () -> Unit,
    onSeekStart: () -> Unit = {},
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
): Modifier {
    // rememberUpdatedState ensures the gesture coroutine always invokes the latest lambda
    // without pointerInput being cancelled and restarted on every recomposition.
    val onToggleControlsRef by rememberUpdatedState(onToggleControls)
    val onSeekStartRef by rememberUpdatedState(onSeekStart)
    val onSeekDeltaRef by rememberUpdatedState(onSeekDelta)
    val onVolumeChangeRef by rememberUpdatedState(onVolumeChange)
    val onBrightnessChangeRef by rememberUpdatedState(onBrightnessChange)
    val onScaleChangeRef by rememberUpdatedState(onScaleChange)
    val onPanDeltaRef by rememberUpdatedState(onPanDelta)
    val onSeekBackwardRef by rememberUpdatedState(onSeekBackward)
    val onSeekForwardRef by rememberUpdatedState(onSeekForward)
    val onTogglePlayPauseRef by rememberUpdatedState(onTogglePlayPause)
    val onDragStartedRef by rememberUpdatedState(onDragStarted)
    val onDragEndedRef by rememberUpdatedState(onDragEnded)
    val onEdgeSwipeProgressRef by rememberUpdatedState(onEdgeSwipeProgress)
    val isControlsLockedRef by rememberUpdatedState(isControlsLocked)
    val isZoomedRef by rememberUpdatedState(isZoomed)

    return this.pointerInput(currentItemId) {
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

                                if (!isControlsLockedRef) {
                                    val x = changes[0].position.x
                                    val width = size.width
                                    when {
                                        x < width / 3f -> onSeekBackwardRef()
                                        x > width * 2f / 3f -> onSeekForwardRef()
                                        else -> onTogglePlayPauseRef()
                                    }
                                }
                                lastTapTime = 0L
                            } else {
                                lastTapTime = currentTime
                                // Use the passed-in coroutineScope to launch delay outside the
                                // restricted awaitPointerEventScope context.
                                singleTapJob = coroutineScope.launch {
                                    delay(doubleTapTimeout)
                                    if (lastTapTime != 0L) {
                                        onToggleControlsRef()
                                        lastTapTime = 0L
                                    }
                                }
                            }
                        }

                        if (dragType != 0) {
                            onDragEndedRef()
                        }
                        if (dragType == 3) onEdgeSwipeProgressRef(SwipeEdge.NONE, 0f)

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
                            if (!isControlsLockedRef) onScaleChangeRef(zoom)
                            changes.forEach { it.consume() }
                        } else if (changes.size == 1 && dragType != -1) {
                            val change = changes[0]
                            val dragAmount = change.position - change.previousPosition
                            totalDragOffset += dragAmount

                            if (dragType == 0) {
                                if (abs(totalDragOffset.y) > slop && abs(totalDragOffset.y) > abs(totalDragOffset.x)) {
                                    dragType = if (isZoomedRef) 4 else 1
                                    onDragStartedRef()
                                } else if (abs(totalDragOffset.x) > slop && abs(totalDragOffset.x) > abs(totalDragOffset.y)) {
                                    when {
                                        initialEdge != SwipeEdge.NONE -> dragType = 3
                                        isZoomedRef -> { dragType = 4; onDragStartedRef() }
                                        else -> { dragType = 2; onSeekStartRef() }
                                    }
                                }
                            }

                            when (dragType) {
                                3 -> {
                                    val pullDistance = if (initialEdge == SwipeEdge.LEFT) totalDragOffset.x else -totalDragOffset.x
                                    val progress = (pullDistance / maxEdgeSwipeDistancePx).coerceIn(0f, 1.2f)
                                    onEdgeSwipeProgressRef(initialEdge, progress)
                                }
                                1 -> if (!isControlsLockedRef) {
                                    val delta = -dragAmount.y / size.height.toFloat()
                                    if (change.position.x < screenWidth / 2f) onBrightnessChangeRef(delta) else onVolumeChangeRef(delta)
                                    change.consume()
                                }
                                2 -> if (!isControlsLockedRef) {
                                    val deltaRatio = dragAmount.x / screenWidth.toFloat()
                                    onSeekDeltaRef((deltaRatio * 120_000).toLong())
                                    change.consume()
                                }
                                4 -> if (!isControlsLockedRef) {
                                    onPanDeltaRef(dragAmount)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
