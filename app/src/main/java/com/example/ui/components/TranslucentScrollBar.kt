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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.translucentScrollBar(
    listState: LazyListState,
    color: Color = Color(0xAAFFFFFF),
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
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
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
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
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
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
