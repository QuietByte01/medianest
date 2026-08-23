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
                    if (!isSvg) {
                        context.contentResolver.query(source.uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val name = cursor.getString(0)?.lowercase() ?: ""
                                if (name.endsWith(".svg") || name.contains("svg")) {
                                    isSvg = true
                                    mimeType = "image/svg+xml"
                                }
                            }
                        }
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
                    // Read up to 4KB to check for SVG tag even if preceded by XML declaration, DOCTYPE, or long comments
                    val buffer = ByteArray(4096)
                    val bytesRead = stream.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val header = String(buffer, 0, bytesRead, Charsets.UTF_8).trimStart()
                        if (header.contains("<svg", ignoreCase = true)) {
                            isSvg = true
                            mimeType = "image/svg+xml"

                            // Try to extract viewBox or width/height from svg header for better initial aspect ratio
                            try {
                                val svgTag = header.substringAfter("<svg", "").substringBefore(">")
                                val viewBoxMatch = Regex("""viewBox\s*=\s*["']\s*[\d.-]+\s+[\d.-]+\s+([\d.-]+)\s+([\d.-]+)\s*["']""", RegexOption.IGNORE_CASE).find(svgTag)
                                if (viewBoxMatch != null) {
                                    width = viewBoxMatch.groupValues[1].toFloatOrNull()?.toInt() ?: 0
                                    height = viewBoxMatch.groupValues[2].toFloatOrNull()?.toInt() ?: 0
                                }
                                if (width <= 0 || height <= 0) {
                                    val wMatch = Regex("""width\s*=\s*["']\s*([\d.]+)(?:px)?\s*["']""", RegexOption.IGNORE_CASE).find(svgTag)
                                    val hMatch = Regex("""height\s*=\s*["']\s*([\d.]+)(?:px)?\s*["']""", RegexOption.IGNORE_CASE).find(svgTag)
                                    if (wMatch != null && hMatch != null) {
                                        width = wMatch.groupValues[1].toFloatOrNull()?.toInt() ?: 0
                                        height = hMatch.groupValues[1].toFloatOrNull()?.toInt() ?: 0
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    if (!isSvg) {
                        // Re-open stream since we read the first 4KB
                        try { stream.close() } catch (_: Exception) {}
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
