package com.medianest.ui.image.hybrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import android.os.Build

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
            inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    decoder = BitmapRegionDecoder.newInstance(it)
                } else {
                    @Suppress("DEPRECATION")
                    decoder = BitmapRegionDecoder.newInstance(it, false)
                }
            }
        }
    }

    fun decodeTileAsync(
        tile: Tile,
        onDecoded: (Bitmap) -> Unit
    ) {
        val tileId = "${tile.sampleSize}_${tile.x}_${tile.y}"
        if (jobs.containsKey(tileId)) return // Already decoding

        val job = scope.launch(Dispatchers.IO) {
            val currentDecoder = decoder ?: return@launch
            val rect = android.graphics.Rect(
                tile.bounds.left.toInt(),
                tile.bounds.top.toInt(),
                tile.bounds.right.toInt(),
                tile.bounds.bottom.toInt()
            )
            val options = BitmapFactory.Options().apply {
                inSampleSize = tile.sampleSize
                // inPreferredConfig = Bitmap.Config.ARGB_8888 // Must be software bitmap for GLUtils.texImage2D upload
                inPreferredConfig = Bitmap.Config.HARDWARE // Phase 6 optimization setup
            }

            decodeMutex.withLock {
                val bitmap = currentDecoder.decodeRegion(rect, options)
                if (bitmap != null) {
                    launch(Dispatchers.Main) {
                        onDecoded(bitmap)
                    }
                }
            }
            jobs.remove(tileId)
        }
        jobs[tileId] = job
    }

    fun recycle() {
        scope.launch(Dispatchers.IO) {
            decodeMutex.withLock {
                decoder?.recycle()
                decoder = null
                try {
                    inputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                inputStream = null
            }
        }
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
