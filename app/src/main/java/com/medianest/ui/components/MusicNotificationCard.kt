package com.medianest.ui.components

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.data.model.MediaItem
import com.medianest.player.ExoPlayerManager

/**
 * Android 13/14 Media Notification Card view with squiggly/wavy seekbar.
 * Perfectly styled after the Android system media notification card with:
 * - "This phone" device chip
 * - "Media output" button
 * - Wavy progress bar reacting to music playback
 * - Full transport controls (Shuffle, Prev, Play/Pause, Next, Repeat)
 */
@Composable
fun MusicNotificationCard(
    item: MediaItem,
    exoPlayerManager: ExoPlayerManager,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onOpenFullPlayer: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val playerState by exoPlayerManager.playerState.collectAsState()

    val currentItem = playerState.currentItem ?: item
    val isPlaying = playerState.isPlaying
    val currentPosMs = playerState.currentPositionMs
    val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem.durationMs

    val maxSliderVal = durationMs.coerceAtLeast(1L).toFloat()

    // Soft glow pulse behind the card
    val infiniteTransition = rememberInfiniteTransition(label = "notif_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color.Black.copy(alpha = 0.5f),
                ambientColor = Color.White.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF1C2026).copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .clickable(enabled = onOpenFullPlayer != null) {
                onOpenFullPlayer?.invoke(currentItem)
            }
    ) {
        // Blurred Album Artwork Background inside the notification card
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(currentItem.albumArtUri ?: currentItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(28.dp)
        )

        // Dark gradient overlay for extreme text legibility & contrast
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.80f)
                        )
                    )
                )
        )

        // Ambient glowing orb accent
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopStart)
                .offset(x = (-20).dp, y = (-20).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA8C7FA).copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Top Header: "♬ This phone" badge + "Media output" chip (+ Optional Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left badge: "♬ This phone"
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFA8C7FA),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This phone",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Right chip: "Media output"
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = CircleShape,
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.30f)),
                        modifier = Modifier.clip(CircleShape).clickable { /* Media output selector */ }
                    ) {
                        Text(
                            text = "Media output",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    if (onClose != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                exoPlayerManager.exoPlayer.pause()
                                onClose()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title & Subtitle (Artist)
            Text(
                text = currentItem.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentItem.artist ?: currentItem.album ?: "Unknown Artist",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wavy Squiggly Seekbar reacting to music
            WavySeekBar(
                value = currentPosMs.toFloat().coerceIn(0f, maxSliderVal),
                onValueChange = { exoPlayerManager.seekTo(it.toLong()) },
                valueRange = 0f..maxSliderVal,
                isPlaying = isPlaying,
                activeColor = Color(0xFFA8C7FA),
                inactiveColor = Color.White.copy(alpha = 0.25f),
                thumbColor = Color(0xFFA8C7FA),
                waveAmplitudeDp = 6.dp,
                waveLengthDp = 30.dp,
                activeTrackHeightDp = 9.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Timestamps below seekbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosMs),
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDuration(durationMs),
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Transport Controls (Shuffle, Prev, Play/Pause, Next, Repeat / Playlist)
            // Bottom Transport Control Row (Android 13/14 Material You Custom Rounded Controls)
            MaterialYouPlayerControlBar(
                isPlaying = isPlaying,
                onPlayPauseToggle = { exoPlayerManager.togglePlayPause() },
                onPrevious = { exoPlayerManager.previous() },
                onNext = { exoPlayerManager.next() },
                isShuffle = playerState.isShuffle,
                onShuffleToggle = { exoPlayerManager.setShuffleMode(!playerState.isShuffle) },
                repeatMode = playerState.repeatMode,
                onRepeatToggle = { exoPlayerManager.setRepeatMode((playerState.repeatMode + 1) % 3) },
                hasPrevious = playerState.queue.size > 1 || currentPosMs > 3000L,
                hasNext = playerState.queue.size > 1,
                style = ControlButtonStyle.TRANSPARENT_MINIMAL,
                tint = Color.White,
                accentTint = Color(0xFFA8C7FA),
                playButtonSize = 52.dp,
                secondaryButtonSize = 40.dp
            )

            if (onOpenFullPlayer != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFullPlayer(currentItem) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Full Player",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
