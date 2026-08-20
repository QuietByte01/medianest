package com.medianest.ui.videoeditor.components

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.videoeditor.data.ThumbnailRepository
import com.medianest.ui.videoeditor.model.VideoThumbnail
import com.medianest.ui.videoeditor.model.VideoTimelineState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VideoTimeline(
    state: VideoTimelineState,
    thumbnailRepository: ThumbnailRepository?,
    onPositionChanged: (Long) -> Unit,
    onTrimChanged: (Long, Long) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var containerWidth by remember { mutableFloatStateOf(0f) }
    
    val thumbnails by thumbnailRepository?.thumbnails?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    // Constants
    val trackHeight = 60.dp
    val thumbnailWidthDp = 60.dp
    val thumbnailWidthPx = with(density) { thumbnailWidthDp.toPx() }

    // Coordinate conversion
    fun timeToPx(timeMs: Long, pps: Float = state.pixelsPerSecond): Float = (timeMs / 1000f) * pps
    fun pxToTime(px: Float, pps: Float = state.pixelsPerSecond): Long = ((px / pps) * 1000).toLong()

    val totalVideoWidth = timeToPx(state.durationMs)
    val horizontalPadding = containerWidth / 2
    
    val lazyListState = rememberLazyListState()

    // Sync scroll with positionMs
    LaunchedEffect(state.positionMs, state.pixelsPerSecond) {
        if (!lazyListState.isScrollInProgress && state.durationMs > 0) {
            val totalPx = timeToPx(state.positionMs)
            val itemIndex = (totalPx / thumbnailWidthPx).toInt()
            val itemOffset = (totalPx % thumbnailWidthPx).toInt()
            lazyListState.scrollToItem(itemIndex, itemOffset)
        }
    }

    // Sync positionMs with scroll
    val currentScrollPx = remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex * thumbnailWidthPx + lazyListState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(currentScrollPx.value) {
        if (lazyListState.isScrollInProgress) {
            val newPos = pxToTime(currentScrollPx.value).coerceIn(0, state.durationMs)
            onPositionChanged(newPos)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidth = it.width.toFloat() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight + 32.dp)
                .pointerInput(state.pixelsPerSecond) {
                    detectTransformGestures { centroid, _, zoom, _ ->
                        if (zoom != 1f) {
                            val focalTime = pxToTime(currentScrollPx.value + centroid.x - horizontalPadding)
                            val oldPps = state.pixelsPerSecond
                            val newPps = (oldPps * zoom).coerceIn(30f, 1000f)
                            
                            if (newPps != oldPps) {
                                onZoomChanged(newPps)
                                // Scroll adjustment is harder with LazyRow, but let's try
                                scope.launch {
                                    val pxBefore = (focalTime / 1000f) * oldPps
                                    val pxAfter = (focalTime / 1000f) * newPps
                                    val delta = pxAfter - pxBefore
                                    val newTotalPx = currentScrollPx.value + delta
                                    lazyListState.scrollToItem(
                                        (newTotalPx / thumbnailWidthPx).toInt(),
                                        (newTotalPx % thumbnailWidthPx).toInt()
                                    )
                                }
                            }
                        }
                    }
                }
        ) {
            // 1. Filmstrip
            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Padding
                item { Spacer(modifier = Modifier.width(with(density) { horizontalPadding.toDp() })) }

                // Thumbnails
                val thumbnailCount = (totalVideoWidth / thumbnailWidthPx).toInt().coerceAtLeast(1)
                items(thumbnailCount) { index ->
                    val timeMs = pxToTime(index * thumbnailWidthPx)
                    
                    // Request thumbnail if visible
                    DisposableEffect(timeMs) {
                        thumbnailRepository?.getThumbnail(timeMs, (thumbnailWidthPx * 1.5).toInt(), with(density) { trackHeight.toPx() * 1.5 }.toInt())
                        onDispose {}
                    }

                    Box(
                        modifier = Modifier
                            .width(thumbnailWidthDp)
                            .height(trackHeight)
                            .background(Color.DarkGray.copy(alpha = 0.3f))
                            .border(0.5.dp, Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        thumbnails[timeMs]?.bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Right Padding
                item { Spacer(modifier = Modifier.width(with(density) { horizontalPadding.toDp() })) }
            }

            // 2. Overlays (need to be anchored to the LazyRow's content)
            // This is tricky. We'll use a Box that covers the whole area and draw on it based on scroll position.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .align(Alignment.Center)
            ) {
                TrimSelectionOverlay(
                    state = state,
                    scrollOffset = currentScrollPx.value - horizontalPadding,
                    timeToPx = { timeToPx(it) },
                    pxToTime = { pxToTime(it) },
                    onTrimChanged = onTrimChanged,
                    trackHeight = trackHeight
                )
            }

            // 3. Playhead (Fixed)
            Playhead(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(trackHeight + 12.dp)
            )
        }
    }
}

@Composable
fun TrimSelectionOverlay(
    state: VideoTimelineState,
    scrollOffset: Float,
    timeToPx: (Long) -> Float,
    pxToTime: (Float) -> Long,
    onTrimChanged: (Long, Long) -> Unit,
    trackHeight: androidx.compose.ui.unit.Dp
) {
    val startPx = timeToPx(state.trimStartMs) - scrollOffset
    val endPx = timeToPx(state.trimEndMs) - scrollOffset
    val handleWidth = 16.dp
    val handleWidthPx = with(LocalDensity.current) { handleWidth.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Darken areas outside trim
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Left scrim
            if (startPx > 0) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, 0f),
                    size = Size(startPx, size.height)
                )
            }
            // Right scrim
            if (endPx < size.width) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(endPx.coerceAtLeast(0f), 0f),
                    size = Size(size.width - endPx.coerceAtLeast(0f), size.height)
                )
            }
        }

        // Selection Border
        if (endPx > startPx) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx.roundToInt(), 0) }
                    .width(with(LocalDensity.current) { (endPx - startPx).toDp() })
                    .fillMaxHeight()
                    .border(2.dp, Color.White, RoundedCornerShape(4.dp))
            )
        }

        // Handles
        // Start Handle
        Box(
            modifier = Modifier
                .offset { IntOffset((startPx - handleWidthPx).roundToInt(), 0) }
                .width(handleWidth)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val newTime = pxToTime(timeToPx(state.trimStartMs) + dragAmount)
                        onTrimChanged(newTime, state.trimEndMs)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.width(2.dp).height(20.dp).background(Color.Gray))
        }

        // End Handle
        Box(
            modifier = Modifier
                .offset { IntOffset(endPx.roundToInt(), 0) }
                .width(handleWidth)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val newTime = pxToTime(timeToPx(state.trimEndMs) + dragAmount)
                        onTrimChanged(state.trimStartMs, newTime)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.width(2.dp).height(20.dp).background(Color.Gray))
        }
    }
}

@Composable
fun Playhead(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(2.dp)
            .background(Color.White)
    )
}
