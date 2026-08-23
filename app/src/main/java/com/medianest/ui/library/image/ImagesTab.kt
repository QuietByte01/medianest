package com.medianest.ui.library.image

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
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.components.RenameFileDialog
import com.medianest.ui.components.SortRow
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
    pagedImages: LazyPagingItems<MediaItem>? = null,
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
            val ids = listOf("CAMERA", "FAVORITES", "NOTES", "SCREENSHOTS", "GIFS", "SOCIAL", "PNG_SVG", "EDITED", "AI_GENERATED", "ANIME", "COOKING", "GARDENING", "WALLPAPERS", "EXCLUDED")
            ids.forEach { id ->
                counts[id] = filterImageList(imagesList, id, favoriteUris).size
            }
            filterCounts = counts
        }
    }

    var isFolderSelectionActive by remember { mutableStateOf(false) }
    var selectedFolderNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    var imageToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var itemToRename by remember { mutableStateOf<MediaItem?>(null) }
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
        enabled = isFolderSelectionActive || selectedFolder != null || viewMode != 0
    ) {
        when {
            isFolderSelectionActive -> {
                isFolderSelectionActive = false
                selectedFolderNames = emptySet()
            }
            selectedFolder != null -> selectedFolder = null
            viewMode != 0 -> viewMode = 0
        }
    }

    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)

    val folderGroups = remember(imagesList, showHiddenSetting, viewMode, activeFilterTab, selectedFolder) {
        if (viewMode != 1 && activeFilterTab != "FOLDERS" && activeFilterTab != "HIDDEN" && activeFilterTab != "EXCLUDED" && selectedFolder == null) {
            emptyMap()
        } else {
            val filteredList = if (showHiddenSetting) {
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

    fun isFolderExcluded(folderKey: String, items: List<MediaItem>?): Boolean {
        val lowerKey = folderKey.lowercase()
        val folderName = lowerKey.substringAfterLast('/')
        if (com.medianest.util.FolderHiddenUtils.isFolderExcludedByDefault(folderKey, folderName)) return true
        if (lowerKey in allHiddenImageFolders || folderName in allHiddenImageFolders) return true
        return items?.any { it.isExcluded } == true
    }

    fun isFolderSystemHidden(folderKey: String, items: List<MediaItem>?): Boolean {
        if (isFolderExcluded(folderKey, items)) return false
        val lowerKey = folderKey.lowercase().trim('/')
        val folderName = lowerKey.substringAfterLast('/')
        return lowerKey.split('/').any { it.startsWith(".") && it.length > 1 } || folderName.startsWith(".")
    }

    fun isFolderHidden(folderKey: String, items: List<MediaItem>?): Boolean {
        return isFolderExcluded(folderKey, items) || isFolderSystemHidden(folderKey, items)
    }

    val sortedFolderNames = remember(folderGroups, allHiddenImageFolders, sortField, isAscending) {
        if (folderGroups.isEmpty()) emptyList()
        else {
            val keys = folderGroups.keys.toList()
            val comp = when (sortField) {
                "Name" -> compareBy<String> { it.lowercase() }
                "Date" -> compareBy<String> { folderName ->
                    folderGroups[folderName]?.maxOfOrNull { maxOf(it.dateAdded, it.dateCreated) } ?: 0L
                }
                "Size" -> compareBy<String> { folderName ->
                    folderGroups[folderName]?.sumOf { it.size } ?: 0L
                }
                else -> compareBy<String> { it.lowercase() }
            }

            val baseSorted = if (isAscending) keys.sortedWith(comp) else keys.sortedWith(comp).reversed()
            baseSorted.sortedBy { fn -> isFolderHidden(fn, folderGroups[fn]) && activeFilterTab != "HIDDEN" && activeFilterTab != "EXCLUDED" }
        }
    }

    val imageMinSize = when (gridSizeLevel) {
        0 -> 100.dp
        2 -> 180.dp
        3 -> 220.dp
        else -> 135.dp
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            Box(modifier = Modifier.weight(1f)) {
                val visibleFolders = remember(sortedFolderNames, folderGroups, activeFilterTab, allHiddenImageFolders, showHiddenSetting) {
                    when (activeFilterTab) {
                        "HIDDEN" -> sortedFolderNames.filter { fn -> isFolderSystemHidden(fn, folderGroups[fn]) }
                        "EXCLUDED" -> sortedFolderNames.filter { fn -> isFolderExcluded(fn, folderGroups[fn]) }
                        else -> sortedFolderNames.filter { fn ->
                            val items = folderGroups[fn]
                            !isFolderExcluded(fn, items) && (showHiddenSetting || !isFolderSystemHidden(fn, items))
                        }
                    }
                }

                val displayList = remember(imagesList, activeFilterTab, favoriteUris, showHiddenSetting) {
                    val baseList = when (activeFilterTab) {
                        "EXCLUDED" -> imagesList.filter { it.isExcluded || com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
                        "HIDDEN" -> imagesList.filter { it.isHidden && !it.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
                        else -> imagesList.filter { item ->
                            !item.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(item) &&
                            (showHiddenSetting || !item.isHidden)
                        }
                    }

                    if (activeFilterTab == "ALL" || activeFilterTab == "EXCLUDED" || activeFilterTab == "HIDDEN") {
                        baseList
                    } else {
                        filterImageList(baseList, activeFilterTab, favoriteUris)
                    }
                }

                val currentDisplayList = if ((viewMode == 1 || activeFilterTab == "FOLDERS" || activeFilterTab == "HIDDEN") && selectedFolder != null) {
                    folderGroups[selectedFolder]
                        ?: folderGroups.entries.firstOrNull { (k, _) ->
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
                    displayList
                }

                val sortedDisplayList = remember(currentDisplayList, sortField, isAscending) {
                    if (sortField == "Date" && !isAscending) {
                        currentDisplayList
                    } else {
                        val comp = when (sortField) {
                            "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                            "Type" -> compareBy<MediaItem> { it.mimeType.lowercase() }
                            "Size" -> compareBy<MediaItem> { it.size }
                            else -> compareBy<MediaItem> { it.dateAdded }
                        }
                        if (isAscending) currentDisplayList.sortedWith(comp) else currentDisplayList.sortedWith(comp).reversed()
                    }
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
                            onFolderDeleteRequest = { folderToDelete = it },
                            onFolderInfoRequest = { folderForInfo = it },
                            isFolderHidden = ::isFolderHidden,
                            onToggleFolderHidden = { folderName, folderItems, isHidden ->
                                scope.launch {
                                    val current = settingsManager.hiddenFolders.first()
                                    val folderPathKey = folderItems.firstOrNull()?.relativePath?.trim('/') ?: folderName
                                    if (isHidden) {
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
                            onInfoItemChange = { infoItem = it },
                            onImageToDeleteChange = { imageToDelete = it },
                            onContextSheetItemChange = { contextSheetItem = it },
                            onRemoveFromCategory = {},
                            selectedCategory = null,
                            isLoading = isLoading,
                            activeFilterTab = activeFilterTab,
                            targetImageUri = targetImageUri,
                            onTargetImageChange = { targetImageUri = it },
                            gridState = mainGridState,
                            pagedImages = if (viewMode == 0 && activeFilterTab == "ALL" && selectedFolder == null) pagedImages else null
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
            MoveImageFolderDialog(
                folderName = folderToMove!!,
                folderGroups = folderGroups,
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

        if (folderForInfo != null) {
            ImageFolderInfoDialog(
                srcFolder = folderForInfo!!,
                folderGroups = folderGroups,
                context = currentContext,
                onDismiss = { folderForInfo = null }
            )
        }

        if (contextSheetItem != null) {
            val activeItem = contextSheetItem!!
            val isDark = LocalDarkTheme.current

            GlassDropdownMenu(
                expanded = true,
                onDismissRequest = { contextSheetItem = null },
                modifier = Modifier.width(220.dp),
                shape = RoundedCornerShape(20.dp)
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
                    text = { Text("View Image", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        val listForContext = when {
                            selectedFolder != null -> folderGroups[selectedFolder] ?: emptyList()
                            else -> imagesList
                        }
                        onImageClick(target, listForContext)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        onImageLongClick(target)
                    }
                )

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
            AlertDialog(
                onDismissRequest = { imageToDelete = null },
                title = { Text("Delete Image File") },
                text = { Text("Are you sure you want to delete '${target.title}'? This will permanently remove the image file from your device storage.") },
                confirmButton = {
                    Button(
                        onClick = {
                            imageToDelete = null
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
                    TextButton(onClick = { imageToDelete = null }) {
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
                    activeFilterTab = "FOLDERS"
                    viewMode = 1
                    targetImageUri = item.uri.toString()
                    val relPath = item.relativePath?.trim('/')
                    val folderKey = if (!relPath.isNullOrBlank()) relPath else (item.bucketName ?: "Pictures")
                    selectedFolder = folderGroups.keys.firstOrNull { key ->
                        key.equals(folderKey, ignoreCase = true) || key.lowercase().endsWith(folderKey.lowercase()) || folderKey.lowercase().endsWith(key.lowercase())
                    } ?: folderKey
                }
            )
        }

        if (itemToRename != null) {
            RenameFileDialog(
                item = itemToRename!!,
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