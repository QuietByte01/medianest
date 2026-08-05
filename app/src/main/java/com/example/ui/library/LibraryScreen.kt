@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.CategoryIconUtils
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.FileStatSnapshot
import com.example.data.db.FormatStat
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.player.ExoPlayerManager
import com.example.ui.analytics.AnalyticsScreen
import com.example.ui.components.GlassSurface
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.dismissKeyboardOnOutsideTap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>,
    videoCategories: List<MediaCategory>,
    audioPlaylists: List<MediaCategory>,
    imageCollections: List<MediaCategory> = emptyList(),
    categoryCrossRefs: List<CategoryMediaCrossRef> = emptyList(),
    gridGapDp: Int,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    largeImageGrid: Boolean = false,
    enableAnalyticsTab: Boolean = true,
    isLoading: Boolean = false,
    analyticsSnapshot: FileStatSnapshot? = null,
    analyticsFormatStats: List<FormatStat> = emptyList(),
    isAnalyticsRefreshing: Boolean = false,
    onRefreshAnalytics: () -> Unit = {},
    exoPlayerManager: ExoPlayerManager,
    initialTab: Int = 0,
    audioSubTab: Int = 0,
    audioAlbum: String? = null,
    audioArtist: String? = null,
    audioFolder: String? = null,
    onOpenQuickView: (MediaItem) -> Unit,
    onOpenVideoPlayer: (MediaItem) -> Unit,
    onOpenAudioPlayer: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateCategory: (String, String, String?) -> Unit,
    onCreateImageCollection: (String, List<String>, String?) -> Unit = { _, _, _ -> },
    onUpdateImageCollection: (Long, String, List<String>, String?) -> Unit = { _, _, _, _ -> },
    onDeleteImageCollection: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var currentTab by remember(initialTab) { mutableIntStateOf(initialTab) } // 0: Dashboard (if enabled), 1: Images, 2: Videos, 3: Audio

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf<MediaCategory?>(null) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedUris.isNotEmpty()

    var showCreateCategoryModal by remember { mutableStateOf(false) }
    var showAddVideosToCategoryModal by remember { mutableStateOf(false) }
    var showDeleteSelectedModal by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val playerState by exoPlayerManager.playerState.collectAsState()

    // Map tab indices depending on whether Dashboard is enabled
    val isDashboardTab = enableAnalyticsTab && currentTab == 0
    val isImagesTab = if (enableAnalyticsTab) currentTab == 1 else currentTab == 0
    val isVideosTab = if (enableAnalyticsTab) currentTab == 2 else currentTab == 1
    val isAudioTab = if (enableAnalyticsTab) currentTab == 3 else currentTab == 2

    // Filter list based on search query
    val filteredImages = remember(imagesList, searchQuery) {
        if (searchQuery.isEmpty()) imagesList
        else imagesList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val filteredVideos = remember(videosList, searchQuery, selectedCategory) {
        var res = videosList
        if (searchQuery.isNotEmpty()) {
            res = res.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        res
    }

    val filteredAudio = remember(audioList, searchQuery) {
        if (searchQuery.isEmpty()) audioList
        else audioList.filter { it.title.contains(searchQuery, ignoreCase = true) || (it.artist?.contains(searchQuery, ignoreCase = true) == true) }
    }

    val currentTabItems = when {
        isImagesTab -> filteredImages
        isVideosTab -> filteredVideos
        isAudioTab -> filteredAudio
        else -> emptyList()
    }

    // Handle back button presses inside Library screen
    BackHandler(enabled = showDeleteSelectedModal || showCreateCategoryModal || isSelectionMode || isSearchActive || selectedCategory != null || currentTab != 0) {
        when {
            showDeleteSelectedModal -> showDeleteSelectedModal = false
            showCreateCategoryModal -> showCreateCategoryModal = false
            isSelectionMode -> selectedUris = emptySet()
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            selectedCategory != null -> selectedCategory = null
            currentTab != 0 -> currentTab = 0
        }
    }

    val currentPlayingTrack = playerState.currentItem
    val albumArtUri = currentPlayingTrack?.albumArtUri

    var activeHue by remember { mutableStateOf<Float?>(null) }

    val activeMediaTarget = remember(currentTab, currentPlayingTrack, imagesList, videosList, audioList) {
        when (currentTab) {
            0 -> null // Dashboard
            1 -> imagesList.firstOrNull()
            2 -> currentPlayingTrack?.takeIf { it.type == MediaType.VIDEO } ?: videosList.firstOrNull()
            3 -> currentPlayingTrack ?: audioList.firstOrNull()
            else -> currentPlayingTrack ?: audioList.firstOrNull()
        }
    }

    LaunchedEffect(activeMediaTarget?.id, activeMediaTarget?.uri) {
        if (activeMediaTarget != null) {
            val uri = activeMediaTarget.albumArtUri ?: activeMediaTarget.uri
            activeHue = com.example.ui.components.extractBaseHueFromArt(context, uri)
        } else {
            activeHue = null
        }
    }

    val ambientTopColor = remember(activeHue, currentTab) {
        if (currentTab == 0) Color(0xFF1E222A)
        else if (activeHue != null) Color.hsv(activeHue!!, 0.65f, 0.40f, 0.35f)
        else Color(0x354A3B2C)
    }

    val ambientBottomColor = remember(activeHue, currentTab) {
        if (currentTab == 0) Color(0xFF121419)
        else if (activeHue != null) Color.hsv((activeHue!! + 25f) % 360f, 0.55f, 0.28f, 0.30f)
        else Color(0x301E2838)
    }

    val ambientImageUri = remember(currentTab, activeMediaTarget) {
        if (currentTab == 0) null
        else activeMediaTarget?.albumArtUri ?: activeMediaTarget?.uri
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
    ) {
        // Dynamic Ambient Album Art Layer
        if (ambientImageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ambientImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .graphicsLayer { alpha = 0.35f }
            )
        }

        // Ambient Background Radial Glows (Top-Left & Bottom-Right)
        Box(
            modifier = Modifier
                .size(450.dp)
                .offset(x = (-120).dp, y = (-100).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientTopColor,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(480.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 120.dp, y = 120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientBottomColor,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.dismissKeyboardOnOutsideTap(),
            topBar = {
                val configuration = LocalConfiguration.current
                val isTablet = configuration.screenWidthDp >= 600

                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        if (isTablet) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when {
                                        isDashboardTab -> "Dashboard"
                                        isImagesTab -> "Image Gallery"
                                        isVideosTab -> "Video Library"
                                        isAudioTab -> "Music Library"
                                        else -> "Dashboard"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Expanded Search Bar for Tablets
                                    Surface(
                                        modifier = Modifier
                                            .width(260.dp)
                                            .height(40.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0x221C1F2B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2EFFFFFF))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = Color(0xFF8E95A5),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC0C5D0)),
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = Color.White,
                                                    fontSize = 13.5.sp
                                                ),
                                                modifier = Modifier.weight(1f),
                                                decorationBox = { innerTextField ->
                                                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                                        if (searchQuery.isEmpty()) {
                                                            Text(
                                                                text = when {
                                                                    isAudioTab -> "Search folders & tracks..."
                                                                    isVideosTab -> "Search videos..."
                                                                    isImagesTab -> "Search photos..."
                                                                    else -> "Search..."
                                                                },
                                                                fontSize = 13.sp,
                                                                color = Color(0xFF8E95A5)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                            if (searchQuery.isNotEmpty()) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = Color(0xFF8E95A5),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { searchQuery = "" }
                                                )
                                            }
                                        }
                                    }

                                    // Settings Gear Icon
                                    Surface(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable { onOpenSettings() },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0x221C1F2B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2EFFFFFF))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (isSearchActive) {
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0x221C1F2B),
                                borderColor = Color(0x2EFFFFFF)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF8E95A5),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC0C5D0)),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 13.5.sp
                                        ),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = "Search filename...",
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF8E95A5)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF8E95A5),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = when {
                                    isDashboardTab -> "Dashboard"
                                    isImagesTab -> "MediaNest Gallery"
                                    isVideosTab -> "Video Library"
                                    isAudioTab -> "Audio Player"
                                    else -> "Dashboard"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        if (!isTablet) {
                            IconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                )
            },
        bottomBar = {
            Column {
                // Persistent Mini Player above bottom bar only in Audio tab
                if (isAudioTab && playerState.currentItem != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniPlayerBar(
                            playerState = playerState,
                            onPlayPauseToggle = { exoPlayerManager.togglePlayPause() },
                            onNext = { exoPlayerManager.next() },
                            onClickExpand = { onOpenAudioPlayer(currentTab) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                val navItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = Color(0xFFF1F5F9),
                    selectedTextColor = Color(0xFFF1F5F9),
                    unselectedIconColor = Color(0xFF9EA3B0),
                    unselectedTextColor = Color(0xFF9EA3B0)
                )

                GlassSurface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    backgroundColor = Color(0x6612151F),
                    borderColor = Color(0x28FFFFFF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent
                    ) {
                        if (enableAnalyticsTab) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(Icons.Default.BarChart, contentDescription = "Dashboard") },
                                label = { Text("Dashboard") },
                                colors = navItemColors
                            )
                        }
                        NavigationBarItem(
                            selected = if (enableAnalyticsTab) currentTab == 1 else currentTab == 0,
                            onClick = { currentTab = if (enableAnalyticsTab) 1 else 0 },
                            icon = { Icon(Icons.Default.Image, contentDescription = "Images") },
                            label = { Text("Images") },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = if (enableAnalyticsTab) currentTab == 2 else currentTab == 1,
                            onClick = { currentTab = if (enableAnalyticsTab) 2 else 1 },
                            icon = { Icon(Icons.Default.Movie, contentDescription = "Videos") },
                            label = { Text("Videos") },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = if (enableAnalyticsTab) currentTab == 3 else currentTab == 2,
                            onClick = { currentTab = if (enableAnalyticsTab) 3 else 2 },
                            icon = { Icon(Icons.Default.Audiotrack, contentDescription = "Audio") },
                            label = { Text("Audio") },
                            colors = navItemColors
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isDashboardTab -> AnalyticsScreen(
                    snapshot = analyticsSnapshot,
                    formatStats = analyticsFormatStats,
                    isRefreshing = isAnalyticsRefreshing,
                    onRefresh = onRefreshAnalytics,
                    onSelectCategoryFilter = { _ -> },
                    onSelectFormatFilter = { _ -> },
                    imagesList = imagesList,
                    videosList = videosList,
                    audioList = audioList,
                    onOpenQuickView = onOpenQuickView,
                    onOpenVideoPlayer = onOpenVideoPlayer,
                    onOpenAudioPlayer = { item ->
                        val idx = audioList.indexOf(item)
                        if (idx != -1) exoPlayerManager.playMediaList(audioList, idx)
                        onOpenAudioPlayer(currentTab)
                    },
                    onOpenSettings = onOpenSettings
                )

                isImagesTab -> ImagesTab(
                    imagesList = filteredImages,
                    imageCollections = imageCollections,
                    categoryCrossRefs = categoryCrossRefs,
                    selectedUris = selectedUris,
                    isSelectionMode = isSelectionMode,
                    gridGapDp = gridGapDp,
                    cornerRadiusDp = cornerRadiusDp,
                    roundedCornersEnabled = roundedCornersEnabled,
                    largeImageGrid = largeImageGrid,
                    isLoading = isLoading,
                    onCreateCollection = onCreateImageCollection,
                    onUpdateCollection = onUpdateImageCollection,
                    onDeleteCollection = onDeleteImageCollection,
                    onImageClick = { item ->
                        if (isSelectionMode) {
                            val uriStr = item.uri.toString()
                            selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                        } else {
                            onOpenQuickView(item)
                        }
                    },
                    onImageLongClick = { item ->
                        selectedUris = selectedUris + item.uri.toString()
                    }
                )

                isVideosTab -> VideosTab(
                    videosList = filteredVideos,
                    categories = videoCategories,
                    selectedCategory = selectedCategory,
                    selectedUris = selectedUris,
                    isSelectionMode = isSelectionMode,
                    gridGapDp = gridGapDp,
                    cornerRadiusDp = cornerRadiusDp,
                    roundedCornersEnabled = roundedCornersEnabled,
                    isLoading = isLoading,
                    onCategorySelect = { selectedCategory = it },
                    onCreateCategoryClick = { showCreateCategoryModal = true },
                    onVideoClick = { item ->
                        if (isSelectionMode) {
                            val uriStr = item.uri.toString()
                            selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                        } else {
                            onOpenVideoPlayer(item)
                        }
                    },
                    onVideoLongClick = { item ->
                        selectedUris = selectedUris + item.uri.toString()
                    },
                    showAddVideosDialog = showAddVideosToCategoryModal,
                    onDismissAddVideosDialog = { showAddVideosToCategoryModal = false },
                    onClearSelection = { selectedUris = emptySet() }
                )

                isAudioTab -> AudioTab(
                    audioList = filteredAudio,
                    playlists = audioPlaylists,
                    selectedUris = selectedUris,
                    isSelectionMode = isSelectionMode,
                    isLoading = isLoading,
                    onSongClick = { item ->
                        val idx = audioList.indexOf(item)
                        if (idx != -1) exoPlayerManager.playMediaList(audioList, idx)
                    },
                    onSongLongClick = { item ->
                        selectedUris = selectedUris + item.uri.toString()
                    },
                    onCreatePlaylistClick = { showCreateCategoryModal = true },
                    initialSubTab = audioSubTab,
                    initialAlbum = audioAlbum,
                    initialArtist = audioArtist,
                    initialFolder = audioFolder
                )
            }

            // Multi-Select Batch Action Bar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x331C1F2B),
                    borderColor = Color(0x38FFFFFF)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedUris.size} selected",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val allUris = currentTabItems.map { it.uri.toString() }.toSet()
                                selectedUris = if (selectedUris.size == allUris.size && allUris.isNotEmpty()) {
                                    emptySet()
                                } else {
                                    allUris
                                }
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = Color.White)
                            }

                            if (isVideosTab) {
                                IconButton(onClick = {
                                    showAddVideosToCategoryModal = true
                                }) {
                                    Icon(Icons.Default.LibraryAdd, contentDescription = "Add to Category", tint = Color.White)
                                }
                            }

                            IconButton(onClick = {
                                val urisToShare = selectedUris.map { Uri.parse(it) }
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }

                            IconButton(onClick = {
                                showDeleteSelectedModal = true
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFFF5252))
                            }

                            IconButton(onClick = {
                                selectedUris = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

        // Modal dialog for Creating Category / Playlist
        if (showCreateCategoryModal) {
            var selectedIconName by remember { mutableStateOf("Category") }
            AlertDialog(
                onDismissRequest = { showCreateCategoryModal = false },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(if (isVideosTab) "Create Video Category" else "Create Audio Playlist")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Category Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Choose Icon",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            val iconsToDisplay = if (isVideosTab) CategoryIconUtils.AVAILABLE_ICONS else CategoryIconUtils.AUDIO_ICONS
                            items(iconsToDisplay) { (iconKey, vector) ->
                                val isSelected = selectedIconName == iconKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedIconName = iconKey },
                                    label = { Text(iconKey) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = vector,
                                            contentDescription = iconKey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            val typeStr = if (isVideosTab) "VIDEO" else "AUDIO"
                            onCreateCategory(newCategoryName, typeStr, selectedIconName)
                            newCategoryName = ""
                            showCreateCategoryModal = false
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateCategoryModal = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Modal dialog for Deleting Selected Items
        if (showDeleteSelectedModal) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedModal = false },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Delete Selected Items?") },
                text = { Text("Are you sure you want to delete ${selectedUris.size} selected item(s)?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteSelectedModal = false
                        selectedUris.forEach { uriStr ->
                            try {
                                com.example.util.FolderHiddenUtils.deleteMediaUri(context, Uri.parse(uriStr))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        selectedUris = emptySet()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedModal = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
