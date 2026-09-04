package com.medianest.ui.library.image

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import com.medianest.MediaNestApp
import com.medianest.data.db.MediaType
import com.medianest.data.db.SelectiveHiddenFolder
import com.medianest.data.model.MediaItem
import com.medianest.data.settings.SettingsManager
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.components.RenameFileDialog
import com.medianest.ui.components.SortRow
import com.medianest.ui.library.MoveFolderDialog
import com.medianest.ui.library.MoveOrCopyFileDialog
import com.medianest.ui.library.RenameFolderDialog
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.FolderHiddenUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImagesTab(
    imagesList: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridGapDp: Int,
    gridSizeLevel: Int = 1,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    isLoading: Boolean = false,
    isScanningHidden: Boolean = false,
    onImageClick: (MediaItem, List<MediaItem>) -> Unit,
    onImageLongClick: (MediaItem) -> Unit,
    initialFolder: String? = null,
    initialTargetImageUri: String? = null,
    onBackToDashboard: () -> Unit = {},
    onRescanHiddenMedia: () -> Unit = {},
    onShowInfo: (MediaItem) -> Unit = {},
    viewModel: com.medianest.ui.MediaViewModel = viewModel()
) {
    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeFilterTab by remember { mutableStateOf("ALL") }
    var viewMode by remember(initialFolder) { mutableIntStateOf(if (initialFolder != null) 1 else 0) }
    var selectedFolder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var targetImageUri by remember(initialTargetImageUri) { mutableStateOf(initialTargetImageUri) }

    LaunchedEffect(initialTargetImageUri) {
        if (!initialTargetImageUri.isNullOrBlank()) {
            targetImageUri = initialTargetImageUri
        }
    }

    LaunchedEffect(initialFolder) {
        if (!initialFolder.isNullOrBlank()) {
            viewMode = 1
            selectedFolder = initialFolder
        }
    }

    val settingsManager = MediaNestApp.instance.settingsManager
    val persistedSortField by settingsManager.imageSortField.collectAsState(initial = "Date")
    val persistedSortAscending by settingsManager.imageSortAscending.collectAsState(initial = false)
    
    val sortField = persistedSortField
    val isAscending = persistedSortAscending

    LaunchedEffect(sortField, isAscending) {
        viewModel.updateImageSort(sortField, isAscending)
    }

    LaunchedEffect(activeFilterTab) {
        viewModel.updateImageFilter(activeFilterTab)
    }

    val db = remember { MediaNestApp.instance.database }
    val observedCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())

    val allImageCategories by db.categoryDao().getCategoriesByType("IMAGE").collectAsState(initial = emptyList())
    val favoriteCat = remember(allImageCategories) { allImageCategories.find { it.name.equals("Favorites", ignoreCase = true) } }

    val favoriteUris = remember(observedCrossRefs, favoriteCat) {
        if (favoriteCat != null) observedCrossRefs.filter { it.categoryId == favoriteCat.id }.map { it.mediaUri }.toSet()
        else emptySet()
    }

    var filterCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(imagesList, favoriteUris) {
        kotlinx.coroutines.delay(600)
        withContext(Dispatchers.Default) {
            val counts = mutableMapOf<String, Int>()
            val ids = listOf("CAMERA", "FAVORITES", "COOKING", "TRAVEL", "NOTES", "AI_GENERATED", "GARDENING", "ANIME", "PETS", "FAMILY", "DOCUMENTS", "MEMES", "SCREENSHOTS", "GIFS", "SOCIAL", "PNG_SVG", "EDITED", "WALLPAPERS", "EXCLUDED", "HIDDEN")
            ids.forEach { id ->
                counts[id] = filterImageList(imagesList, id, favoriteUris).size
            }
            filterCounts = counts
        }
    }

    var isFolderSelectionActive by remember { mutableStateOf(false) }
    var selectedFolderNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    var folderToRename by remember { mutableStateOf<String?>(null) }
    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    var imageToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var itemToRename by remember { mutableStateOf<MediaItem?>(null) }
    var itemToMove by remember { mutableStateOf<MediaItem?>(null) }
    var itemToCopy by remember { mutableStateOf<MediaItem?>(null) }
    var itemToMoveFilter by remember { mutableStateOf<MediaItem?>(null) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var contextSheetItem by remember { mutableStateOf<MediaItem?>(null) }
    var showFolderBatchInfoModal by remember { mutableStateOf(false) }

    val (isSortVisible, nestedScrollConnection) = com.medianest.ui.components.rememberSortRevealConnection()

    // Persistent Scroll States
    val mainGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val folderGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

    // Smooth Scroll on Sort Change
    var isInitialSortEffect by remember { mutableStateOf(true) }
    LaunchedEffect(sortField, isAscending) {
        if (isInitialSortEffect) {
            isInitialSortEffect = false
            return@LaunchedEffect
        }
        scope.launch {
            try {
                if (viewMode == 1 && selectedFolder == null) {
                    if (folderGridState.layoutInfo.totalItemsCount > 0) folderGridState.animateScrollToItem(0)
                } else {
                    if (mainGridState.layoutInfo.totalItemsCount > 0) mainGridState.animateScrollToItem(0)
                }
            } catch (_: Exception) {
                // Ignore if list is not yet ready
            }
        }
    }

    BackHandler(
        enabled = isFolderSelectionActive || selectedFolder != null || viewMode != 0 || activeFilterTab != "ALL"
    ) {
        when {
            isFolderSelectionActive -> {
                isFolderSelectionActive = false
                selectedFolderNames = emptySet()
            }
            selectedFolder != null -> selectedFolder = null
            viewMode != 0 -> {
                viewMode = 0
                activeFilterTab = "ALL"
            }
            activeFilterTab != "ALL" -> activeFilterTab = "ALL"
        }
    }

    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)

    val folderGroups = remember(imagesList, showHiddenSetting, viewMode, activeFilterTab, selectedFolder) {
        if (viewMode != 1 && activeFilterTab != "FOLDERS" && activeFilterTab != "HIDDEN" && activeFilterTab != "EXCLUDED" && selectedFolder == null) {
            emptyMap()
        } else {
            val filteredList = if (showHiddenSetting || activeFilterTab == "HIDDEN" || activeFilterTab == "EXCLUDED") {
                imagesList
            } else {
                imagesList.filter { item ->
                    val relPath = item.relativePath?.trim('/') ?: ""
                    !relPath.split('/').any { it.startsWith(".") && it.length > 1 }
                }
            }

            filteredList.groupBy { item ->
                val relPath = item.relativePath?.trim('/')
                if (!relPath.isNullOrBlank()) relPath else item.bucketName ?: "Pictures"
            }
        }
    }

    val appHiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())
    val selectiveHiddenFolders by db.selectiveHiddenFolderDao().getAllHiddenFolders().collectAsState(initial = emptyList())

    val selectiveImageHidden = remember(selectiveHiddenFolders) {
        selectiveHiddenFolders.filter { it.mediaType == "IMAGE" && it.isHidden }
            .flatMap { listOf(it.folderPath, it.folderName) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    val allHiddenImageFolders = remember(appHiddenFolders, selectiveImageHidden) {
        (appHiddenFolders + selectiveImageHidden).map { it.lowercase() }.toSet()
    }

    val folderStatusMap = remember(folderGroups, allHiddenImageFolders) {
        folderGroups.keys.associateWith { folderKey ->
            val items = folderGroups[folderKey]
            val lowerKey = folderKey.lowercase().trim('/')
            val folderName = lowerKey.substringAfterLast('/')
            val isExcluded = com.medianest.util.FolderHiddenUtils.isFolderExcludedByDefault(folderKey, folderName) ||
                    lowerKey in allHiddenImageFolders ||
                    folderName in allHiddenImageFolders ||
                    allHiddenImageFolders.any { hf -> lowerKey == hf || lowerKey.startsWith("$hf/") || lowerKey.contains("/$hf/") } ||
                    (items?.firstOrNull()?.let { com.medianest.util.FolderHiddenUtils.isItemExcluded(it) } == true)

            val isSystemHidden = if (isExcluded) false else {
                (items?.firstOrNull()?.let { com.medianest.util.FolderHiddenUtils.isItemHidden(it) && !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) } == true) ||
                lowerKey.split('/').any { it.startsWith(".") && it.length > 1 } ||
                folderName.startsWith(".")
            }
            Pair(isExcluded, isSystemHidden)
        }
    }

    fun isFolderExcluded(folderKey: String, items: List<MediaItem>? = null): Boolean {
        return folderStatusMap[folderKey]?.first ?: false
    }

    fun isFolderSystemHidden(folderKey: String, items: List<MediaItem>? = null): Boolean {
        return folderStatusMap[folderKey]?.second ?: false
    }

    fun isFolderHidden(folderKey: String, items: List<MediaItem>? = null): Boolean {
        val status = folderStatusMap[folderKey]
        return (status?.first == true) || (status?.second == true)
    }

    val searchQuery by viewModel.searchQuery.collectAsState()

    val sortedFolderNames = remember(folderGroups, folderStatusMap, sortField, isAscending, activeFilterTab) {
        if (folderGroups.isEmpty()) emptyList()
        else {
            val keys = folderGroups.keys.toList()
            val comp = when (sortField) {
                "Name" -> compareBy<String> { it.lowercase() }
                "Date" -> compareBy<String> { folderName ->
                    folderGroups[folderName]?.maxOfOrNull { maxOf(it.dateAdded, it.dateCreated, it.dateModified) } ?: 0L
                }
                "Size" -> compareBy<String> { folderName ->
                    folderGroups[folderName]?.sumOf { it.size } ?: 0L
                }
                else -> compareBy<String> { it.lowercase() }
            }

            val baseSorted = if (isAscending) keys.sortedWith(comp) else keys.sortedWith(comp).reversed()
            baseSorted.sortedBy { fn -> isFolderHidden(fn) && activeFilterTab != "HIDDEN" && activeFilterTab != "EXCLUDED" }
        }
    }

    val imageMinSize = when (gridSizeLevel) {
        0 -> 100.dp
        2 -> 180.dp
        3 -> 220.dp
        else -> 135.dp
    }
    val backdropState = LocalBackdropState.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ImageFilterRow(
                activeFilterTab = activeFilterTab,
                onFilterTabChange = { activeFilterTab = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                selectedCategory = null,
                onSelectedCategoryChange = {},
                onSelectedFolderChange = { selectedFolder = it },
                onShowHiddenFiles = { scope.launch { settingsManager.setShowHiddenFiles(true) } },
                showHiddenFiles = showHiddenSetting,
                filterCounts = filterCounts
            )

            SortRow(
                sortField = sortField,
                onSortFieldChange = { scope.launch { settingsManager.setImageSortField(it) } },
                isAscending = isAscending,
                onIsAscendingChange = { scope.launch { settingsManager.setImageSortAscending(it) } },
                isVisible = isSortVisible.value,
                onBack = when {
                    selectedFolder != null -> ({ selectedFolder = null })
                    activeFilterTab != "ALL" -> ({ activeFilterTab = "ALL" })
                    viewMode == 1 -> ({ viewMode = 0 })
                    else -> onBackToDashboard
                },
                backLabel = when {
                    selectedFolder != null -> selectedFolder!!.substringAfterLast('/')
                    activeFilterTab != "ALL" -> "All Photos"
                    viewMode == 1 -> "All Photos"
                    else -> "Dashboard"
                }
            )

            if ((viewMode == 1 || activeFilterTab in listOf("FOLDERS", "HIDDEN", "EXCLUDED")) && selectedFolder != null) {
                com.medianest.ui.components.FolderBreadcrumbBar(
                    rootTab = activeFilterTab,
                    selectedFolder = selectedFolder,
                    onNavigateToRoot = { selectedFolder = null },
                    onNavigateToSegment = { targetSubPath ->
                        selectedFolder = targetSubPath
                    }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                val visibleFolders = remember(sortedFolderNames, folderStatusMap, activeFilterTab, showHiddenSetting, searchQuery) {
                    val base = when (activeFilterTab) {
                        "HIDDEN" -> sortedFolderNames.filter { fn -> isFolderSystemHidden(fn) }
                        "EXCLUDED" -> sortedFolderNames.filter { fn -> isFolderExcluded(fn) }
                        else -> sortedFolderNames.filter { fn ->
                            !isFolderExcluded(fn) && (showHiddenSetting || !isFolderSystemHidden(fn))
                        }
                    }
                    if (searchQuery.isNotBlank()) {
                        base.filter { fn ->
                            fn.contains(searchQuery, ignoreCase = true) ||
                            folderGroups[fn]?.any { it.title.contains(searchQuery, ignoreCase = true) } == true
                        }
                    } else {
                        base
                    }
                }

                val displayList = remember(imagesList, activeFilterTab, favoriteUris, showHiddenSetting, searchQuery) {
                    val baseList = when (activeFilterTab) {
                        "EXCLUDED" -> imagesList.filter { com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
                        "HIDDEN" -> imagesList.filter { com.medianest.util.FolderHiddenUtils.isItemHidden(it) && !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
                        else -> imagesList.filter { item ->
                            !com.medianest.util.FolderHiddenUtils.isItemExcluded(item) &&
                            (showHiddenSetting || !com.medianest.util.FolderHiddenUtils.isItemHidden(item))
                        }
                    }

                    val tabFiltered = if (activeFilterTab == "ALL" || activeFilterTab == "EXCLUDED" || activeFilterTab == "HIDDEN") {
                        baseList
                    } else {
                        filterImageList(baseList, activeFilterTab, favoriteUris)
                    }

                    if (searchQuery.isNotBlank()) {
                        tabFiltered.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    } else {
                        tabFiltered
                    }
                }

                val currentDisplayList = if ((viewMode == 1 || activeFilterTab == "FOLDERS" || activeFilterTab == "HIDDEN" || activeFilterTab == "EXCLUDED") && selectedFolder != null) {
                    val directItems = folderGroups[selectedFolder]
                    val folderItems = if (directItems != null) {
                        directItems
                    } else {
                        val subfolderItems = folderGroups.entries.filter { (k, _) ->
                            val normKey = k.trim('/').lowercase()
                            val normTarget = selectedFolder!!.trim('/').lowercase()
                            normKey == normTarget ||
                            normKey.startsWith("$normTarget/") ||
                            normKey.contains("/$normTarget/") ||
                            normKey.substringAfterLast('/') == normTarget
                        }.flatMap { it.value }

                        if (subfolderItems.isNotEmpty()) {
                            subfolderItems.distinctBy { it.uri }
                        } else {
                            imagesList.filter { item ->
                                val rel = item.relativePath?.trim('/')?.lowercase() ?: ""
                                val bucket = item.bucketName?.trim('/')?.lowercase() ?: ""
                                val normTarget = selectedFolder!!.trim('/').lowercase()
                                bucket == normTarget || rel == normTarget || rel.startsWith("$normTarget/") || rel.contains("/$normTarget/")
                            }
                        }
                    }
                    if (searchQuery.isNotBlank()) {
                        folderItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    } else {
                        folderItems
                    }
                } else {
                    displayList
                }

                val sortedDisplayList = remember(currentDisplayList, sortField, isAscending) {
                    val comp = when (sortField) {
                        "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                        "Type" -> compareBy<MediaItem> { it.mimeType.lowercase() }
                        "Size" -> compareBy<MediaItem> { if (it.size > 0) it.size else Long.MAX_VALUE }
                        else -> compareBy<MediaItem> { maxOf(it.dateAdded, it.dateCreated, it.dateModified) }
                    }
                    if (isAscending) currentDisplayList.sortedWith(comp) else currentDisplayList.sortedWith(comp).reversed()
                }

                when {
                    (isLoading && (imagesList.isEmpty() || (sortedDisplayList.isEmpty() && viewMode == 0 && activeFilterTab != "FOLDERS"))) ||
                    (isScanningHidden && (activeFilterTab == "HIDDEN" || activeFilterTab == "EXCLUDED") && sortedDisplayList.isEmpty()) -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            com.medianest.ui.components.MediaLoadingAnimation(
                                mediaType = com.medianest.data.db.MediaType.IMAGE,
                                iconSize = 52.dp,
                                showLabel = isScanningHidden && (activeFilterTab == "HIDDEN" || activeFilterTab == "EXCLUDED"),
                                customMessage = when {
                                    isScanningHidden && activeFilterTab == "HIDDEN" -> "Scanning hidden photos..."
                                    isScanningHidden && activeFilterTab == "EXCLUDED" -> "Scanning excluded photos..."
                                    else -> null
                                }
                            )
                        }
                    }

                    viewMode == 1 && selectedFolder == null -> {
                        ImagesFolderGrid(
                            visibleFolders = visibleFolders,
                            folderGroups = folderGroups,
                            isFolderSelectionActive = isFolderSelectionActive,
                            onIsFolderSelectionActiveChange = { isFolderSelectionActive = it },
                            selectedFolderNames = selectedFolderNames,
                            onSelectedFolderNamesChange = { selectedFolderNames = it },
                            isLoading = isLoading,
                            isScanningHidden = isScanningHidden,
                            activeFilterTab = activeFilterTab,
                            onSelectedFolderChange = { selectedFolder = it },
                            onFolderRenameRequest = { folderToRename = it },
                            onFolderMoveRequest = { folderToMove = it },
                            onFolderDeleteRequest = { folderToDelete = it },
                            onFolderInfoRequest = { folderForInfo = it },
                            isFolderHidden = ::isFolderHidden,
                            isFolderExcluded = ::isFolderExcluded,
                            onRescanHiddenMedia = onRescanHiddenMedia,
                            onToggleFolderHidden = { folderName, folderItems, isExcluded ->
                                scope.launch {
                                    val current = settingsManager.hiddenFolders.first()
                                    val folderPathKey = folderItems.firstOrNull()?.relativePath?.trim('/') ?: folderName
                                    if (isExcluded) {
                                        settingsManager.setHiddenFolders(current - folderName - folderPathKey)
                                        db.selectiveHiddenFolderDao().unhideFolder(folderName, "IMAGE")
                                        db.selectiveHiddenFolderDao().unhideFolder(folderPathKey, "IMAGE")
                                    } else {
                                        settingsManager.setHiddenFolders(current + folderPathKey + folderName)
                                        db.selectiveHiddenFolderDao().insertOrUpdate(
                                            SelectiveHiddenFolder(
                                                folderPath = folderPathKey,
                                                folderName = folderName,
                                                mediaType = "IMAGE",
                                                isHidden = true
                                            )
                                        )
                                    }
                                }
                            },
                            gridState = folderGridState
                        )
                    }

                    else -> {
                        ImagesMainGrid(
                            images = sortedDisplayList,
                            selectedFolder = selectedFolder,
                            onSelectedFolderChange = { selectedFolder = it },
                            viewMode = viewMode,
                            onViewModeChange = { viewMode = it },
                            selectedUris = selectedUris,
                            isSelectionMode = isSelectionMode,
                            cornerRadiusDp = cornerRadiusDp,
                            roundedCornersEnabled = roundedCornersEnabled,
                            gridGapDp = gridGapDp,
                            imageMinSize = imageMinSize,
                            onImageClick = onImageClick,
                            onImageLongClick = onImageLongClick,
                            onInfoItemChange = { if (it != null) infoItem = it },
                            onImageToDeleteChange = { imageToDelete = it },
                            onContextSheetItemChange = { contextSheetItem = it },
                            onRename = { itemToRename = it },
                            onMove = { itemToMove = it },
                            onCopy = { itemToCopy = it },
                            onMoveToFilter = { itemToMoveFilter = it },
                            onRemoveFromCategory = {},
                            selectedCategory = null,
                            isLoading = isLoading,
                            activeFilterTab = activeFilterTab,
                            targetImageUri = targetImageUri,
                            onTargetImageChange = { targetImageUri = it },
                            gridState = mainGridState,
                            gridSizeLevel = gridSizeLevel
                        )
                    }
                }
            }
        }

        FolderBatchActionBar(
            visible = isFolderSelectionActive && viewMode == 1 && selectedFolder == null,
            selectedFolderNames = selectedFolderNames,
            folderGroups = folderGroups,
            context = currentContext,
            onSelectAllToggle = {
                selectedFolderNames = if (selectedFolderNames.size == folderGroups.size) emptySet() else folderGroups.keys.toSet()
            },
            onShowBatchInfo = { showFolderBatchInfoModal = true },
            onClearSelection = {
                isFolderSelectionActive = false
                selectedFolderNames = emptySet()
            }
        )

        if (folderToMove != null) {
            MoveFolderDialog(
                folderName = folderToMove!!,
                folderGroups = folderGroups,
                mediaType = MediaType.IMAGE,
                scope = scope,
                onDismiss = { folderToMove = null }
            )
        }

        if (folderToDelete != null) {
            val srcFolder = folderToDelete!!
            val itemsToDelete = folderGroups[srcFolder] ?: emptyList()
            DeleteImageFolderDialog(
                folderName = srcFolder,
                itemsToDelete = itemsToDelete,
                context = currentContext,
                scope = scope,
                onDismiss = { folderToDelete = null }
            )
        }

        if (folderToRename != null) {
            val srcFolder = folderToRename!!
            val itemsInFolder = folderGroups[srcFolder] ?: emptyList()
            RenameFolderDialog(
                folderName = srcFolder,
                itemsInFolder = itemsInFolder,
                defaultMediaType = MediaType.IMAGE,
                scope = scope,
                onDismiss = { folderToRename = null },
                onRenameComplete = { folderToRename = null }
            )
        }

        if (itemToMove != null) {
            MoveOrCopyFileDialog(
                item = itemToMove!!,
                allItems = imagesList,
                isCopy = false,
                backdropState = backdropState,
                onDismiss = { itemToMove = null }
            )
        }

        if (itemToCopy != null) {
            MoveOrCopyFileDialog(
                item = itemToCopy!!,
                allItems = imagesList,
                isCopy = true,
                backdropState = backdropState,
                onDismiss = { itemToCopy = null }
            )
        }

        if (folderForInfo != null) {
            ImageFolderInfoDialog(
                srcFolder = folderForInfo!!,
                folderGroups = folderGroups,
                context = currentContext,
                backdropState = backdropState,
                onDismiss = { folderForInfo = null }
            )
        }

        if (itemToMoveFilter != null) {
            MoveToFilterDialog(
                item = itemToMoveFilter!!,
                currentFilterTab = activeFilterTab,
                backdropState = backdropState,
                onDismiss = { itemToMoveFilter = null }
            )
        }

        if (contextSheetItem != null) {
            val activeItem = contextSheetItem!!
            val isDark = LocalDarkTheme.current

            GlassDropdownMenu(
                expanded = true,
                onDismissRequest = { contextSheetItem = null },
                modifier = Modifier.width(200.dp),
                shape = RoundedCornerShape(20.dp),
                useImageBackground = true,
                backgroundImage = activeItem.albumArtUri ?: activeItem.uri
            ) {
                Text(
                    text = activeItem.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                HorizontalDivider(color = if (isDark) Color(0x1AFFFFFF) else Color(0x1A000000))

                DropdownMenuItem(
                    text = { Text("File Info", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        infoItem = target
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Image", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        imageToDelete = target
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        itemToRename = target
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to Folder", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        itemToMove = target
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy to Folder", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        itemToCopy = target
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (viewMode == 1 && selectedFolder != null) "Open with" else "Show in Folder", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = {
                        Icon(
                            if (viewMode == 1 && selectedFolder != null) Icons.Default.Image else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        if (viewMode == 1 && selectedFolder != null) {
                            com.medianest.util.IntentUtils.openInGallery(currentContext, target)
                        } else {
                            val targetFolderKey = target.relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: (target.bucketName ?: "DCIM")
                            viewMode = 1
                            selectedFolder = targetFolderKey
                        }
                    }
                )

                if (activeFilterTab !in listOf("FOLDERS", "HIDDEN", "EXCLUDED")) {
                    DropdownMenuItem(
                        text = { Text("Move to Filter...", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            val target = activeItem
                            contextSheetItem = null
                            itemToMoveFilter = target
                        }
                    )

                    if (activeFilterTab != "ALL") {
                        val filterLabel = when (activeFilterTab) {
                            "CAMERA" -> "Camera"
                            "FAVORITES" -> "Favorites"
                            "NOTES" -> "Notes & Studies"
                            "SCREENSHOTS" -> "Screenshots"
                            "GIFS" -> "GIFs"
                            "SOCIAL" -> "Social Media"
                            "PNG_SVG" -> "PNG & SVG"
                            "EDITED" -> "Edited"
                            "AI_GENERATED" -> "AI Generated"
                            "ANIME" -> "Anime & Art"
                            "COOKING" -> "Cooking & Food"
                            "TRAVEL" -> "Travel & Places"
                            "GARDENING" -> "Gardening & Nature"
                            "PETS" -> "Pets & Animals"
                            "FAMILY" -> "Family & People"
                            "DOCUMENTS" -> "Receipts & Docs"
                            "MEMES" -> "Memes & Funny"
                            "WALLPAPERS" -> "Wallpapers"
                            else -> activeFilterTab.lowercase().replaceFirstChar { it.uppercase() }
                        }

                        DropdownMenuItem(
                            text = { Text("Remove from $filterLabel", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.FilterListOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                val target = activeItem
                                contextSheetItem = null
                                com.medianest.util.ImageExclusionManager.excludeFromFilter(currentContext, target.uri.toString(), activeFilterTab)
                                Toast.makeText(currentContext, "Removed from $filterLabel", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (selectedFolder != null) {
                    DropdownMenuItem(
                        text = { Text("Delete Image", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            val target = activeItem
                            contextSheetItem = null
                            imageToDelete = target
                        }
                    )
                }
            }
        }

        if (imageToDelete != null) {
            val target = imageToDelete!!
            com.medianest.ui.components.DeleteConfirmationDialog(
                title = "Delete Image File",
                itemTitle = target.title,
                backdropState = backdropState,
                onDismiss = { imageToDelete = null },
                onConfirm = {
                    imageToDelete = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            FolderHiddenUtils.deleteMediaUri(currentContext, target.uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )
        }

        if (infoItem != null) {
            com.medianest.ui.components.mediainfo.ImageInfoOverlay(
                item = infoItem!!,
                onClose = { infoItem = null },
                backdropState = backdropState
            )
        }

        if (itemToRename != null) {
            RenameFileDialog(
                item = itemToRename!!,
                backdropState = backdropState,
                onDismiss = { itemToRename = null },
                onRenameSuccess = { itemToRename = null }
            )
        }

        if (showFolderBatchInfoModal) {
            FolderBatchInfoModal(
                selectedFolderNames = selectedFolderNames,
                folderGroups = folderGroups,
                onDismiss = { showFolderBatchInfoModal = false }
            )
        }
    }
}