package com.example.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glossyLazyListScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.White.copy(alpha = 0.22f)
): Modifier = drawWithContent {
    drawContent()
    val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
    val totalItemsCount = state.layoutInfo.totalItemsCount
    if (firstVisibleElementIndex != null && totalItemsCount > 0) {
        val elementHeight = size.height / totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = (state.layoutInfo.visibleItemsInfo.size * elementHeight).coerceAtLeast(24.dp.toPx())

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollbarOffsetY),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2f)
        )
    }
}

fun Modifier.glossyLazyGridScrollbar(
    state: LazyGridState,
    width: Dp = 4.dp,
    color: Color = Color.White.copy(alpha = 0.22f)
): Modifier = drawWithContent {
    drawContent()
    val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
    val totalItemsCount = state.layoutInfo.totalItemsCount
    if (firstVisibleElementIndex != null && totalItemsCount > 0) {
        val elementHeight = size.height / totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = (state.layoutInfo.visibleItemsInfo.size * elementHeight).coerceAtLeast(24.dp.toPx())

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollbarOffsetY),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2f)
        )
    }
}
