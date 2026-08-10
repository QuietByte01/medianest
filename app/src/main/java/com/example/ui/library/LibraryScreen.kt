@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.FileStatSnapshot
import com.example.data.db.FormatStat
import com.example.data.db.MediaCategory
import com.example.data.model.MediaItem
import com.example.player.ExoPlayerManager
import com.example.ui.analytics.AnalyticsScreen
import com.example.ui.components.dismissKeyboardOnOutsideTap
import com.example.ui.library.audio.AudioTab
import com.example.ui.library.image.ImagesTab
import com.example.ui.library.video.VideosTab

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
    gridSizeLevel: Int = 1,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
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
    initialVideoFolder: String? = null,
    initialImageFolder: String? = null,
    onOpenQuickView: (MediaItem, List<MediaItem>) -> Unit,
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
    var showBatchInfoModal by remember { mutableStateOf(false) }
    var showMoveModal by remember { mutableStateOf(false) }
    var showCopyModal by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dismissKeyboardOnOutsideTap()
    ) {
        LibraryAmbientBackground(
            isDashboardTab = isDashboardTab,
            isImagesTab = isImagesTab,
            isVideosTab = isVideosTab,
            isAudioTab = isAudioTab,
            currentPlayingTrack = currentPlayingTrack,
            imagesList = imagesList,
            videosList = videosList,
            audioList = audioList
        )

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                LibraryTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    isDashboardTab = isDashboardTab,
                    isImagesTab = isImagesTab,
                    isVideosTab = isVideosTab,
                    isAudioTab = isAudioTab,
                    onOpenSettings = onOpenSettings
                )
            },
            bottomBar = {
                LibraryBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    enableAnalyticsTab = enableAnalyticsTab,
                    isAudioTab = isAudioTab,
                    playerState = playerState,
                    exoPlayerManager = exoPlayerManager,
                    onOpenAudioPlayer = onOpenAudioPlayer
                )
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
                        gridSizeLevel = gridSizeLevel,
                        cornerRadiusDp = cornerRadiusDp,
                        roundedCornersEnabled = roundedCornersEnabled,
                        isLoading = isLoading,
                        onCreateCollection = onCreateImageCollection,
                        onUpdateCollection = onUpdateImageCollection,
                        onDeleteCollection = onDeleteImageCollection,
                        onImageClick = { item, currentList ->
                            if (isSelectionMode) {
                                val uriStr = item.uri.toString()
                                selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                            } else {
                                onOpenQuickView(item, currentList)
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
                        gridSizeLevel = gridSizeLevel,
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
                        onClearSelection = { selectedUris = emptySet() },
                        initialFolder = initialVideoFolder
                    )

                    isAudioTab -> AudioTab(
                        audioList = filteredAudio,
                        playlists = audioPlaylists,
                        selectedUris = selectedUris,
                        isSelectionMode = isSelectionMode,
                        gridSizeLevel = gridSizeLevel,
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

                LibraryBatchActionBar(
                    isSelectionMode = isSelectionMode,
                    selectedUris = selectedUris,
                    currentTabItems = currentTabItems,
                    isVideosTab = isVideosTab,
                    onSelectAll = {
                        val allUris = currentTabItems.map { it.uri.toString() }.toSet()
                        selectedUris = if (selectedUris.size == allUris.size && allUris.isNotEmpty()) {
                            emptySet()
                        } else {
                            allUris
                        }
                    },
                    onShowBatchInfo = { showBatchInfoModal = true },
                    onDeleteSelected = { showDeleteSelectedModal = true },
                    onMoveSelected = { showMoveModal = true },
                    onCopySelected = { showCopyModal = true },
                    onAddToCategory = { showAddVideosToCategoryModal = true },
                    onClearSelection = { selectedUris = emptySet() },
                    context = context
                )
            }
        }

        if (showCreateCategoryModal) {
            CreateCategoryDialog(
                isVideosTab = isVideosTab,
                onDismiss = { showCreateCategoryModal = false },
                onCreateCategory = onCreateCategory
            )
        }

        if (showDeleteSelectedModal) {
            DeleteSelectedDialog(
                selectedUris = selectedUris,
                onDismiss = { showDeleteSelectedModal = false },
                onConfirm = { selectedUris = emptySet() },
                context = context
            )
        }

        if (showBatchInfoModal) {
            BatchInfoDialog(
                selectedUris = selectedUris,
                currentTabItems = currentTabItems,
                onDismiss = { showBatchInfoModal = false }
            )
        }

        if (showMoveModal) {
            BatchMoveDialog(
                selectedUris = selectedUris,
                currentTabItems = currentTabItems,
                onDismiss = { showMoveModal = false },
                onComplete = { selectedUris = emptySet() },
                coroutineScope = coroutineScope
            )
        }

        if (showCopyModal) {
            BatchCopyDialog(
                selectedUris = selectedUris,
                currentTabItems = currentTabItems,
                onDismiss = { showCopyModal = false },
                onComplete = { selectedUris = emptySet() },
                coroutineScope = coroutineScope
            )
        }
    }
}
