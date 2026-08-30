package com.medianest.ui.audioplayer

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.MediaNestApp
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.*
import com.medianest.data.repository.NetworkRepository
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.components.extractBaseHueFromArt
import com.medianest.ui.components.debug.AudioDebugOverlay
import com.medianest.ui.library.audio.AudioMetadataEditDialog
import com.medianest.ui.theme.LocalDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Main Audio Playback Screen.
 * Handles lyrics synchronization, favorites, and playback control layouts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    playerManager: ExoPlayerManager,
    networkRepository: NetworkRepository,
    onClose: () -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenFolder: (String, String?) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    allAudioItems: List<MediaItem> = emptyList(),
    showHidden: Boolean = false,
    hiddenFolders: Set<String> = emptySet()
) {
    val playerState by playerManager.playerState.collectAsState()
    val currentItem = playerState.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.AUDIO }

    val settingsManager = MediaNestApp.instance.settingsManager
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    val showAudioVisualizer by settingsManager.showAudioVisualizer.collectAsState(initial = true)
    val showAudioDebug by settingsManager.showAudioDebugInfo.collectAsState(initial = false)

    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = LocalDarkTheme.current

    val baseArtSize = if (isTablet && !isLandscape) (screenWidthDp * 0.75f).coerceAtMost(620f).dp else if (isTablet) (screenWidthDp * 0.55f).coerceAtMost(520f).dp else (screenWidthDp - 80f).dp

    val albumArtSize by animateDpAsState(
        targetValue = baseArtSize,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "albumArtSize"
    )

    var lyricsLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var rawLyricsText by remember { mutableStateOf<String?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showManualLyricsDialog by remember { mutableStateOf(false) }
    var manualLyricsInput by remember { mutableStateOf("") }

    var showMetadataModal by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDspSheet by remember { mutableStateOf(false) }
    var showAlbumSongsSheet by remember { mutableStateOf(false) }
    var showAlbumSongsInPanel by remember { mutableStateOf(false) }
    var showQueueInPortraitBox by remember { mutableStateOf(false) }
    var showAddAlbumToPlaylistDialog by remember { mutableStateOf(false) }
    var isVisualizerFullscreen by remember { mutableStateOf(false) }
    var showArtistInfoPanel by remember { mutableStateOf(false) }
    var showSidePanelLandscape by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var displayedArtistName by remember { mutableStateOf<String?>(null) }
    var artistNavigationStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var browsingAlbumName by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val db = MediaNestApp.instance.database
    
    val artistMetadataRepo = remember { MediaNestApp.instance.artistMetadataRepository }
    var artistInfo by remember { mutableStateOf<ArtistInfo?>(null) }
    
    var libraryAudioList by remember { mutableStateOf(allAudioItems) }
    var isScanningLibrary by remember { mutableStateOf(false) }

    LaunchedEffect(allAudioItems) {
        if (allAudioItems.isNotEmpty()) {
            libraryAudioList = allAudioItems
        } else {
            isScanningLibrary = true
            val items = withContext(Dispatchers.IO) {
                val mediaRepo = MediaStoreRepository(context)
                mediaRepo.getAudio()
            }
            libraryAudioList = items
            isScanningLibrary = false
        }
    }

    LaunchedEffect(currentItem?.artist) {
        if (!showArtistInfoPanel) {
            displayedArtistName = currentItem?.artist ?: "Unknown Artist"
            artistNavigationStack = emptyList()
        }
    }

    LaunchedEffect(showArtistInfoPanel) {
        if (!showArtistInfoPanel) {
            displayedArtistName = currentItem?.artist ?: "Unknown Artist"
            artistNavigationStack = emptyList()
            browsingAlbumName = null
        }
    }

    LaunchedEffect(displayedArtistName, libraryAudioList, showArtistInfoPanel) {
        if (!showArtistInfoPanel) return@LaunchedEffect
        val artistName = displayedArtistName ?: currentItem?.artist ?: "Unknown Artist"
        val computedInfo = withContext(Dispatchers.IO) {
            val info = artistMetadataRepo.getArtistInfo(artistName)
            
            // 1. Find real albums in library
            val artistSongs = libraryAudioList.filter { it.artist.equals(artistName, ignoreCase = true) }
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
            val playbackStates = if (uris.isNotEmpty()) db.playbackStateDao().getPlaybackStatesForUris(uris) else emptyList<com.medianest.data.db.PlaybackState>()
            
            val totalPlays = playbackStates.sumOf { it.playCount }
            val topPlayedUri = playbackStates.maxByOrNull { it.playCount }?.mediaUri
            val topPlayedSong = artistSongs.find { it.uri.toString() == topPlayedUri }?.title
            val firstDiscovered = artistSongs.minOfOrNull { it.dateAdded } ?: 0L
            
            val pStats = PersonalArtistStats(
                totalPlays = totalPlays,
                topPlayedSong = topPlayedSong,
                firstDiscovered = firstDiscovered
            )

            info.copy(
                localAlbums = realLocalAlbums,
                personalStats = pStats
            )
        }
        artistInfo = computedInfo
    }

    val audioPlaylists by db.categoryDao().getCategoriesByType("AUDIO").collectAsState(initial = emptyList())

    // Reactive Favorite Status
    val isFavoriteFromDb by remember(currentItem, audioPlaylists) {
        if (currentItem == null) kotlinx.coroutines.flow.flowOf(false)
        else db.categoryDao().getAllCrossRefs()
            .map { refs -> 
                val favoritesCat = audioPlaylists.find { it.name.equals("Favorites", ignoreCase = true) }
                favoritesCat != null && refs.any { it.categoryId == favoritesCat.id && it.mediaUri == currentItem.uri.toString() }
            }
    }.collectAsState(initial = false)

    var optimisticFavorite by remember(currentItem?.uri) { mutableStateOf<Boolean?>(null) }
    val isFavorite = optimisticFavorite ?: isFavoriteFromDb

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var isSeeking by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(0f) }

    val albumSongs = remember(playerState.queue, currentItem) {
        if (currentItem == null) emptyList()
        else {
            playerState.queue.filter {
                it.album == currentItem.album || (currentItem.album == null && it.artist == currentItem.artist)
            }.ifEmpty { listOf(currentItem) }
        }
    }

    val toggleFavoriteLambda = {
        val item = currentItem
        if (item != null) {
            val targetState = !isFavorite
            optimisticFavorite = targetState
            scope.launch(Dispatchers.IO) {
                try {
                    var favCat = db.categoryDao().getCategoryByNameAndType("Favorites", "AUDIO")
                    if (favCat == null) {
                        val newId = db.categoryDao().insertCategory(
                            MediaCategory(name = "Favorites", type = "AUDIO")
                        )
                        favCat = MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                    }
                    
                    if (!targetState) {
                        db.categoryDao().removeMediaFromCategory(favCat.id, item.uri.toString())
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        db.categoryDao().insertCategoryCrossRef(
                            CategoryMediaCrossRef(categoryId = favCat.id, mediaUri = item.uri.toString())
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        optimisticFavorite = null
                    }
                }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            browsingAlbumName != null -> browsingAlbumName = null
            artistNavigationStack.isNotEmpty() -> {
                displayedArtistName = artistNavigationStack.last()
                artistNavigationStack = artistNavigationStack.dropLast(1)
            }
            showArtistInfoPanel -> showArtistInfoPanel = false
            showDetailsSheet -> showDetailsSheet = false
            showMetadataModal -> showMetadataModal = false
            showAddAlbumToPlaylistDialog -> showAddAlbumToPlaylistDialog = false
            showAlbumSongsSheet -> showAlbumSongsSheet = false
            showDspSheet -> showDspSheet = false
            showLyricsView -> showLyricsView = false
            showQueueInPortraitBox -> showQueueInPortraitBox = false
            showAlbumSongsInPanel -> showAlbumSongsInPanel = false
            showSidePanelLandscape -> showSidePanelLandscape = false
            else -> onClose()
        }
    }

    LaunchedEffect(currentItem) {
        if (currentItem != null) {
            isLoadingLyrics = true
            val (savedManual, lyrics) = withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("manual_lyrics", Context.MODE_PRIVATE)
                val manual = prefs.getString(currentItem.uri.toString(), null)
                if (!manual.isNullOrBlank()) {
                    manual to networkRepository.parseLrcLyrics(manual)
                } else {
                    val lrc = networkRepository.fetchSyncedLyrics(
                        title = currentItem.title,
                        artist = currentItem.artist,
                        album = currentItem.album,
                        offlineMode = offlineMode
                    )
                    lrc to (if (lrc != null) networkRepository.parseLrcLyrics(lrc) else emptyList())
                }
            }
            rawLyricsText = savedManual
            lyricsLines = lyrics
            isLoadingLyrics = false
        }
    }

    val activeLyricIndex = remember(playerState.currentPositionMs, lyricsLines) {
        if (lyricsLines.isEmpty()) -1
        else {
            val idx = lyricsLines.indexOfLast { it.timeMs <= playerState.currentPositionMs }
            if (idx != -1) idx else 0
        }
    }

    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0 && activeLyricIndex < lyricsLines.size && showLyricsView) {
            listState.animateScrollToItem(activeLyricIndex)
        }
    }

    var albumArtHue by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(currentItem, playerState.queue) {
        val targetItem = currentItem ?: playerState.queue.firstOrNull()
        if (targetItem != null) {
            val artUri = targetItem.albumArtUri ?: targetItem.uri
            albumArtHue = withContext(Dispatchers.IO) {
                extractBaseHueFromArt(context, artUri)
            }
        } else {
            albumArtHue = null
        }
    }

    val targetTopColor = albumArtHue?.let { Color.hsv(it, 0.55f, 0.22f) } ?: Color(0xFF232830)
    val targetMidColor = albumArtHue?.let { Color.hsv((it + 15f) % 360f, 0.42f, 0.14f) } ?: Color(0xFF1A1D23)
    val targetBottomColor = Color(0xFF0D0F12)

    val topColor by animateColorAsState(targetValue = targetTopColor, animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "BgTop")
    val midColor by animateColorAsState(targetValue = targetMidColor, animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "BgMid")

    val playerBgBrush = remember(topColor, midColor) {
        Brush.verticalGradient(colors = listOf(topColor, midColor, targetBottomColor))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerBgBrush)
    ) {
        if (isLandscape) {
            LandscapePlayerLayout(
                playerState = playerState, currentItem = currentItem, playerManager = playerManager,
                showLyricsView = showLyricsView, showAudioVisualizer = showAudioVisualizer && !isVisualizerFullscreen,
                showAlbumSongsInPanel = showAlbumSongsInPanel, isLoadingLyrics = isLoadingLyrics,
                lyricsLines = lyricsLines, rawLyricsText = rawLyricsText, activeLyricIndex = activeLyricIndex,
                listState = listState, isFavorite = isFavorite, isTablet = isTablet,
                albumSongs = albumSongs, albumArtHue = albumArtHue,
                onSeekChange = { sliderPos = it; isSeeking = true },
                onSeekFinished = { isSeeking = false; playerManager.seekTo(sliderPos.toLong()) },
                onToggleShowLyrics = { showLyricsView = it },
                onToggleAlbumSongsPanel = { showAlbumSongsInPanel = !showAlbumSongsInPanel },
                onToggleFavorite = { toggleFavoriteLambda() },
                onOpenAddPlaylist = { showAddAlbumToPlaylistDialog = true },
                onEditLyrics = { manualLyricsInput = rawLyricsText ?: ""; showManualLyricsDialog = true },
                onToggleVisualizer = { scope.launch { settingsManager.setShowAudioVisualizer(!showAudioVisualizer) } },
                onFullscreenVisualizerClick = { isVisualizerFullscreen = true },
                onToggleArtistInfo = { showArtistInfoPanel = !showArtistInfoPanel },
                onOpenArtist = { artistName ->
                    displayedArtistName?.let { current ->
                        if (current != artistName) {
                            artistNavigationStack = artistNavigationStack + current
                            displayedArtistName = artistName
                        }
                    } ?: run {
                        displayedArtistName = artistName
                    }
                },
                showArtistInfo = showArtistInfoPanel,
                onPopularAlbumClick = { albumName ->
                    browsingAlbumName = albumName
                },
                onLocalAlbumClick = { albumName ->
                    onClose()
                    onOpenAlbum(albumName)
                },
                allAudioItems = libraryAudioList,
                showHidden = showHidden,
                hiddenFolders = hiddenFolders,
                artistInfo = artistInfo,
                browsingAlbumName = browsingAlbumName,
                onBackToArtist = { browsingAlbumName = null },
                isScanningLibrary = isScanningLibrary,
                showSidePanel = showSidePanelLandscape,
                onToggleSidePanel = { showSidePanelLandscape = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Close", tint = Color.White)
                    }
                    Text("MediaNest Music", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                        }
                        com.medianest.ui.components.GlassDropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            backgroundImage = currentItem?.albumArtUri ?: currentItem?.uri,
                            hue = albumArtHue
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                DropdownMenuItem(text = { Text("File Info", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.Info, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; showDetailsSheet = true })
                                DropdownMenuItem(text = { Text("Add to Playlist", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; showAddAlbumToPlaylistDialog = true })
                                DropdownMenuItem(text = { Text("Edit Tag & Metadata", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.EditNote, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; showMetadataModal = true })
                                if (currentItem != null) {
                                    DropdownMenuItem(text = { Text("Show Album", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.Album, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; onClose(); onOpenAlbum(currentItem.album ?: "Unknown Album") })
                                    val currentArtist = artistInfo
                                    if (currentArtist != null && !currentArtist.isPlaceholder) {
                                        DropdownMenuItem(text = { Text("Show Artist", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.Person, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; onClose(); onOpenArtist(currentItem.artist ?: "Unknown Artist") })
                                    }
                                    DropdownMenuItem(text = { Text("Show In Folder", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.Folder, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; onClose(); onOpenFolder(currentItem.relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: (currentItem.bucketName ?: "Music"), currentItem.uri.toString()) })
                                }
                                DropdownMenuItem(text = { Text("Native Audio DSP", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.Equalizer, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; showDspSheet = true })
                                DropdownMenuItem(text = { Text(if (showAudioVisualizer) "Hide Audio Visualizer" else "Show Audio Visualizer", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.GraphicEq, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; scope.launch { settingsManager.setShowAudioVisualizer(!showAudioVisualizer) } })
                                DropdownMenuItem(text = { Text("Settings", color = if (isDark) Color.White else Color.Black) }, leadingIcon = { Icon(Icons.Default.Settings, null, tint = if (isDark) Color.White else Color.Black) }, onClick = { showOverflowMenu = false; onClose(); onOpenSettings() })
                                if (currentItem != null) {
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteConfirmDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                PortraitPlayerLayout(
                    playerState = playerState, currentItem = currentItem, playerManager = playerManager,
                    albumArtSize = albumArtSize, isTablet = isTablet, showLyricsView = showLyricsView,
                    showAudioVisualizer = showAudioVisualizer && !isVisualizerFullscreen,
                    showQueueInPortraitBox = showQueueInPortraitBox,
                    isLoadingLyrics = isLoadingLyrics, lyricsLines = lyricsLines, rawLyricsText = rawLyricsText,
                    activeLyricIndex = activeLyricIndex, listState = listState, isFavorite = isFavorite,
                    albumArtHue = albumArtHue,
                    onSeekChange = { sliderPos = it; isSeeking = true },
                    onSeekFinished = { isSeeking = false; playerManager.seekTo(sliderPos.toLong()) },
                    onToggleShowLyrics = { showLyricsView = it },
                    onToggleQueueInPortraitBox = { showQueueInPortraitBox = !showQueueInPortraitBox },
                    onToggleFavorite = { toggleFavoriteLambda() },
                    onOpenAddPlaylist = { showAddAlbumToPlaylistDialog = true },
                    onEditLyrics = { manualLyricsInput = rawLyricsText ?: ""; showManualLyricsDialog = true },
                    onFullscreenVisualizerClick = { isVisualizerFullscreen = true },
                    onToggleArtistInfo = { showArtistInfoPanel = !showArtistInfoPanel },
                    onOpenArtist = { artistName ->
                        displayedArtistName?.let { current ->
                            if (current != artistName) {
                                artistNavigationStack = artistNavigationStack + current
                                displayedArtistName = artistName
                            }
                        } ?: run {
                            displayedArtistName = artistName
                        }
                    },
                    showArtistInfo = showArtistInfoPanel,
                    artistInfo = artistInfo,
                    onPopularAlbumClick = { albumName ->
                        browsingAlbumName = albumName
                    },
                    onLocalAlbumClick = { albumName ->
                        onClose()
                        onOpenAlbum(albumName)
                    },
                    browsingAlbumName = browsingAlbumName,
                    allAudioItems = libraryAudioList,
                    onBackToArtist = { browsingAlbumName = null },
                    isScanningLibrary = isScanningLibrary,
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
            }
        }

        if (showMetadataModal && currentItem != null) AudioMetadataEditDialog(item = currentItem, onDismiss = { showMetadataModal = false })
        if (showAlbumSongsSheet && currentItem != null) AlbumSongsSheet(albumName = currentItem.album ?: "Unknown Album", albumSongs = albumSongs, playerState = playerState, playerManager = playerManager, onDismiss = { showAlbumSongsSheet = false }, currentItem = currentItem)
        
        if (showAddAlbumToPlaylistDialog && currentItem != null) AddAlbumToPlaylistDialog(albumSongs = albumSongs, audioPlaylists = audioPlaylists, db = db, playerManager = playerManager, context = context, scope = scope, onDismiss = { showAddAlbumToPlaylistDialog = false })
        if (showDetailsSheet && currentItem != null) MediaInfoBottomSheet(item = currentItem, onDismiss = { showDetailsSheet = false })
        if (showManualLyricsDialog) ManualLyricsDialog(currentItem = currentItem, rawLyricsText = rawLyricsText, initialInput = manualLyricsInput, networkRepository = networkRepository, context = context, onLyricsUpdated = { raw, lines -> rawLyricsText = raw; lyricsLines = lines; if (raw != null) showLyricsView = true }, onDismiss = { showManualLyricsDialog = false })
        if (showDspSheet) com.medianest.ui.components.NativeAudioDspSheet(playerState = playerState, playerManager = playerManager, onDismiss = { showDspSheet = false }, backgroundImage = currentItem?.albumArtUri ?: currentItem?.uri)
        if (isVisualizerFullscreen) FullscreenVisualizerDialog(isPlaying = playerState.isPlaying, audioSessionId = playerState.audioSessionId, albumArtHue = albumArtHue, currentItem = currentItem, onDismiss = { isVisualizerFullscreen = false })

        if (showDeleteConfirmDialog && currentItem != null) {
            val targetItem = currentItem
            com.medianest.ui.components.DeleteConfirmationDialog(
                title = "Delete Audio File",
                itemTitle = targetItem.title,
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirm = {
                    showDeleteConfirmDialog = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, targetItem.uri)
                            withContext(Dispatchers.Main) {
                                if (playerState.queue.size > 1) {
                                    playerManager.next()
                                } else {
                                    playerManager.stop()
                                    onClose()
                                }
                                Toast.makeText(context, "Deleted '${targetItem.title}'", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )
        }
        
        if (showAudioDebug) {
            AudioDebugOverlay(
                playerState = playerState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 80.dp, start = 20.dp)
            )
        }
    }
}
