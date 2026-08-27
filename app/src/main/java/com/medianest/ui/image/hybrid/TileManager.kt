package com.medianest.ui.image.hybrid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil

data class Tile(
    val x: Int,
    val y: Int,
    val sampleSize: Int, // 1 for full res, 2 for half res, etc.
    val bounds: Rect
)

class TileManager(
    var imageSize: Size,
    val tileSize: Int = 512
) {
    fun calculateVisibleTiles(
        viewportBounds: Rect,
        currentScale: Float,
        panVelocity: Offset = Offset.Zero
    ): List<Tile> {
        // Determine optimal sample size (1, 2, 4, 8)
        val idealScale = 1f / currentScale
        var sampleSize = 1
        while (sampleSize * 2 < idealScale) {
            sampleSize *= 2
        }

        // Project viewport into the future based on velocity (150ms trajectory)
        val projectedViewport = viewportBounds.translate(panVelocity * 0.15f)
        val searchArea = Rect(
            left = minOf(viewportBounds.left, projectedViewport.left),
            top = minOf(viewportBounds.top, projectedViewport.top),
            right = maxOf(viewportBounds.right, projectedViewport.right),
            bottom = maxOf(viewportBounds.bottom, projectedViewport.bottom)
        )

        val visibleTiles = mutableListOf<Tile>()
        
        // Map search area to intrinsic image coordinates
        val intrinsicLeft = searchArea.left.coerceAtLeast(0f)
        val intrinsicTop = searchArea.top.coerceAtLeast(0f)
        val intrinsicRight = searchArea.right.coerceAtMost(imageSize.width)
        val intrinsicBottom = searchArea.bottom.coerceAtMost(imageSize.height)

        val effectiveTileSize = tileSize * sampleSize

        val startX = (intrinsicLeft / effectiveTileSize).toInt()
        val startY = (intrinsicTop / effectiveTileSize).toInt()
        val endX = ceil(intrinsicRight / effectiveTileSize).toInt()
        val endY = ceil(intrinsicBottom / effectiveTileSize).toInt()

        for (y in startY until endY) {
            for (x in startX until endX) {
                val left = x * effectiveTileSize.toFloat()
                val top = y * effectiveTileSize.toFloat()
                val right = (left + effectiveTileSize).coerceAtMost(imageSize.width)
                val bottom = (top + effectiveTileSize).coerceAtMost(imageSize.height)
                
                visibleTiles.add(Tile(x, y, sampleSize, Rect(left, top, right, bottom)))
            }
        }
        
        return visibleTiles
    }
}
