package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.ui.components.GlassSurface
import com.example.ui.components.AdaptiveBottomSheet
import com.example.data.model.MediaItem
import com.example.ui.components.MediaGridItem
import com.example.ui.components.MediaLoadingAnimation

@OptIn(ExperimentalMaterial3Api::class)
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
    onImageLongClick: (MediaItem) -> Unit
) {
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { com.example.data.settings.SettingsManager(currentContext) }

    var activeFilterTab by remember { mutableStateOf("ALL") } // "ALL", "FOLDERS", "CAMERA", "FAVORITES", "TRASH", "HIDDEN"
    var viewMode by remember { mutableIntStateOf(0) } // 0: All, 1: Folders, 2: Collections
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<MediaCategory?>(null) }
    val db = remember { com.example.MediaNestApp.instance.database }
    val categoryCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())
    val allImageCategories by db.categoryDao().getCategoriesByType("IMAGE").collectAsState(initial = emptyList())
    val favoriteCat = remember(allImageCategories) { allImageCategories.find { it.name.equals("Favorites", ignoreCase = true) } }
    val favoriteUris = remember(categoryCrossRefs, favoriteCat) {
        if (favoriteCat != null) categoryCrossRefs.filter { it.categoryId == favoriteCat.id }.map { it.mediaUri }.toSet()
        else emptySet()
    }


    val trashUris by remember { mutableStateOf(setOf<String>()) }

    // Folder selection mode for grouping
    var isFolderSelectionActive by remember { mutableStateOf(false) }
    var selectedFolderNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Dialog states
    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    var showEditCollectionDialog by remember { mutableStateOf(false) }
    var editCollectionName by remember { mutableStateOf("") }
    var editSelectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showUngroupConfirmDialog by remember { mutableStateOf(false) }

    // Folder Actions State
    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    var imageToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var contextSheetItem by remember { mutableStateOf<MediaItem?>(null) }

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

    // Folder groupings
    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)

    val folderGroups = remember(imagesList, showHiddenSetting) {
        // 1. Filter out hidden items first if the setting is disabled
        val filteredList = if (showHiddenSetting) {
            imagesList
        } else {
            imagesList.filter { item ->
                val relPath = item.relativePath?.trim('/') ?: ""
                // Keep it ONLY if it does NOT contain a hidden folder segment
                !relPath.split('/').any { it.startsWith(".") && it.length > 1 }
            }
        }

        // 2. Group the remaining images using their full paths
        filteredList.groupBy { item ->
            val relPath = item.relativePath?.trim('/')
            if (!relPath.isNullOrBlank()) relPath else item.bucketName ?: "Pictures"
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
        appHiddenFolders + selectiveImageHidden
    }

    fun isFolderHidden(folderKey: String, items: List<MediaItem>?): Boolean {
        val folderName = folderKey.substringAfterLast('/')
        if (folderKey.startsWith(".") || folderName.startsWith(".")) return true
        if (folderKey in allHiddenImageFolders || folderName in allHiddenImageFolders) return true
        if (items.isNullOrEmpty()) return false
        return items.any { item ->
            item.title.startsWith(".") ||
            (item.relativePath != null && (
                allHiddenImageFolders.any { hidden ->
                    hidden.equals(item.relativePath.trimEnd('/'), ignoreCase = true) ||
                    item.relativePath.contains("/$hidden") ||
                    item.relativePath.startsWith("$hidden/") ||
                    hidden.equals(folderKey, ignoreCase = true)
                } ||
                item.relativePath.contains("/.") ||
                item.relativePath.startsWith(".") ||
                item.relativePath.split("/").any { it.startsWith(".") }
            ))
        }
    }

    val sortedFolderNames = remember(folderGroups) {
        folderGroups.keys.sortedWith(
            compareByDescending<String> { fn -> isFolderHidden(fn, folderGroups[fn]) }
                .thenBy { it.lowercase() }
        )
    }

    // Category folder mappings
    val categoryFolderMap = remember(categoryCrossRefs) {
        categoryCrossRefs.groupBy({ it.categoryId }, { it.mediaUri })
    }

    val cardShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)
    
    val imageMinSize = when (gridSizeLevel) {
        0 -> 100.dp
        2 -> 180.dp
        3 -> 220.dp
        else -> 135.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mode & Collections Filter Chips Bar (Exact order matching images.tablets.png)
            // Mode & Collections Filter Chips Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. All Photos
                item {
                    val isSelected = activeFilterTab == "ALL" && viewMode == 0 && selectedCategory == null
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "ALL"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("All Photos", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 2. Folders & Albums
                item {
                    val isSelected = activeFilterTab == "FOLDERS" && viewMode == 1 && selectedCategory == null
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "FOLDERS"
                                viewMode = 1
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FolderCopy, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Folders & Albums", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 3. Camera
                item {
                    val isSelected = activeFilterTab == "CAMERA"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "CAMERA"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Camera", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 4. Favorites
                item {
                    val isSelected = activeFilterTab == "FAVORITES"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "FAVORITES"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Favorites", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 14. Notes (Moved before Screenshots)
                item {
                    val isSelected = activeFilterTab == "NOTES"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "NOTES"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Note, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Notes & Studies", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 13. Screenshots (Moved before GIFs)
                item {
                    val isSelected = activeFilterTab == "SCREENSHOTS"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "SCREENSHOTS"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Screenshot, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Screenshots", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 7. GIFs
                item {
                    val isSelected = activeFilterTab == "GIFS"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "GIFS"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Animation, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("GIFs", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 6. Social Media
                item {
                    val isSelected = activeFilterTab == "SOCIAL"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "SOCIAL"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Social Media", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 8. PNG & SVG
                item {
                    val isSelected = activeFilterTab == "PNG_SVG"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "PNG_SVG"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.HighQuality, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("PNG & SVG", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 9. Edited
                item {
                    val isSelected = activeFilterTab == "EDITED"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "EDITED"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Edited", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                        }
                    }
                }

                // 10. AI Generated
                item {
                    val isSelected = activeFilterTab == "AI_GENERATED"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "AI_GENERATED"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("AI Generated", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 11. Anime
                item {
                    val isSelected = activeFilterTab == "ANIME"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "ANIME"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Anime", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 12. Wallpapers
                item {
                    val isSelected = activeFilterTab == "WALLPAPERS"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "WALLPAPERS"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Wallpapers", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 15. Hidden Folders
                item {
                    val isSelected = activeFilterTab == "HIDDEN"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "HIDDEN"
                                viewMode = 1
                                selectedFolder = null
                                selectedCategory = null
                                scope.launch { settingsManager.setShowHiddenFiles(true) }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Hidden Folders", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }

                // 5. Trash (Moved to Last)
                item {
                    val isSelected = activeFilterTab == "TRASH"
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                activeFilterTab = "TRASH"
                                viewMode = 0
                                selectedFolder = null
                                selectedCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFC0C5D0), modifier = Modifier.size(16.dp))
                            Text("Trash", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C5D0))
                        }
                    }
                }
            }

            // Main Content Area
            when {
                // 1. Viewing a Specific Image Collection
                selectedCategory != null -> {
                    val memberFolders = categoryFolderMap[selectedCategory!!.id] ?: emptyList()
                    val collectionImages = remember(imagesList, memberFolders) {
                        imagesList.filter { (it.bucketName ?: "Pictures") in memberFolders }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Collection Header Card
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = cardShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { selectedCategory = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedCategory!!.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = if (memberFolders.isEmpty()) "No folders assigned"
                                        else "Folders: ${memberFolders.joinToString(", ")} • ${collectionImages.size} items",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = {
                                    editCollectionName = selectedCategory!!.name
                                    editSelectedFolders = memberFolders.toSet()
                                    showEditCollectionDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Edit Collection Settings",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (collectionImages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No images in this collection",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap settings above to add folders to this collection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Adaptive(minSize = imageMinSize),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                                horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                                verticalItemSpacing = Dp(gridGapDp.toFloat())
                            ) {
                                items(collectionImages, key = { it.id }) { item ->
                                    MediaGridItem(
                                        item = item,
                                        isSelected = selectedUris.contains(item.uri.toString()),
                                        isSelectionMode = isSelectionMode,
                                        cornerRadiusDp = cornerRadiusDp,
                                        roundedCornersEnabled = roundedCornersEnabled,
                                        onClick = { onImageClick(item, collectionImages) },
                                        onLongClick = { onImageLongClick(item) },
                                        onMoreClick = { contextSheetItem = item }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Single Folder View
                viewMode == 1 && selectedFolder != null -> {
                    val folderImages = remember(folderGroups, selectedFolder) {
                        folderGroups[selectedFolder] ?: emptyList()
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedFolder = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to folders")
                            }
                            Text(
                                text = selectedFolder!!.substringAfterLast('/'),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${folderImages.size} items",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = imageMinSize),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                            horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                            verticalItemSpacing = Dp(gridGapDp.toFloat())
                        ) {
                            items(folderImages, key = { it.id }) { item ->
                                MediaGridItem(
                                    item = item,
                                    isSelected = selectedUris.contains(item.uri.toString()),
                                    isSelectionMode = isSelectionMode,
                                    cornerRadiusDp = cornerRadiusDp,
                                    roundedCornersEnabled = roundedCornersEnabled,
                                    onClick = { onImageClick(item, folderImages) },
                                    onLongClick = { onImageLongClick(item) },
                                    onInfo = { infoItem = item },
                                    onDelete = { imageToDelete = item },
                                    showRemoveOption = selectedCategory != null,
                                    onRemoveFromCategory = {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            db.categoryDao().removeMediaFromCategory(selectedCategory!!.id, item.uri.toString())
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Folders Grid View
                viewMode == 1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val visibleFolders = remember(sortedFolderNames, folderGroups, activeFilterTab, allHiddenImageFolders) {
                            if (activeFilterTab == "HIDDEN") {
                                sortedFolderNames.filter { isFolderHidden(it, folderGroups[it]) }
                            } else {
                                sortedFolderNames
                            }
                        }

                        // Sub-header controls for folder selection mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFolderSelectionActive) "${selectedFolderNames.size} Folders Selected" else if (activeFilterTab == "HIDDEN") "Hidden Folders (${visibleFolders.size})" else "All Folders (${folderGroups.size})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFFC0C5D0)
                            )

                            Row {
                                if (isFolderSelectionActive) {
                                    TextButton(onClick = {
                                        selectedFolderNames = if (selectedFolderNames.size == folderGroups.size) {
                                            emptySet()
                                        } else {
                                            folderGroups.keys.toSet()
                                        }
                                    }) {
                                        Text(if (selectedFolderNames.size == folderGroups.size) "Deselect All" else "Select All", color = Color(0xFFC0C5D0))
                                    }

                                    TextButton(onClick = {
                                        isFolderSelectionActive = false
                                        selectedFolderNames = emptySet()
                                    }) {
                                        Text("Done", color = Color(0xFFC0C5D0))
                                    }
                                } else {
                                    TextButton(onClick = {
                                        isFolderSelectionActive = true
                                    }) {
                                        Icon(Icons.Default.Checklist, contentDescription = null, tint = Color(0xFFC0C5D0), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Select Folders", color = Color(0xFFC0C5D0))
                                    }
                                }
                            }
                        }

                        if (visibleFolders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlassSurface(
                                    modifier = Modifier.padding(16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = Color(0x221C1F2B),
                                    borderColor = Color(0x28FFFFFF)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFC0C5D0), modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("No Hidden Folders", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Folders marked hidden will be listed here", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(visibleFolders, key = { it }) { folderName ->
                                val folderItems = folderGroups[folderName] ?: emptyList()
                                val isChecked = selectedFolderNames.contains(folderName)
                                val isHidden = isFolderHidden(folderName, folderItems)

                                val totalSizeBytes = remember(folderItems) { folderItems.sumOf { it.size } }
                                val formattedSize = remember(totalSizeBytes) {
                                    val mb = totalSizeBytes / (1024.0 * 1024.0)
                                    if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
                                }
                                val samplePath = remember(folderItems) {
                                    folderItems.firstOrNull()?.relativePath?.trimEnd('/')?.let { "/$it" } ?: "/DCIM/$folderName"
                                }

                                GlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = if (isChecked) Color(0x44C0C0C0) else Color(0x221C1F2B),
                                    borderColor = if (isChecked) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            if (isFolderSelectionActive) {
                                                selectedFolderNames = if (isChecked) {
                                                    selectedFolderNames - folderName
                                                } else {
                                                    selectedFolderNames + folderName
                                                }
                                            } else {
                                                selectedFolder = folderName
                                            }
                                        }
                                ) {
                                    Box {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp)
                                        ) {
                                            // Thumbnail Block (4-quadrant collage or single cover)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF181B26)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (folderItems.size >= 4) {
                                                    // 2x2 Collage
                                                    Column(modifier = Modifier.fillMaxSize()) {
                                                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                            AsyncImage(
                                                                model = folderItems[0].uri,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                                            )
                                                            Spacer(modifier = Modifier.width(1.dp))
                                                            AsyncImage(
                                                                model = folderItems[1].uri,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(1.dp))
                                                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                            AsyncImage(
                                                                model = folderItems[2].uri,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                                            )
                                                            Spacer(modifier = Modifier.width(1.dp))
                                                            AsyncImage(
                                                                model = folderItems[3].uri,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                                            )
                                                        }
                                                    }
                                                } else if (folderItems.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = folderItems[0].uri,
                                                        contentDescription = folderName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = Color(0xFF9EA3B0),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                }

                                                // Bottom Right Pill Badge (matching images.tablets.png)
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PhotoLibrary,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = String.format("%,d", folderItems.size),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }

                                                if (isHidden) {
                                                    Surface(
                                                        color = Color.Transparent,
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.VisibilityOff,
                                                            contentDescription = "Hidden Folder",
                                                            tint = Color.White.copy(alpha = 0.85f),
                                                            modifier = Modifier.padding(2.dp).size(14.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (isHidden) "${folderName.substringAfterLast('/')} (Hidden)" else folderName.substringAfterLast('/'),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = Color.White
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = samplePath,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF9EA3B0),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = formattedSize,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF9EA3B0)
                                                )
                                            }
                                        }

                                        if (isFolderSelectionActive) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedFolderNames = if (checked == true) {
                                                        selectedFolderNames + folderName
                                                    } else {
                                                        selectedFolderNames - folderName
                                                    }
                                                },
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            )
                                        } else {
                                            var showFolderMenu by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                                IconButton(
                                                    onClick = { showFolderMenu = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Folder options",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showFolderMenu,
                                                    onDismissRequest = { showFolderMenu = false },
                                                    containerColor = Color(0xDC141722),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                        onClick = {
                                                            showFolderMenu = false
                                                            folderToDelete = folderName
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (isHidden) "Unhide Folder" else "Hide Folder") },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                                contentDescription = null
                                                            )
                                                        },
                                                        onClick = {
                                                            showFolderMenu = false
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
                                                                        com.example.data.db.SelectiveHiddenFolder(
                                                                            folderPath = folderPathKey,
                                                                            folderName = folderName,
                                                                            mediaType = "IMAGE",
                                                                            isHidden = true
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Folder Info") },
                                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                                        onClick = {
                                                            showFolderMenu = false
                                                            folderForInfo = folderName
                                                        }
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

                // 4. Collections Grid View
                viewMode == 2 -> {
                    if (imageCollections.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FolderCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Collections Created",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select folders in the Folders view and tap 'Group into Collection' to create one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    viewMode = 1
                                    isFolderSelectionActive = true
                                }) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select Folders to Group")
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(imageCollections, key = { it.id }) { cat ->
                                val memberFolders = categoryFolderMap[cat.id] ?: emptyList()
                                val collectionImages = remember(imagesList, memberFolders) {
                                    imagesList.filter { (it.bucketName ?: "Pictures") in memberFolders }
                                }
                                val coverImage = collectionImages.firstOrNull()

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .clickable { selectedCategory = cat },
                                    shape = cardShape,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverImage != null) {
                                                AsyncImage(
                                                    model = coverImage.uri,
                                                    contentDescription = cat.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Collections,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = cat.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${memberFolders.size} folders • ${collectionImages.size} items",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Default All Images Grid [Please Do Not Change: Important]
                else -> {
                    val displayList = remember(imagesList, activeFilterTab, favoriteUris, trashUris) {
                        when (activeFilterTab) {
                            "CAMERA" -> imagesList.filter { item ->
                                val bucket = (item.bucketName ?: "").lowercase()
                                val path = (item.relativePath ?: "").lowercase()
                                val title = item.title.lowercase()
                                val uriStr = item.uri.toString().lowercase()
                                val full = "$path/$bucket/$uriStr/$title"

                                val isInDcim = full.contains("dcim") || bucket == "camera" || path.contains("camera") || title.startsWith("img_") || title.startsWith("pxl_")
                                if (!isInDcim) return@filter false

                                val excluded = listOf("game media", "gif", "snapchat", "instagram", "screenshots", "reddit", "twitter", "whatsapp", "facebook", "telegram", "tiktok", "pinterest", "snap", "insta", "fb")
                                val isExcluded = excluded.any { exc -> full.contains(exc) }
                                !isExcluded
                            }
                            "FAVORITES" -> imagesList.filter { favoriteUris.contains(it.uri.toString()) || it.title.lowercase().contains("fav") }
                            "TRASH" -> imagesList.filter { trashUris.contains(it.uri.toString()) }
                            "SOCIAL" -> imagesList.filter { item ->
                                val title = (item.title ?: "").lowercase()
                                val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

                                val targetFolders = listOf(
                                    "whatsapp/media", "pictures/whatsapp images", "telegram",
                                    "pictures/instagram", "download/instagram", "dcim/instagram",
                                    "pictures/facebook", "pictures/messenger", "movies/tiktok",
                                    "pictures/tiktok", "dcim/tiktok", "pictures/snapchat",
                                    "dcim/snapchat", "pictures/pinterest", "pictures/twitter",
                                    "pictures/x", "pictures/reddit"
                                )
                                val matchesFolder = targetFolders.any { path.contains(it) }

                                val socialPackages = listOf(
                                    "com.whatsapp", "org.telegram.messenger", "com.instagram.android",
                                    "com.facebook.katana", "com.zhiliaoapp.musically",
                                    "com.snapchat.android", "com.twitter.android"
                                )
                                val matchesAndroidDir = socialPackages.any { pkg ->
                                    path.contains("/android/data/$pkg") || path.contains("/android/media/$pkg")
                                }

                                val socialNames = listOf(
                                    "whatsapp", "telegram", "instagram", "insta",
                                    "facebook", "fb", "messenger", "tiktok",
                                    "snapchat", "snap", "pinterest", "twitter", "reddit"
                                )
                                val matchesTitle = socialNames.any { title.contains(it) }

                                matchesFolder || matchesAndroidDir || matchesTitle
                            }
                            "GIFS" -> imagesList.filter { item ->
                                val full = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
                                full.contains("gif")
                            }
                            "PNG_SVG" -> imagesList.filter { item ->
                                val full = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
                                full.endsWith(".png") || full.endsWith(".svg") || full.contains("png") || full.contains("svg")
                            }
                            "EDITED" -> imagesList.filter { item ->
                                val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
                                val editKeywords = listOf("edited", "snapseed", "lightroom", "picsart", "vsco", "photoshop", "canva", "enhance", "remini")
                                editKeywords.any { full.contains(it) }
                            }
                            "AI_GENERATED" -> imagesList.filter { item ->
                                val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

                                // 1. Path & keyword check
                                val aiKeywords = listOf(
                                    "ai_generated", "midjourney", "dall-e", "stable diffusion",
                                    "chatgpt", "gemini", "bing image creator", "leonardo",
                                    "civitai", "flux", "imagen", "generative fill"
                                )
                                val matchesKeywords = aiKeywords.any { path.contains(it) }

                                // 2. Specific folder path check where AI apps save images
                                val aiFolders =listOf(
                                    "pictures/midjourney",
                                    "pictures/stablediffusion",
                                    "pictures/dall-e",
                                    "pictures/bing image creator",
                                    "pictures/leonardo",
                                    "pictures/generated"
                                )
                                val matchesFolder = aiFolders.any { path.contains(it) }

                                // 3. Optional: Read EXIF metadata if you have access to the file descriptor/path
                                // (Note: Requires resolving item.uri to a physical file path or using context.contentResolver.openInputStream(item.uri))
                                /*
                                val matchesExif = try {
                                    context.contentResolver.openInputStream(item.uri)?.use { inputStream ->
                                        val exif = androidx.exifinterface.media.ExifInterface(inputStream)
                                        val software = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE)?.lowercase() ?: ""
                                        software.contains("stable") || software.contains("midjourney") || software.contains("adobe") || software.contains("generative")
                                    } ?: false
                                } catch (e: Exception) {
                                    false
                                }
                                */

                                matchesKeywords || matchesFolder // || matchesExif
                            }
                            "ANIME" -> imagesList.filter { item ->
                                val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
                                val animeKeywords = listOf("anime", "manga", "otaku", "crunchyroll", "goku", "naruto", "luffy")
                                animeKeywords.any { full.contains(it) }
                            }
                            "WALLPAPERS" -> imagesList.filter { item ->
                                val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
                                val wallpaperKeywords = listOf("wallpaper", "wallpapers", "background", "lockscreen", "zedge", "unsplash")
                                wallpaperKeywords.any { full.contains(it) }
                            }
                            "SCREENSHOTS" -> imagesList.filter { item ->
                                val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
                                full.contains("screenshot") || full.contains("screenshots")
                            }
                            "NOTES" -> imagesList.filter { item ->
                                val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()

                                // Explicitly skip WhatsApp specific folders that aren't for personal notes
                                if (path.contains("whatsapp documents") || path.contains("whatsapp video notes")) {
                                    return@filter false
                                }

                                // Use only path and title for matching, excluding URI noise
                                val full = (path + "/" + item.title).lowercase()
                                val notesKeywords = listOf(
                                    // Core notes, docs & receipts
                                    "note", "notes", "document", "documents", "doc", "scan", "scanner", "receipt", "whiteboard",

                                    // Programming Languages
                                    "kotlin", "java", "python", "javascript", "typescript", "c++", "c#", "rust", "golang", "swift",
                                    "objc", "php", "ruby", "scala", "html", "css", "sql", "bash", "shell", "assembly",

                                    // Engineering & Development Tools/Tech
                                    "programming", "coding", "developer", "development", "software", "android", "ios", "flutter",
                                    "react", "angular", "node", "git", "github", "docker", "kubernetes", "linux", "ubuntu",
                                    "terminal", "database", "api", "backend", "frontend", "algorithm", "datastructure", "syntax",
                                    "architecture", "bug", "debug", "compile", "ide", "vscode", "androidstudio", "intellij",

                                    // General Knowledge, Academics & Science
                                    "gk", "general knowledge", "history", "english", "disscussion", "discussion", "nutrients",
                                    "medical", "quotes", "isro", "nasa", "communication", "rules", "science", "physics",
                                    "chemistry", "biology", "math", "mathematics", "geography", "economics", "civics", "study",
                                    "education", "tutorial", "exam", "syllabus", "lecture", "assignment", "homework", "formula"
                                )
                                // Ensure whole word matching using regex word boundaries (\b)
                                notesKeywords.any { kw -> 
                                    val regex = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE)
                                    regex.containsMatchIn(full)
                                }
                            }
                            else -> imagesList
                        }
                    }

                    if (isLoading && displayList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            MediaLoadingAnimation(
                                mediaType = MediaType.IMAGE,
                                iconSize = 52.dp,
                                showLabel = true
                            )
                        }
                    } else if (displayList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassSurface(
                                modifier = Modifier.padding(24.dp),
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0x221C1F2B),
                                borderColor = Color(0x28FFFFFF)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = when (activeFilterTab) {
                                            "FAVORITES" -> Icons.Default.Favorite
                                            "TRASH" -> Icons.Default.Delete
                                            "CAMERA" -> Icons.Default.PhotoCamera
                                            else -> Icons.Default.PhotoLibrary
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFFC0C5D0),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = when (activeFilterTab) {
                                            "FAVORITES" -> "No Favorite Photos"
                                            "TRASH" -> "Trash is Empty"
                                            "CAMERA" -> "No Camera Photos"
                                            else -> "No Images Found"
                                        },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (activeFilterTab) {
                                            "FAVORITES" -> "Photos you mark as favorite will appear here"
                                            "TRASH" -> "Deleted photos in trash will appear here"
                                            "CAMERA" -> "Photos taken with your camera will appear here"
                                            else -> "Photos added to your device will appear here"
                                        },
                                        fontSize = 12.sp,
                                        color = Color(0xFF9EA3B0)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = imageMinSize),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                            horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                            verticalItemSpacing = Dp(gridGapDp.toFloat())
                        ) {
                            items(displayList, key = { it.id }) { item ->
                                MediaGridItem(
                                    item = item,
                                    isSelected = selectedUris.contains(item.uri.toString()),
                                    isSelectionMode = isSelectionMode,
                                    cornerRadiusDp = cornerRadiusDp,
                                    roundedCornersEnabled = roundedCornersEnabled,
                                    onClick = { onImageClick(item, displayList) },
                                    onLongClick = { onImageLongClick(item) },
                                    onInfo = { infoItem = item },
                                    onDelete = { imageToDelete = item },
                                    showRemoveOption = selectedCategory != null,
                                    onRemoveFromCategory = {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            db.categoryDao().removeMediaFromCategory(selectedCategory!!.id, item.uri.toString())
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Contextual Floating Action Bar for Folder Grouping
        AnimatedVisibility(
            visible = isFolderSelectionActive && viewMode == 1 && selectedFolder == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedFolderNames.size} folders selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Button(
                        enabled = selectedFolderNames.isNotEmpty(),
                        onClick = {
                            newCollectionName = if (selectedFolderNames.size == 1) {
                                selectedFolderNames.first() + " Collection"
                            } else {
                                "New Collection"
                            }
                            showCreateCollectionDialog = true
                        }
                    ) {
                        Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Group into Collection")
                    }
                }
            }
        }

        // Dialog: Create Collection
        if (showCreateCollectionDialog) {
            AlertDialog(
                onDismissRequest = { showCreateCollectionDialog = false },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Group Folders into Collection") },
                text = {
                    Column {
                        Text(
                            text = "Group ${selectedFolderNames.size} folder(s): ${selectedFolderNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newCollectionName,
                            onValueChange = { newCollectionName = it },
                            label = { Text("Collection Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = newCollectionName.isNotBlank(),
                        onClick = {
                            showCreateCollectionDialog = false
                            val coverImg = imagesList.firstOrNull { (it.bucketName ?: "Pictures") in selectedFolderNames }?.uri?.toString()
                            onCreateCollection(newCollectionName.trim(), selectedFolderNames.toList(), coverImg)
                            isFolderSelectionActive = false
                            selectedFolderNames = emptySet()
                            viewMode = 2 // Switch to Collections view
                        }
                    ) {
                        Text("Create Collection")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateCollectionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: Edit Collection Settings
        if (showEditCollectionDialog && selectedCategory != null) {
            AlertDialog(
                onDismissRequest = { showEditCollectionDialog = false },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Edit Collection Settings") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = editCollectionName,
                            onValueChange = { editCollectionName = it },
                            label = { Text("Collection Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Assign Folders to Collection:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        folderGroups.keys.forEach { folderName ->
                            val isChecked = editSelectedFolders.contains(folderName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editSelectedFolders = if (isChecked) {
                                            editSelectedFolders - folderName
                                        } else {
                                            editSelectedFolders + folderName
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        editSelectedFolders = if (checked == true) {
                                            editSelectedFolders + folderName
                                        } else {
                                            editSelectedFolders - folderName
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = folderName,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${folderGroups[folderName]?.size ?: 0} items",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showEditCollectionDialog = false
                                showUngroupConfirmDialog = true
                            }
                        ) {
                            Text("Ungroup Collection", color = MaterialTheme.colorScheme.error)
                        }

                        Row {
                            TextButton(onClick = { showEditCollectionDialog = false }) {
                                Text("Cancel")
                            }
                            Button(
                                enabled = editCollectionName.isNotBlank(),
                                onClick = {
                                    showEditCollectionDialog = false
                                    val coverImg = imagesList.firstOrNull { (it.bucketName ?: "Pictures") in editSelectedFolders }?.uri?.toString()
                                    onUpdateCollection(
                                        selectedCategory!!.id,
                                        editCollectionName.trim(),
                                        editSelectedFolders.toList(),
                                        coverImg
                                    )
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            )
        }

        // Dialog: Confirm Ungroup Collection
        if (showUngroupConfirmDialog && selectedCategory != null) {
            AlertDialog(
                onDismissRequest = { showUngroupConfirmDialog = false },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Ungroup Collection?") },
                text = {
                    Text("Are you sure you want to ungroup '${selectedCategory!!.name}'? This only removes the collection grouping. Your original folders and images on your device will NOT be deleted.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUngroupConfirmDialog = false
                        onDeleteCollection(selectedCategory!!.id)
                        selectedCategory = null
                        viewMode = 2
                    }) {
                        Text("Ungroup", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUngroupConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Folder Action Dialog: Move Folder
        if (folderToMove != null) {
            var targetName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { folderToMove = null },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Move Folder: $folderToMove") },
                text = {
                    Column {
                        Text("Enter target folder name to move all items from '$folderToMove':")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = targetName,
                            onValueChange = { targetName = it },
                            label = { Text("Destination Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = targetName.isNotBlank(),
                        onClick = {
                            val target = targetName.trim()
                            val srcFolder = folderToMove
                            folderToMove = null
                            if (target.isNotBlank() && srcFolder != null) {
                                val itemsToMove = folderGroups[srcFolder] ?: emptyList()
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val root = android.os.Environment.getExternalStorageDirectory()
                                    val destDir = java.io.File(root, "Pictures/$target")
                                    destDir.mkdirs()
                                    itemsToMove.forEach { item ->
                                        try {
                                            val file = java.io.File(item.uri.path ?: "")
                                            if (file.exists()) {
                                                file.copyTo(java.io.File(destDir, file.name), overwrite = true)
                                                file.delete()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Move")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToMove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Folder Action Dialog: Delete Folder
        if (folderToDelete != null) {
            val srcFolder = folderToDelete
            val itemsToDelete = folderGroups[srcFolder] ?: emptyList()
            AlertDialog(
                onDismissRequest = { folderToDelete = null },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Delete Folder: $srcFolder") },
                text = {
                    Text("Are you sure you want to delete this folder and all ${itemsToDelete.size} items inside? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            folderToDelete = null
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                itemsToDelete.forEach { item ->
                                    try {
                                        com.example.util.FolderHiddenUtils.deleteMediaUri(currentContext, item.uri)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Folder Action Dialog: Folder Info
        if (folderForInfo != null) {
            val srcFolder = folderForInfo
            val items = folderGroups[srcFolder] ?: emptyList()
            val totalSize = items.sumOf { it.size }
            AlertDialog(
                onDismissRequest = { folderForInfo = null },
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Folder Info") },
                text = {
                    Column {
                        Text("Folder Name: $srcFolder", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Total Items: ${items.size}")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Total Size: ${android.text.format.Formatter.formatFileSize(currentContext, totalSize)}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { folderForInfo = null }) {
                        Text("OK")
                    }
                }
            )
        }

        // Uniform Glossy Context Menu
        if (contextSheetItem != null) {
            val isDark = com.example.ui.theme.LocalDarkTheme.current
            val activeItem = contextSheetItem!!
            
            // Obsidian for Dark, Light Glossy for Light
            val menuBg = if (isDark) Color(0xBF0F1015) else Color(0xA6FFFFFF)

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
                        // Determine appropriate list for filmstrip
                        val listForContext = when {
                            selectedCategory != null -> imagesList.filter { (it.bucketName ?: "Pictures") in (categoryFolderMap[selectedCategory!!.id] ?: emptyList()) }
                            selectedFolder != null -> folderGroups[selectedFolder] ?: emptyList()
                            else -> imagesList // Fallback to all images
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
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

        // Warning confirmation dialog before deleting image file inside folder
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
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    com.example.util.FolderHiddenUtils.deleteMediaUri(currentContext, target.uri)
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
            com.example.ui.components.MediaInfoBottomSheet(
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
    }
}