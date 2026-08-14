package com.medianest.ui.image.hybrid

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Tiled renderer for large images and deep zoom.
 * Uses TiledImageDecoder to fetch and display visible tiles.
 */
class TiledImageRenderer : ImageRenderer {
    private var decoder: TiledImageDecoder? = null
    private val tiles = mutableStateMapOf<String, Bitmap>()
    private var cleanupJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Composable
    override fun Render(
        source: ImageSource,
        viewport: ImageViewport,
        config: HybridImageViewerConfig,
        colorFilter: ColorFilter?,
        modifier: Modifier
    ) {
        val context = LocalContext.current
        
        // Cancel any pending cleanup when Render is active
        LaunchedEffect(Unit) {
            cleanupJob?.cancel()
            cleanupJob = null
        }

        var isInitialized by remember { mutableStateOf(false) }
        val decoderInstance = remember(source) {
            val existing = decoder
            if (existing != null) {
                isInitialized = true
                existing
            } else {
                TiledImageDecoder(context, source, config).also { decoder = it }
            }
        }

        LaunchedEffect(decoderInstance) {
            if (!isInitialized) {
                isInitialized = decoderInstance.initialize()
            }
        }

        if (!isInitialized) return

        Canvas(modifier = modifier.fillMaxSize()) {
            val contentSize = viewport.contentSize
            if (contentSize.width <= 0f || contentSize.height <= 0f) return@Canvas

            val fitScale = viewport.fitScale
            val totalScale = viewport.scale * fitScale
            
            val rawSampleSize = 1f / totalScale
            val sampleSize = floor(2.0.pow(floor(kotlin.math.log2(rawSampleSize.toDouble().coerceAtLeast(1.0))))).toInt().coerceAtLeast(1)

            val tileSize = config.tileSizePx * sampleSize
            
            val viewWidth = size.width / (viewport.scale * fitScale)
            val viewHeight = size.height / (viewport.scale * fitScale)
            
            val viewCenterX = contentSize.width / 2f - viewport.offsetX / (viewport.scale * fitScale)
            val viewCenterY = contentSize.height / 2f - viewport.offsetY / (viewport.scale * fitScale)
            
            val viewLeft = (viewCenterX - viewWidth / 2f).coerceIn(0f, contentSize.width)
            val viewTop = (viewCenterY - viewHeight / 2f).coerceIn(0f, contentSize.height)
            val viewRight = (viewCenterX + viewWidth / 2f).coerceIn(0f, contentSize.width)
            val viewBottom = (viewCenterY + viewHeight / 2f).coerceIn(0f, contentSize.height)

            val startCol = floor(viewLeft / tileSize).toInt()
            val endCol = ceil(viewRight / tileSize).toInt()
            val startRow = floor(viewTop / tileSize).toInt()
            val endRow = ceil(viewBottom / tileSize).toInt()

            val visibleKeys = mutableSetOf<String>()

            drawIntoCanvas { canvas ->
                for (row in startRow until endRow) {
                    for (col in startCol until endCol) {
                        val left = col * tileSize
                        val top = row * tileSize
                        val right = minOf(left + tileSize, contentSize.width.toInt())
                        val bottom = minOf(top + tileSize, contentSize.height.toInt())
                        
                        val region = Rect(left, top, right, bottom)
                        val tileKey = "${left}_${top}_${right}_${bottom}_$sampleSize"
                        visibleKeys.add(tileKey)
                        
                        val bitmap = tiles[tileKey]
                        if (bitmap == null) {
                            decoderInstance.decodeTile(region, sampleSize) { decoded ->
                                if (decoded != null) tiles[tileKey] = decoded
                            }
                        } else if (!bitmap.isRecycled) {
                            val screenLeft = (left - viewCenterX + viewWidth / 2f) * (viewport.scale * fitScale)
                            val screenTop = (top - viewCenterY + viewHeight / 2f) * (viewport.scale * fitScale)
                            val screenWidth = (right - left) * (viewport.scale * fitScale)
                            val screenHeight = (bottom - top) * (viewport.scale * fitScale)
                            
                            canvas.nativeCanvas.drawBitmap(
                                bitmap,
                                null,
                                android.graphics.RectF(screenLeft, screenTop, screenLeft + screenWidth, screenTop + screenHeight),
                                android.graphics.Paint().apply {
                                    this.isAntiAlias = true
                                    this.isFilterBitmap = true
                                }
                            )
                        }
                    }
                }
            }
            
            // Cancel obsolete tile requests that are no longer in the visible set
            decoderInstance.cancelObsoleteTiles(visibleKeys)
        }

        // Handle delayed cleanup when this renderer is no longer rendered (e.g. zoomed out)
        DisposableEffect(source) {
            onDispose {
                cleanupJob = scope.launch {
                    delay(config.tiledCleanupDelayMs)
                    release()
                }
            }
        }
    }

    override fun release() {
        cleanupJob?.cancel()
        cleanupJob = null
        decoder?.release()
        decoder = null
        tiles.values.forEach { if (!it.isRecycled) it.recycle() }
        tiles.clear()
    }
}
