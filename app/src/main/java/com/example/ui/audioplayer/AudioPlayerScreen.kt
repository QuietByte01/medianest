@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.audioplayer

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.MediaNestApp
import com.example.data.model.LyricLine
import com.example.data.repository.NetworkRepository
import com.example.player.ExoPlayerManager
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.ThinSeekBar
import com.example.ui.components.formatDuration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

    val isVisualizerActive = showAudioVisualizer && playerState.isPlaying

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val baseArtSize = if (isTablet) (screenWidthDp * 0.61f).coerceAtMost(540f).dp else (screenHeightDp * 0.41f).dp
    val compactArtSize = if (isTablet) (baseArtSize * 0.90f) else baseArtSize * 0.85f

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

    BackHandler(enabled = true) {
        when {
            showDetailsSheet -> showDetailsSheet = false
            showMetadataModal -> showMetadataModal = false
            showAddAlbumToPlaylistDialog -> showAddAlbumToPlaylistDialog = false
            showAlbumSongsSheet -> showAlbumSongsSheet = false
            showLyricsView -> showLyricsView = false
            else -> onClose()
        }
    }

    // Fetch lyrics when song changes
    LaunchedEffect(currentItem) {
        if (currentItem != null) {
            isLoadingLyrics = true
            val prefs = context.getSharedPreferences("manual_lyrics", android.content.Context.MODE_PRIVATE)
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
            albumArtHue = com.example.ui.components.extractBaseHueFromArt(context, artUri)
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
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isDark = com.example.ui.theme.LocalDarkTheme.current
        val menuBg = if (isDark) Color(0xBF0F1015) else Color(0xA6FFFFFF)

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
                            text = { Text("Equalizer", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showOverflowMenu = false
                                try {
                                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerState.audioSessionId)
                                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "System equalizer not found", android.widget.Toast.LENGTH_SHORT).show()
                                }
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

            if (isLandscape) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Artwork & Track / Artist Info below
                    Column(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (showLyricsView) {
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(22.dp),
                                backgroundColor = Color(0x1F24293A),
                                borderColor = Color(0x2EFFFFFF)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Synced Lyrics",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF5F5F5),
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isLoadingLyrics) {
                                        CircularProgressIndicator(color = Color(0xFFF5F5F5))
                                    } else if (lyricsLines.isNotEmpty()) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            itemsIndexed(lyricsLines) { index, line ->
                                                val isActive = index == activeLyricIndex
                                                Text(
                                                    text = line.text,
                                                    fontSize = if (isActive) 18.sp else 14.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isActive) Color(0xFFF5F5F5) else Color.White.copy(alpha = 0.45f),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .clickable { playerManager.seekTo(line.timeMs) }
                                                )
                                            }
                                        }
                                    } else if (rawLyricsText != null) {
                                        Text(
                                            text = rawLyricsText!!,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            text = "No lyrics available",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clickable { showLyricsView = false }
                                        )
                                    }
                                }
                            }
                        } else if (showAudioVisualizer) {
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                backgroundColor = Color(0x12FFFFFF),
                                borderColor = Color.Transparent, // Removed sharp border
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
                                    .clickable { showLyricsView = true },
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

                    // Right Side: UP NEXT queue / Album Songs glass card & Player Controls
                    Column(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // UP NEXT / Album Songs standalone Glass Card
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

                                        val albumSongsList = remember(playerState.queue, currentItem) {
                                            if (currentItem == null) emptyList()
                                            else {
                                                playerState.queue.filter {
                                                    it.album == currentItem.album || (currentItem.album != null && currentItem.album == it.album) || (currentItem.album == null && it.artist == currentItem.artist)
                                                }.ifEmpty { listOf(currentItem) }
                                            }
                                        }

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

                        // Bottom area for actions and controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.35f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Action Row (Toggle Album Songs vs Up Next, Favorite, Add)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showAlbumSongsInPanel = !showAlbumSongsInPanel },
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
                                    onClick = {
                                        val item = currentItem
                                        if (item != null) {
                                            scope.launch {
                                                val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                                                var favCat = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                                if (favCat == null) {
                                                    val newId = db.categoryDao().insertCategory(
                                                        com.example.data.db.MediaCategory(name = "Favorites", type = "AUDIO")
                                                    )
                                                    favCat = com.example.data.db.MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                                                }
                                                db.categoryDao().insertCategoryCrossRefs(
                                                    listOf(com.example.data.db.CategoryMediaCrossRef(categoryId = favCat!!.id, mediaUri = item.uri.toString()))
                                                )
                                                isFavorite = !isFavorite
                                                android.widget.Toast.makeText(context, if (isFavorite) "Added to Favorites" else "Removed from Favorites", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
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
                                    onClick = { showAddAlbumToPlaylistDialog = true },
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
                            var isSeekingLs by remember { mutableStateOf(false) }
                            var sliderPosLs by remember { mutableFloatStateOf(0f) }
                            val currentPosMsLs = playerState.currentPositionMs
                            val durationMsLs = if (playerState.durationMs > 0) playerState.durationMs else currentItem?.durationMs ?: 0L
                            val effectiveSliderValLs = if (isSeekingLs) sliderPosLs else currentPosMsLs.toFloat()
                            val maxSliderValLs = durationMsLs.coerceAtLeast(1L).toFloat()

                            Column(modifier = Modifier.fillMaxWidth()) {
                                ThinSeekBar(
                                    value = effectiveSliderValLs.coerceIn(0f, maxSliderValLs),
                                    onValueChange = {
                                        isSeekingLs = true
                                        sliderPosLs = it
                                    },
                                    onValueChangeFinished = {
                                        isSeekingLs = false
                                        playerManager.seekTo(sliderPosLs.toLong())
                                    },
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

                            // Main Player Transport Row
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
            } else {
                // PORTRAIT LAYOUT
                Spacer(modifier = Modifier.height(2.dp))

                // Main Artwork OR Lyrics view toggle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyricsView) {
                        GlassSurface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = Color.Transparent,
                            borderColor = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Synced Lyrics",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF5F5F5),
                                        fontSize = 14.sp
                                    )
                                    IconButton(
                                        onClick = {
                                            manualLyricsInput = rawLyricsText ?: ""
                                            showManualLyricsDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Lyrics",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                if (isLoadingLyrics) {
                                    CircularProgressIndicator(color = Color(0xFFF5F5F5))
                                } else if (lyricsLines.isNotEmpty()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        itemsIndexed(lyricsLines) { index, line ->
                                            val isActive = index == activeLyricIndex
                                            Text(
                                                text = line.text,
                                                fontSize = if (isActive) 20.sp else 15.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isActive) Color(0xFFF5F5F5) else Color.White.copy(alpha = 0.45f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp)
                                                    .clickable { playerManager.seekTo(line.timeMs) }
                                            )
                                        }
                                    }
                                } else if (rawLyricsText != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = rawLyricsText!!,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = {
                                                manualLyricsInput = rawLyricsText ?: ""
                                                showManualLyricsDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Edit Lyrics")
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "No lyrics available",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        IconButton(
                                            onClick = {
                                                manualLyricsInput = rawLyricsText ?: ""
                                                showManualLyricsDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Lyrics", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
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
                                    val albumSongs = remember(playerState.queue, currentItem) {
                                        playerState.queue.filter {
                                            it.album == currentItem?.album || (currentItem?.album == null && it.artist == currentItem?.artist)
                                        }.ifEmpty { if (currentItem != null) listOf(currentItem) else emptyList() }
                                    }
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

                            // Title and Artist Info
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
                                borderColor = Color.Transparent, // Removed sharp border
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
                                        .padding(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Title and Artist Info BELOW visualizer (aligned with size)
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
                            /* Visualizer pattern removed to reclaim space */
                            Card(
                                modifier = Modifier
                                    .size(albumArtSize)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showLyricsView = true },
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

                            // Title and Artist Info BELOW album art (aligned with album art width)
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

                // Top action row (Album Songs, Favorite, Add Album to Playlist)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left button: Toggle album songs list in place of album art
                    IconButton(
                        onClick = { showAlbumSongsInPortraitBox = !showAlbumSongsInPortraitBox },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Album Songs",
                            tint = if (showAlbumSongsInPortraitBox) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Heart button: Add song to Favorites playlist
                    IconButton(
                        onClick = {
                            val item = currentItem
                            if (item != null) {
                                scope.launch {
                                    val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                                    var favCat = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                    if (favCat == null) {
                                        val newId = db.categoryDao().insertCategory(
                                            com.example.data.db.MediaCategory(name = "Favorites", type = "AUDIO")
                                        )
                                        favCat = com.example.data.db.MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                                    }
                                    db.categoryDao().insertCategoryCrossRefs(
                                        listOf(com.example.data.db.CategoryMediaCrossRef(categoryId = favCat!!.id, mediaUri = item.uri.toString()))
                                    )
                                    isFavorite = !isFavorite
                                    android.widget.Toast.makeText(context, if (isFavorite) "Added to Favorites" else "Removed from Favorites", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFF4081) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // '+' button: Add album to a playlist
                    IconButton(
                        onClick = { showAddAlbumToPlaylistDialog = true },
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
                var isSeeking by remember { mutableStateOf(false) }
                var sliderPos by remember { mutableFloatStateOf(0f) }
                val currentPosMs = playerState.currentPositionMs
                val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem?.durationMs ?: 0L
                val effectiveSliderVal = if (isSeeking) sliderPos else currentPosMs.toFloat()
                val maxSliderVal = durationMs.coerceAtLeast(1L).toFloat()

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    com.example.ui.components.ThinSeekBar(
                        value = effectiveSliderVal.coerceIn(0f, maxSliderVal),
                        onValueChange = {
                            isSeeking = true
                            sliderPos = it
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            playerManager.seekTo(sliderPos.toLong())
                        },
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

                // Main Player Transport (Shuffle, Prev, Play/Pause, Next, Repeat)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button
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

                    // Previous Button
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

                    // Play/Pause Button
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

                    // Next Button
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

                    // Repeat Button
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

        // Metadata Edit / Fetch Modal
        if (showMetadataModal && currentItem != null) {
            com.example.ui.library.AudioMetadataEditDialog(
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

        // Add Album/Track to Playlist Dialog (centered dialog with wrap content width & glass background)
        if (showAddAlbumToPlaylistDialog && currentItem != null) {
            var isCreatingPlaylist by remember { mutableStateOf(false) }
            var newPlaylistNameInput by remember { mutableStateOf("") }

            val albumSongs = remember(playerState.queue, currentItem) {
                playerState.queue.filter {
                    it.album == currentItem.album || (currentItem.album == null && it.artist == currentItem.artist)
                }.ifEmpty { listOf(currentItem) }
            }

            Dialog(
                onDismissRequest = { showAddAlbumToPlaylistDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = Color(0xDC0E111A),
                    borderColor = Color.White.copy(alpha = 0.22f),
                    enableBlur = true,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.88f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Top header: "< Add to"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showAddAlbumToPlaylistDialog = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add to",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Glassmorphic container
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x1AFFFFFF),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                // Row 1: Create playlist
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isCreatingPlaylist = !isCreatingPlaylist }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Create playlist",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                if (isCreatingPlaylist) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newPlaylistNameInput,
                                            onValueChange = { newPlaylistNameInput = it },
                                            placeholder = { Text("Playlist name", color = Color.White.copy(alpha = 0.5f)) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color.White,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black
                                            ),
                                            onClick = {
                                                if (newPlaylistNameInput.isNotBlank()) {
                                                    scope.launch {
                                                        val name = newPlaylistNameInput.trim()
                                                        val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                                                        var targetCat = categories.find { it.name.equals(name, ignoreCase = true) }
                                                        if (targetCat == null) {
                                                            val newId = db.categoryDao().insertCategory(
                                                                com.example.data.db.MediaCategory(name = name, type = "AUDIO")
                                                            )
                                                            targetCat = com.example.data.db.MediaCategory(id = newId, name = name, type = "AUDIO")
                                                        }
                                                        val refs = albumSongs.map {
                                                            com.example.data.db.CategoryMediaCrossRef(categoryId = targetCat!!.id, mediaUri = it.uri.toString())
                                                        }
                                                        db.categoryDao().insertCategoryCrossRefs(refs)
                                                        android.widget.Toast.makeText(context, "Added ${albumSongs.size} tracks to \"$name\"", android.widget.Toast.LENGTH_SHORT).show()
                                                        showAddAlbumToPlaylistDialog = false
                                                    }
                                                }
                                            }
                                        ) {
                                            Text("Save", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                )

                                // Row 2: Queue
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerManager.addToQueue(albumSongs)
                                            android.widget.Toast.makeText(context, "Added ${albumSongs.size} tracks to queue", android.widget.Toast.LENGTH_SHORT).show()
                                            showAddAlbumToPlaylistDialog = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistPlay,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Queue",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                )

                                // Row 3: Favourite tracks
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                                                var favCat = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                                if (favCat == null) {
                                                    val newId = db.categoryDao().insertCategory(
                                                        com.example.data.db.MediaCategory(name = "Favorites", type = "AUDIO")
                                                    )
                                                    favCat = com.example.data.db.MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                                                }
                                                val refs = albumSongs.map {
                                                    com.example.data.db.CategoryMediaCrossRef(categoryId = favCat!!.id, mediaUri = it.uri.toString())
                                                }
                                                db.categoryDao().insertCategoryCrossRefs(refs)
                                                android.widget.Toast.makeText(context, "Added ${albumSongs.size} tracks to Favourite tracks", android.widget.Toast.LENGTH_SHORT).show()
                                                showAddAlbumToPlaylistDialog = false
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.FavoriteBorder,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Favourite tracks",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                // List existing playlists
                                val userPlaylists = audioPlaylists.filter { !it.name.equals("Favorites", ignoreCase = true) }
                                if (userPlaylists.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        color = Color.White.copy(alpha = 0.12f)
                                    )
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp)
                                    ) {
                                        items(userPlaylists.size) { idx ->
                                            val playlist = userPlaylists[idx]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        scope.launch {
                                                            val refs = albumSongs.map {
                                                                com.example.data.db.CategoryMediaCrossRef(categoryId = playlist.id, mediaUri = it.uri.toString())
                                                            }
                                                            db.categoryDao().insertCategoryCrossRefs(refs)
                                                            android.widget.Toast.makeText(context, "Added ${albumSongs.size} tracks to \"${playlist.name}\"", android.widget.Toast.LENGTH_SHORT).show()
                                                            showAddAlbumToPlaylistDialog = false
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(40.dp),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(
                                                    text = playlist.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                            if (idx < userPlaylists.size - 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 14.dp),
                                                    color = Color.White.copy(alpha = 0.12f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDetailsSheet && currentItem != null) {
            MediaInfoBottomSheet(
                item = currentItem,
                onDismiss = { showDetailsSheet = false }
            )
        }

        if (showManualLyricsDialog) {
            AlertDialog(
                onDismissRequest = { showManualLyricsDialog = false },
                containerColor = Color(0xEB101114),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Manual Lyrics Entry", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Paste or type lyrics for '${currentItem?.title}'. You can enter plain text or synced LRC format (e.g. [00:12.30] Lyrics).",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.70f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualLyricsInput,
                            onValueChange = { manualLyricsInput = it },
                            label = { Text("Lyrics", color = Color.White.copy(alpha = 0.8f)) },
                            placeholder = { Text("Type or paste lyrics here...", color = Color.White.copy(alpha = 0.4f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp),
                            maxLines = 15
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        onClick = {
                            val item = currentItem
                            if (item != null) {
                                val textToSave = manualLyricsInput.trim()
                                val prefs = context.getSharedPreferences("manual_lyrics", android.content.Context.MODE_PRIVATE)
                                if (textToSave.isNotBlank()) {
                                    prefs.edit().putString(item.uri.toString(), textToSave).apply()
                                    rawLyricsText = textToSave
                                    lyricsLines = networkRepository.parseLrcLyrics(textToSave)
                                    showLyricsView = true
                                    android.widget.Toast.makeText(context, "Lyrics saved", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    prefs.edit().remove(item.uri.toString()).apply()
                                    rawLyricsText = null
                                    lyricsLines = emptyList()
                                    android.widget.Toast.makeText(context, "Lyrics cleared", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            showManualLyricsDialog = false
                        }
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        if (rawLyricsText != null) {
                            TextButton(
                                onClick = {
                                    val item = currentItem
                                    if (item != null) {
                                        val prefs = context.getSharedPreferences("manual_lyrics", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().remove(item.uri.toString()).apply()
                                        rawLyricsText = null
                                        lyricsLines = emptyList()
                                        android.widget.Toast.makeText(context, "Lyrics removed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    showManualLyricsDialog = false
                                }
                            ) {
                                Text("Clear", color = Color(0xFFFF6B6B))
                            }
                        }
                        TextButton(onClick = { showManualLyricsDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun AudioReactiveVisualizerPattern(
    isPlaying: Boolean,
    currentPosMs: Long,
    trackSeed: Long = 0L,
    albumArtUri: android.net.Uri? = null,
    audioSessionId: Int = 0,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    barColor: Color = Color.White.copy(alpha = 0.90f)
) {
    var styleIndex by remember { mutableIntStateOf(0) }
    val styles = com.example.ui.components.VisualizerStyle.entries.toTypedArray()
    val currentStyle = styles[styleIndex.coerceIn(0, styles.size - 1)]

    com.example.ui.components.AudioVisualizer(
        isPlaying = isPlaying,
        audioSessionId = audioSessionId,
        currentPosMs = currentPosMs,
        trackSeed = trackSeed,
        albumArtUri = albumArtUri,
        numBands = barCount,
        style = currentStyle,
        primaryColor = Color.White,
        secondaryColor = Color(0xFFD0D0D0),
        accentColor = Color(0xFFE8E8E8),
        modifier = modifier.clickable {
            styleIndex = (styleIndex + 1) % styles.size
        }
    )
}
