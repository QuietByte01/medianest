package com.example.ui.image.hybrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages region-based decoding using BitmapRegionDecoder.
 * Handles tile caching, async decoding, and downsampling (inSampleSize) based on zoom.
 */
class TiledImageDecoder(
    private val context: Context,
    private val source: ImageSource,
    private val config: HybridImageViewerConfig = HybridImageViewerConfig()
) {
    private var regionDecoder: BitmapRegionDecoder? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tileCache = ConcurrentHashMap<String, Bitmap>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        var stream: InputStream? = null
        try {
            stream = source.openInputStream(context)
            if (stream != null) {
                regionDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(stream)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(stream, false)
                }
                
                regionDecoder?.let {
                    imageWidth = it.width
                    imageHeight = it.height
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stream?.close()
        }
        false
    }

    /**
     * Decodes a specific tile region.
     * @param region Rect in original image coordinates.
     * @param sampleSize Downsampling factor (1, 2, 4, 8, etc.)
     */
    fun decodeTile(region: Rect, sampleSize: Int, onResult: (Bitmap?) -> Unit) {
        val tileKey = "${region.left}_${region.top}_${region.right}_${region.bottom}_$sampleSize"
        
        // Return from cache if available
        tileCache[tileKey]?.let {
            if (!it.isRecycled) {
                onResult(it)
                return
            } else {
                tileCache.remove(tileKey)
            }
        }

        // Cancel existing job for same tile if any
        activeJobs[tileKey]?.cancel()

        val job = scope.launch {
            try {
                val decoder = regionDecoder ?: return@launch
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                
                // Perform decode in background
                val bitmap = decoder.decodeRegion(region, options)
                
                if (bitmap != null && isActive) {
                    tileCache[tileKey] = bitmap
                    withContext(Dispatchers.Main) {
                        onResult(bitmap)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(null) }
            } finally {
                activeJobs.remove(tileKey)
            }
        }
        activeJobs[tileKey] = job
    }

    /**
     * Cancels all pending decode jobs for tiles NOT in the visible set.
     */
    fun cancelObsoleteTiles(visibleKeys: Set<String>) {
        val toCancel = activeJobs.keys - visibleKeys
        toCancel.forEach { key ->
            activeJobs[key]?.cancel()
            activeJobs.remove(key)
        }
    }

    fun release() {
        scope.cancel()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        regionDecoder?.recycle()
        regionDecoder = null
        tileCache.values.forEach { it.recycle() }
        tileCache.clear()
    }
}
