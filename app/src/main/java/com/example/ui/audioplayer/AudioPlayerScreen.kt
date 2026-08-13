package com.example.ui.audioplayer

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MediaNestApp
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.MediaCategory
import com.example.data.model.LyricLine
import com.example.data.repository.NetworkRepository
import com.example.player.ExoPlayerManager
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.extractBaseHueFromArt
import com.example.ui.library.audio.AudioMetadataEditDialog
import com.example.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    playerManager: ExoPlayerManager,
    networkRepository: NetworkRepository,
    onClose: () -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val playerState by playerManager.playerState.collectAsState()
    val currentItem = playerState.currentItem

    val settingsManager = MediaNestApp.instance.settingsManager
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    val showAudioVisualizer by settingsManager.showAudioVisualizer.collectAsState(initial = true)

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val baseArtSize = if (isTablet) (screenWidthDp * 0.61f).coerceAtMost(540f).dp else (screenHeightDp * 0.41f).dp

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
    var showAlbumSongsInPortraitBox by remember { mutableStateOf(false) }
    var showAddAlbumToPlaylistDialog by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val db = MediaNestApp.instance.database
    val audioPlaylists by db.categoryDao().getCategoriesByType("AUDIO").collectAsState(initial = emptyList())

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Lifted Seek State
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(0f) }

    // Lifted Album Songs filtering
    val albumSongs = remember(playerState.queue, currentItem) {
        if (currentItem == null) emptyList()
        else {
            playerState.queue.filter {
                it.album == currentItem.album || (currentItem.album == null && it.artist == currentItem.artist)
            }.ifEmpty { listOf(currentItem) }
        }
    }

    BackHandler(enabled = true) {
        when {
            showDetailsSheet -> showDetailsSheet = false
            showMetadataModal -> showMetadataModal = false
            showAddAlbumToPlaylistDialog -> showAddAlbumToPlaylistDialog = false
            showAlbumSongsSheet -> showAlbumSongsSheet = false
            showDspSheet -> showDspSheet = false
            showLyricsView -> showLyricsView = false
            else -> onClose()
        }
    }

    // Fetch lyrics when song changes
    LaunchedEffect(currentItem) {
        if (currentItem != null) {
            isLoadingLyrics = true
            val prefs = context.getSharedPreferences("manual_lyrics", Context.MODE_PRIVATE)
            val savedManual = prefs.getString(currentItem.uri.toString(), null)
            if (!savedManual.isNullOrBlank()) {
                rawLyricsText = savedManual
                lyricsLines = networkRepository.parseLrcLyrics(savedManual)
                isLoadingLyrics = false
            } else {
                val lrc = networkRepository.fetchSyncedLyrics(
                    title = currentItem.title,
                    artist = currentItem.artist,
                    album = currentItem.album,
                    offlineMode = offlineMode
                )
                rawLyricsText = lrc
                if (lrc != null) {
                    lyricsLines = networkRepository.parseLrcLyrics(lrc)
                } else {
                    lyricsLines = emptyList()
                }
                isLoadingLyrics = false
            }
        }
    }

    // Auto-scroll lyrics to active line
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

    // Dynamic background gradient matching album art
    var albumArtHue by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(currentItem, playerState.queue) {
        val targetItem = currentItem ?: playerState.queue.firstOrNull()
        if (targetItem != null) {
            val artUri = targetItem.albumArtUri ?: targetItem.uri
            albumArtHue = extractBaseHueFromArt(context, artUri)
        } else {
            albumArtHue = null
        }
    }

    val playerBgBrush = remember(albumArtHue) {
        val hue = albumArtHue
        if (hue != null) {
            val topColor = Color.hsv(hue, 0.55f, 0.22f)
            val midColor = Color.hsv((hue + 15f) % 360f, 0.42f, 0.14f)
            val bottomColor = Color(0xFF0D0F12)
            Brush.verticalGradient(colors = listOf(topColor, midColor, bottomColor))
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF232830),
                    Color(0xFF121418)
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerBgBrush)
    ) {
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isDark = LocalDarkTheme.current
        val menuBg = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isLandscape) 12.dp else 20.dp, vertical = if (isLandscape) 8.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = "MediaNest Music",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Box {
                    Surface(
                        onClick = { showOverflowMenu = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = menuBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .border(1.dp, if (isDark) Color(0x28FFFFFF) else Color(0x33000000), RoundedCornerShape(16.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                showAddAlbumToPlaylistDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File Info", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                showDetailsSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Tag & Metadata", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                showMetadataModal = true
                            }
                        )
                        if (currentItem != null) {
                            DropdownMenuItem(
                                text = { Text("Show Album", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Album, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                                onClick = {
                                    showOverflowMenu = false
                                    onClose()
                                    onOpenAlbum(currentItem.album ?: "Unknown Album")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Show Artist", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                                onClick = {
                                    showOverflowMenu = false
                                    onClose()
                                    onOpenArtist(currentItem.artist ?: "Unknown Artist")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Show In Folder", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                                onClick = {
                                    showOverflowMenu = false
                                    onClose()
                                    onOpenFolder(currentItem.bucketName ?: currentItem.relativePath ?: "Music")
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Native Audio DSP", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                showDspSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                onClose()
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showAudioVisualizer) "Hide Audio Visualizer" else "Show Audio Visualizer", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                scope.launch { settingsManager.setShowAudioVisualizer(!showAudioVisualizer) }
                            }
                        )
                    }
                }
            }

            val toggleFavoriteLambda = {
                val item = currentItem
                if (item != null) {
                    scope.launch {
                        val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                        var favCat = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                        if (favCat == null) {
                            val newId = db.categoryDao().insertCategory(
                                MediaCategory(name = "Favorites", type = "AUDIO")
                            )
                            favCat = MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                        }
                        db.categoryDao().insertCategoryCrossRefs(
                            listOf(CategoryMediaCrossRef(categoryId = favCat!!.id, mediaUri = item.uri.toString()))
                        )
                        isFavorite = !isFavorite
                        Toast.makeText(context, if (isFavorite) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (isLandscape) {
                Spacer(modifier = Modifier.height(6.dp))
                LandscapePlayerLayout(
                    playerState = playerState,
                    currentItem = currentItem,
                    playerManager = playerManager,
                    showLyricsView = showLyricsView,
                    showAudioVisualizer = showAudioVisualizer,
                    showAlbumSongsInPanel = showAlbumSongsInPanel,
                    isLoadingLyrics = isLoadingLyrics,
                    lyricsLines = lyricsLines,
                    rawLyricsText = rawLyricsText,
                    activeLyricIndex = activeLyricIndex,
                    listState = listState,
                    isFavorite = isFavorite,
                    albumSongs = albumSongs,
                    isSeeking = isSeeking,
                    sliderPos = sliderPos,
                    onSeekChange = {
                        sliderPos = it
                        isSeeking = true
                    },
                    onSeekFinished = {
                        isSeeking = false
                        playerManager.seekTo(sliderPos.toLong())
                    },
                    onToggleShowLyrics = { showLyricsView = it },
                    onToggleAlbumSongsPanel = { showAlbumSongsInPanel = !showAlbumSongsInPanel },
                    onToggleFavorite = { toggleFavoriteLambda() },
                    onOpenAddPlaylist = { showAddAlbumToPlaylistDialog = true },
                    onEditLyrics = {
                        manualLyricsInput = rawLyricsText ?: ""
                        showManualLyricsDialog = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                PortraitPlayerLayout(
                    playerState = playerState,
                    currentItem = currentItem,
                    playerManager = playerManager,
                    albumArtSize = albumArtSize,
                    isTablet = isTablet,
                    showLyricsView = showLyricsView,
                    showAudioVisualizer = showAudioVisualizer,
                    showAlbumSongsInPortraitBox = showAlbumSongsInPortraitBox,
                    isLoadingLyrics = isLoadingLyrics,
                    lyricsLines = lyricsLines,
                    rawLyricsText = rawLyricsText,
                    activeLyricIndex = activeLyricIndex,
                    listState = listState,
                    isFavorite = isFavorite,
                    albumSongs = albumSongs,
                    isSeeking = isSeeking,
                    sliderPos = sliderPos,
                    onSeekChange = {
                        sliderPos = it
                        isSeeking = true
                    },
                    onSeekFinished = {
                        isSeeking = false
                        playerManager.seekTo(sliderPos.toLong())
                    },
                    onToggleShowLyrics = { showLyricsView = it },
                    onToggleAlbumSongsPortraitBox = { showAlbumSongsInPortraitBox = !showAlbumSongsInPortraitBox },
                    onToggleFavorite = { toggleFavoriteLambda() },
                    onOpenAddPlaylist = { showAddAlbumToPlaylistDialog = true },
                    onEditLyrics = {
                        manualLyricsInput = rawLyricsText ?: ""
                        showManualLyricsDialog = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Metadata Edit / Fetch Modal
        if (showMetadataModal && currentItem != null) {
            AudioMetadataEditDialog(
                item = currentItem,
                onDismiss = { showMetadataModal = false }
            )
        }

        // Album Songs Sheet
        if (showAlbumSongsSheet && currentItem != null) {
            val albumSongs = remember(playerState.queue, currentItem) {
                playerState.queue.filter { it.album == currentItem.album || (currentItem.album == null && it.artist == currentItem.artist) }
                    .ifEmpty { listOf(currentItem) }
            }
            AdaptiveBottomSheet(
                onDismissRequest = { showAlbumSongsSheet = false },
                containerColor = Color(0xEB101114),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Album: ${currentItem.album ?: "Unknown Album"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(albumSongs) { idx, track ->
                            val isPlayingThis = track.uri == currentItem.uri
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPlayingThis) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isPlayingThis) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        val queueIndex = playerState.queue.indexOf(track)
                                        if (queueIndex != -1) {
                                            playerManager.playMediaList(playerState.queue, queueIndex)
                                        }
                                        showAlbumSongsSheet = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 14.sp,
                                    color = if (isPlayingThis) Color.White else Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist ?: "Unknown Artist",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isPlayingThis) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Playing",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Add Album/Track to Playlist Dialog
        if (showAddAlbumToPlaylistDialog && currentItem != null) {
            val albumSongs = remember(playerState.queue, currentItem) {
                playerState.queue.filter {
                    it.album == currentItem.album || (currentItem.album == null && it.artist == currentItem.artist)
                }.ifEmpty { listOf(currentItem) }
            }

            AddAlbumToPlaylistDialog(
                albumSongs = albumSongs,
                audioPlaylists = audioPlaylists,
                db = db,
                playerManager = playerManager,
                context = context,
                scope = scope,
                onDismiss = { showAddAlbumToPlaylistDialog = false }
            )
        }

        if (showDetailsSheet && currentItem != null) {
            MediaInfoBottomSheet(
                item = currentItem,
                onDismiss = { showDetailsSheet = false }
            )
        }

        if (showManualLyricsDialog) {
            ManualLyricsDialog(
                currentItem = currentItem,
                rawLyricsText = rawLyricsText,
                initialInput = manualLyricsInput,
                networkRepository = networkRepository,
                context = context,
                onLyricsUpdated = { raw, lines ->
                    rawLyricsText = raw
                    lyricsLines = lines
                    if (raw != null) showLyricsView = true
                },
                onDismiss = { showManualLyricsDialog = false }
            )
        }

        if (showDspSheet) {
            com.example.ui.components.NativeAudioDspSheet(
                playerState = playerState,
                playerManager = playerManager,
                onDismiss = { showDspSheet = false }
            )
        }
    }
}
