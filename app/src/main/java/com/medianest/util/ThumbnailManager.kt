package com.medianest.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Global Video Thumbnail Manager with LruCache and sequential processing.
 * Prevents OOM and system instability by limiting concurrent MediaMetadataRetriever instances.
 */
object ThumbnailManager {
    private const val TAG = "ThumbnailManager"

    // Max 20MB for thumbnail cache (enough for ~20-30 hardware-sized bitmaps)
    private val memoryCache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Strictly limit concurrency to prevent Samsung MediaService exhaustion
    private val extractionSemaphore = Semaphore(1)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun getThumbnail(context: Context, uri: Uri, rebuildToken: Int = 0): Bitmap? {
        val cacheKey = "${uri}_$rebuildToken"
        memoryCache.get(cacheKey)?.let { return it }

        return try {
            extractionSemaphore.withPermit {
                // Double check cache under lock
                memoryCache.get(cacheKey)?.let { return@withPermit it }

                val bitmap = extractVideoThumbnail(context, uri, rebuildToken)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                }
                bitmap
            }
        } catch (e: CancellationException) {
            null
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to load thumbnail for $uri", e)
            null
        }
    }

    private fun extractVideoThumbnail(
        context: Context,
        uri: Uri,
        attemptOffset: Int = 0
    ): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val baseOffsets = listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.25f, 0.05f)
            val shiftedOffsets = if (attemptOffset > 0) {
                val shift = attemptOffset % baseOffsets.size
                baseOffsets.drop(shift) + baseOffsets.take(shift)
            } else {
                baseOffsets
            }

            val candidates = shiftedOffsets.map { factor ->
                if (durationMs > 1_000) (durationMs * 1000L * factor).toLong() else 0L
            }.distinct()

            var result: Bitmap? = null
            for (seekMicros in candidates) {
                val frame = retriever.getFrameAtTime(seekMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null && !isFrameBlack(frame)) {
                    result = frame
                    break
                }
                if (frame != null && result == null) result = frame
            }
            result
        } catch (e: Exception) {
            Logger.e(TAG, "Error in retriever for $uri: ${e.message}")
            null
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    private fun isFrameBlack(bitmap: Bitmap): Boolean {
        return try {
            val readableBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
            } else {
                bitmap
            }
            if (readableBitmap.width < 4 || readableBitmap.height < 4) return true
            val step = (readableBitmap.width / 5).coerceAtLeast(1)
            val stepY = (readableBitmap.height / 5).coerceAtLeast(1)
            var darkCount = 0
            var total = 0
            var x = 0
            while (x < readableBitmap.width) {
                var y = 0
                while (y < readableBitmap.height) {
                    val pixel = readableBitmap.getPixel(x, y)
                    val luma = (0.299 * android.graphics.Color.red(pixel) +
                                0.587 * android.graphics.Color.green(pixel) +
                                0.114 * android.graphics.Color.blue(pixel))
                    if (luma < 15.0) darkCount++
                    total++
                    y += stepY
                }
                x += step
            }
            total > 0 && (darkCount.toFloat() / total) > 0.88f
        } catch (_: Exception) { false }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
