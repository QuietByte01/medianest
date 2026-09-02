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

import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * High-performance Region Decoder Engine (Google Photos Architecture).
 * Uses Android native BitmapRegionDecoder with Display P3 / Ultra HDR support,
 * EXIF orientation correction, and neutral filtering without artificial edge halos.
 */
class RegionDecoderEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var decoder: BitmapRegionDecoder? = null
    private val decodeMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()
    private var inputStream: InputStream? = null
    var rotationDegrees: Int = 0
        private set
    private var rawDimensions: Pair<Int, Int>? = null

    suspend fun initialize(uri: Uri) {
        decodeMutex.withLock {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
            
            try {
                // 1. Read EXIF orientation
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        rotationDegrees = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            else -> 0
                        }
                    }
                } catch (e: Exception) {
                    rotationDegrees = 0
                }

                // 2. Decode raw dimensions using inJustDecodeBounds (zero pixel memory allocation)
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, boundsOptions)
                        if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                            rawDimensions = Pair(boundsOptions.outWidth, boundsOptions.outHeight)
                        }
                    }
                } catch (ignored: Throwable) {}

                // 3. Initialize BitmapRegionDecoder for deep tile zooming
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
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    val imageDimensions: Pair<Int, Int>?
        get() {
            val (w, h) = decoder?.let { Pair(it.width, it.height) } ?: rawDimensions ?: return null
            return if (rotationDegrees == 90 || rotationDegrees == 270) {
                Pair(h, w)
            } else {
                Pair(w, h)
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
            
            // Map visually oriented tile bounds to raw unrotated decoder coordinates
            val rawRect = when (rotationDegrees) {
                90 -> {
                    // Visual (x, y) with dimensions (visualW, visualH) where visualW = rawH, visualH = rawW
                    // Raw rect: left = visualTop, top = rawH - visualRight, right = visualBottom, bottom = rawH - visualLeft
                    android.graphics.Rect(
                        tile.bounds.top.toInt().coerceIn(0, currentDecoder.width),
                        (currentDecoder.height - tile.bounds.right).toInt().coerceIn(0, currentDecoder.height),
                        tile.bounds.bottom.toInt().coerceIn(0, currentDecoder.width),
                        (currentDecoder.height - tile.bounds.left).toInt().coerceIn(0, currentDecoder.height)
                    )
                }
                180 -> {
                    android.graphics.Rect(
                        (currentDecoder.width - tile.bounds.right).toInt().coerceIn(0, currentDecoder.width),
                        (currentDecoder.height - tile.bounds.bottom).toInt().coerceIn(0, currentDecoder.height),
                        (currentDecoder.width - tile.bounds.left).toInt().coerceIn(0, currentDecoder.width),
                        (currentDecoder.height - tile.bounds.top).toInt().coerceIn(0, currentDecoder.height)
                    )
                }
                270 -> {
                    android.graphics.Rect(
                        (currentDecoder.width - tile.bounds.bottom).toInt().coerceIn(0, currentDecoder.width),
                        tile.bounds.left.toInt().coerceIn(0, currentDecoder.height),
                        (currentDecoder.width - tile.bounds.top).toInt().coerceIn(0, currentDecoder.width),
                        tile.bounds.right.toInt().coerceIn(0, currentDecoder.height)
                    )
                }
                else -> {
                    android.graphics.Rect(
                        tile.bounds.left.toInt().coerceIn(0, currentDecoder.width),
                        tile.bounds.top.toInt().coerceIn(0, currentDecoder.height),
                        tile.bounds.right.toInt().coerceIn(0, currentDecoder.width),
                        tile.bounds.bottom.toInt().coerceIn(0, currentDecoder.height)
                    )
                }
            }
            
            if (rawRect.width() <= 0 || rawRect.height() <= 0) return@launch

            val options = BitmapFactory.Options().apply {
                inSampleSize = tile.sampleSize
                inMutable = false
                
                // Preserve Display P3 & Ultra HDR tone curves
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
                }
                
                // Use HARDWARE config for zero JVM heap memory allocation on modern Android
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredConfig = Bitmap.Config.HARDWARE
                } else {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            }

            val decodedRaw = try {
                decodeMutex.withLock {
                    currentDecoder.decodeRegion(rawRect, options)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            val finalBitmap = if (decodedRaw != null && rotationDegrees != 0) {
                try {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(
                        decodedRaw, 0, 0,
                        decodedRaw.width, decodedRaw.height,
                        matrix, true
                    )
                    if (rotated != decodedRaw) {
                        decodedRaw.recycle()
                    }
                    rotated
                } catch (e: Throwable) {
                    decodedRaw
                }
            } else {
                decodedRaw
            }

            if (finalBitmap != null) {
                launch(Dispatchers.Main) {
                    onDecoded(finalBitmap)
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
