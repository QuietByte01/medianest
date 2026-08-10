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
import androidx.compose.ui.text.style.TextOverflow
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
    albumSongs: List<MediaItem>,
    isSeeking: Boolean,
    sliderPos: Float,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleShowLyrics: (Boolean) -> Unit,
    onToggleAlbumSongsPanel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onEditLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Artwork / Visualizer / Lyrics & Track Title below
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                    titleFontSize = 13,
                    activeLyricFontSize = 18,
                    inactiveLyricFontSize = 14,
                    glassSurfaceModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    cardShapeRadius = 22.dp
                )
            } else if (showAudioVisualizer) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onToggleShowLyrics(true) },
                    shape = RoundedCornerShape(24.dp),
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
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title and Artist Info below artwork
            Text(
                text = currentItem?.title ?: "No Track Selected",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${currentItem?.artist ?: "Unknown Artist"} — ${currentItem?.album ?: "Unknown Album"}",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right Side: UP NEXT queue / Album Songs panel & Player Transport
        Column(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Standalone Glass Card for Queue / Album Songs
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0x1F24293A),
                borderColor = Color(0x2EFFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (showAlbumSongsInPanel) {
                        // ALBUM SONGS Section
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ALBUM SONGS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64B5F6),
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = currentItem?.album ?: "Current Album",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            val albumSongsList = albumSongs

                            if (albumSongsList.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (track in albumSongsList.take(6)) {
                                        val isPlayingTrack = track.uri == currentItem?.uri
                                        GlassSurface(
                                            shape = RoundedCornerShape(16.dp),
                                            backgroundColor = if (isPlayingTrack) Color(0x3364B5F6) else Color(0x1AFFFFFF),
                                            borderColor = if (isPlayingTrack) Color(0x6664B5F6) else Color(0x22FFFFFF),
                                            modifier = Modifier.clickable {
                                                val idx = playerState.queue.indexOf(track)
                                                if (idx != -1) {
                                                    playerManager.playMediaList(playerState.queue, idx)
                                                } else {
                                                    playerManager.playMediaList(listOf(track), 0)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0x33FFFFFF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (track.albumArtUri != null) {
                                                        AsyncImage(
                                                            model = track.albumArtUri,
                                                            contentDescription = track.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        track.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isPlayingTrack) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isPlayingTrack) Color(0xFF64B5F6) else Color.White,
                                                        maxLines = 1
                                                    )
                                                    Text(track.artist ?: "Unknown Artist", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                                                }
                                                if (isPlayingTrack) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                GlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = Color(0x12FFFFFF)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No other tracks found in album",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // UP NEXT Section
                        Column {
                            Text(
                                text = "UP NEXT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.65f),
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val upcomingList = remember(playerState.queue, playerState.queueIndex) {
                                if (playerState.queue.size > 1) {
                                    playerState.queue.drop(playerState.queueIndex + 1).take(2)
                                } else emptyList()
                            }

                            if (upcomingList.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (track in upcomingList) {
                                        GlassSurface(
                                            shape = RoundedCornerShape(16.dp),
                                            backgroundColor = Color(0x1AFFFFFF),
                                            borderColor = Color(0x22FFFFFF)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0x33FFFFFF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (track.albumArtUri != null) {
                                                        AsyncImage(
                                                            model = track.albumArtUri,
                                                            contentDescription = track.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                                    Text(track.artist ?: "Unknown Artist", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                                                }
                                                Icon(Icons.Default.Menu, contentDescription = "Queue Item", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            } else {
                                GlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = Color(0x12FFFFFF)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "End of playback queue",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f),
                verticalArrangement = Arrangement.Center
            ) {
                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleAlbumSongsPanel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Toggle Album Songs / Up Next",
                            tint = if (showAlbumSongsInPanel) Color(0xFF64B5F6) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenAddPlaylist,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Seek Slider
                val currentPosMsLs = playerState.currentPositionMs
                val durationMsLs = if (playerState.durationMs > 0) playerState.durationMs else currentItem?.durationMs ?: 0L
                val effectiveSliderValLs = if (isSeeking) sliderPos else currentPosMsLs.toFloat()
                val maxSliderValLs = durationMsLs.coerceAtLeast(1L).toFloat()

                Column(modifier = Modifier.fillMaxWidth()) {
                    ThinSeekBar(
                        value = effectiveSliderValLs.coerceIn(0f, maxSliderValLs),
                        onValueChange = onSeekChange,
                        onValueChangeFinished = onSeekFinished,
                        valueRange = 0f..maxSliderValLs,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                        trackHeight = 3.dp,
                        thumbRadius = 5.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(effectiveSliderValLs.toLong()), fontSize = 11.sp, color = Color.White.copy(alpha = 0.70f))
                        Text(formatDuration(durationMsLs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.70f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transport Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { playerManager.setShuffleMode(!playerState.isShuffle) },
                        shape = CircleShape,
                        color = if (playerState.isShuffle) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                        border = if (playerState.isShuffle) BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)) else null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { playerManager.previous() },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { playerManager.togglePlayPause() },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.40f)),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { playerManager.next() },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
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
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val icon = if (playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                            Icon(
                                imageVector = icon,
                                contentDescription = "Repeat",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}