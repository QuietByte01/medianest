package com.medianest.ui.image.hybrid

import android.content.Context
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Metadata extracted from image source headers without full bitmap decode.
 */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val isSvg: Boolean,
    val isGiantImage: Boolean,
    val estimatedMemoryBytes: Long,
    val aspectRatio: Float
) {
    companion object {
        fun extract(
            context: Context,
            source: ImageSource,
            config: HybridImageViewerConfig = HybridImageViewerConfig()
        ): ImageMetadata {
            var width = 0
            var height = 0
            var mimeType = ""
            var isSvg = false

            // Check key or source for SVG extension/mime
            val key = source.key.lowercase()
            if (key.endsWith(".svg") || key.contains("svg")) {
                isSvg = true
                mimeType = "image/svg+xml"
            }

            if (!isSvg) {
                var stream: InputStream? = null
                try {
                    stream = source.openInputStream(context)
                    if (stream != null) {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(stream, null, options)
                        width = options.outWidth
                        height = options.outHeight
                        mimeType = options.outMimeType ?: ""
                        if (mimeType.lowercase().contains("svg")) {
                            isSvg = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        stream?.close()
                    } catch (_: Exception) {}
                }
            }

            val estimatedMemoryBytes = width.toLong() * height.toLong() * 4L // ARGB_8888 assumption
            val pixelCount = width.toLong() * height.toLong()
            val aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1.0f

            val extremeRatio = if (aspectRatio >= 1.0f) aspectRatio else 1.0f / aspectRatio
            val isExtremeRatio = extremeRatio >= config.extremeAspectRatio

            // Classify as Giant Image if dimensions, pixel count, memory exceed standard limits
            // Note: Extreme aspect ratio is combined with dimension and memory rules per prompt specification.
            val isGiant = when {
                isSvg -> false
                width > config.maxStandardDimension || height > config.maxStandardDimension -> true
                pixelCount > config.maxStandardPixelCount -> true
                estimatedMemoryBytes > config.maxStandardMemoryBytes -> true
                isExtremeRatio && (width > 2048 || height > 2048) -> true
                else -> false
            }

            return ImageMetadata(
                width = width,
                height = height,
                mimeType = mimeType,
                isSvg = isSvg,
                isGiantImage = isGiant,
                estimatedMemoryBytes = estimatedMemoryBytes,
                aspectRatio = aspectRatio
            )
        }
    }
}
