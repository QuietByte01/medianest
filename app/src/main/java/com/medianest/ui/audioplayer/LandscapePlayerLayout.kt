package com.medianest.ui.audioplayer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.medianest.data.model.*
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.BubblingHeartButton
import com.medianest.ui.components.BubblingHeartBurstEffect
import com.medianest.ui.components.GlossySidePanel
import com.medianest.ui.components.ArtistInfoPanel
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
    modifier: Modifier = Modifier,
    onToggleVisualizer: () -> Unit = {},
    onFullscreenVisualizerClick: () -> Unit = {},
    onToggleArtistInfo: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    showArtistInfo: Boolean = false,
    showSidePanel: Boolean = false,
    onToggleSidePanel: (Boolean) -> Unit = {},
    onPopularAlbumClick: (String) -> Unit = {},
    onLocalAlbumClick: (String) -> Unit = {},
    allAudioItems: List<MediaItem> = emptyList(),
    showHidden: Boolean = false,
    hiddenFolders: Set<String> = emptySet(),
    artistInfo: ArtistInfo? = null,
    browsingAlbumName: String? = null,
    onBackToArtist: () -> Unit = {},
    isScanningLibrary: Boolean = false
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
        onToggleFavorite = onToggleFavorite,
        onPopularAlbumClick = onPopularAlbumClick,
        onLocalAlbumClick = onLocalAlbumClick,
        onOpenArtist = onOpenArtist,
        isFavorite = isFavorite,
        isTablet = isTablet,
        allAudioItems = allAudioItems,
        showHidden = showHidden,
        hiddenFolders = hiddenFolders,
        passedArtistInfo = artistInfo,
        showArtistInfo = showArtistInfo,
        onToggleArtistInfo = onToggleArtistInfo,
        showSidePanel = showSidePanel,
        onToggleSidePanel = onToggleSidePanel,
        browsingAlbumName = browsingAlbumName,
        onBackToArtist = onBackToArtist,
        isScanningLibrary = isScanningLibrary,
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
    onToggleFavorite: () -> Unit,
    onPopularAlbumClick: (String) -> Unit,
    onLocalAlbumClick: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    isFavorite: Boolean,
    isTablet: Boolean = false,
    allAudioItems: List<MediaItem>,
    showHidden: Boolean,
    hiddenFolders: Set<String>,
    showArtistInfo: Boolean,
    onToggleArtistInfo: () -> Unit,
    showSidePanel: Boolean,
    onToggleSidePanel: (Boolean) -> Unit,
    browsingAlbumName: String? = null,
    onBackToArtist: () -> Unit = {},
    isScanningLibrary: Boolean = false,
    passedArtistInfo: ArtistInfo? = null,
    modifier: Modifier = Modifier
) {
    val artworkUri = currentItem?.albumArtUri ?: currentItem?.uri

    var showPlayPauseIndicator by remember { mutableStateOf(false) }
    var showPrevIndicator by remember { mutableStateOf(false) }
    var showNextIndicator by remember { mutableStateOf(false) }
    var showFavoriteIndicator by remember { mutableStateOf(false) }

    var sidePanelSwipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedSidePanelOffset by animateFloatAsState(
        targetValue = if (showSidePanel) 1f else sidePanelSwipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SidePanel"
    )

    LaunchedEffect(showFavoriteIndicator) {
        if (showFavoriteIndicator) {
            delay(1800)
            showFavoriteIndicator = false
        }
    }

    val animatedArtistSlide by animateFloatAsState(
        targetValue = if (showArtistInfo) -1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "ArtistSlide"
    )

    val artistMetadataRepo = remember { com.medianest.MediaNestApp.instance.artistMetadataRepository }
    var artistInfo by remember(passedArtistInfo) { mutableStateOf<ArtistInfo?>(passedArtistInfo) }

    LaunchedEffect(currentItem?.artist, allAudioItems) {
        val artistName = currentItem?.artist ?: "Unknown Artist"
        val info = artistMetadataRepo.getArtistInfo(artistName)
        val db = com.medianest.MediaNestApp.instance.database
        
        // 1. Find real albums in library
        val artistSongs = allAudioItems.filter { it.artist.equals(artistName, ignoreCase = true) }
        val realLocalAlbums = if (artistSongs.isNotEmpty()) {
            artistSongs.groupBy { it.album ?: "Unknown Album" }
                .map { (title, songs) ->
                    LocalAlbumInfo(
                        title = title,
                        artworkUri = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: songs.firstOrNull()?.uri,
                        songCount = songs.size
                    )
                }
                .sortedBy { it.title.lowercase() }
        } else {
            info.localAlbums
        }

        // 2. Personal Library Stats
        val uris = artistSongs.map { it.uri.toString() }
        val playbackStates = if (uris.isNotEmpty()) db.playbackStateDao().getPlaybackStatesForUris(uris) else emptyList()
        
        val totalPlays = playbackStates.sumOf { it.playCount }
        val topPlayedUri = playbackStates.maxByOrNull { it.playCount }?.mediaUri
        val topPlayedSong = artistSongs.find { it.uri.toString() == topPlayedUri }?.title
        val firstDiscovered = artistSongs.minOfOrNull { it.dateAdded } ?: 0L
        
        // Playlist presence
        val playlists = mutableSetOf<String>()
        if (uris.isNotEmpty()) {
            // For simplicity, we'd need a DAO query for this. 
            // Let's assume we can get it or just show a placeholder if too complex for now.
        }

        val pStats = PersonalArtistStats(
            totalPlays = totalPlays,
            topPlayedSong = topPlayedSong,
            firstDiscovered = firstDiscovered,
            inPlaylists = playlists.toList()
        )

        artistInfo = info.copy(
            localAlbums = realLocalAlbums,
            personalStats = pStats
        )
    }

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
                    onTap = {
                        if (showSidePanel) {
                            onToggleSidePanel(false)
                            sidePanelSwipeOffset = 0f
                        } else if (showArtistInfo) {
                            onToggleArtistInfo()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (offset.y > size.height * 0.7f) {
                            onToggleFavorite()
                            showFavoriteIndicator = true
                        } else if (offset.x < size.width / 2) {
                            playerManager.previous()
                            showPrevIndicator = true
                        } else {
                            playerManager.next()
                            showNextIndicator = true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (!showArtistInfo) {
                            val dragStep = dragAmount / size.width.toFloat()
                            sidePanelSwipeOffset = (sidePanelSwipeOffset + dragStep * 2.5f).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        if (sidePanelSwipeOffset > 0.3f) {
                            onToggleSidePanel(true)
                            sidePanelSwipeOffset = 1f
                        } else {
                            onToggleSidePanel(false)
                            sidePanelSwipeOffset = 0f
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
        val mainContentAlpha by animateFloatAsState(targetValue = 1f - animatedSidePanelOffset * 0.4f)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = animatedArtistSlide * this.size.width * 0.25f
                    alpha = mainContentAlpha
                },
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
                                .clip(RoundedCornerShape(16.dp))
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
                            shape = RoundedCornerShape(16.dp),
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

                        // Artist Image Small Icon (Top Right)
                        val currentArtist = artistInfo
                        if (currentArtist != null && !currentArtist.isPlaceholder) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(52.dp)
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
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            }
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
                            waveAmplitudeDp = if (isTablet) 7.5.dp else 6.dp,
                            waveLengthDp = if (isTablet) 76.dp else 48.dp,
                            activeTrackHeightDp = 4.dp,
                            heightDp = if (isTablet) 18.dp else 14.dp,
                            showThumb = false,
                            fullTrackBackground = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
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
                        IndicatorOverlay(
                            visible = showFavoriteIndicator,
                            type = if (isFavorite) IndicatorType.FavoriteOn else IndicatorType.FavoriteOff,
                            alignment = Alignment.Center,
                            hue = albumArtHue
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    TrackInfoSection(
                        currentItem = currentItem,
                        onDoubleTap = {
                            onToggleFavorite()
                            showFavoriteIndicator = true
                        }
                    )
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
                IndicatorOverlay(
                    visible = showFavoriteIndicator,
                    type = if (isFavorite) IndicatorType.FavoriteOn else IndicatorType.FavoriteOff,
                    alignment = Alignment.Center,
                    hue = albumArtHue
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
                TrackInfoSection(
                    currentItem = currentItem,
                    onDoubleTap = {
                        onToggleFavorite()
                        showFavoriteIndicator = true
                    }
                )
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
            // Hide visualizer button when artist info panel is open
            if (!showArtistInfo) {
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

        // Dismiss Overlay for Side Panels
        if (showSidePanel || showArtistInfo || sidePanelSwipeOffset > 0.01f || animatedArtistSlide < 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (showSidePanel || sidePanelSwipeOffset > 0.01f) {
                                onToggleSidePanel(false)
                                sidePanelSwipeOffset = 0f
                            }
                            if (showArtistInfo || animatedArtistSlide < 0f) {
                                onToggleArtistInfo()
                            }
                        })
                    }
            )
        }

        // 4. Side Panel (Queue)
        if (animatedSidePanelOffset > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .graphicsLayer {
                        translationX = (animatedSidePanelOffset - 1f) * this.size.width
                    }
                    .background(Color.Transparent)
            ) {
                GlossySidePanel(
                    playerState = playerState,
                    currentItem = currentItem,
                    onSongClick = { index ->
                        playerManager.playMediaList(playerState.queue, index)
                    },
                    backgroundArt = artworkUri,
                    showHidden = showHidden,
                    hiddenFolders = hiddenFolders
                )
            }
        }

        // 5. Artist Info Panel (Right)
        if (showArtistInfo || animatedArtistSlide < 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        translationX = (1f + animatedArtistSlide) * this.size.width
                    }
            ) {
                artistInfo?.let {
                    ArtistInfoPanel(
                        artistInfo = it,
                        onPopularAlbumClick = onPopularAlbumClick,
                        onLocalAlbumClick = onLocalAlbumClick,
                        onArtistClick = onOpenArtist,
                        browsingAlbumName = browsingAlbumName,
                        allAudioItems = allAudioItems,
                        onBackToArtist = onBackToArtist,
                        playerManager = playerManager,
                        isLoading = isScanningLibrary
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackInfoSection(
    currentItem: MediaItem?,
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .pointerInput(currentItem?.uri) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = currentItem?.title ?: "No Track",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .basicMarquee()
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
    Play, Pause, Previous, Next, FavoriteOn, FavoriteOff
}

@Composable
private fun BoxScope.IndicatorOverlay(
    visible: Boolean,
    type: IndicatorType,
    alignment: Alignment = Alignment.Center,
    hue: Float? = null
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
                IndicatorType.FavoriteOn -> {
                    val heartColor = hue?.let { Color.hsv(it, 0.90f, 1.0f) } ?: Color(0xFFFF2D55)
                    Box(contentAlignment = Alignment.Center) {
                        BubblingHeartBurstEffect(
                            triggerKey = 1,
                            hue = hue,
                            particleCount = 8,
                            durationMs = 1900,
                            minUpwardDistance = 80f,
                            maxUpwardDistance = 140f,
                            modifier = Modifier.size(120.dp)
                        )
                        Icon(Icons.Default.Favorite, null, tint = heartColor.copy(alpha = 0.90f), modifier = Modifier.size(64.dp))
                    }
                }
                IndicatorType.FavoriteOff -> Icon(Icons.Default.HeartBroken, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(60.dp))
            }
        }
    }
}
