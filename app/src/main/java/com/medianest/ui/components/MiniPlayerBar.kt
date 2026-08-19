package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.player.PlayerState

import com.medianest.ui.components.GlassSurface

@Composable
fun MiniPlayerBar(
    playerState: PlayerState,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onClickExpand: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onSeekTo: ((Long) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    modifier: Modifier = Modifier
) {
    val currentItem = playerState.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.AUDIO } ?: return
    val context = LocalContext.current

    val titleText = remember(currentItem.title, currentItem.uri) {
        val raw = currentItem.title
        if (raw.isBlank() || raw.startsWith("Track_") || raw.startsWith("Media")) {
            val lastSeg = currentItem.uri.lastPathSegment ?: ""
            if (lastSeg.isNotBlank() && !lastSeg.startsWith("audio:", ignoreCase = true) && !lastSeg.startsWith("document", ignoreCase = true)) {
                if (lastSeg.contains('.')) lastSeg.substringBeforeLast('.') else lastSeg
            } else {
                "Audio Track"
            }
        } else {
            raw
        }
    }

    val density = context.resources.displayMetrics.density
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val swipeGestureModifier = Modifier.pointerInput(playerState.isPlaying) {
        detectDragGestures(
            onDragStart = {
                totalDragX = 0f
                totalDragY = 0f
            },
            onDragEnd = {
                val thresholdPx = 36f * density
                val absX = kotlin.math.abs(totalDragX)
                val absY = kotlin.math.abs(totalDragY)
                val isSwipeDown = totalDragY > thresholdPx && totalDragY > absX
                val isSwipeUp = totalDragY < -thresholdPx && absY > absX
                val isSwipeHorizontal = absX > thresholdPx && absX > absY
                val isPaused = !playerState.isPlaying

                if (isSwipeDown || (isPaused && (isSwipeUp || isSwipeHorizontal))) {
                    if (onDismiss != null) {
                        onDismiss()
                    } else {
                        com.medianest.player.ExoPlayerManager.getInstance(context).stopPlayback()
                    }
                }
            },
            onDragCancel = {
                totalDragX = 0f
                totalDragY = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
            }
        )
    }

    val subtitle = remember(currentItem.artist, currentItem.album) {
        val artist = currentItem.artist ?: "Unknown Artist"
        val album = currentItem.album ?: ""
        if (album.isNotBlank()) "$artist • $album" else artist
    }

    GlassSurface(
        modifier = modifier
            .then(swipeGestureModifier)
            .clip(shape)
            .clickable(onClick = onClickExpand),
        shape = shape,
        backgroundColor = Color(0x381F2332),
        borderColor = Color(0x40FFFFFF)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork Thumbnail
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentItem.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentItem.albumArtUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = titleText,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Title & Subtitle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titleText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = Color.White,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF9EA3B0)
                    )
                }

                // Playback Action Buttons (Naked Icons with no background/border)
                // TODO: Replace single skip buttons with double skip buttons for prev/next
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onPrevious?.invoke() ?: com.medianest.player.ExoPlayerManager.getInstance(context).previous()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedSkipPreviousIcon(
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPlayPauseToggle
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playerState.isPlaying) {
                            RoundedPauseIcon(
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        } else {
                            RoundedPlayIcon(
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onNext
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedSkipNextIcon(
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // Sleek progress line at bottom
            val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem.durationMs
            if (durationMs > 0) {
                val progress = (playerState.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
