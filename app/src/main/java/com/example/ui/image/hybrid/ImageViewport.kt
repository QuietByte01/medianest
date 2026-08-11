package com.example.ui.image.hybrid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Represents the unified viewport state across standard, tiled, and vector renderers.
 */
data class ImageViewport(
    val scale: Float = 1.0f,
    val offsetX: Float = 0.0f,
    val offsetY: Float = 0.0f,
    val rotation: Float = 0.0f,
    val containerSize: Size = Size.Zero,
    val contentSize: Size = Size.Zero
) {
    /**
     * Calculates the fitting scale factor when content is scaled to fit inside the container (ContentScale.Fit).
     */
    val fitScale: Float
        get() {
            if (containerSize.width <= 0f || containerSize.height <= 0f || contentSize.width <= 0f || contentSize.height <= 0f) {
                return 1.0f
            }
            return minOf(containerSize.width / contentSize.width, containerSize.height / contentSize.height)
        }

    /**
     * Intrinsic displayed size of content when fit inside container before zoom.
     */
    val baseDisplayedSize: Size
        get() {
            val f = fitScale
            return Size(contentSize.width * f, contentSize.height * f)
        }

    /**
     * Currently rendered bounds of the image inside the container space.
     */
    val displayedRect: Rect
        get() {
            val base = baseDisplayedSize
            val scaledW = base.width * scale
            val scaledH = base.height * scale
            val left = (containerSize.width - scaledW) / 2f + offsetX
            val top = (containerSize.height - scaledH) / 2f + offsetY
            return Rect(left, top, left + scaledW, top + scaledH)
        }

    /**
     * Calculates maximum pan offset bounds for horizontal and vertical panning.
     */
    val maxOffsetX: Float
        get() {
            val baseW = baseDisplayedSize.width
            return maxOf(0f, (baseW * scale - containerSize.width) / 2f)
        }

    val maxOffsetY: Float
        get() {
            val baseH = baseDisplayedSize.height
            return maxOf(0f, (baseH * scale - containerSize.height) / 2f)
        }

    /**
     * Clamps the given offset to stay within image bounds.
     */
    fun clampOffset(x: Float, y: Float): Offset {
        val maxX = maxOffsetX
        val maxY = maxOffsetY
        return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
    }
}
