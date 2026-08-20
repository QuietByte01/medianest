package com.medianest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Helper — detect drag ONLY on the visible scrollbar thumb and translate to list-scroll commands.
// Does NOT intercept or consume taps anywhere on the screen/track.
private fun Modifier.scrollBarDrag(
    getThumbBounds: (containerHeight: Float) -> Pair<Float, Float>?,
    isScrollBarVisible: () -> Boolean,
    touchZoneWidthPx: Float,
    onScroll: (fraction: Float) -> Unit,
    onInteraction: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val touch = down.changes.firstOrNull() ?: continue

            // Only interact if scrollbar is visible and touch is on the rightmost strip
            if (!isScrollBarVisible()) continue
            val xThreshold = size.width - touchZoneWidthPx
            if (touch.position.x < xThreshold) continue

            val bounds = getThumbBounds(size.height.toFloat()) ?: continue
            val (thumbY, thumbH) = bounds

            // Check if touch is vertically on/near the actual scrollbar thumb (with padding)
            val touchPaddingY = 16.dp.toPx()
            if (touch.position.y < thumbY - touchPaddingY || touch.position.y > thumbY + thumbH + touchPaddingY) {
                continue
            }

            // Touch is on the thumb. Wait to see if user actually drags (do NOT consume down on tap)
            var isDragging = false
            val initialTouchY = touch.position.y
            val initialThumbY = thumbY

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break

                val deltaY = change.position.y - initialTouchY
                if (!isDragging && abs(deltaY) > 8f) {
                    isDragging = true
                }

                if (isDragging) {
                    onInteraction()
                    change.consume()

                    val maxScrollY = (size.height - thumbH).coerceAtLeast(1f)
                    val newThumbY = (initialThumbY + deltaY).coerceIn(0f, maxScrollY)
                    val fraction = (newThumbY / maxScrollY).coerceIn(0f, 1f)
                    onScroll(fraction)
                }
            }
        }
    }
}

fun Modifier.translucentScrollBar(
    listState: LazyListState,
    color: Color = Color(0xAAFFFFFF),
    width: Dp = 6.dp
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val density = LocalDensity.current
    val minHPx = remember(density) { with(density) { 36.dp.toPx() } }
    val touchZonePx = remember(density) { with(density) { 36.dp.toPx() } }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    fun computeThumb(containerHeight: Float): Pair<Float, Float>? {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val totalItemsCount = listState.layoutInfo.totalItemsCount
        if (totalItemsCount <= visibleItems.size || visibleItems.isEmpty() || containerHeight <= 0f) return null

        val firstVisibleElementIndex = visibleItems.first().index
        val visibleRatio = visibleItems.size.toFloat() / totalItemsCount

        val minH = minHPx.coerceAtMost(containerHeight)
        val maxH = (containerHeight * 0.7f).coerceAtLeast(minH)
        val scrollBarHeight = (containerHeight * visibleRatio).coerceIn(minH, maxH)

        val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
        val scrollProgress = (firstVisibleElementIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
        val scrollBarOffsetY = scrollProgress * (containerHeight - scrollBarHeight)
        return Pair(scrollBarOffsetY, scrollBarHeight)
    }

    this.scrollBarDrag(
        getThumbBounds = { h -> computeThumb(h) },
        isScrollBarVisible = { alpha.value > 0.05f },
        touchZoneWidthPx = touchZonePx,
        onScroll = { fraction ->
            val total = listState.layoutInfo.totalItemsCount
            val target = (fraction * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
            CoroutineScope(Dispatchers.Main).launch {
                listState.scrollToItem(target)
            }
        },
        onInteraction = {
            CoroutineScope(Dispatchers.Main).launch { alpha.snapTo(1f) }
        }
    ).drawWithContent {
        drawContent()
        val thumb = computeThumb(size.height)
        if (thumb != null) {
            val (scrollBarOffsetY, scrollBarHeight) = thumb
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha.value),
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}

fun Modifier.translucentScrollBarGrid(
    gridState: LazyGridState,
    color: Color = Color(0xAAFFFFFF),
    width: Dp = 6.dp
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val density = LocalDensity.current
    val minHPx = remember(density) { with(density) { 36.dp.toPx() } }
    val touchZonePx = remember(density) { with(density) { 36.dp.toPx() } }

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    fun computeThumb(containerHeight: Float): Pair<Float, Float>? {
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        val totalItemsCount = gridState.layoutInfo.totalItemsCount
        if (totalItemsCount <= visibleItems.size || visibleItems.isEmpty() || containerHeight <= 0f) return null

        val firstVisibleElementIndex = visibleItems.first().index
        val visibleRatio = visibleItems.size.toFloat() / totalItemsCount

        val minH = minHPx.coerceAtMost(containerHeight)
        val maxH = (containerHeight * 0.7f).coerceAtLeast(minH)
        val scrollBarHeight = (containerHeight * visibleRatio).coerceIn(minH, maxH)

        val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
        val scrollProgress = (firstVisibleElementIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
        val scrollBarOffsetY = scrollProgress * (containerHeight - scrollBarHeight)
        return Pair(scrollBarOffsetY, scrollBarHeight)
    }

    this.scrollBarDrag(
        getThumbBounds = { h -> computeThumb(h) },
        isScrollBarVisible = { alpha.value > 0.05f },
        touchZoneWidthPx = touchZonePx,
        onScroll = { fraction ->
            val total = gridState.layoutInfo.totalItemsCount
            val target = (fraction * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
            CoroutineScope(Dispatchers.Main).launch {
                gridState.scrollToItem(target)
            }
        },
        onInteraction = {
            CoroutineScope(Dispatchers.Main).launch { alpha.snapTo(1f) }
        }
    ).drawWithContent {
        drawContent()
        val thumb = computeThumb(size.height)
        if (thumb != null) {
            val (scrollBarOffsetY, scrollBarHeight) = thumb
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha.value),
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}

fun Modifier.translucentScrollBarStaggeredGrid(
    staggeredGridState: LazyStaggeredGridState,
    color: Color = Color(0xAAFFFFFF),
    width: Dp = 6.dp
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val density = LocalDensity.current
    val minHPx = remember(density) { with(density) { 36.dp.toPx() } }
    val touchZonePx = remember(density) { with(density) { 36.dp.toPx() } }

    LaunchedEffect(staggeredGridState.isScrollInProgress) {
        if (staggeredGridState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    fun computeThumb(containerHeight: Float): Pair<Float, Float>? {
        val visibleItems = staggeredGridState.layoutInfo.visibleItemsInfo
        val totalItemsCount = staggeredGridState.layoutInfo.totalItemsCount
        if (totalItemsCount <= visibleItems.size || visibleItems.isEmpty() || containerHeight <= 0f) return null

        val firstVisibleElementIndex = visibleItems.first().index
        val visibleRatio = visibleItems.size.toFloat() / totalItemsCount

        val minH = minHPx.coerceAtMost(containerHeight)
        val maxH = (containerHeight * 0.7f).coerceAtLeast(minH)
        val scrollBarHeight = (containerHeight * visibleRatio).coerceIn(minH, maxH)

        val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
        val scrollProgress = (firstVisibleElementIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
        val scrollBarOffsetY = scrollProgress * (containerHeight - scrollBarHeight)
        return Pair(scrollBarOffsetY, scrollBarHeight)
    }

    this.scrollBarDrag(
        getThumbBounds = { h -> computeThumb(h) },
        isScrollBarVisible = { alpha.value > 0.05f },
        touchZoneWidthPx = touchZonePx,
        onScroll = { fraction ->
            val total = staggeredGridState.layoutInfo.totalItemsCount
            val target = (fraction * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
            CoroutineScope(Dispatchers.Main).launch {
                staggeredGridState.scrollToItem(target)
            }
        },
        onInteraction = {
            CoroutineScope(Dispatchers.Main).launch { alpha.snapTo(1f) }
        }
    ).drawWithContent {
        drawContent()
        val thumb = computeThumb(size.height)
        if (thumb != null) {
            val (scrollBarOffsetY, scrollBarHeight) = thumb
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha.value),
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}
