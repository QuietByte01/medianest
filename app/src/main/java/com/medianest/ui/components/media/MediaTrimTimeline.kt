package com.medianest.ui.components.media

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.formatDuration
import kotlin.math.roundToInt

@Composable
fun MediaTrimTimeline(
    thumbnails: List<Bitmap>,
    isExtracting: Boolean,
    videoDurationMs: Long,
    currentPositionMs: Long = 0L,
    startMs: Long,
    endMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    onScrubPosition: ((scrubMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showSprockets: Boolean = false,
    showDurationLabels: Boolean = false,
    showPlayhead: Boolean = false,
    accentColor: Color = Color(0xFF38BDF8),
    handleColor: Color = accentColor,
    handleWidthDp: Int = 18,
    trackHeightDp: Int = 56
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val durationFloat = videoDurationMs.toFloat().coerceAtLeast(1000f)

    val startFrac = (startMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val currentFrac = (currentPositionMs.toFloat() / durationFloat).coerceIn(0f, 1f)

    val handleWidthPx = with(density) { handleWidthDp.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showDurationLabels) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Start: ${formatDuration(startMs)}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(text = "Clip: ${formatDuration((endMs - startMs).coerceAtLeast(0L))}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                Text(text = "End: ${formatDuration(endMs)}", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeightDp.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(onScrubPosition) {
                    if (onScrubPosition != null) {
                        detectTapGestures { offset ->
                            val tw = trackWidthPx
                            if (tw > 0f) {
                                val frac = (offset.x / tw).coerceIn(0f, 1f)
                                onScrubPosition((frac * durationFloat).toLong())
                            }
                        }
                    }
                }
        ) {
            // 1. Thumbnails
            if (thumbnails.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    thumbnails.forEach { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(8) { i ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp)
                                .background(Color.White.copy(alpha = if (isExtracting) 0.08f + (i % 2) * 0.04f else 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 2. Sprockets
            if (showSprockets) {
                listOf(Alignment.TopCenter, Alignment.BottomCenter).forEach { align ->
                    Row(
                        modifier = Modifier.fillMaxWidth().align(align).padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(16) {
                            Box(modifier = Modifier.size(width = 4.dp, height = 2.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                        }
                    }
                }
            }

            // 3. Selection Window & Scrims
            if (trackWidthPx > 0f) {
                val usableWidth = (trackWidthPx - (handleWidthPx * 2)).coerceAtLeast(10f)
                val startLeftPx = (startFrac * usableWidth).coerceAtLeast(0f)
                val endRightPx = (handleWidthPx * 2 + endFrac * usableWidth).coerceAtMost(trackWidthPx)
                val selectionWidthPx = (endRightPx - startLeftPx).coerceAtLeast(handleWidthPx * 2)

                // Left Scrim
                if (startLeftPx > 0f) {
                    Box(modifier = Modifier.fillMaxHeight().width(with(density) { startLeftPx.toDp() }).background(Color.Black.copy(alpha = 0.7f)))
                }
                // Right Scrim
                if (endRightPx < trackWidthPx) {
                    Box(modifier = Modifier.fillMaxHeight().offset { IntOffset(endRightPx.roundToInt(), 0) }.width(with(density) { (trackWidthPx - endRightPx).toDp() }).background(Color.Black.copy(alpha = 0.7f)))
                }

                // Selection Box
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset(startLeftPx.roundToInt(), 0) }
                        .width(with(density) { selectionWidthPx.toDp() })
                        .border(2.dp, accentColor, RoundedCornerShape(8.dp))
                        .pointerInput(startMs, endMs, videoDurationMs, trackWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val curLen = endMs - startMs
                                val newStart = (startMs + deltaMs).coerceIn(0L, videoDurationMs - curLen)
                                onRangeChange(newStart, newStart + curLen)
                            }
                        }
                )

                // Handles
                // Left
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset(startLeftPx.roundToInt(), 0) }
                        .width(handleWidthDp.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(handleColor)
                        .pointerInput(startMs, endMs, trackWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val newStart = (startMs + deltaMs).coerceIn(0L, endMs - 500L)
                                onRangeChange(newStart, endMs)
                                onScrubPosition?.invoke(newStart)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) { Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.Black.copy(0.4f))) }
                    }
                }
                // Right
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset((endRightPx - handleWidthPx).roundToInt(), 0) }
                        .width(handleWidthDp.dp)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(handleColor)
                        .pointerInput(startMs, endMs, videoDurationMs, trackWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val newEnd = (endMs + deltaMs).coerceIn(startMs + 500L, videoDurationMs)
                                onRangeChange(startMs, newEnd)
                                onScrubPosition?.invoke(newEnd)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) { Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.Black.copy(0.4f))) }
                    }
                }

                // Playhead
                if (showPlayhead) {
                    val playheadX = (handleWidthPx + (currentFrac * usableWidth)).coerceIn(0f, trackWidthPx)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .offset { IntOffset(playheadX.roundToInt() - with(density) { 10.dp.toPx() }.roundToInt(), 0) }
                            .width(20.dp)
                            .pointerInput(currentPositionMs, startMs, endMs, trackWidthPx) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                    onScrubPosition?.invoke((currentPositionMs + deltaMs).coerceIn(0L, videoDurationMs))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(accentColor, RoundedCornerShape(1.5.dp)))
                    }
                }
            }
        }
    }
}
