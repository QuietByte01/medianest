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

    // 64MB Memory Cache for instant scroll response
    private val memoryCache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val extractionSemaphore = Semaphore(2)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getDiskCacheDir(context: Context): java.io.File {
        val dir = java.io.File(context.cacheDir, "video_thumb_disk_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getDiskCacheFile(context: Context, cacheKey: String): java.io.File {
        val safeHash = (cacheKey.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val safePrefix = cacheKey.substringAfterLast('/').filter { it.isLetterOrDigit() || it == '_' }.take(30)
        return java.io.File(getDiskCacheDir(context), "${safePrefix}_${safeHash}.webp")
    }

    suspend fun getThumbnail(context: Context, uri: Uri, rebuildToken: Int = 0): Bitmap? {
        val cacheKey = "${uri}_$rebuildToken"

        // 1. Instant Memory Cache Check (0ms)
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Fast Persistent Disk Cache Check (1-2ms)
        val diskFile = getDiskCacheFile(context, cacheKey)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val cached = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (cached != null) {
                    memoryCache.put(cacheKey, cached)
                    return cached
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback extraction via MediaMetadataRetriever (Only executed once per video ever)
        return try {
            extractionSemaphore.withPermit {
                // Double check under permit
                memoryCache.get(cacheKey)?.let { return@withPermit it }
                if (diskFile.exists() && diskFile.length() > 0) {
                    val cached = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (cached != null) {
                        memoryCache.put(cacheKey, cached)
                        return@withPermit cached
                    }
                }

                val bitmap = extractVideoThumbnail(context, uri, rebuildToken)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    // Persist to disk cache
                    try {
                        java.io.FileOutputStream(diskFile).use { out ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                            } else {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        }
                    } catch (_: Exception) {}
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
            retriever.setDataSourceSafe(context, uri)

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
