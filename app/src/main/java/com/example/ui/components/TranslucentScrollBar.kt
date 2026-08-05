package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.translucentScrollBar(
    listState: LazyListState,
    color: Color = Color(0x66FFFFFF),
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val firstVisibleElementIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val elementCount = listState.layoutInfo.totalItemsCount
    if (elementCount > 0) {
        val elementHeight = size.height / elementCount
        val scrollBarHeight = (size.height * (listState.layoutInfo.visibleItemsInfo.size.toFloat() / elementCount)).coerceAtLeast(30f)
        val scrollBarOffsetY = firstVisibleElementIndex * elementHeight

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollBarOffsetY),
            size = Size(width.toPx(), scrollBarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}

fun Modifier.translucentScrollBarGrid(
    gridState: LazyGridState,
    color: Color = Color(0x66FFFFFF),
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val firstVisibleElementIndex = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val elementCount = gridState.layoutInfo.totalItemsCount
    if (elementCount > 0) {
        val elementHeight = size.height / elementCount
        val scrollBarHeight = (size.height * (gridState.layoutInfo.visibleItemsInfo.size.toFloat() / elementCount)).coerceAtLeast(30f)
        val scrollBarOffsetY = firstVisibleElementIndex * elementHeight

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollBarOffsetY),
            size = Size(width.toPx(), scrollBarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}
