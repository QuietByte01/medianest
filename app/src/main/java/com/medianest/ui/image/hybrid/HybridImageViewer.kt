package com.medianest.ui.image.hybrid

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Google Photos / Pixel standard Image Viewer:
 * - Flicker-free high-resolution tiling pipeline with seamless crossfade & cache retention.
 * - Single-tap controls toggle without immediate-hide collisions.
 * - Dynamic BitmapRegionDecoder + 2-Tier LRU Tile Cache for deep zoom > 1.0x up to 25x.
 * - Full Ultra HDR / Display P3 color accuracy.
 * - 120Hz smooth multi-touch pinch, double-tap, and physical drag-to-dismiss.
 * - Floating auto-hiding zoom percentage pill (e.g., "150%").
 */
@Composable
fun HybridImageViewer(
    source: ImageSource,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    backgroundColor: Color = Color.Black,
    zoomControlsBottomPadding: Dp = 80.dp,
    onDismiss: () -> Unit = {},
    onInteraction: () -> Unit = {},
    onToggleControls: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
    onDismissProgress: (Float) -> Unit = {},
    onSwipeUpForInfo: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewportState = remember { ViewportState(scope) }

    // Configure 10-bit HDR / Wide Color Gamut on Window
    SideEffect {
        (context as? Activity)?.let { activity ->
            UltraHdrManager.configureWindowColorMode(activity)
        }
    }

    val isZoomed = viewportState.isZoomed
    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    LaunchedEffect(viewportState.dismissFraction) {
        onDismissProgress(viewportState.dismissFraction)
    }

    // Decoder & Tile Cache engine for zoom levels > 1.0x
    val decoderEngine = remember { RegionDecoderEngine(context, scope) }
    val tileCache = remember { TileCache(context) }
    val tileManager = remember { TileManager(androidx.compose.ui.geometry.Size(2000f, 2000f)) }
    val activeTileBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    val uri = remember(source) {
        when (source) {
            is ImageSource.FromUri -> source.uri
            is ImageSource.FromFile -> android.net.Uri.fromFile(source.file)
            else -> null
        }
    }

    LaunchedEffect(uri) {
        uri?.let { decoderEngine.initialize(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            decoderEngine.recycle()
            activeTileBitmaps.clear()
        }
    }

    // Floating Zoom Percentage Pill
    var showZoomPill by remember { mutableStateOf(false) }
    var zoomPillText by remember { mutableStateOf("100%") }

    LaunchedEffect(viewportState.scale) {
        val pct = (viewportState.scale * 100).toInt()
        zoomPillText = "$pct%"
        if (viewportState.scale > 1.05f) {
            showZoomPill = true
            delay(800)
            showZoomPill = false
        } else {
            showZoomPill = false
        }
    }

    // Scrim Alpha decays smoothly during pull-to-dismiss
    val scrimAlpha = (1f - viewportState.dismissFraction).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor.copy(alpha = scrimAlpha))
            .onSizeChanged { viewportState.viewportSize = it.toSize() }
            .pointerInput(Unit) {
                detectGooglePhotosGestures(
                    isZoomed = { viewportState.isZoomed },
                    onGestureStart = { /* Don't reset hide timer on down */ },
                    onGesture = { centroid, pan, zoom ->
                        onInteraction()
                        viewportState.onGesture(centroid, pan, zoom)
                    },
                    onGestureEnd = { velocity ->
                        viewportState.onGestureEnd(velocity)
                    },
                    onDragDismiss = { dragAmount ->
                        onInteraction()
                        viewportState.onDragDismiss(dragAmount)
                    },
                    onDragDismissEnd = { velocity ->
                        viewportState.onDragDismissEnd(velocity, onDismiss = onDismiss)
                    },
                    onSwipeUp = {
                        onInteraction()
                        onSwipeUpForInfo()
                    },
                    onTap = {
                        onToggleControls()
                    },
                    onDoubleTap = { centroid ->
                        onInteraction()
                        viewportState.toggleZoom(centroid)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        var intrinsicImageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

        LaunchedEffect(intrinsicImageSize, viewportState.viewportSize) {
            if (intrinsicImageSize.width > 0 && intrinsicImageSize.height > 0 && viewportState.viewportSize.width > 0) {
                val scaleX = viewportState.viewportSize.width / intrinsicImageSize.width
                val scaleY = viewportState.viewportSize.height / intrinsicImageSize.height
                val fitScale = minOf(scaleX, scaleY)
                viewportState.contentSize = androidx.compose.ui.geometry.Size(
                    intrinsicImageSize.width * fitScale,
                    intrinsicImageSize.height * fitScale
                )
                tileManager.imageSize = intrinsicImageSize
            }
        }

        // Active Tile Scheduler for Deep Zoom > 1.8x
        LaunchedEffect(viewportState.scale, viewportState.offset, intrinsicImageSize, viewportState.viewportSize) {
            if (viewportState.scale > 1.8f && intrinsicImageSize.width > 0 && viewportState.viewportSize.width > 0) {
                val fitScaleX = viewportState.contentSize.width / tileManager.imageSize.width
                val fitScaleY = viewportState.contentSize.height / tileManager.imageSize.height
                val fitScale = minOf(fitScaleX, fitScaleY).takeIf { !it.isNaN() && it > 0f } ?: 1f

                val imageLeft = (viewportState.viewportSize.width - viewportState.contentSize.width) / 2f
                val imageTop = (viewportState.viewportSize.height - viewportState.contentSize.height) / 2f
                val originX = viewportState.viewportSize.width / 2f
                val originY = viewportState.viewportSize.height / 2f

                val left1x = (0f - viewportState.offset.x - originX) / viewportState.scale + originX
                val top1x = (0f - viewportState.offset.y - originY) / viewportState.scale + originY
                val right1x = (viewportState.viewportSize.width - viewportState.offset.x - originX) / viewportState.scale + originX
                val bottom1x = (viewportState.viewportSize.height - viewportState.offset.y - originY) / viewportState.scale + originY

                val intrinsicViewportBounds = Rect(
                    (left1x - imageLeft) / fitScale,
                    (top1x - imageTop) / fitScale,
                    (right1x - imageLeft) / fitScale,
                    (bottom1x - imageTop) / fitScale
                )

                val tiles = tileManager.calculateVisibleTiles(intrinsicViewportBounds, viewportState.scale * fitScale)
                for (tile in tiles) {
                    val tileId = "${tile.sampleSize}_${tile.x}_${tile.y}"
                    if (!activeTileBitmaps.containsKey(tileId)) {
                        val cached = tileCache.getL2(tileId)
                        if (cached != null && !cached.isRecycled) {
                            activeTileBitmaps[tileId] = cached
                        } else {
                            decoderEngine.decodeTileAsync(tile) { bitmap ->
                                scope.launch {
                                    tileCache.putL2(tileId, bitmap)
                                }
                                activeTileBitmaps[tileId] = bitmap
                            }
                        }
                    }
                }
            } else if (viewportState.scale <= 1.2f && activeTileBitmaps.isNotEmpty()) {
                delay(250)
                if (viewportState.scale <= 1.2f) {
                    activeTileBitmaps.clear()
                }
            }
        }

        val request = remember(source, context) {
            ImageRequest.Builder(context)
                .data(uri ?: source.key)
                .size(coil.size.Size.ORIGINAL)
                .precision(Precision.EXACT)
                .crossfade(true)
                .build()
        }

        // Layer 1: Base original full-resolution RenderThread image
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                intrinsicImageSize = androidx.compose.ui.geometry.Size(size.width, size.height)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (viewportState.scale.isNaN() || viewportState.scale <= 0f) 1f else viewportState.scale
                    scaleX = s
                    scaleY = s
                    translationX = if (viewportState.offset.x.isNaN()) 0f else viewportState.offset.x
                    translationY = if (viewportState.offset.y.isNaN()) 0f else viewportState.offset.y
                }
        )

        // Layer 2: Native BitmapRegionDecoder Ultra High-Res Tiles for Deep Zoom > 1.8x
        if (viewportState.scale > 1.8f && activeTileBitmaps.isNotEmpty() && intrinsicImageSize.width > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = if (viewportState.scale.isNaN() || viewportState.scale <= 0f) 1f else viewportState.scale
                        scaleX = s
                        scaleY = s
                        translationX = if (viewportState.offset.x.isNaN()) 0f else viewportState.offset.x
                        translationY = if (viewportState.offset.y.isNaN()) 0f else viewportState.offset.y
                    }
            ) {
                val fitScaleX = viewportState.contentSize.width / intrinsicImageSize.width
                val fitScaleY = viewportState.contentSize.height / intrinsicImageSize.height
                val fitScale = minOf(fitScaleX, fitScaleY).takeIf { !it.isNaN() && it > 0f } ?: 1f

                val imageLeft = (size.width - viewportState.contentSize.width) / 2f
                val imageTop = (size.height - viewportState.contentSize.height) / 2f

                for ((tileId, bitmap) in activeTileBitmaps) {
                    if (bitmap.isRecycled) continue
                    val parts = tileId.split("_")
                    if (parts.size != 3) continue
                    val sampleSize = parts[0].toIntOrNull() ?: 1
                    val tileX = parts[1].toIntOrNull() ?: 0
                    val tileY = parts[2].toIntOrNull() ?: 0

                    val effectiveTileSize = tileManager.tileSize * sampleSize
                    val leftIntrinsic = tileX * effectiveTileSize.toFloat()
                    val topIntrinsic = tileY * effectiveTileSize.toFloat()

                    val left1x = imageLeft + leftIntrinsic * fitScale
                    val top1x = imageTop + topIntrinsic * fitScale
                    val dstW = bitmap.width.toFloat() * sampleSize * fitScale
                    val dstH = bitmap.height.toFloat() * sampleSize * fitScale

                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(left1x.toInt(), top1x.toInt()),
                        dstSize = IntSize(dstW.toInt(), dstH.toInt())
                    )
                }
            }
        }

        // Google Photos style floating zoom badge
        AnimatedVisibility(
            visible = showZoomPill && !viewportState.isDismissing,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = zoomControlsBottomPadding)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.70f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = zoomPillText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
