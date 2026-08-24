package com.medianest.ui.library.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.medianest.MediaNestApp
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.MediaType
import com.medianest.data.db.SelectiveHiddenFolder
import com.medianest.data.model.MediaItem
import com.medianest.data.settings.SettingsManager
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MediaGridItem
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.components.MediaLoadingAnimation
import com.medianest.ui.components.RenameFileDialog
import com.medianest.ui.components.SortRow
import com.medianest.ui.components.translucentScrollBarGrid
import com.medianest.ui.components.translucentScrollBarStaggeredGrid
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.CategoryIconUtils
import com.medianest.util.FolderHiddenUtils
import com.medianest.util.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF9EA3B0), fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosTab(
    videosList: List<MediaItem>,
    categories: List<MediaCategory>,
    selectedCategory: MediaCategory?,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridGapDp: Int,
    gridSizeLevel: Int = 1,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    isLoading: Boolean = false,
    isScanningHidden: Boolean = false,
    onCategorySelect: (MediaCategory?) -> Unit,
    onCreateCategoryClick: () -> Unit,
    onVideoClick: (MediaItem, List<MediaItem>?, String?) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    showAddVideosDialog: Boolean = false,
    onDismissAddVideosDialog: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    initialFolder: String? = null,
    initialTargetVideoUri: String? = null,
    onBackToDashboard: () -> Unit = {},
    viewModel: com.medianest.ui.MediaViewModel = viewModel()
) {
    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(currentContext) }

    val persistedSortField by settingsManager.videoSortField.collectAsState(initial = "Date")
    val persistedSortAscending by settingsManager.videoSortAscending.collectAsState(initial = false)
    
    val sortField = persistedSortField
    val isAscending = persistedSortAscending

    // Sync sort state to ViewModel
    LaunchedEffect(sortField, isAscending) {
        viewModel.updateVideoSort(sortField, isAscending)
    }

    var activeFilterTab by remember(initialFolder) { mutableStateOf(if (initialFolder != null) "FOLDERS" else "ALL") }
    
    // Sync filter tab to ViewModel
    LaunchedEffect(activeFilterTab) {
        viewModel.updateVideoFilter(activeFilterTab)
    }

    var isFolderViewActive by remember(initialFolder) { mutableStateOf(initialFolder != null) }
    var selectedFolder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var targetVideoUri by remember(initialTargetVideoUri) { mutableStateOf(initialTargetVideoUri) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var videoToDelete by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(initialTargetVideoUri) {
        if (!initialTargetVideoUri.isNullOrBlank()) {
            targetVideoUri = initialTargetVideoUri
        }
    }

    var selectedSeriesName by remember { mutableStateOf<String?>(null) }
    var selectedSeasonName by remember { mutableStateOf<String?>(null) }

    // Folder Actions State
    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    var showAddVideosToCategoryDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<MediaItem?>(null) }

    // Category options state
    var categoryForOptions by remember { mutableStateOf<MediaCategory?>(null) }
    var showCategoryInfoDialog by remember { mutableStateOf(false) }
    var showCategoryDeleteConfirm by remember { mutableStateOf(false) }

    val (isSortVisible, nestedScrollConnection) = com.medianest.ui.components.rememberSortRevealConnection()

    // Persistent Scroll States
    val mainGridState = rememberLazyStaggeredGridState()
    val wideGridState = rememberLazyGridState()
    val folderGridState = rememberLazyGridState()
    val chronologicalGridState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Smooth Scroll on Sort Change
    LaunchedEffect(sortField, isAscending) {
        scope.launch {
            if (activeFilterTab in listOf("MUSIC", "MOVIES", "SERIES", "EDITED")) {
                wideGridState.animateScrollToItem(0)
            } else if (selectedCategory != null || activeFilterTab == "CATEGORIES") {
                chronologicalGridState.animateScrollToItem(0)
            } else if (isFolderViewActive && selectedFolder == null) {
                folderGridState.animateScrollToItem(0)
            } else {
                mainGridState.animateScrollToItem(0)
            }
        }
    }

    val db = remember { MediaNestApp.instance.database }
    val allCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())

    val defaultVideoCategories = remember {
        listOf(
            MediaCategory(id = -101, name = "Travel & Vlogs", type = "VIDEO", iconName = "flight"),
            MediaCategory(id = -102, name = "Birthday Parties", type = "VIDEO", iconName = "cake"),
            MediaCategory(id = -103, name = "Training Videos", type = "VIDEO", iconName = "school"),
            MediaCategory(id = -104, name = "Workout", type = "VIDEO", iconName = "fitness")
        )
    }

    val allVideoCategories = remember(categories, defaultVideoCategories) {
        val userNames = categories.map { it.name.lowercase() }.toSet()
        val filteredDefaults = defaultVideoCategories.filter { it.name.lowercase() !in userNames }
        categories + filteredDefaults
    }

    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)

    val videoFolderGroups by viewModel.videoFolderGroups.collectAsState()
    val sharedTitleWords by viewModel.sharedTitleWords.collectAsState()
    val videoCounts by viewModel.videoCounts.collectAsState()

    val categoryUris = remember(allCrossRefs, selectedCategory, videosList) {
        if (selectedCategory != null) {
            val crossRefUris = allCrossRefs.filter { it.categoryId == selectedCategory.id }.map { it.mediaUri }.toSet()
            videosList.filter { item ->
                isItemInCategory(item, selectedCategory, crossRefUris)
            }.map { it.uri.toString() }.toSet()
        } else emptySet()
    }

    val folderGroups = remember(videosList) {
        videosList.groupBy { it.bucketName ?: "Movies" }
    }

    BackHandler(enabled = selectedSeasonName != null || selectedSeriesName != null || selectedFolder != null || selectedCategory != null || isFolderViewActive || activeFilterTab != "ALL") {
        when {
            selectedSeasonName != null -> selectedSeasonName = null
            selectedSeriesName != null -> selectedSeriesName = null
            selectedFolder != null -> selectedFolder = null
            selectedCategory != null -> onCategorySelect(null)
            isFolderViewActive -> {
                isFolderViewActive = false
                activeFilterTab = "ALL"
            }
            activeFilterTab != "ALL" -> activeFilterTab = "ALL"
        }
    }

    val musicCount = videoCounts.music
    val moviesCount = videoCounts.movies
    val seriesCount = videoCounts.series
    val clipsCount = videoCounts.clips
    val shortsCount = videoCounts.shorts
    val socialCount = videoCounts.social
    val editedCount = videoCounts.edited
    val downloadedCount = videoCounts.downloaded
    // Trash functionality commented out
    // val trashedVideoItems = remember(videosList) {
    //     TrashManager.getTrashedItems(currentContext)
    //         .filter { it.mimeType.startsWith("video") }
    // }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Filter Tabs
        VideoFilterRow(
            isFolderViewActive = isFolderViewActive,
            selectedCategory = selectedCategory,
            activeFilterTab = activeFilterTab,
            musicCount = musicCount,
            moviesCount = moviesCount,
            seriesCount = seriesCount,
            clipsCount = clipsCount,
            shortsCount = shortsCount,
            socialCount = socialCount,
            editedCount = editedCount,
            downloadedCount = downloadedCount,
            trashedCount = 0,
            excludedCount = videoCounts.excluded,
            hiddenCount = videoCounts.hidden,
            showHiddenFiles = showHiddenSetting,
            onFilterSelect = { tab ->
                when (tab) {
                    "FOLDERS", "HIDDEN", "EXCLUDED" -> {
                        isFolderViewActive = true
                        selectedFolder = null
                        activeFilterTab = tab
                        onCategorySelect(null)
                    }
                    "ALL" -> {
                        isFolderViewActive = false
                        selectedFolder = null
                        activeFilterTab = "ALL"
                        onCategorySelect(null)
                    }
                    else -> {
                        isFolderViewActive = false
                        selectedFolder = null
                        activeFilterTab = tab
                        onCategorySelect(null)
                    }
                }
            }
        )

        if (!isFolderViewActive && (activeFilterTab == "ALL" || activeFilterTab == "CATEGORIES")) {
            VideoCategoryRow(
                allVideoCategories = allVideoCategories,
                selectedCategory = selectedCategory,
                isFolderViewActive = isFolderViewActive,
                allCrossRefs = allCrossRefs,
                videosList = videosList,
                onCategorySelect = onCategorySelect,
                onCreateCategoryClick = onCreateCategoryClick,
                onCategoryInfoClick = {
                    categoryForOptions = it
                    showCategoryInfoDialog = true
                },
                onCategoryDeleteClick = {
                    categoryForOptions = it
                    showCategoryDeleteConfirm = true
                }
            )
        }

        SortRow(
            sortField = sortField,
            onSortFieldChange = { scope.launch { settingsManager.setVideoSortField(it) } },
            isAscending = isAscending,
            onIsAscendingChange = { scope.launch { settingsManager.setVideoSortAscending(it) } },
            isVisible = isSortVisible.value,
            onBack = when {
                isFolderViewActive && selectedFolder != null -> ({ selectedFolder = null })
                selectedCategory != null -> ({ onCategorySelect(null) })
                activeFilterTab == "SERIES" && selectedSeasonName != null -> ({ selectedSeasonName = null })
                activeFilterTab == "SERIES" && selectedSeriesName != null -> ({ selectedSeriesName = null })
                isFolderViewActive -> ({ isFolderViewActive = false })
                activeFilterTab != "ALL" -> ({ activeFilterTab = "ALL" })
                else -> onBackToDashboard
            },
            backLabel = when {
                isFolderViewActive && selectedFolder != null -> selectedFolder?.substringAfterLast('/')
                selectedCategory != null -> selectedCategory.name
                activeFilterTab == "SERIES" && selectedSeasonName != null -> selectedSeasonName
                activeFilterTab == "SERIES" && selectedSeriesName != null -> selectedSeriesName
                isFolderViewActive -> "All Videos"
                activeFilterTab != "ALL" -> "All Videos"
                else -> "Dashboard"
            }
        )

        val displayList = remember(videosList, videoFolderGroups, activeFilterTab, isFolderViewActive, selectedFolder, selectedCategory, categoryUris, showHiddenSetting) {
            if (isFolderViewActive || selectedFolder != null) {
                if (selectedFolder != null) {
                    videoFolderGroups[selectedFolder]
                        ?: videoFolderGroups.entries.firstOrNull { (k, _) ->
                            val normKey = k.trim('/').lowercase()
                            val normTarget = selectedFolder!!.trim('/').lowercase()
                            normKey == normTarget ||
                            normKey.substringAfterLast('/') == normTarget ||
                            normTarget.substringAfterLast('/') == normKey ||
                            normKey.endsWith("/$normTarget") ||
                            normTarget.endsWith("/$normKey")
                        }?.value
                        ?: emptyList()
                } else {
                    videosList
                }
            } else if (selectedCategory != null) {
                videosList.filter { categoryUris.contains(it.uri.toString()) }
            } else if (activeFilterTab == "CATEGORIES") {
                emptyList()
            } else {
                filterVideoList(videosList, activeFilterTab, showHiddenSetting)
            }
        }

        val sortedDisplayList = remember(displayList, sortField, isAscending) {
            val comp = when (sortField) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Type" -> compareBy<MediaItem> { it.mimeType.lowercase() }
                "Size" -> compareBy<MediaItem> { it.size }
                else -> compareBy<MediaItem> { it.dateAdded }
            }
            if (isAscending) displayList.sortedWith(comp) else displayList.sortedWith(comp).reversed()
        }

        val currentContextTitle = remember(selectedCategory, isFolderViewActive, selectedFolder, activeFilterTab, selectedSeriesName, selectedSeasonName) {
            when {
                selectedCategory != null -> selectedCategory.name
                isFolderViewActive && selectedFolder != null -> selectedFolder?.substringAfterLast('/') ?: "Folder"
                activeFilterTab == "SERIES" && selectedSeasonName != null -> "$selectedSeriesName • $selectedSeasonName"
                activeFilterTab == "SERIES" && selectedSeriesName != null -> selectedSeriesName ?: "Series"
                activeFilterTab == "MUSIC" -> "Music Videos"
                activeFilterTab == "MOVIES" -> "Movies"
                activeFilterTab == "CLIPS" -> "Clips"
                activeFilterTab == "SHORTS" -> "Shorts"
                activeFilterTab == "SOCIAL" -> "Social Media"
                activeFilterTab == "EDITED" -> "Edited Videos"
                activeFilterTab == "DOWNLOADED" -> "Downloads"
                activeFilterTab == "HIDDEN" -> "Hidden Videos"
                activeFilterTab == "EXCLUDED" -> "Excluded Videos"
                isFolderViewActive -> "Folders"
                else -> "All Videos"
            }
        }

        if ((isLoading && (videosList.isEmpty() || (displayList.isEmpty() && activeFilterTab != "CATEGORIES" && activeFilterTab != "SERIES"))) ||
            (isScanningHidden && (activeFilterTab == "HIDDEN" || activeFilterTab == "EXCLUDED") && displayList.isEmpty())) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MediaLoadingAnimation(
                    mediaType = MediaType.VIDEO,
                    iconSize = 52.dp,
                    showLabel = isScanningHidden && (activeFilterTab == "HIDDEN" || activeFilterTab == "EXCLUDED"),
                    customMessage = when {
                        isScanningHidden && activeFilterTab == "HIDDEN" -> "Scanning hidden videos..."
                        isScanningHidden && activeFilterTab == "EXCLUDED" -> "Scanning excluded videos..."
                        else -> null
                    }
                )
            }
        } else if (videosList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedCategory != null) "No videos in ${selectedCategory.name}" else "No Videos Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (selectedCategory != null) {
            val categoryVideos = remember(videosList, categoryUris) {
                videosList.filter { categoryUris.contains(it.uri.toString()) }
            }

            ChronologicalCategoryVideoGrid(
                category = selectedCategory,
                videos = categoryVideos,
                onVideoClick = onVideoClick,
                onVideoLongClick = onVideoLongClick,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onDelete = { videoToDelete = it },
                onRemoveFromCategory = { item ->
                    scope.launch(Dispatchers.IO) {
                        db.categoryDao().removeMediaFromCategory(selectedCategory.id, item.uri.toString())
                    }
                },
                onShowInfo = { infoItem = it },
                gridState = chronologicalGridState
            )
        } else if (activeFilterTab == "CATEGORIES" && selectedCategory == null) {
            val combinedCategoryVideos = remember(videosList, allCrossRefs, allVideoCategories) {
                val videoCatIds = allVideoCategories.map { it.id }.toSet()
                val crossRefsByCat = allCrossRefs.filter { it.categoryId in videoCatIds }.groupBy { it.categoryId }
                
                videosList.filter { item ->
                    allVideoCategories.any { cat ->
                        val crossRefUris = crossRefsByCat[cat.id]?.map { it.mediaUri }?.toSet() ?: emptySet()
                        isItemInCategory(item, cat, crossRefUris)
                    }
                }
            }
            val allCategoriesHeader = remember {
                MediaCategory(id = -999, name = "All Categorized Videos", type = "VIDEO", iconName = "category")
            }

            ChronologicalCategoryVideoGrid(
                category = allCategoriesHeader,
                videos = combinedCategoryVideos,
                onVideoClick = onVideoClick,
                onVideoLongClick = onVideoLongClick,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onDelete = { videoToDelete = it },
                onRemoveFromCategory = { /* No-op for combined view */ },
                gridState = chronologicalGridState
            )
        } else if (isFolderViewActive && selectedFolder == null) {
            VideoFoldersGrid(
                videoFolderGroups = videoFolderGroups,
                activeFilterTab = activeFilterTab,
                settingsManager = settingsManager,
                db = db,
                roundedCornersEnabled = roundedCornersEnabled,
                cornerRadiusDp = cornerRadiusDp,
                sortField = sortField,
                isAscending = isAscending,
                onFolderClick = { selectedFolder = it },
                onFolderDelete = { folderToDelete = it },
                onFolderInfo = { folderForInfo = it },
                onCreateCategoryClick = onCreateCategoryClick,
                isLoading = isLoading,
                isScanningHidden = isScanningHidden,
                gridState = folderGridState
            )
        } else if (activeFilterTab == "SERIES") {
            VideoSeriesView(
                videosList = videosList,
                sharedTitleWords = sharedTitleWords,
                selectedSeriesName = selectedSeriesName,
                selectedSeasonName = selectedSeasonName,
                onSeriesClick = { selectedSeriesName = it },
                onSeasonClick = { selectedSeasonName = it },
                onVideoClick = onVideoClick,
                onVideoLongClick = onVideoLongClick,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                cornerRadiusDp = cornerRadiusDp,
                roundedCornersEnabled = roundedCornersEnabled,
                gridSizeLevel = gridSizeLevel,
                gridGapDp = gridGapDp,
                onInfoItem = { infoItem = it },
                onVideoDelete = { videoToDelete = it },
                onRename = { itemToRename = it }
            )
        } else {
            VideosMainGrid(
                sortedDisplayList = sortedDisplayList,
                videoFolderGroups = videoFolderGroups,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                gridSizeLevel = gridSizeLevel,
                gridGapDp = gridGapDp,
                cornerRadiusDp = cornerRadiusDp,
                roundedCornersEnabled = roundedCornersEnabled,
                selectedCategory = selectedCategory,
                activeFilterTab = activeFilterTab,
                onVideoClick = { item -> onVideoClick(item, sortedDisplayList, currentContextTitle) },
                onVideoLongClick = onVideoLongClick,
                onInfoItem = { infoItem = it },
                onVideoDelete = { videoToDelete = it },
                onRemoveFromCategory = { item ->
                    scope.launch(Dispatchers.IO) {
                        db.categoryDao().removeMediaFromCategory(selectedCategory!!.id, item.uri.toString())
                    }
                },
                onOpenFolder = { matchedKey, targetUri ->
                    isFolderViewActive = true
                    selectedFolder = matchedKey
                    if (targetUri != null) targetVideoUri = targetUri
                },
                onRename = { itemToRename = it },
                selectedFolder = selectedFolder,
                targetVideoUri = targetVideoUri,
                isFolderViewActive = isFolderViewActive,
                gridState = wideGridState,
                staggeredGridState = mainGridState
            )
        }


        if (itemToRename != null) {
            RenameFileDialog(
                item = itemToRename!!,
                onDismiss = { itemToRename = null },
                onRenameSuccess = { itemToRename = null }
            )
        }

        if (videoToDelete != null) {
            val target = videoToDelete!!
            AlertDialog(
                onDismissRequest = { videoToDelete = null },
                title = { Text("Delete Video File") },
                text = { Text("Are you sure you want to delete '${target.title}'? This will permanently remove the video file from your device storage.") },
                confirmButton = {
                    Button(
                        onClick = {
                            videoToDelete = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    FolderHiddenUtils.deleteMediaUri(currentContext, target.uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { videoToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (infoItem != null) {
            MediaInfoBottomSheet(
                item = infoItem!!,
                onDismiss = { infoItem = null },
                onShowFileLocation = { item ->
                    infoItem = null
                    isFolderViewActive = true
                    activeFilterTab = "FOLDERS"
                    onCategorySelect(null)
                    targetVideoUri = item.uri.toString()
                    val relPath = item.relativePath?.trim('/')
                    val folderKey = if (!relPath.isNullOrBlank()) relPath else (item.bucketName ?: "Movies")
                    selectedFolder = videoFolderGroups.keys.firstOrNull { key ->
                        key.equals(folderKey, ignoreCase = true) || key.lowercase().endsWith(folderKey.lowercase()) || folderKey.lowercase().endsWith(key.lowercase())
                    } ?: folderKey
                }
            )
        }

        if (showAddVideosToCategoryDialog || showAddVideosDialog) {
            AddVideosToCategoryDialog(
                allVideoCategories = allVideoCategories,
                selectedUris = selectedUris,
                db = db,
                scope = scope,
                onDismiss = {
                    showAddVideosToCategoryDialog = false
                    onDismissAddVideosDialog()
                },
                onClearSelection = onClearSelection
            )
        }

        if (folderToMove != null) {
            MoveFolderDialog(
                folderName = folderToMove!!,
                folderGroups = folderGroups,
                scope = scope,
                onDismiss = { folderToMove = null }
            )
        }

        if (folderToDelete != null) {
            val srcFolder = folderToDelete!!
            val itemsToDelete = videoFolderGroups[srcFolder] ?: emptyList()
            DeleteFolderDialog(
                folderName = srcFolder,
                itemsToDelete = itemsToDelete,
                context = currentContext,
                scope = scope,
                onDismiss = { folderToDelete = null }
            )
        }

        if (folderForInfo != null) {
            FolderInfoDialog(
                srcFolder = folderForInfo!!,
                videoFolderGroups = videoFolderGroups,
                videosList = videosList,
                context = currentContext,
                onDismiss = { folderForInfo = null }
            )
        }

        if (showCategoryInfoDialog && categoryForOptions != null) {
            val cat = categoryForOptions!!
            val itemCount = remember(allCrossRefs, cat, videosList) {
                val crossRefUris = allCrossRefs.filter { it.categoryId == cat.id }.map { it.mediaUri }.toSet()
                val filterKeywords = when (cat.name.trim().lowercase()) {
                    "workout" -> listOf("workout", "gym", "fitness", "exercise", "cardio", "lifting", "abs", "squat")
                    "training videos" -> listOf("train", "tutorial", "learn", "course", "coaching", "drills", "practice")
                    "birthday parties" -> listOf("birthday", "bday", "party", "celebration", "cake")
                    "travel & vlogs" -> listOf("travel", "vlog", "trip", "tour", "vacation", "journey", "holiday")
                    else -> emptyList()
                }
                videosList.count { item ->
                    crossRefUris.contains(item.uri.toString()) ||
                            crossRefUris.any { ref -> ref == item.uri.toString() || ref == item.uri.path } ||
                            (filterKeywords.isNotEmpty() && filterKeywords.any { kw ->
                                item.title.lowercase().contains(kw) ||
                                        (item.relativePath ?: "").lowercase().contains(kw) ||
                                        (item.bucketName ?: "").lowercase().contains(kw)
                            })
                }
            }
            Dialog(onDismissRequest = {
                showCategoryInfoDialog = false
                categoryForOptions = null
            }) {
                GlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Category Details",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow("Category Name", cat.name)
                            InfoRow("Category Type", cat.type)
                            InfoRow("Total Items", "$itemCount items")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                showCategoryInfoDialog = false
                                categoryForOptions = null
                            }) {
                                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showCategoryDeleteConfirm && categoryForOptions != null) {
            val cat = categoryForOptions!!
            Dialog(onDismissRequest = {
                showCategoryDeleteConfirm = false
                categoryForOptions = null
            }) {
                GlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Delete Category",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Are you sure you want to delete category '${cat.name}'? The videos in this category will not be deleted.",
                            color = Color(0xFFC0C5D0),
                            fontSize = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                showCategoryDeleteConfirm = false
                                categoryForOptions = null
                            }) {
                                Text("Cancel", color = Color(0xFF9EA3B0))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.categoryDao().deleteCategory(cat)
                                    }
                                    if (selectedCategory?.id == cat.id) {
                                        onCategorySelect(null)
                                    }
                                    showCategoryDeleteConfirm = false
                                    categoryForOptions = null
                                }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
