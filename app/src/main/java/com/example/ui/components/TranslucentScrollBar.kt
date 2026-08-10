package com.example.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Helper — detect touch + drag on the scrollbar track and translate to list-scroll commands.
// Uses awaitPointerEventScope so we control hit-testing explicitly (the old detectVerticalDragGestures
// approach missed the initial touch-down in the scrollbar zone on many devices).
private fun Modifier.scrollBarDrag(
    scrollBarWidthPx: () -> Float,      // Width of the visible scrollbar pill
    touchZoneWidthPx: () -> Float,      // Extra horizontal touch zone to the left of pill
    totalItems: () -> Int,
    onScroll: (fraction: Float) -> Unit // 0f = top, 1f = bottom
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            // Wait for any pointer event on the initial pass
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val touch = down.changes.firstOrNull() ?: continue
            val xThreshold = size.width - touchZoneWidthPx()
            if (touch.position.x < xThreshold) continue   // Not in scrollbar zone — ignore

            touch.consume()
            var lastY = touch.position.y

            // Track pointer while it's held down
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                change.consume()

                val deltaY = change.position.y - lastY
                lastY = change.position.y

                val total = totalItems()
                if (total > 0 && size.height > 0) {
                    val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
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
): Modifier = this
    .scrollBarDrag(
        scrollBarWidthPx = { width.value * 3 },
        touchZoneWidthPx = { 56f },
        totalItems = { listState.layoutInfo.totalItemsCount },
        onScroll = { fraction ->
            val total = listState.layoutInfo.totalItemsCount
            val target = (fraction * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
            CoroutineScope(Dispatchers.Main).launch {
                listState.scrollToItem(target)
            }
        }
    )
    .drawWithContent {
        drawContent()
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val totalItemsCount = listState.layoutInfo.totalItemsCount
        if (totalItemsCount > visibleItems.size && visibleItems.isNotEmpty()) {
            val firstVisibleElementIndex = visibleItems.first().index
            val visibleRatio = visibleItems.size.toFloat() / totalItemsCount
            val scrollBarHeight = (size.height * visibleRatio).coerceIn(36.dp.toPx(), size.height * 0.7f)
            val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
            val scrollProgress = (firstVisibleElementIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
            val scrollBarOffsetY = scrollProgress * (size.height - scrollBarHeight)

            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }

fun Modifier.translucentScrollBarGrid(
    gridState: LazyGridState,
    color: Color = Color(0xAAFFFFFF),
    width: Dp = 6.dp
): Modifier = this
    .scrollBarDrag(
        scrollBarWidthPx = { width.value * 3 },
        touchZoneWidthPx = { 56f },
        totalItems = { gridState.layoutInfo.totalItemsCount },
        onScroll = { fraction ->
            val total = gridState.layoutInfo.totalItemsCount
            val target = (fraction * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
            CoroutineScope(Dispatchers.Main).launch {
                gridState.scrollToItem(target)
            }
        }
    )
    .drawWithContent {
        drawContent()
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        val totalItemsCount = gridState.layoutInfo.totalItemsCount
        if (totalItemsCount > visibleItems.size && visibleItems.isNotEmpty()) {
            val firstVisibleElementIndex = visibleItems.first().index
            val visibleRatio = visibleItems.size.toFloat() / totalItemsCount
            val scrollBarHeight = (size.height * visibleRatio).coerceIn(36.dp.toPx(), size.height * 0.7f)
            val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
            val scrollProgress = (firstVisibleElementIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
            val scrollBarOffsetY = scrollProgress * (size.height - scrollBarHeight)

            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }

fun Modifier.translucentScrollBarStaggeredGrid(
    staggeredGridState: LazyStaggeredGridState,
    color: Color = Color(0xAAFFFFFF),
    width: Dp = 6.dp
): Modifier = this
    .scrollBarDrag(
        scrollBarWidthPx = { width.value * 3 },
        touchZoneWidthPx = { 56f },
        totalItems = { staggeredGridState.layoutInfo.totalItemsCount },
        onScroll = { fraction ->
            val total = staggeredGridState.layoutInfo.totalItemsCount
            val target = (fraction * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
            CoroutineScope(Dispatchers.Main).launch {
                staggeredGridState.scrollToItem(target)
            }
        }
    )
    .drawWithContent {
        drawContent()
        val visibleItems = staggeredGridState.layoutInfo.visibleItemsInfo
        val totalItemsCount = staggeredGridState.layoutInfo.totalItemsCount
        if (totalItemsCount > visibleItems.size && visibleItems.isNotEmpty()) {
            val firstVisibleElementIndex = visibleItems.first().index
            val visibleRatio = visibleItems.size.toFloat() / totalItemsCount
            val scrollBarHeight = (size.height * visibleRatio).coerceIn(36.dp.toPx(), size.height * 0.7f)
            val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
            val scrollProgress = (firstVisibleElementIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
            val scrollBarOffsetY = scrollProgress * (size.height - scrollBarHeight)

            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
