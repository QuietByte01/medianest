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
import com.medianest.MediaNestApp
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.MediaType
import com.medianest.data.db.SelectiveHiddenFolder
import com.medianest.data.model.MediaItem
import com.medianest.data.settings.SettingsManager
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.components.RenameFileDialog
import com.medianest.ui.components.SortRow
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.FolderHiddenUtils
import com.medianest.util.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImagesTab(
    imagesList: List<MediaItem>,
    imageCollections: List<MediaCategory> = emptyList(),
    categoryCrossRefs: List<CategoryMediaCrossRef> = emptyList(),
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridGapDp: Int,
    gridSizeLevel: Int = 1,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    isLoading: Boolean = false,
    onCreateCollection: (String, List<String>, String?) -> Unit = { _, _, _ -> },
    onUpdateCollection: (Long, String, List<String>, String?) -> Unit = { _, _, _, _ -> },
    onDeleteCollection: (Long) -> Unit = {},
    onImageClick: (MediaItem, List<MediaItem>) -> Unit,
    onImageLongClick: (MediaItem) -> Unit,
    onBackToDashboard: () -> Unit = {}
) {
    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeFilterTab by remember { mutableStateOf("ALL") }
    var viewMode by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<MediaCategory?>(null) }

    val settingsManager = MediaNestApp.instance.settingsManager
    val persistedSortField by settingsManager.imageSortField.collectAsState(initial = "Date")
    val persistedSortAscending by settingsManager.imageSortAscending.collectAsState(initial = false)
    
    val sortField = persistedSortField
    val isAscending = persistedSortAscending

    val db = remember { MediaNestApp.instance.database }
    val observedCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())
    val effectiveCrossRefs = if (observedCrossRefs.isNotEmpty()) observedCrossRefs else categoryCrossRefs

    val allImageCategories by db.categoryDao().getCategoriesByType("IMAGE").collectAsState(initial = emptyList())
    val favoriteCat = remember(allImageCategories) { allImageCategories.find { it.name.equals("Favorites", ignoreCase = true) } }

    val favoriteUris = remember(effectiveCrossRefs, favoriteCat) {
        if (favoriteCat != null) effectiveCrossRefs.filter { it.categoryId == favoriteCat.id }.map { it.mediaUri }.toSet()
        else emptySet()
    }

    val trashUris by remember { mutableStateOf(setOf<String>()) }
    // Trash functionality commented out
    // var trashedItems by remember { mutableStateOf<List<com.medianest.util.TrashedMediaItem>>(emptyList()) }
    // LaunchedEffect(currentContext) {
    //     withContext(Dispatchers.IO) {
    //         trashedItems = TrashManager.getTrashedItems(currentContext)
    //     }
    // }

    var filterCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(imagesList, favoriteUris) {
        kotlinx.coroutines.delay(600)
        withContext(Dispatchers.Default) {
            val counts = mutableMapOf<String, Int>()
            val ids = listOf("CAMERA", "FAVORITES", "NOTES", "SCREENSHOTS", "GIFS", "SOCIAL", "PNG_SVG", "EDITED", "AI_GENERATED", "ANIME", "WALLPAPERS")
            ids.forEach { id ->
                counts[id] = filterImageList(imagesList, id, favoriteUris, emptySet()).size
            }
            // counts["TRASH"] = 0
            filterCounts = counts
        }
    }

    var isFolderSelectionActive by remember { mutableStateOf(false) }
    var selectedFolderNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    var showSortMenu by remember { mutableStateOf(false) }

    var showEditCollectionDialog by remember { mutableStateOf(false) }
    var editCollectionName by remember { mutableStateOf("") }
    var editSelectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showUngroupConfirmDialog by remember { mutableStateOf(false) }

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
    val collectionsGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

    // Smooth Scroll on Sort Change
    var isInitialSortEffect by remember { mutableStateOf(true) }
    LaunchedEffect(sortField, isAscending) {
        if (isInitialSortEffect) {
            isInitialSortEffect = false
            return@LaunchedEffect
        }
        scope.launch {
            try {
                when {
                    selectedCategory != null || viewMode == 2 -> {
                        if (collectionsGridState.layoutInfo.totalItemsCount > 0) collectionsGridState.animateScrollToItem(0)
                    }
                    viewMode == 1 && selectedFolder == null -> {
                        if (folderGridState.layoutInfo.totalItemsCount > 0) folderGridState.animateScrollToItem(0)
                    }
                    else -> {
                        if (mainGridState.layoutInfo.totalItemsCount > 0) mainGridState.animateScrollToItem(0)
                    }
                }
            } catch (_: Exception) {
                // Ignore if list is not yet ready
            }
        }
    }

    BackHandler(
        enabled = showUngroupConfirmDialog || showEditCollectionDialog || showCreateCollectionDialog ||
                isFolderSelectionActive || selectedFolder != null || selectedCategory != null || viewMode != 0
    ) {
        when {
            showUngroupConfirmDialog -> showUngroupConfirmDialog = false
            showEditCollectionDialog -> showEditCollectionDialog = false
            showCreateCollectionDialog -> showCreateCollectionDialog = false
            isFolderSelectionActive -> {
                isFolderSelectionActive = false
                selectedFolderNames = emptySet()
            }
            selectedFolder != null -> selectedFolder = null
            selectedCategory != null -> selectedCategory = null
            viewMode != 0 -> viewMode = 0
        }
    }

    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)

    val folderGroups = remember(imagesList, showHiddenSetting, viewMode, activeFilterTab) {
        if (viewMode != 1 && activeFilterTab != "FOLDERS" && activeFilterTab != "HIDDEN") {
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

    fun isFolderHidden(folderKey: String, items: List<MediaItem>?): Boolean {
        val lowerKey = folderKey.lowercase()
        val folderName = lowerKey.substringAfterLast('/')
        if (folderKey.startsWith(".") || folderName.startsWith(".")) return true
        if (lowerKey in allHiddenImageFolders || folderName in allHiddenImageFolders) return true
        if (items.isNullOrEmpty()) return false
        return items.any { item ->
            item.title.startsWith(".") ||
                    (item.relativePath != null && item.relativePath.contains("/."))
        }
    }

    val sortedFolderNames = remember(folderGroups, allHiddenImageFolders) {
        if (folderGroups.isEmpty()) emptyList()
        else {
            folderGroups.keys.sortedWith(
                compareByDescending<String> { fn -> isFolderHidden(fn, folderGroups[fn]) }
                    .thenBy { it.lowercase() }
            )
        }
    }

    val categoryFolderMap = remember(effectiveCrossRefs) {
        effectiveCrossRefs.groupBy({ it.categoryId }, { it.mediaUri })
    }

    val cardShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)

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
                selectedCategory = selectedCategory,
                onSelectedCategoryChange = { selectedCategory = it },
                onSelectedFolderChange = { selectedFolder = it },
                onShowHiddenFiles = { scope.launch { settingsManager.setShowHiddenFiles(true) } },
                filterCounts = filterCounts
            )

            SortRow(
                sortField = sortField,
                onSortFieldChange = { scope.launch { settingsManager.setImageSortField(it) } },
                isAscending = isAscending,
                onIsAscendingChange = { scope.launch { settingsManager.setImageSortAscending(it) } },
                isVisible = isSortVisible.value,
                onBack = when {
                    selectedCategory != null -> ({ selectedCategory = null })
                    selectedFolder != null -> ({ selectedFolder = null })
                    activeFilterTab != "ALL" -> ({ activeFilterTab = "ALL" })
                    viewMode == 1 -> ({ viewMode = 0 })
                    else -> onBackToDashboard
                },
                backLabel = when {
                    selectedCategory != null -> selectedCategory!!.name
                    selectedFolder != null -> selectedFolder!!.substringAfterLast('/')
                    activeFilterTab != "ALL" -> "All Photos"
                    viewMode == 1 -> "All Photos"
                    else -> "Dashboard"
                }
            )


            Box(modifier = Modifier.weight(1f)) {
                val visibleFolders = remember(sortedFolderNames, folderGroups, activeFilterTab, allHiddenImageFolders) {
                    if (activeFilterTab == "HIDDEN") {
                        sortedFolderNames.filter { isFolderHidden(it, folderGroups[it]) }
                    } else {
                        sortedFolderNames
                    }
                }

                val displayList = remember(imagesList, activeFilterTab, favoriteUris, trashUris) {
                    if (activeFilterTab == "ALL") {
                        imagesList
                    } else {
                        filterImageList(imagesList, activeFilterTab, favoriteUris, trashUris)
                    }
                }

                val currentDisplayList = if (viewMode == 1 && selectedFolder != null) {
                    folderGroups[selectedFolder] ?: emptyList()
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
                    selectedCategory != null || viewMode == 2 -> {
                        // Filter out empty collections for display in Grid view, but allow selected one to show
                        val displayCollections = remember(allImageCategories, categoryFolderMap, imagesList) {
                            allImageCategories.filter { cat ->
                                val memberFolders = categoryFolderMap[cat.id] ?: emptyList()
                                val hasItems = imagesList.any { (it.bucketName ?: "Pictures") in memberFolders }
                                hasItems || cat.id == selectedCategory?.id
                            }
                        }

                        CollectionViews(
                            selectedCategory = selectedCategory,
                            onSelectedCategoryChange = { selectedCategory = it },
                            imageCollections = displayCollections,
                            categoryFolderMap = categoryFolderMap,
                            imagesList = imagesList,
                            isLoading = isLoading,
                            viewMode = viewMode,
                            onViewModeChange = { viewMode = it },
                            onIsFolderSelectionActiveChange = { isFolderSelectionActive = it },
                            onEditCollectionRequest = { cat, folders ->
                                editCollectionName = cat.name
                                editSelectedFolders = folders
                                showEditCollectionDialog = true
                            },
                            onImageClick = onImageClick,
                            onImageLongClick = onImageLongClick,
                            onContextSheetItemChange = { contextSheetItem = it },
                            selectedUris = selectedUris,
                            isSelectionMode = isSelectionMode,
                            cornerRadiusDp = cornerRadiusDp,
                            roundedCornersEnabled = roundedCornersEnabled,
                            gridGapDp = gridGapDp,
                            imageMinSize = imageMinSize,
                            gridState = collectionsGridState
                        )
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
                                        settingsManager.setHiddenFolders(current + folderPathKey)
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
                            onRemoveFromCategory = { item ->
                                scope.launch(Dispatchers.IO) {
                                    db.categoryDao().removeMediaFromCategory(selectedCategory!!.id, item.uri.toString())
                                }
                            },
                            selectedCategory = selectedCategory,
                            isLoading = isLoading,
                            activeFilterTab = activeFilterTab,
                            gridState = mainGridState
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
            onGroupClick = {
                newCollectionName = if (selectedFolderNames.size == 1) {
                    selectedFolderNames.first() + " Collection"
                } else {
                    "New Collection"
                }
                showCreateCollectionDialog = true
            },
            onClearSelection = {
                isFolderSelectionActive = false
                selectedFolderNames = emptySet()
            }
        )

        if (showCreateCollectionDialog) {
            CreateCollectionDialog(
                selectedFolderNames = selectedFolderNames,
                initialCollectionName = newCollectionName,
                imagesList = imagesList,
                onCreateCollection = { name, folders, cover ->
                    onCreateCollection(name, folders, cover)
                    isFolderSelectionActive = false
                    selectedFolderNames = emptySet()
                    viewMode = 2
                },
                onDismiss = { showCreateCollectionDialog = false }
            )
        }

        if (showEditCollectionDialog && selectedCategory != null) {
            val memberFolders = categoryFolderMap[selectedCategory!!.id] ?: emptyList()
            EditCollectionDialog(
                selectedCategory = selectedCategory!!,
                initialFolders = memberFolders.toSet(),
                folderGroups = folderGroups,
                imagesList = imagesList,
                onUpdateCollection = onUpdateCollection,
                onUngroupRequest = {
                    showEditCollectionDialog = false
                    showUngroupConfirmDialog = true
                },
                onDismiss = { showEditCollectionDialog = false }
            )
        }

        if (showUngroupConfirmDialog && selectedCategory != null) {
            UngroupConfirmDialog(
                categoryName = selectedCategory!!.name,
                onConfirmUngroup = {
                    showUngroupConfirmDialog = false
                    onDeleteCollection(selectedCategory!!.id)
                    selectedCategory = null
                    viewMode = 2
                },
                onDismiss = { showUngroupConfirmDialog = false }
            )
        }

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
            val isDark = LocalDarkTheme.current
            val activeItem = contextSheetItem!!
            val menuBg = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF)

            DropdownMenu(
                expanded = true,
                onDismissRequest = { contextSheetItem = null },
                containerColor = menuBg,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.width(220.dp).border(1.dp, if (isDark) Color(0x28FFFFFF) else Color(0x33000000), RoundedCornerShape(20.dp))
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
                    text = { Text("View Image", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        val listForContext = when {
                            selectedCategory != null -> imagesList.filter { (it.bucketName ?: "Pictures") in (categoryFolderMap[selectedCategory!!.id] ?: emptyList()) }
                            selectedFolder != null -> folderGroups[selectedFolder] ?: emptyList()
                            else -> imagesList
                        }
                        onImageClick(target, listForContext)
                    }
                )
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
                    text = { Text("Select", color = if (isDark) Color.White else Color.Black) },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        val target = activeItem
                        contextSheetItem = null
                        onImageLongClick(target)
                    }
                )

                if (selectedCategory != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from Collection", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            val target = activeItem
                            val cat = selectedCategory!!
                            contextSheetItem = null
                            scope.launch(Dispatchers.IO) {
                                db.categoryDao().removeMediaFromCategory(cat.id, target.uri.toString())
                            }
                        }
                    )
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
                    selectedCategory = null
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