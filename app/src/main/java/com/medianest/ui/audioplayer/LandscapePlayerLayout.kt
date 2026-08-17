package com.medianest.ui.audioplayer

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.data.model.LyricLine
import com.medianest.data.model.MediaItem
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.WavySeekBar
import com.medianest.ui.components.RoundedPlayIcon
import com.medianest.ui.components.RoundedPauseIcon
import com.medianest.ui.components.RoundedDoubleSkipPreviousIcon
import com.medianest.ui.components.RoundedDoubleSkipNextIcon
import kotlinx.coroutines.delay

@Composable
fun LandscapePlayerLayout(
    playerState: PlayerState,
    currentItem: MediaItem?,
    playerManager: ExoPlayerManager,
    showLyricsView: Boolean,
    showAudioVisualizer: Boolean,
    showAlbumSongsInPanel: Boolean,
    isLoadingLyrics: Boolean,
    lyricsLines: List<LyricLine>,
    rawLyricsText: String?,
    activeLyricIndex: Int,
    listState: LazyListState,
    isFavorite: Boolean,
    isTablet: Boolean,
    albumSongs: List<MediaItem>,
    albumArtHue: Float?,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleShowLyrics: (Boolean) -> Unit,
    onToggleAlbumSongsPanel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onEditLyrics: () -> Unit,
    onToggleVisualizer: () -> Unit = {},
    onFullscreenVisualizerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ImmersiveLandscapeLayout(
        playerState = playerState,
        currentItem = currentItem,
        playerManager = playerManager,
        showAudioVisualizer = showAudioVisualizer,
        albumArtHue = albumArtHue,
        onSeekChange = onSeekChange,
        onSeekFinished = onSeekFinished,
        onToggleVisualizer = onToggleVisualizer,
        modifier = modifier
    )
}

@Composable
private fun ImmersiveLandscapeLayout(
    playerState: PlayerState,
    currentItem: MediaItem?,
    playerManager: ExoPlayerManager,
    showAudioVisualizer: Boolean,
    albumArtHue: Float?,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleVisualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artworkUri = currentItem?.albumArtUri ?: currentItem?.uri

    var showPlayPauseIndicator by remember { mutableStateOf(false) }
    var showPrevIndicator by remember { mutableStateOf(false) }
    var showNextIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(showPlayPauseIndicator) {
        if (showPlayPauseIndicator) {
            delay(1000)
            showPlayPauseIndicator = false
        }
    }
    LaunchedEffect(showPrevIndicator) {
        if (showPrevIndicator) {
            delay(1000)
            showPrevIndicator = false
        }
    }
    LaunchedEffect(showNextIndicator) {
        if (showNextIndicator) {
            delay(1000)
            showNextIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            playerManager.previous()
                            showPrevIndicator = true
                        } else {
                            playerManager.next()
                            showNextIndicator = true
                        }
                    }
                )
            }
    ) {
        // 1. Background Content (Immersive)
        Box(modifier = Modifier.fillMaxSize()) {
            if (artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(28.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            if (showAudioVisualizer) {
                AudioReactiveVisualizerPattern(
                    isPlaying = playerState.isPlaying,
                    audioSessionId = playerState.audioSessionId,
                    hue = albumArtHue,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Center Content (Artwork + Text)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!showAudioVisualizer && artworkUri != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.68f)
                            .aspectRatio(1f)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .pointerInput(currentItem?.id) {
                                    detectTapGestures(
                                        onTap = {
                                            playerManager.togglePlayPause()
                                            showPlayPauseIndicator = true
                                        },
                                        onDoubleTap = { offset ->
                                            if (offset.x < size.width / 2) {
                                                playerManager.previous()
                                                showPrevIndicator = true
                                            } else {
                                                playerManager.next()
                                                showNextIndicator = true
                                            }
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artworkUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentItem?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Seekbar as bottom border of the Card
                        val currentPosMs = playerState.currentPositionMs
                        val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem?.durationMs ?: 0L
                        val maxSliderVal = durationMs.coerceAtLeast(1L).toFloat()

                        val seekbarColor = remember(albumArtHue) {
                            albumArtHue?.let { Color.hsv(it, 0.6f, 0.9f) } ?: Color.White
                        }

                        WavySeekBar(
                            value = currentPosMs.toFloat().coerceIn(0f, maxSliderVal),
                            onValueChange = onSeekChange,
                            onValueChangeFinished = onSeekFinished,
                            valueRange = 0f..maxSliderVal,
                            isPlaying = playerState.isPlaying,
                            activeColor = seekbarColor,
                            inactiveColor = seekbarColor.copy(alpha = 0.10f),
                            thumbColor = seekbarColor,
                            waveAmplitudeDp = 6.dp,
                            waveLengthDp = 36.dp,
                            activeTrackHeightDp = 4.dp,
                            heightDp = 14.dp, // Reduced height to pull it to the bottom
                            showThumb = false,
                            fullTrackBackground = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp) // Removed vertical padding
                        )

                        // Center Indicators
                        IndicatorOverlay(
                            visible = showPlayPauseIndicator,
                            type = if (playerState.isPlaying) IndicatorType.Pause else IndicatorType.Play
                        )
                        IndicatorOverlay(
                            visible = showPrevIndicator,
                            type = IndicatorType.Previous,
                            alignment = Alignment.CenterStart
                        )
                        IndicatorOverlay(
                            visible = showNextIndicator,
                            type = IndicatorType.Next,
                            alignment = Alignment.CenterEnd
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    TrackInfoSection(currentItem)
                }
            } else {
                // When visualizer is on, show indicators in center
                IndicatorOverlay(
                    visible = showPlayPauseIndicator,
                    type = if (playerState.isPlaying) IndicatorType.Pause else IndicatorType.Play
                )
                IndicatorOverlay(
                    visible = showPrevIndicator,
                    type = IndicatorType.Previous,
                    alignment = Alignment.CenterStart
                )
                IndicatorOverlay(
                    visible = showNextIndicator,
                    type = IndicatorType.Next,
                    alignment = Alignment.CenterEnd
                )
            }
        }

        // 2.5 Bottom Content (Text when Visualizer is ON)
        if (showAudioVisualizer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                TrackInfoSection(currentItem)
            }
        }

        // 3. Top Right Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            IconButton(
                onClick = onToggleVisualizer,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Toggle Visualizer",
                    tint = if (showAudioVisualizer) Color(0xFF64B5F6) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (showAudioVisualizer) {
                IconButton(
                    onClick = { playerManager.togglePlayPause() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackInfoSection(currentItem: MediaItem?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = currentItem?.title ?: "No Track",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = currentItem?.artist ?: "Unknown Artist",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class IndicatorType {
    Play, Pause, Previous, Next
}

@Composable
private fun BoxScope.IndicatorOverlay(
    visible: Boolean,
    type: IndicatorType,
    alignment: Alignment = Alignment.Center
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = Modifier.align(alignment)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (type) {
                IndicatorType.Play -> RoundedPlayIcon(Modifier.size(30.dp), tint = Color.White)
                IndicatorType.Pause -> RoundedPauseIcon(Modifier.size(30.dp), tint = Color.White)
                IndicatorType.Previous -> RoundedDoubleSkipPreviousIcon(Modifier.size(30.dp), tint = Color.White)
                IndicatorType.Next -> RoundedDoubleSkipNextIcon(Modifier.size(30.dp), tint = Color.White)
            }
        }
    }
}
