package com.medianest.ui.audioplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.medianest.data.model.ArtistInfo
import com.medianest.data.model.LyricLine
import com.medianest.data.model.MediaItem
import com.medianest.data.db.MediaType
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.MediaLoadingAnimation
import com.medianest.player.PlayerState
import com.medianest.ui.components.*

@Composable
fun PortraitPlayerLayout(
    playerState: PlayerState,
    currentItem: MediaItem?,
    playerManager: ExoPlayerManager,
    albumArtSize: Dp,
    isTablet: Boolean,
    showLyricsView: Boolean,
    showAudioVisualizer: Boolean,
    showQueueInPortraitBox: Boolean,
    isLoadingLyrics: Boolean,
    lyricsLines: List<LyricLine>,
    rawLyricsText: String?,
    activeLyricIndex: Int,
    listState: LazyListState,
    isFavorite: Boolean,
    albumArtHue: Float?,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleShowLyrics: (Boolean) -> Unit,
    onToggleQueueInPortraitBox: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onEditLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreenVisualizerClick: () -> Unit = {},
    onToggleArtistInfo: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    showArtistInfo: Boolean = false,
    artistInfo: ArtistInfo? = null,
    onPopularAlbumClick: (String) -> Unit = {},
    onLocalAlbumClick: (String) -> Unit = {},
    browsingAlbumName: String? = null,
    allAudioItems: List<MediaItem> = emptyList(),
    onBackToArtist: () -> Unit = {},
    isScanningLibrary: Boolean = false
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
            if (showArtistInfo) {
                if (artistInfo != null) {
                    ArtistInfoPanel(
                        artistInfo = artistInfo,
                        onPopularAlbumClick = onPopularAlbumClick,
                        onLocalAlbumClick = onLocalAlbumClick,
                        onArtistClick = onOpenArtist,
                        browsingAlbumName = browsingAlbumName,
                        allAudioItems = allAudioItems,
                        onBackToArtist = onBackToArtist,
                        playerManager = playerManager,
                        isLoading = isScanningLibrary,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MediaLoadingAnimation(
                            mediaType = MediaType.AUDIO,
                            iconSize = 52.dp
                        )
                    }
                }
            } else if (showLyricsView) {
                LyricsView(
                    songTitle = currentItem?.title ?: "Unknown Track",
                    songArtist = currentItem?.artist ?: "Unknown Artist",
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
                    cardShapeRadius = 16.dp
                )
            } else if (showQueueInPortraitBox) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    enableBlur = true,
                    backgroundColor = Color(0x1F24293A),
                    borderColor = Color(0x2EFFFFFF)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Text(
                                text = "UP NEXT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6),
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (playerState.queue.isNotEmpty()) {
                            itemsIndexed(playerState.queue) { idx, track ->
                                val isSelected = track.uri == currentItem?.uri
                                GlassSurface(
                                    shape = RoundedCornerShape(12.dp),
                                    enableBlur = false,
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
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.White, maxLines = 1)
                                            Text(track.artist ?: "Unknown Artist", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "Playing", tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Text("Queue is empty", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
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
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x12FFFFFF),
                        borderColor = Color.Transparent,
                        blurRadius = 30.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AudioReactiveVisualizerPattern(
                                isPlaying = playerState.isPlaying,
                                audioSessionId = playerState.audioSessionId,
                                hue = albumArtHue,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = onFullscreenVisualizerClick,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33000000))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen Visualizer",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(18.dp)
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
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                GlassSurface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(16.dp),
                                    enableBlur = true,
                                    backgroundColor = Color.White.copy(alpha = 0.10f), // 90% Transparent
                                    borderColor = Color.White.copy(alpha = 0.25f),
                                    backgroundImage = currentItem?.uri, // Use blurred media context as backdrop
                                    blurRadius = 32.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .size(if (isTablet) 96.dp else 72.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }

                            // Artist Image Small Icon (Top Right)
                            val currentArtist = artistInfo
                            if (currentArtist != null && !currentArtist.isPlaceholder) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.2.dp,
                                            color = Color.White.copy(0.35f),
                                            shape = CircleShape
                                        )
                                        .clickable { onToggleArtistInfo() }
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.White.copy(0.25f), Color.White.copy(0.05f))
                                            )
                                        )
                                ) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(currentArtist.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Artist Info",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().alpha(0.90f)
                                    ) {
                                        val state = painter.state
                                        if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                                            // Transparent Glossy Fallback
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        } else {
                                            SubcomposeAsyncImageContent()
                                        }
                                    }
                                }
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
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleQueueInPortraitBox,
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = (-8).dp)
            ) {
                CustomPlaylistIcon(
                    modifier = Modifier.size(24.dp),
                    tint = if (showQueueInPortraitBox) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.85f)
                )
            }

            BubblingHeartButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                size = 24.dp,
                hue = albumArtHue,
                inactiveColor = Color.White.copy(alpha = 0.85f)
            )

            IconButton(
                onClick = onOpenAddPlaylist,
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = 8.dp)
            ) {
                CustomPlusIcon(
                    modifier = Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

// Seek Slider
        val currentPosMs = playerState.currentPositionMs
        val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem?.durationMs ?: 0L
        val maxSliderVal = durationMs.coerceAtLeast(1L).toFloat()

        val seekbarColor = remember(albumArtHue) {
            albumArtHue?.let { Color.hsv(it, 0.6f, 0.9f) } ?: Color.White
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {

            WavySeekBar(
                value = currentPosMs.toFloat().coerceIn(0f, maxSliderVal),
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..maxSliderVal,
                isPlaying = playerState.isPlaying,
                activeColor = seekbarColor,
                inactiveColor = seekbarColor.copy(alpha = 0.10f),
                thumbColor = seekbarColor,
                fullTrackBackground = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f)
                )
                Text(
                    text = formatDuration(durationMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Player Transport Row (Custom Android 13/14 Rounded Controls)
        MaterialYouPlayerControlBar(
            isPlaying = playerState.isPlaying,
            onPlayPauseToggle = { playerManager.togglePlayPause() },
            onPrevious = { playerManager.previous() },
            onNext = { playerManager.next() },
            isShuffle = playerState.isShuffle,
            onShuffleToggle = { playerManager.setShuffleMode(!playerState.isShuffle) },
            repeatMode = playerState.repeatMode,
            onRepeatToggle = {
                val nextRepeat = when (playerState.repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                    else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                }
                playerManager.setRepeatMode(nextRepeat)
            },
            hasPrevious = playerState.queue.size > 1 || playerState.currentPositionMs > 3000L,
            hasNext = playerState.queue.size > 1,
            style = ControlButtonStyle.GLASS_SQUIRCLE,
            playButtonSize = 68.dp,
            secondaryButtonSize = 48.dp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}