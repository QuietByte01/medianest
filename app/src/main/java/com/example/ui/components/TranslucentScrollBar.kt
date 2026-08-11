package com.example.ui.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Helper — detect touch + drag on the scrollbar track and translate to list-scroll commands.
private fun Modifier.scrollBarDrag(
    scrollBarWidthPx: () -> Float,
    touchZoneWidthPx: () -> Float,
    totalItems: () -> Int,
    onScroll: (fraction: Float) -> Unit,
    onInteraction: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val touch = down.changes.firstOrNull() ?: continue
            val xThreshold = size.width - touchZoneWidthPx()
            if (touch.position.x < xThreshold) continue

            onInteraction()
            touch.consume()
            
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                onInteraction()
                change.consume()

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
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    this.scrollBarDrag(
        scrollBarWidthPx = { width.value * 3 },
        touchZoneWidthPx = { 56f },
        totalItems = { listState.layoutInfo.totalItemsCount },
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
    
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    this.scrollBarDrag(
        scrollBarWidthPx = { width.value * 3 },
        touchZoneWidthPx = { 56f },
        totalItems = { gridState.layoutInfo.totalItemsCount },
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
    
    LaunchedEffect(staggeredGridState.isScrollInProgress) {
        if (staggeredGridState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    this.scrollBarDrag(
        scrollBarWidthPx = { width.value * 3 },
        touchZoneWidthPx = { 56f },
        totalItems = { staggeredGridState.layoutInfo.totalItemsCount },
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
                color = color.copy(alpha = color.alpha * alpha.value),
                topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollBarOffsetY),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}
