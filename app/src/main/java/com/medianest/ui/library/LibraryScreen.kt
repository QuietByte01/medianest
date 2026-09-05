@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.medianest.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.FileStatSnapshot
import com.medianest.data.db.FormatStat
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.MediaViewModel
import com.medianest.ui.dashboard.AnalyticsScreen
import com.medianest.ui.components.backdropSource
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.ui.components.dismissKeyboardOnOutsideTap
import com.medianest.ui.components.media.SlideshowViewer
import com.medianest.ui.library.audio.AudioTab
import com.medianest.ui.library.image.ImagesTab
import com.medianest.ui.library.video.VideosTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>,
    videoCategories: List<MediaCategory>,
    audioPlaylists: List<MediaCategory>,
    imageCollections: List<MediaCategory> = emptyList(),
    gridGapDp: Int,
    gridSizeLevel: Int = 1,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    enableAnalyticsTab: Boolean = true,
    isLoading: Boolean = false,
    isScanningHidden: Boolean = false,
    analyticsSnapshot: FileStatSnapshot? = null,
    analyticsFormatStats: List<FormatStat> = emptyList(),
    isAnalyticsRefreshing: Boolean = false,
    onRefreshAnalytics: () -> Unit = {},
    onRescanHiddenMedia: () -> Unit = {},
    exoPlayerManager: ExoPlayerManager,
    initialTab: Int = 0,
    audioSubTab: Int = 0,
    audioAlbum: String? = null,
    audioArtist: String? = null,
    audioFolder: String? = null,
    initialVideoFolder: String? = null,
    initialImageFolder: String? = null,
    targetMediaUri: String? = null,
    onOpenQuickView: (MediaItem, List<MediaItem>, Boolean) -> Unit,
    onOpenVideoPlayer: (MediaItem, List<MediaItem>?, String?) -> Unit,
    onOpenAudioPlayer: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateCategory: (String, String, String?) -> Unit,
    onCreateImageCollection: (String, List<String>, String?) -> Unit = { _, _, _ -> },
    onUpdateImageCollection: (Long, String, List<String>, String?) -> Unit = { _, _, _, _ -> },
    onDeleteImageCollection: (Long) -> Unit = {},
    isSettingsOpen: Boolean = false,
    viewModel: MediaViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(imagesList, videosList, audioList) {
        viewModel.setMediaLists(imagesList, videosList, audioList)
    }

    val settingsManager = com.medianest.MediaNestApp.instance.settingsManager
    val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val hiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())
    val autoPlayVideoPreviews by settingsManager.autoPlayVideoPreviews.collectAsState(initial = true)
    val autoPlayGifPreviews by settingsManager.autoPlayGifPreviews.collectAsState(initial = true)

        val totalTabs = if (enableAnalyticsTab) 4 else 3
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialTab.coerceIn(0, totalTabs - 1)) { totalTabs }

        LaunchedEffect(initialTab) {
            if (initialTab in 0 until totalTabs && pagerState.currentPage != initialTab) {
                pagerState.scrollToPage(initialTab)
            }
        }

        val currentTab = pagerState.targetPage

        val searchQuery by viewModel.searchQuery.collectAsState()
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
        var showBatchMoveToFilterModal by remember { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()
        val playerState by exoPlayerManager.playerState.collectAsState()

        // Map tab indices depending on whether Dashboard is enabled
        val isDashboardTab = enableAnalyticsTab && currentTab == 0
        val isImagesTab = if (enableAnalyticsTab) currentTab == 1 else currentTab == 0
        val isVideosTab = if (enableAnalyticsTab) currentTab == 2 else currentTab == 1
        val isAudioTab = if (enableAnalyticsTab) currentTab == 3 else currentTab == 2

        // Filter list based on search query (offloaded to ViewModel)
        val filteredImages by viewModel.filteredImagesList.collectAsState()
        val filteredVideos by viewModel.filteredVideosList.collectAsState()
        val filteredAudio by viewModel.filteredAudioList.collectAsState()

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
                    viewModel.updateSearchQuery("")
                }
                selectedCategory != null -> selectedCategory = null
                currentTab != 0 -> coroutineScope.launch { pagerState.animateScrollToPage(0) }
            }
        }

        val currentPlayingTrack = remember(playerState.currentItem) { playerState.currentItem }
        var libraryInfoItem by remember { mutableStateOf<MediaItem?>(null) }
        var activeImageItem by remember { mutableStateOf<MediaItem?>(null) }
        val libraryBackdropState = rememberBackdropBlurState()

        CompositionLocalProvider(
            com.medianest.ui.components.LocalBackdropState provides libraryBackdropState
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .dismissKeyboardOnOutsideTap()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .backdropSource(
                            state = libraryBackdropState,
                            backgroundColor = Color(0xFF0C0E14)
                        )
                ) {
                    LibraryAmbientBackground(
                        isDashboardTab = isDashboardTab,
                        isImagesTab = isImagesTab,
                        isVideosTab = isVideosTab,
                        isAudioTab = isAudioTab,
                        currentPlayingTrack = currentPlayingTrack,
                        imagesList = imagesList,
                        videosList = videosList,
                        audioList = audioList,
                        activeImageItem = activeImageItem
                    )
                }

                Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0), // Eliminate automatic padding
                topBar = {
                    LibraryTopBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
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
                    if (isSelectionMode) {
                        LibraryBatchActionBar(
                            isSelectionMode = isSelectionMode,
                            selectedUris = selectedUris,
                            currentTabItems = currentTabItems,
                            isVideosTab = isVideosTab,
                            isImagesTab = isImagesTab,
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
                            onMoveToFilter = { showBatchMoveToFilterModal = true },
                            onStartSlideshow = {
                                val itemsToPlay = currentTabItems.filter { selectedUris.contains(it.uri.toString()) }.ifEmpty { imagesList.filter { selectedUris.contains(it.uri.toString()) } }
                                if (itemsToPlay.isNotEmpty()) {
                                    selectedUris = emptySet()
                                    onOpenQuickView(itemsToPlay.first(), itemsToPlay, true)
                                }
                            },
                            onClearSelection = { selectedUris = emptySet() },
                            context = context
                        )
                    } else {
                        LibraryBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { targetTab ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetTab)
                                }
                            },
                            enableAnalyticsTab = enableAnalyticsTab,
                            isAudioTab = isAudioTab,
                            playerState = playerState,
                            exoPlayerManager = exoPlayerManager,
                            onOpenAudioPlayer = onOpenAudioPlayer
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        beyondViewportPageCount = 3,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val isPageImages = (enableAnalyticsTab && page == 1) || (!enableAnalyticsTab && page == 0)
                        val isPageVideos = (enableAnalyticsTab && page == 2) || (!enableAnalyticsTab && page == 1)
                        val shouldAutoPlayVideos = autoPlayVideoPreviews && isVideosTab && isPageVideos
                        val shouldAutoPlayGifs = autoPlayGifPreviews && isImagesTab && isPageImages

                        CompositionLocalProvider(
                            com.medianest.ui.components.LocalAutoPlayVideoPreviews provides shouldAutoPlayVideos,
                            com.medianest.ui.components.LocalAutoPlayGifPreviews provides shouldAutoPlayGifs
                        ) {
                            when (page) {
                                0 -> {
                                if (enableAnalyticsTab) {
                                    AnalyticsScreen(
                                        snapshot = analyticsSnapshot,
                                        formatStats = analyticsFormatStats,
                                        isRefreshing = isAnalyticsRefreshing,
                                        onRefresh = onRefreshAnalytics,
                                        onSelectCategoryFilter = { _ -> },
                                        onSelectFormatFilter = { _ -> },
                                        imagesList = imagesList,
                                        videosList = videosList,
                                        audioList = audioList,
                                        onOpenQuickView = { item, list -> onOpenQuickView(item, list, false) },
                                        onOpenVideoPlayer = onOpenVideoPlayer,
                                        onOpenAudioPlayer = { item ->
                                            val idx = audioList.indexOf(item)
                                            if (idx != -1) exoPlayerManager.playMediaList(audioList, idx)
                                            onOpenAudioPlayer(currentTab)
                                        },
                                        onOpenSettings = onOpenSettings,
                                        searchQuery = searchQuery
                                    )
                                } else {
                                    ImagesTab(
                                        imagesList = imagesList,
                                        selectedUris = selectedUris,
                                        isSelectionMode = isSelectionMode,
                                        gridGapDp = gridGapDp,
                                        gridSizeLevel = gridSizeLevel,
                                        cornerRadiusDp = cornerRadiusDp,
                                        roundedCornersEnabled = roundedCornersEnabled,
                                        isLoading = isLoading,
                                        isScanningHidden = isScanningHidden,
                                        initialFolder = initialImageFolder,
                                        initialTargetImageUri = targetMediaUri,
                                        onImageClick = { item, currentList, startSlideshow ->
                                            activeImageItem = item
                                            if (isSelectionMode) {
                                                val uriStr = item.uri.toString()
                                                selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                                            } else {
                                                onOpenQuickView(item, currentList, startSlideshow)
                                            }
                                        },
                                        onImageLongClick = { item ->
                                            selectedUris = selectedUris + item.uri.toString()
                                        },
                                        onBackToDashboard = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                        onShowInfo = { libraryInfoItem = it },
                                        onActiveImageChange = { activeImageItem = it },
                                        viewModel = viewModel
                                    )
                                }
                            }

                            1 -> {
                                if (enableAnalyticsTab) {
                                    ImagesTab(
                                        imagesList = imagesList,
                                        selectedUris = selectedUris,
                                        isSelectionMode = isSelectionMode,
                                        gridGapDp = gridGapDp,
                                        gridSizeLevel = gridSizeLevel,
                                        cornerRadiusDp = cornerRadiusDp,
                                        roundedCornersEnabled = roundedCornersEnabled,
                                        isLoading = isLoading,
                                        isScanningHidden = isScanningHidden,
                                        initialFolder = initialImageFolder,
                                        initialTargetImageUri = targetMediaUri,
                                        onImageClick = { item, currentList, startSlideshow ->
                                            activeImageItem = item
                                            if (isSelectionMode) {
                                                val uriStr = item.uri.toString()
                                                selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                                            } else {
                                                onOpenQuickView(item, currentList, startSlideshow)
                                            }
                                        },
                                        onImageLongClick = { item ->
                                            selectedUris = selectedUris + item.uri.toString()
                                        },
                                        onBackToDashboard = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                        onRescanHiddenMedia = onRescanHiddenMedia,
                                        onShowInfo = { libraryInfoItem = it },
                                        onActiveImageChange = { activeImageItem = it },
                                        viewModel = viewModel
                                    )
                                } else {
                                    VideosTab(
                                        videosList = videosList,
                                        categories = videoCategories,
                                        selectedCategory = selectedCategory,
                                        selectedUris = selectedUris,
                                        isSelectionMode = isSelectionMode,
                                        gridGapDp = gridGapDp,
                                        gridSizeLevel = gridSizeLevel,
                                        cornerRadiusDp = cornerRadiusDp,
                                        roundedCornersEnabled = roundedCornersEnabled,
                                        isLoading = isLoading,
                                        isScanningHidden = isScanningHidden,
                                        onCategorySelect = { selectedCategory = it },
                                        onCreateCategoryClick = { showCreateCategoryModal = true },
                                        onVideoClick = { item, currentList, contextTitle ->
                                            if (isSelectionMode) {
                                                val uriStr = item.uri.toString()
                                                selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                                            } else {
                                                onOpenVideoPlayer(item, currentList, contextTitle)
                                            }
                                        },
                                        onVideoLongClick = { item ->
                                            selectedUris = selectedUris + item.uri.toString()
                                        },
                                        showAddVideosDialog = showAddVideosToCategoryModal,
                                        onDismissAddVideosDialog = { showAddVideosToCategoryModal = false },
                                        onClearSelection = { selectedUris = emptySet() },
                                        initialFolder = initialVideoFolder,
                                        initialTargetVideoUri = targetMediaUri,
                                        onBackToDashboard = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                        onRescanHiddenMedia = onRescanHiddenMedia,
                                        onShowInfo = { libraryInfoItem = it },
                                        viewModel = viewModel
                                    )
                                }
                            }

                            2 -> {
                                if (enableAnalyticsTab) {
                                    VideosTab(
                                        videosList = videosList,
                                        categories = videoCategories,
                                        selectedCategory = selectedCategory,
                                        selectedUris = selectedUris,
                                        isSelectionMode = isSelectionMode,
                                        gridGapDp = gridGapDp,
                                        gridSizeLevel = gridSizeLevel,
                                        cornerRadiusDp = cornerRadiusDp,
                                        roundedCornersEnabled = roundedCornersEnabled,
                                        isLoading = isLoading,
                                        isScanningHidden = isScanningHidden,
                                        onCategorySelect = { selectedCategory = it },
                                        onCreateCategoryClick = { showCreateCategoryModal = true },
                                        onVideoClick = { item, currentList, contextTitle ->
                                            if (isSelectionMode) {
                                                val uriStr = item.uri.toString()
                                                selectedUris = if (selectedUris.contains(uriStr)) selectedUris - uriStr else selectedUris + uriStr
                                            } else {
                                                onOpenVideoPlayer(item, currentList, contextTitle)
                                            }
                                        },
                                        onVideoLongClick = { item ->
                                            selectedUris = selectedUris + item.uri.toString()
                                        },
                                        showAddVideosDialog = showAddVideosToCategoryModal,
                                        onDismissAddVideosDialog = { showAddVideosToCategoryModal = false },
                                        onClearSelection = { selectedUris = emptySet() },
                                        initialFolder = initialVideoFolder,
                                        initialTargetVideoUri = targetMediaUri,
                                        onBackToDashboard = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                        onRescanHiddenMedia = onRescanHiddenMedia,
                                        onShowInfo = { libraryInfoItem = it },
                                        viewModel = viewModel
                                    )
                                } else {
                                    AudioTab(
                                        audioList = audioList,
                                        playlists = audioPlaylists,
                                        selectedUris = selectedUris,
                                        isSelectionMode = isSelectionMode,
                                        gridSizeLevel = gridSizeLevel,
                                        isLoading = isLoading,
                                        isScanningHidden = isScanningHidden,
                                        onSongClick = { list, idx ->
                                            exoPlayerManager.playMediaList(list, idx)
                                        },
                                        onSongLongClick = { item ->
                                            selectedUris = selectedUris + item.uri.toString()
                                        },
                                        onCreatePlaylistClick = { showCreateCategoryModal = true },
                                        initialSubTab = audioSubTab,
                                        initialAlbum = audioAlbum,
                                        initialArtist = audioArtist,
                                        initialFolder = audioFolder,
                                        initialTargetSongUri = targetMediaUri,
                                        onBackToDashboard = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                        onRescanHiddenMedia = onRescanHiddenMedia,
                                        onShowInfo = { libraryInfoItem = it },
                                        viewModel = viewModel
                                    )
                                }
                            }

                            3 -> {
                                AudioTab(
                                    audioList = audioList,
                                    playlists = audioPlaylists,
                                    selectedUris = selectedUris,
                                    isSelectionMode = isSelectionMode,
                                    gridSizeLevel = gridSizeLevel,
                                    isLoading = isLoading,
                                    isScanningHidden = isScanningHidden,
                                    onSongClick = { list, idx ->
                                        exoPlayerManager.playMediaList(list, idx)
                                    },
                                    onSongLongClick = { item ->
                                        selectedUris = selectedUris + item.uri.toString()
                                    },
                                    onCreatePlaylistClick = { showCreateCategoryModal = true },
                                    initialSubTab = audioSubTab,
                                    initialAlbum = audioAlbum,
                                    initialArtist = audioArtist,
                                    initialFolder = audioFolder,
                                    initialTargetSongUri = targetMediaUri,
                                    onBackToDashboard = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                    onRescanHiddenMedia = onRescanHiddenMedia,
                                    onShowInfo = { libraryInfoItem = it },
                                    viewModel = viewModel
                                )
                            }
                        }
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

        if (showBatchMoveToFilterModal) {
            com.medianest.ui.library.image.BatchMoveToFilterDialog(
                selectedUris = selectedUris,
                onDismiss = {
                    showBatchMoveToFilterModal = false
                    selectedUris = emptySet()
                }
            )
        }

        if (libraryInfoItem != null) {
            com.medianest.ui.components.MediaInfoBottomSheet(
                item = libraryInfoItem,
                onDismiss = { libraryInfoItem = null }
            )
        }
        }

        val showLibraryDebug by settingsManager.showLibraryDebugInfo.collectAsState(initial = false)
        if (showLibraryDebug && !isSettingsOpen) {
            val activeTabName = when {
                isDashboardTab -> "DASHBOARD"
                isImagesTab -> "IMAGES"
                isVideosTab -> "VIDEOS"
                isAudioTab -> "AUDIO"
                else -> "LIBRARY"
            }
            val filteredCount = when {
                isImagesTab -> filteredImages.size
                isVideosTab -> filteredVideos.size
                isAudioTab -> filteredAudio.size
                else -> imagesList.size + videosList.size + audioList.size
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, end = 12.dp),
                contentAlignment = androidx.compose.ui.Alignment.TopEnd
            ) {
                com.medianest.ui.components.debug.LibraryDebugOverlay(
                    activeTabName = activeTabName,
                    filteredItemsCount = filteredCount,
                    isSearchActive = isSearchActive
                )
            }
        }
    }
}
}
}
