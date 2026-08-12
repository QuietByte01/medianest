package com.example.ui.audioplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LyricLine
import com.example.data.model.MediaItem
import com.example.player.ExoPlayerManager
import com.example.player.PlayerState
import com.example.ui.components.GlassSurface
import com.example.ui.components.ThinSeekBar
import com.example.ui.components.formatDuration

@Composable
fun PortraitPlayerLayout(
    playerState: PlayerState,
    currentItem: MediaItem?,
    playerManager: ExoPlayerManager,
    albumArtSize: Dp,
    isTablet: Boolean,
    showLyricsView: Boolean,
    showAudioVisualizer: Boolean,
    showAlbumSongsInPortraitBox: Boolean,
    isLoadingLyrics: Boolean,
    lyricsLines: List<LyricLine>,
    rawLyricsText: String?,
    activeLyricIndex: Int,
    listState: LazyListState,
    isFavorite: Boolean,
    albumSongs: List<MediaItem>,
    isSeeking: Boolean,
    sliderPos: Float,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleShowLyrics: (Boolean) -> Unit,
    onToggleAlbumSongsPortraitBox: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onEditLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        // Main Center Area (Artwork / Lyrics / Album Songs / Visualizer)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (showLyricsView) {
                LyricsView(
                    isLoadingLyrics = isLoadingLyrics,
                    lyricsLines = lyricsLines,
                    rawLyricsText = rawLyricsText,
                    activeLyricIndex = activeLyricIndex,
                    listState = listState,
                    onSeekTo = { playerManager.seekTo(it) },
                    onEditLyrics = onEditLyrics,
                    onHideLyrics = { onToggleShowLyrics(false) },
                    titleFontSize = 14,
                    activeLyricFontSize = 20,
                    inactiveLyricFontSize = 15,
                    glassSurfaceModifier = Modifier.fillMaxSize(),
                    cardShapeRadius = 24.dp
                )
            } else if (showAlbumSongsInPortraitBox) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .width(albumArtSize)
                            .height(albumArtSize),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x1F24293A),
                        borderColor = Color(0x2EFFFFFF)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "ALBUM SONGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6),
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (albumSongs.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (track in albumSongs) {
                                        val isSelected = track.uri == currentItem?.uri
                                        GlassSurface(
                                            shape = RoundedCornerShape(12.dp),
                                            backgroundColor = if (isSelected) Color(0x3564B5F6) else Color(0x1AFFFFFF),
                                            borderColor = if (isSelected) Color(0x5564B5F6) else Color(0x22FFFFFF),
                                            modifier = Modifier.clickable {
                                                val queueIndex = playerState.queue.indexOf(track)
                                                if (queueIndex != -1) {
                                                    playerManager.playMediaList(playerState.queue, queueIndex)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(track.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.White, maxLines = 1)
                                                    Text(track.artist ?: "Unknown Artist", fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                                                }
                                                if (isSelected) {
                                                    Icon(Icons.Default.VolumeUp, contentDescription = "Playing", tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("No other tracks in album", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Track Title & Artist Info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(albumArtSize)
                            .padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = currentItem?.title ?: "No Track Selected",
                            fontSize = if (isTablet) 26.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = buildString {
                                append(currentItem?.artist ?: "Unknown Artist")
                                if (!currentItem?.album.isNullOrBlank()) {
                                    append(" — ")
                                    append(currentItem?.album)
                                }
                            },
                            fontSize = if (isTablet) 15.sp else 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .basicMarquee()
                        )
                    }
                }
            } else if (showAudioVisualizer) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x12FFFFFF),
                        borderColor = Color.Transparent,
                        blurRadius = 30.dp
                    ) {
                        AudioReactiveVisualizerPattern(
                            isPlaying = playerState.isPlaying,
                            currentPosMs = playerState.currentPositionMs,
                            trackSeed = currentItem?.id ?: 0L,
                            albumArtUri = currentItem?.albumArtUri,
                            audioSessionId = playerState.audioSessionId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(albumArtSize)
                            .padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = currentItem?.title ?: "No Track Selected",
                            fontSize = if (isTablet) 26.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = buildString {
                                append(currentItem?.artist ?: "Unknown Artist")
                                if (!currentItem?.album.isNullOrBlank()) {
                                    append(" — ")
                                    append(currentItem?.album)
                                }
                            },
                            fontSize = if (isTablet) 15.sp else 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .basicMarquee()
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggleShowLyrics(true) },
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        if (currentItem?.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentItem.albumArtUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.28f),
                                                Color.White.copy(alpha = 0.10f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.15f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isTablet) 96.dp else 72.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(albumArtSize)
                            .padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = currentItem?.title ?: "No Track Selected",
                            fontSize = if (isTablet) 26.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = buildString {
                                append(currentItem?.artist ?: "Unknown Artist")
                                if (!currentItem?.album.isNullOrBlank()) {
                                    append(" — ")
                                    append(currentItem?.album)
                                }
                            },
                            fontSize = if (isTablet) 15.sp else 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .basicMarquee()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleAlbumSongsPortraitBox,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FormatListBulleted,
                    contentDescription = "Album Songs",
                    tint = if (showAlbumSongsInPortraitBox) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF4081) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onOpenAddPlaylist,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Album to Playlist",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Seek Slider
        val currentPosMs = playerState.currentPositionMs
        val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem?.durationMs ?: 0L
        val effectiveSliderVal = if (isSeeking) sliderPos else currentPosMs.toFloat()
        val maxSliderVal = durationMs.coerceAtLeast(1L).toFloat()

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            ThinSeekBar(
                value = effectiveSliderVal.coerceIn(0f, maxSliderVal),
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..maxSliderVal,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                thumbColor = Color.White,
                trackHeight = 3.5.dp,
                thumbRadius = 5.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(effectiveSliderVal.toLong()), fontSize = 12.sp, color = Color.White.copy(alpha = 0.70f))
                Text(formatDuration(durationMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.70f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Player Transport Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { playerManager.setShuffleMode(!playerState.isShuffle) },
                shape = CircleShape,
                color = if (playerState.isShuffle) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                border = if (playerState.isShuffle) BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)) else null,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                onClick = { playerManager.previous() },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Surface(
                onClick = { playerManager.togglePlayPause() },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Surface(
                onClick = { playerManager.next() },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Surface(
                onClick = {
                    val nextRepeat = when (playerState.repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                    }
                    playerManager.setRepeatMode(nextRepeat)
                },
                shape = CircleShape,
                color = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                border = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)) else null,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = if (playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}