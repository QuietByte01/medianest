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
            // Check key or source for SVG extension/mime or query content resolver
            var isSvg = false
            var mimeType = ""
            var width = 0
            var height = 0

            if (source is ImageSource.FromUri) {
                try {
                    val crMime = context.contentResolver.getType(source.uri)
                    if (crMime != null && (crMime.contains("svg", ignoreCase = true) || crMime == "image/svg+xml")) {
                        isSvg = true
                        mimeType = "image/svg+xml"
                    }
                } catch (_: Exception) {}
            }

            val key = source.key.lowercase()
            if (!isSvg && (key.endsWith(".svg") || key.contains(".svg?") || key.contains("image/svg") || key.contains("svg"))) {
                isSvg = true
                mimeType = "image/svg+xml"
            }

            var stream: InputStream? = null
            try {
                stream = source.openInputStream(context)
                if (stream != null) {
                    if (!isSvg) {
                        // Inspect first 512 bytes for SVG tags (<?xml or <svg)
                        val buffer = ByteArray(512)
                        stream.mark(512)
                        val bytesRead = stream.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            val header = String(buffer, 0, bytesRead, Charsets.UTF_8).trimStart()
                            if (header.contains("<svg", ignoreCase = true) || 
                                (header.startsWith("<?xml", ignoreCase = true) && header.contains("<svg", ignoreCase = true))) {
                                isSvg = true
                                mimeType = "image/svg+xml"
                            }
                        }
                    }

                    if (!isSvg) {
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    stream?.close()
                } catch (_: Exception) {}
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
