package com.medianest.ui.image.hybrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream

/**
 * High-performance Region Decoder Engine (Google Photos Architecture).
 * Uses Android native BitmapRegionDecoder with Display P3 / Ultra HDR support
 * and neutral filtering without artificial edge halos.
 */
class RegionDecoderEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var decoder: BitmapRegionDecoder? = null
    private val decodeMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()
    private var inputStream: InputStream? = null

    suspend fun initialize(uri: Uri) {
        decodeMutex.withLock {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
            
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                inputStream?.let {
                    decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(it)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(it, false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val imageDimensions: Pair<Int, Int>?
        get() = decoder?.let { Pair(it.width, it.height) }

    fun decodeTileAsync(
        tile: Tile,
        onDecoded: (Bitmap) -> Unit
    ) {
        val tileId = "${tile.sampleSize}_${tile.x}_${tile.y}"
        if (jobs.containsKey(tileId)) return // Already decoding

        val job = scope.launch(Dispatchers.IO) {
            val currentDecoder = decoder ?: return@launch
            val rect = android.graphics.Rect(
                tile.bounds.left.toInt().coerceIn(0, currentDecoder.width),
                tile.bounds.top.toInt().coerceIn(0, currentDecoder.height),
                tile.bounds.right.toInt().coerceIn(0, currentDecoder.width),
                tile.bounds.bottom.toInt().coerceIn(0, currentDecoder.height)
            )
            
            if (rect.width() <= 0 || rect.height() <= 0) return@launch

            val options = BitmapFactory.Options().apply {
                inSampleSize = tile.sampleSize
                inMutable = false
                
                // Preserve Display P3 & Ultra HDR tone curves
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    inPreferredConfig = Bitmap.Config.RGBA_1010102
                } else {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            }

            val bitmap = try {
                decodeMutex.withLock {
                    currentDecoder.decodeRegion(rect, options)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (bitmap != null) {
                launch(Dispatchers.Main) {
                    onDecoded(bitmap)
                }
            }
            jobs.remove(tileId)
        }
        jobs[tileId] = job
    }

    fun recycle() {
        scope.launch(Dispatchers.IO) {
            decodeMutex.withLock {
                try {
                    decoder?.recycle()
                } catch (ignored: Exception) {}
                decoder = null
                try {
                    inputStream?.close()
                } catch (ignored: Exception) {}
                inputStream = null
            }
        }
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
