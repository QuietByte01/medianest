package com.medianest.studio.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.medianest.ui.components.media.MediaAspectRatio
import com.medianest.studio.engine.MediaProcessorEngine
import kotlin.math.roundToInt

@Composable
internal fun CropViewfinderView(
    isCustomCrop: Boolean,
    cropNormX: Float,
    cropNormY: Float,
    cropNormW: Float,
    cropNormH: Float,
    onCropChange: (nx: Float, ny: Float, nw: Float, nh: Float) -> Unit
) {
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }

    val currentCropX by rememberUpdatedState(cropNormX)
    val currentCropY by rememberUpdatedState(cropNormY)
    val currentCropW by rememberUpdatedState(cropNormW)
    val currentCropH by rememberUpdatedState(cropNormH)
    val currentOnCropChange by rememberUpdatedState(onCropChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewWidth = it.width.toFloat().coerceAtLeast(1f)
                viewHeight = it.height.toFloat().coerceAtLeast(1f)
            }
    ) {
        val leftPx = cropNormX * viewWidth
        val topPx = cropNormY * viewHeight
        val widthPx = (cropNormW * viewWidth).coerceAtLeast(60f)
        val heightPx = (cropNormH * viewHeight).coerceAtLeast(60f)
        val rightPx = leftPx + widthPx
        val bottomPx = topPx + heightPx

        Canvas(modifier = Modifier.fillMaxSize()) {
            val maskColor = Color(0xB3000000)
            drawRect(color = maskColor, topLeft = Offset.Zero, size = Size(size.width, topPx))
            drawRect(color = maskColor, topLeft = Offset(0f, bottomPx), size = Size(size.width, size.height - bottomPx))
            drawRect(color = maskColor, topLeft = Offset(0f, topPx), size = Size(leftPx, heightPx))
            drawRect(color = maskColor, topLeft = Offset(rightPx, topPx), size = Size(size.width - rightPx, heightPx))

            drawRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(leftPx, topPx),
                size = Size(widthPx, heightPx),
                style = Stroke(width = 2.dp.toPx())
            )

            val thirdW = widthPx / 3f
            val thirdH = heightPx / 3f
            drawLine(Color.White.copy(0.4f), Offset(leftPx + thirdW, topPx), Offset(leftPx + thirdW, bottomPx), 1.dp.toPx())
            drawLine(Color.White.copy(0.4f), Offset(leftPx + thirdW * 2, topPx), Offset(leftPx + thirdW * 2, bottomPx), 1.dp.toPx())
            drawLine(Color.White.copy(0.4f), Offset(leftPx, topPx + thirdH), Offset(rightPx, topPx + thirdH), 1.dp.toPx())
            drawLine(Color.White.copy(0.4f), Offset(leftPx, topPx + thirdH * 2), Offset(rightPx, topPx + thirdH * 2), 1.dp.toPx())
        }

        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val vw = viewWidth
                        val vh = viewHeight
                        if (vw > 0f && vh > 0f) {
                            val newX = (currentCropX + dragAmount.x / vw).coerceIn(0f, 1f - currentCropW)
                            val newY = (currentCropY + dragAmount.y / vh).coerceIn(0f, 1f - currentCropH)
                            currentOnCropChange(newX, newY, currentCropW, currentCropH)
                        }
                    }
                }
        )

        CornerHandle(
            offsetX = leftPx,
            offsetY = topPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curR = currentCropX + currentCropW
                    val curB = currentCropY + currentCropH
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newL = (currentCropX + dx / vw).coerceIn(0f, curR - minSizeX)
                    val newT = (currentCropY + dy / vh).coerceIn(0f, curB - minSizeY)
                    currentOnCropChange(newL, newT, curR - newL, curB - newT)
                }
            }
        )

        CornerHandle(
            offsetX = rightPx,
            offsetY = topPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curL = currentCropX
                    val curB = currentCropY + currentCropH
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newR = (curL + currentCropW + dx / vw).coerceIn(curL + minSizeX, 1f)
                    val newT = (currentCropY + dy / vh).coerceIn(0f, curB - minSizeY)
                    currentOnCropChange(curL, newT, newR - curL, curB - newT)
                }
            }
        )

        CornerHandle(
            offsetX = leftPx,
            offsetY = bottomPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curR = currentCropX + currentCropW
                    val curT = currentCropY
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newL = (currentCropX + dx / vw).coerceIn(0f, curR - minSizeX)
                    val newB = (curT + currentCropH + dy / vh).coerceIn(curT + minSizeY, 1f)
                    currentOnCropChange(newL, curT, curR - newL, newB - curT)
                }
            }
        )

        CornerHandle(
            offsetX = rightPx,
            offsetY = bottomPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curL = currentCropX
                    val curT = currentCropY
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newR = (curL + currentCropW + dx / vw).coerceIn(curL + minSizeX, 1f)
                    val newB = (curT + currentCropH + dy / vh).coerceIn(curT + minSizeY, 1f)
                    currentOnCropChange(curL, curT, newR - curL, newB - curT)
                }
            }
        )
    }
}

@Composable
internal fun CornerHandle(
    offsetX: Float,
    offsetY: Float,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val handleTouchSizeDp = 44.dp
    val density = LocalDensity.current
    val halfTouchPx = with(density) { (handleTouchSizeDp / 2).toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset((offsetX - halfTouchPx).roundToInt(), (offsetY - halfTouchPx).roundToInt()) }
            .size(handleTouchSizeDp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD54F))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
internal fun StudioFilmstripTrack(
    thumbnails: List<Bitmap>,
    isExtracting: Boolean,
    videoDurationMs: Long,
    currentPositionMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    onScrubPosition: (scrubMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val durationFloat = videoDurationMs.toFloat().coerceAtLeast(1000f)

    val currentStartMs by rememberUpdatedState(startMs)
    val currentEndMs by rememberUpdatedState(endMs)
    val currentPosition by rememberUpdatedState(currentPositionMs)
    val currentVideoDurationMs by rememberUpdatedState(videoDurationMs)
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentOnScrubPosition by rememberUpdatedState(onScrubPosition)

    val startFrac = (startMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val currentFrac = (currentPositionMs.toFloat() / durationFloat).coerceIn(0f, 1f)

    val handleWidthDp = 24.dp
    val handleWidthPx = with(density) { handleWidthDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E222D))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val tw = trackWidthPx
                    if (tw > 0f) {
                        val frac = (offset.x / tw).coerceIn(0f, 1f)
                        val targetMs = (frac * currentVideoDurationMs.toFloat()).toLong()
                        currentOnScrubPosition(targetMs)
                    }
                }
            }
    ) {
        if (thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(8) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(Color.White.copy(alpha = if (isExtracting) 0.08f else 0.04f))
                    )
                }
            }
        }

        if (trackWidthPx > 0f) {
            val usableWidth = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
            val startLeftPx = (startFrac * usableWidth).coerceAtLeast(0f)
            val endRightPx = (handleWidthPx * 2 + endFrac * usableWidth).coerceAtMost(trackWidthPx)
            val selectionWidthPx = (endRightPx - startLeftPx).coerceAtLeast(handleWidthPx * 2)

            if (startLeftPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { startLeftPx.toDp() })
                        .background(Color.Black.copy(alpha = 0.70f))
                )
            }

            if (endRightPx < trackWidthPx) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset(endRightPx.roundToInt(), 0) }
                        .width(with(density) { (trackWidthPx - endRightPx).toDp() })
                        .background(Color.Black.copy(alpha = 0.70f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(startLeftPx.roundToInt(), 0) }
                    .width(with(density) { selectionWidthPx.toDp() })
                    .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        var accumMs = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumMs = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                                val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                                accumMs += (dragAmount / uw) * dur
                                val deltaMs = accumMs.toLong()
                                if (deltaMs != 0L) {
                                    accumMs -= deltaMs
                                    val clipLen = currentEndMs - currentStartMs
                                    val newStart = (currentStartMs + deltaMs).coerceIn(0L, currentVideoDurationMs - clipLen)
                                    val newEnd = newStart + clipLen
                                    currentOnRangeChange(newStart, newEnd)
                                }
                            }
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(startLeftPx.roundToInt(), 0) }
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        var accumMs = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumMs = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                                val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                                accumMs += (dragAmount / uw) * dur
                                val deltaMs = accumMs.toLong()
                                if (deltaMs != 0L) {
                                    accumMs -= deltaMs
                                    val newStart = (currentStartMs + deltaMs).coerceIn(0L, currentEndMs - 500L)
                                    currentOnRangeChange(newStart, currentEndMs)
                                    currentOnScrubPosition(newStart)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(20.dp)
                        .background(Color(0xFF1E222D), RoundedCornerShape(1.dp))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset((endRightPx - handleWidthPx).roundToInt(), 0) }
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        var accumMs = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumMs = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                                val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                                accumMs += (dragAmount / uw) * dur
                                val deltaMs = accumMs.toLong()
                                if (deltaMs != 0L) {
                                    accumMs -= deltaMs
                                    val newEnd = (currentEndMs + deltaMs).coerceIn(currentStartMs + 500L, currentVideoDurationMs)
                                    currentOnRangeChange(currentStartMs, newEnd)
                                    currentOnScrubPosition(newEnd)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(20.dp)
                        .background(Color(0xFF1E222D), RoundedCornerShape(1.dp))
                )
            }

            val playheadX = (handleWidthPx + (currentFrac * (trackWidthPx - handleWidthPx * 2))).coerceIn(0f, trackWidthPx)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(playheadX.roundToInt() - with(density) { 10.dp.toPx() }.roundToInt(), 0) }
                    .width(20.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                            val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                            val deltaMs = (dragAmount / uw * dur).toLong()
                            val newPos = (currentPosition + deltaMs).coerceIn(currentStartMs, currentEndMs)
                            currentOnScrubPosition(newPos)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(Color(0xFFFFD54F), RoundedCornerShape(1.5.dp))
                )
            }
        }
    }
}

@Composable
internal fun StudioToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = Color(0xFFFFD54F)
    val inactiveColor = Color.White.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) activeColor else inactiveColor
        )
    }
}

@Composable
internal fun DraggableTextOverlayView(
    item: StudioTextOverlay,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }

    val bgModifier = when (item.backgroundStyle) {
        TextBackgroundStyle.SOLID_BLACK -> Modifier.background(Color.Black.copy(0.8f), RoundedCornerShape(8.dp))
        TextBackgroundStyle.TRANSLUCENT -> Modifier.background(Color.Black.copy(0.45f), RoundedCornerShape(8.dp))
        TextBackgroundStyle.NEON_PILL -> Modifier.background(Color(0xFF6366F1).copy(0.85f), CircleShape)
        else -> Modifier.background(Color.Black.copy(0.45f), RoundedCornerShape(8.dp))
    }

    val fontFam = when (item.fontFamilyStyle) {
        StudioFontFamily.SERIF -> FontFamily.Serif
        StudioFontFamily.MONOSPACE -> FontFamily.Monospace
        StudioFontFamily.CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    item.offsetX = offsetX
                    item.offsetY = offsetY
                }
            }
            .then(bgModifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = item.text,
            fontSize = item.fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFam,
            color = item.color
        )
    }
}

@Composable
internal fun DraggableEmojiStickerView(
    item: StudioEmojiSticker,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    item.offsetX = offsetX
                    item.offsetY = offsetY
                }
            }
    ) {
        Text(text = item.emoji, fontSize = 38.sp)
    }
}

@Composable
internal fun DraggableImageStickerView(
    item: StudioImageSticker,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }

    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    item.offsetX = offsetX
                    item.offsetY = offsetY
                }
            }
            .size(item.widthDp.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Sticker",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )

        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.7f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
internal fun StudioTopBar(
    clipCount: Int,
    activeClipIndex: Int,
    onClose: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Video Editor",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (clipCount > 1) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clip ${activeClipIndex + 1}/$clipCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White.copy(0.8f))
            }

            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Export", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
internal fun GifMakerControlPanel(
    durationMs: Long,
    maxDurationMs: Long = 60_000L,
    direction: MediaProcessorEngine.GifDirection,
    onDirectionChange: (MediaProcessorEngine.GifDirection) -> Unit,
    qualityPreset: String,
    onQualityPresetChange: (String) -> Unit,
    cropPreset: MediaAspectRatio,
    onCropPresetChange: (MediaAspectRatio) -> Unit,
    onCreateGif: () -> Unit
) {
    val durationSec = (durationMs / 1000f).coerceIn(0.5f, 60f)
    val estSizeMb = remember(durationSec, qualityPreset, direction) {
        val baseSizePerSec = when (qualityPreset) {
            "MAX_CLARITY" -> if (durationSec <= 10f) 1.6f else if (durationSec <= 30f) 0.75f else 0.35f
            "BALANCED" -> if (durationSec <= 10f) 1.1f else if (durationSec <= 30f) 0.5f else 0.25f
            else -> 0.18f // Compact
        }
        val mult = if (direction == MediaProcessorEngine.GifDirection.PING_PONG) 1.8f else 1.0f
        val est = (durationSec * baseSizePerSec * mult).coerceIn(0.5f, 24.5f)
        String.format(java.util.Locale.US, "%.1f", est)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Direction & Quality Presets & Duration Badge (Fully Scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction Chips
            MediaProcessorEngine.GifDirection.values().forEach { dir ->
                val isSel = direction == dir
                Surface(
                    color = if (isSel) Color(0xFFFFD54F) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onDirectionChange(dir) }
                ) {
                    Text(
                        text = "${dir.arrowSymbol} ${dir.label}",
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Quality Presets
            listOf(
                "MAX_CLARITY" to "Ultra HD",
                "BALANCED" to "Balanced",
                "COMPACT" to "Compact"
            ).forEach { (preset, label) ->
                val isSel = qualityPreset == preset
                Surface(
                    color = if (isSel) Color(0xFF6366F1) else Color(0x1AFFFFFF),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onQualityPresetChange(preset) }
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color.White else Color.White.copy(0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Duration Cap Badge
            Surface(
                color = if (durationMs > maxDurationMs) Color(0x33EF4444) else Color(0x336366F1),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1fs", durationSec)} / 60s Max",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (durationMs > maxDurationMs) Color(0xFFFCA5A5) else Color(0xFFA5B4FC),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Row 2: Aspect Ratio & Create Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aspect Ratio Chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    MediaAspectRatio.ORIGINAL to "Original",
                    MediaAspectRatio.P_1_1 to "1:1 Square",
                    MediaAspectRatio.P_16_9 to "16:9 Wide",
                    MediaAspectRatio.P_9_16 to "9:16 Story",
                    MediaAspectRatio.P_4_3 to "4:3 Classic"
                ).forEach { (preset, label) ->
                    val isSel = cropPreset == preset
                    Surface(
                        color = if (isSel) Color.White else Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onCropPresetChange(preset) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color.Black else Color.White.copy(0.85f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Create GIF Button
            Button(
                onClick = onCreateGif,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Animation,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Create HD GIF (~${estSizeMb}MB)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
