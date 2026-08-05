package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.getFilePathFromUri
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.ui.components.GlassSurface
import com.example.data.model.MediaItem
import com.example.util.CategoryIconUtils
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.MediaGridItem
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.MediaLoadingAnimation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosTab(
    videosList: List<MediaItem>,
    categories: List<MediaCategory>,
    selectedCategory: MediaCategory?,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridGapDp: Int,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    isLoading: Boolean = false,
    onCategorySelect: (MediaCategory?) -> Unit,
    onCreateCategoryClick: () -> Unit,
    onVideoClick: (MediaItem) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    showAddVideosDialog: Boolean = false,
    onDismissAddVideosDialog: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    var activeFilterTab by remember { mutableStateOf("ALL") } // "ALL", "MUSIC", "MOVIES", "CLIPS", "SHORTS", "EDITED", "DOWNLOADED", "FOLDERS"
    var isFolderViewActive by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var contextSheetItem by remember { mutableStateOf<MediaItem?>(null) }
    var videoToDelete by remember { mutableStateOf<MediaItem?>(null) }

    // Folder Actions State
    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    var showAddVideosToCategoryDialog by remember { mutableStateOf(false) }

    // Category options state
    var categoryForOptions by remember { mutableStateOf<MediaCategory?>(null) }
    var showCategoryInfoDialog by remember { mutableStateOf(false) }
    var showCategoryDeleteConfirm by remember { mutableStateOf(false) }

    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { com.example.data.settings.SettingsManager(currentContext) }

    val db = remember { com.example.MediaNestApp.instance.database }
    val allCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())

    val defaultVideoCategories = remember {
        listOf(
            MediaCategory(id = -101, name = "Training Videos", type = "VIDEO", iconName = "school"),
            MediaCategory(id = -102, name = "Birthday Parties", type = "VIDEO", iconName = "cake"),
            MediaCategory(id = -103, name = "Travel & Vlogs", type = "VIDEO", iconName = "flight")
        )
    }

    val allVideoCategories = remember(categories, defaultVideoCategories) {
        val userNames = categories.map { it.name.lowercase() }.toSet()
        val filteredDefaults = defaultVideoCategories.filter { it.name.lowercase() !in userNames }
        categories + filteredDefaults
    }

    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)
//    val videoFolderGroups = remember(videosList, showHiddenSetting) {
//        videosList.groupBy { item ->
//            val relPath = item.relativePath?.trim('/')
//            val dotSegment = relPath?.split('/')?.firstOrNull { it.startsWith(".") && it.length > 1 }
//            when {
//                showHiddenSetting && dotSegment != null -> dotSegment
//                !relPath.isNullOrBlank() -> relPath
//                else -> item.bucketName ?: "Movies"
//            }
//        }
//    }

    val videoFolderGroups = remember(videosList, showHiddenSetting) {
        // 1. Filter out hidden items first if the setting is disabled
        val filteredList = if (showHiddenSetting) {
            videosList
        } else {
            videosList.filter { item ->
                val relPath = item.relativePath?.trim('/') ?: ""
                // Keep it ONLY if it does NOT contain a hidden folder segment
                !relPath.split('/').any { it.startsWith(".") && it.length > 1 }
            }
        }

        // 2. Group the remaining videos using their full paths
        filteredList.groupBy { item ->
            val relPath = item.relativePath?.trim('/')
            if (!relPath.isNullOrBlank()) relPath else item.bucketName ?: "Videos"
        }
    }

    val categoryUris = remember(allCrossRefs, selectedCategory, videosList) {
        if (selectedCategory != null) {
            val crossRefUris = allCrossRefs.filter { it.categoryId == selectedCategory.id }.map { it.mediaUri }.toSet()
            val filterKeywords = when (selectedCategory.name.lowercase()) {
                "workout" -> listOf("workout", "gym", "fitness", "exercise", "cardio", "lifting", "abs", "squat")
                "training videos" -> listOf("train", "tutorial", "learn", "course", "coaching", "drills", "practice")
                "birthday parties" -> listOf("birthday", "bday", "party", "celebration", "cake")
                "travel & vlogs" -> listOf("travel", "vlog", "trip", "tour", "vacation", "journey", "holiday")
                else -> emptyList()
            }
            videosList.filter { item ->
                crossRefUris.contains(item.uri.toString()) ||
                        (filterKeywords.isNotEmpty() && filterKeywords.any { kw ->
                            item.title.lowercase().contains(kw) ||
                                    (item.relativePath ?: "").lowercase().contains(kw) ||
                                    (item.bucketName ?: "").lowercase().contains(kw)
                        })
            }.map { it.uri.toString() }.toSet()
        } else emptySet()
    }

    val folderGroups = remember(videosList) {
        videosList.groupBy { it.bucketName ?: "Movies" }
    }

    BackHandler(enabled = selectedFolder != null || selectedCategory != null || isFolderViewActive || activeFilterTab != "ALL") {
        when {
            selectedFolder != null -> selectedFolder = null
            selectedCategory != null -> onCategorySelect(null)
            isFolderViewActive -> {
                isFolderViewActive = false
                activeFilterTab = "ALL"
            }
            activeFilterTab != "ALL" -> activeFilterTab = "ALL"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Filter Tabs (Exact Order: All Videos, Music Videos, Movies & Shows, Clips & Recordings, Shorts, Edited, Downloaded)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. All Videos
            item {
                val isSelected = !isFolderViewActive && selectedCategory == null && activeFilterTab == "ALL"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "ALL"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("All Videos", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 2. Music Videos
            item {
                val isSelected = activeFilterTab == "MUSIC"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "MUSIC"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Music Videos", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 3. Movies & Shows
            item {
                val isSelected = activeFilterTab == "MOVIES"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "MOVIES"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Movies & Shows", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 4. Clips & Recordings
            item {
                val isSelected = activeFilterTab == "CLIPS"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "CLIPS"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Clips & Recordings", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 5. Shorts
            item {
                val isSelected = activeFilterTab == "SHORTS"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "SHORTS"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Shorts", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 6. Edited
            item {
                val isSelected = activeFilterTab == "EDITED"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "EDITED"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Edited", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 7. Downloaded
            item {
                val isSelected = activeFilterTab == "DOWNLOADED"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "DOWNLOADED"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Downloaded", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 8. Social Media
            item {
                val isSelected = activeFilterTab == "SOCIAL"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "SOCIAL"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Social Media", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // 9. Category Tab
            item {
                val isSelected = !isFolderViewActive && activeFilterTab == "CATEGORY"
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = false
                            selectedFolder = null
                            activeFilterTab = "CATEGORY"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Category", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }

            // Folders Tab
            item {
                val isSelected = isFolderViewActive
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            isFolderViewActive = true
                            selectedFolder = null
                            activeFilterTab = "FOLDERS"
                            onCategorySelect(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF9EA3B0), modifier = Modifier.size(16.dp))
                        Text("Folders", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFF9EA3B0))
                    }
                }
            }
        }

        // Bottom chips row: Categories shown on ALL or CATEGORY tabs
        if (!isFolderViewActive && (activeFilterTab == "ALL" || activeFilterTab == "CATEGORY")) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(allVideoCategories, key = { it.id }) { cat ->
                    var showCatMenu by remember { mutableStateOf(false) }
                    val isSelected = !isFolderViewActive && selectedCategory?.id == cat.id
                    val count = remember(allCrossRefs, cat, videosList) {
                        val crossRefUris = allCrossRefs.filter { it.categoryId == cat.id }.map { it.mediaUri }.toSet()
                        val filterKeywords = when (cat.name.trim().lowercase()) {
                            "training videos" -> listOf("train", "workout", "exercise", "tutorial", "learn", "course", "fit", "video", "mp4")
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

                    Box {
                        GlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                            borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                            modifier = Modifier
                                .height(44.dp)
                                .clickable {
                                    isFolderViewActive = false
                                    selectedFolder = null
                                    onCategorySelect(if (isSelected) null else cat)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (cat.iconName) {
                                        "school" -> Icons.Default.School
                                        "cake" -> Icons.Default.Cake
                                        "flight" -> Icons.Default.Flight
                                        else -> CategoryIconUtils.getCategoryIcon(cat.iconName)
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFFC0C5D0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = cat.name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "$count Videos",
                                        fontSize = 10.sp,
                                        color = Color(0xFF9EA3B0)
                                    )
                                }
                                if (cat.id > 0) {
                                    IconButton(
                                        onClick = {
                                            categoryForOptions = cat
                                            showCatMenu = true
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Category Options",
                                            tint = Color(0xFF9EA3B0),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showCatMenu,
                            onDismissRequest = { showCatMenu = false },
                            containerColor = Color(0xDC141722),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Category Info") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showCatMenu = false
                                    categoryForOptions = cat
                                    showCategoryInfoDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Category", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showCatMenu = false
                                    categoryForOptions = cat
                                    showCategoryDeleteConfirm = true
                                }
                            )
                        }
                    }
                }

                // New Category chip button
                item {
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x221C1F2B),
                        borderColor = Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(44.dp)
                            .clickable { onCreateCategoryClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("New Category", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (isFolderViewActive && selectedFolder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedFolder = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to folders", tint = Color.White)
                }
                Text(
                    text = selectedFolder!!.substringAfterLast('/'),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }

        val displayList = remember(videosList, videoFolderGroups, activeFilterTab, isFolderViewActive, selectedFolder, selectedCategory, categoryUris) {
            if (isFolderViewActive) {
                if (selectedFolder != null) {
                    videoFolderGroups[selectedFolder] ?: emptyList()
                } else {
                    videosList
                }
            } else if (selectedCategory != null) {
                videosList.filter { categoryUris.contains(it.uri.toString()) }
            } else {
                filterVideoList(videosList, activeFilterTab)
            }
        }

        if (isLoading && videosList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MediaLoadingAnimation(
                    mediaType = MediaType.VIDEO,
                    iconSize = 52.dp,
                    showLabel = true
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
        } else if (isFolderViewActive && selectedFolder == null) {
            val appHiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())
            val db = remember { com.example.MediaNestApp.instance.database }
            val selectiveHiddenFolders by db.selectiveHiddenFolderDao().getAllHiddenFolders().collectAsState(initial = emptyList())

            val selectiveVideoHidden = remember(selectiveHiddenFolders) {
                selectiveHiddenFolders.filter { it.mediaType == "VIDEO" && it.isHidden }
                    .flatMap { listOf(it.folderPath, it.folderName) }
                    .filter { it.isNotBlank() }
                    .toSet()
            }

            val allHiddenVideoFolders = remember(appHiddenFolders, selectiveVideoHidden) {
                appHiddenFolders + selectiveVideoHidden
            }

            fun checkFolderHidden(folderKey: String, items: List<MediaItem>?): Boolean {
                val folderName = folderKey.substringAfterLast('/')
                if (folderKey.startsWith(".") || folderName.startsWith(".")) return true
                if (folderKey in allHiddenVideoFolders || folderName in allHiddenVideoFolders) return true
                if (items.isNullOrEmpty()) return false
                return items.any { item ->
                    item.title.startsWith(".") ||
                    (item.relativePath != null && (
                        allHiddenVideoFolders.any { hidden ->
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

            val sortedFolderNames = remember(videoFolderGroups) {
                videoFolderGroups.keys.sortedWith(
                    compareByDescending<String> { fn -> checkFolderHidden(fn, videoFolderGroups[fn]) }
                        .thenBy { it.lowercase() }
                )
            }

            val visibleFolders = remember(sortedFolderNames, videoFolderGroups, activeFilterTab) {
                if (activeFilterTab == "HIDDEN") {
                    sortedFolderNames.filter { fn -> checkFolderHidden(fn, videoFolderGroups[fn]) }
                } else {
                    sortedFolderNames
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Section Header matching reference image: "ALL DIRECTORY FOLDERS"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFFC0C5D0),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ALL DIRECTORY FOLDERS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFFC0C5D0)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(visibleFolders, key = { "vfolder_$it" }) { folderName ->
                        val folderItems = videoFolderGroups[folderName] ?: emptyList()
                        val isHidden = checkFolderHidden(folderName, folderItems)
                        val totalSizeBytes = remember(folderItems) { folderItems.sumOf { it.size } }
                        val formattedSize = remember(totalSizeBytes) { formatFolderSize(totalSizeBytes) }
                        val itemCountText = if (folderItems.size == 1) "1 Video" else "${folderItems.size} Videos"
                        val cardShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp.coerceAtLeast(16.dp)) else RoundedCornerShape(0.dp)

                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFolder = folderName },
                            shape = cardShape,
                            backgroundColor = if (isHidden) Color(0x22121520) else Color(0x28181C2B),
                            borderColor = Color(0x28FFFFFF)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Top Row: Folder Icon & Options Menu
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        GlassSurface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            backgroundColor = Color(0x35FFFFFF),
                                            borderColor = Color(0x4DFFFFFF)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        var showFolderMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(
                                                onClick = { showFolderMenu = true },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Folder options",
                                                    tint = Color(0xFF9EA3B0),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showFolderMenu,
                                                onDismissRequest = { showFolderMenu = false },
                                                containerColor = Color(0xDC141722),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                /* Move Option Removed */
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
                                                                db.selectiveHiddenFolderDao().unhideFolder(folderName, "VIDEO")
                                                                db.selectiveHiddenFolderDao().unhideFolder(folderPathKey, "VIDEO")
                                                            } else {
                                                                settingsManager.setHiddenFolders(current + folderPathKey)
                                                                db.selectiveHiddenFolderDao().insertOrUpdate(
                                                                    com.example.data.db.SelectiveHiddenFolder(
                                                                        folderPath = folderPathKey,
                                                                        folderName = folderName,
                                                                        mediaType = "VIDEO",
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

                                    // Folder Title & Metadata
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = if (isHidden) "${folderName.substringAfterLast('/')} (Hidden)" else folderName.substringAfterLast('/'),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
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
                                                text = itemCountText,
                                                fontSize = 12.sp,
                                                color = Color(0xFF9EA3B0)
                                            )
                                            Text(
                                                text = formattedSize,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF9EA3B0)
                                            )
                                        }
                                    }

                                    // 3 Video Thumbnail Slots
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(68.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (i in 0..2) {
                                            if (i < folderItems.size) {
                                                val item = folderItems[i]
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF181B26))
                                                ) {
                                                    coil.compose.AsyncImage(
                                                        model = item.uri,
                                                        contentDescription = item.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            } else {
                                                // Empty slot placeholder with dashed border
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0x0CFFFFFF))
                                                        .drawWithContent {
                                                            drawContent()
                                                            val stroke = Stroke(
                                                                width = 1.dp.toPx(),
                                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                                            )
                                                            drawRoundRect(
                                                                color = Color(0x33FFFFFF),
                                                                style = stroke,
                                                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                                            )
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // "+ Create Folder" Card
                    item {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clickable { onCreateCategoryClick() },
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color(0x12FFFFFF),
                            borderColor = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        drawContent()
                                        val stroke = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                        )
                                        drawRoundRect(
                                            color = Color(0x38FFFFFF),
                                            style = stroke,
                                            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x2B3B82F6)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Create Folder",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Text(
                                        text = "Create Folder",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = 130.dp),
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
                        onClick = { onVideoClick(item) },
                        onLongClick = {
                            if (isSelectionMode) {
                                onVideoLongClick(item)
                            } else {
                                contextSheetItem = item
                            }
                        },
                        onMoreClick = {
                            contextSheetItem = item
                        }
                    )
                }
            }
        }
    }

    if (contextSheetItem != null) {
        val activeItem = contextSheetItem!!
        AlertDialog(
            onDismissRequest = { contextSheetItem = null },
            containerColor = Color(0xDC141722),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = activeItem.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Play Video", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            val target = activeItem
                            contextSheetItem = null
                            onVideoClick(target)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("File Info", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            val target = activeItem
                            contextSheetItem = null
                            infoItem = target
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Select", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            val target = activeItem
                            contextSheetItem = null
                            onVideoLongClick(target)
                        }
                    )

                    // In Category: Show "Remove from Category" with NO warning dialog
                    if (selectedCategory != null) {
                        ListItem(
                            headlineContent = { Text("Remove from Category", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val target = activeItem
                                val cat = selectedCategory!!
                                contextSheetItem = null
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    db.categoryDao().removeMediaFromCategory(cat.id, target.uri.toString())
                                }
                            }
                        )
                    }

                    // In Folder: Show "Delete Video" with warning confirmation dialog
                    if (isFolderViewActive && selectedFolder != null) {
                        ListItem(
                            headlineContent = { Text("Delete Video", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val target = activeItem
                                contextSheetItem = null
                                videoToDelete = target
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contextSheetItem = null }) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // Warning confirmation dialog before deleting file inside folder
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
                val relPath = item.relativePath?.trim('/')
                val folderKey = if (!relPath.isNullOrBlank()) relPath else (item.bucketName ?: "Movies")
                selectedFolder = videoFolderGroups.keys.firstOrNull { key ->
                    key.equals(folderKey, ignoreCase = true) || key.lowercase().endsWith(folderKey.lowercase()) || folderKey.lowercase().endsWith(key.lowercase())
                } ?: folderKey
            }
        )
    }

    // Add Videos to Category Dialog
    if (showAddVideosToCategoryDialog || showAddVideosDialog) {
        var selectedTargetCategory by remember { mutableStateOf<MediaCategory?>(allVideoCategories.firstOrNull()) }
        var isCreatingNewCat by remember { mutableStateOf(allVideoCategories.isEmpty()) }
        var newCatNameState by remember { mutableStateOf("") }
        var newCatIconState by remember { mutableStateOf("Category") }
        val targetUris = if (selectedUris.isNotEmpty()) selectedUris else emptySet()

        AlertDialog(
            onDismissRequest = {
                showAddVideosToCategoryDialog = false
                onDismissAddVideosDialog()
            },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Add Videos to Category") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (allVideoCategories.isNotEmpty()) {
                        Text("Select Category:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        allVideoCategories.forEach { cat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isCreatingNewCat = false
                                        selectedTargetCategory = cat
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = !isCreatingNewCat && selectedTargetCategory?.id == cat.id,
                                    onClick = {
                                        isCreatingNewCat = false
                                        selectedTargetCategory = cat
                                    }
                                )
                                Text(cat.name, modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp)
                            }
                        }
                    } else {
                        Text("No existing categories.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isCreatingNewCat) {
                        TextButton(
                            onClick = { isCreatingNewCat = true },
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Category", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("New Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = newCatNameState,
                                onValueChange = { newCatNameState = it },
                                placeholder = { Text("Category Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Choose Icon:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(CategoryIconUtils.AVAILABLE_ICONS) { (iconKey, vector) ->
                                    FilterChip(
                                        selected = newCatIconState == iconKey,
                                        onClick = { newCatIconState = iconKey },
                                        label = { Text(iconKey, fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(vector, contentDescription = iconKey, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = (isCreatingNewCat && newCatNameState.isNotBlank()) ||
                              (!isCreatingNewCat && selectedTargetCategory != null),
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val catId = if (isCreatingNewCat) {
                                db.categoryDao().insertCategory(
                                    MediaCategory(name = newCatNameState.trim(), type = "VIDEO", iconName = newCatIconState)
                                )
                            } else if (selectedTargetCategory!!.id < 0) {
                                db.categoryDao().insertCategory(
                                    MediaCategory(name = selectedTargetCategory!!.name, type = "VIDEO", iconName = selectedTargetCategory!!.iconName)
                                )
                            } else {
                                selectedTargetCategory!!.id
                            }

                            val refs = targetUris.map { uriStr: String ->
                                CategoryMediaCrossRef(categoryId = catId, mediaUri = uriStr)
                            }
                            db.categoryDao().insertCategoryCrossRefs(refs)

                            showAddVideosToCategoryDialog = false
                            onDismissAddVideosDialog()
                            onClearSelection()
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddVideosToCategoryDialog = false
                    onDismissAddVideosDialog()
                }) {
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
                                val destDir = java.io.File(root, "Movies/$target")
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
        val itemsToDelete = videoFolderGroups[srcFolder] ?: emptyList()
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Folder: $srcFolder") },
            text = {
                Text("Are you sure you want to delete this folder and all ${itemsToDelete.size} videos inside? This action cannot be undone.")
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
        val srcFolder = folderForInfo!!
        val items = videoFolderGroups[srcFolder]
            ?: videoFolderGroups.entries.firstOrNull { it.key.lowercase().endsWith(srcFolder.lowercase()) || it.key.lowercase().contains(srcFolder.lowercase()) || srcFolder.lowercase().contains(it.key.lowercase()) }?.value
            ?: videosList.filter {
                it.bucketName.equals(srcFolder, ignoreCase = true) ||
                it.bucketName.equals(srcFolder.substringAfterLast('/'), ignoreCase = true) ||
                (it.relativePath != null && (it.relativePath.contains(srcFolder) || srcFolder.contains(it.relativePath.trim('/'))))
            }
        val totalSize = items.sumOf { it.size }
        val folderPath = items.firstOrNull()?.let {
            val p = getFilePathFromUri(currentContext, it.uri)
            if (p.contains('/')) p.substringBeforeLast('/') else it.relativePath ?: srcFolder
        } ?: srcFolder

        AlertDialog(
            onDismissRequest = { folderForInfo = null },
            containerColor = Color(0xDC141722),
            shape = RoundedCornerShape(24.dp),
            title = { Text("Folder Info", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Folder Name: ${srcFolder.substringAfterLast('/')}", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Folder Path: $folderPath", fontSize = 13.sp, color = Color(0xFF9EA3B0))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Videos: ${items.size}", color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total Size: ${formatFileSize(totalSize)}", color = Color.White)
                }
            },
            confirmButton = {
                TextButton(onClick = { folderForInfo = null }) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Category Info Dialog
    if (showCategoryInfoDialog && categoryForOptions != null) {
        val cat = categoryForOptions!!
        val itemCount = remember(allCrossRefs, cat.id) {
            allCrossRefs.count { it.categoryId == cat.id }
        }
        AlertDialog(
            onDismissRequest = {
                showCategoryInfoDialog = false
                categoryForOptions = null
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Category Details", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category Name", color = Color(0xFF9EA3B0), fontSize = 13.sp)
                        Text(cat.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category Type", color = Color(0xFF9EA3B0), fontSize = 13.sp)
                        Text(cat.type, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Items", color = Color(0xFF9EA3B0), fontSize = 13.sp)
                        Text("$itemCount items", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCategoryInfoDialog = false
                    categoryForOptions = null
                }) {
                    Text("Close")
                }
            }
        )
    }

    // Category Delete Confirmation Dialog
    if (showCategoryDeleteConfirm && categoryForOptions != null) {
        val cat = categoryForOptions!!
        AlertDialog(
            onDismissRequest = {
                showCategoryDeleteConfirm = false
                categoryForOptions = null
            },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete category '${cat.name}'? The videos in this category will not be deleted.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            db.categoryDao().deleteCategory(cat)
                        }
                        if (selectedCategory?.id == cat.id) {
                            onCategorySelect(null)
                        }
                        showCategoryDeleteConfirm = false
                        categoryForOptions = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCategoryDeleteConfirm = false
                    categoryForOptions = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

//[Please Do Not Change: Important]
private fun filterVideoList(videos: List<MediaItem>, filterTab: String): List<MediaItem> {
    return when (filterTab) {
        "ALL" -> videos
        "CLIPS" -> videos.filter { isClipsAndRecordings(it) }
        "SHORTS" -> videos.filter { isShorts(it) }
        "SERIES" -> videos.filter { isTVSeries(it) }
        "MUSIC" -> videos.filter { isMusicVideo(it) }
        "MOVIES" -> videos.filter { isMovie(it) }
        "DOWNLOADED" -> videos.filter { isDownloaded(it) }
        "SOCIAL" -> videos.filter { isSocialMediaVideo(it) }
        "EDITED" -> videos.filter { isEditedVideo(it) }
        else -> videos
    }
}

private fun isEditedVideo(item: MediaItem): Boolean {
    // Only check folder paths and file titles, avoiding raw content URIs
    val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()

    // Flexible regex for spaced variations like "video editor"
    val videoEditorRegex = Regex("video\\s*editor")

    // Comprehensive keyword list checked against meaningful paths/titles
    val editKeywords = listOf(
        "editor",
        "studio",
        "compress",
        "compressor",
        "capcut",
        "kinemaster",
        "vn",
        "inshot",
        "edited"
    )

    val matchesKeyword = editKeywords.any { path.contains(it) }

    return path.contains("/dcim/video editor/") ||
            videoEditorRegex.containsMatchIn(path) ||
            matchesKeyword
}

private fun isClipsAndRecordings(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    val title = item.title.lowercase()

    // 1. Keywords that should NEVER be treated as simple camera clips/recordings
    val excludedKeywords = listOf(
        // Social media
        "instagram", "snapchat", "tiktok", "facebook", "whatsapp", "telegram", "twitter", "x", "reddit",
        // Editors / Studios / Compressors / GIFs
        "editor", "studio", "compress", "compressor", "gif", "capcut", "kinemaster", "vn", "inshot",
        // Gaming
        "game", "gaming", "screenrecord", "recorder"
    )

    // If the path contains any of the excluded terms, it's not a standard clip/recording
    if (excludedKeywords.any { path.contains(it) }) {
        return false
    }

    // 2. Otherwise, check for standard camera / recording patterns
    val isInDcim = path.contains("/dcim/")
    val isRecording = path.contains("recording") || path.contains("screenrecord")
    val isCameraVid = title.startsWith("vid_")

    return isInDcim || isRecording || isCameraVid
}

private fun isShorts(item: MediaItem): Boolean {
    // Checks if the video is vertical (height > width) OR square (width == height, or aspect ratio around 1.0)
    val isVerticalOrSquare = (item.height >= item.width && item.width > 0) || (item.aspectRatio in 0.01f..1.05f)

    val isUnder90s = item.durationMs in 1L..90_000L || item.durationMs == 0L

    val title = item.title.lowercase()
    val hasKeyword = title.contains("short") ||
            title.contains("reel") ||
            title.contains("tiktok") ||
            title.contains("clip") ||
            title.contains("video clip")

    return (isVerticalOrSquare && isUnder90s) || hasKeyword
}

private fun isTVSeries(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    val patternSeasonEpisode = Regex("(?i)s\\d{1,2}e\\d{1,2}")
    val patternNumberXNumber = Regex("(?i)\\d{1,2}x\\d{1,2}")
    return patternSeasonEpisode.containsMatchIn(path) || patternNumberXNumber.containsMatchIn(path) ||
            path.contains("/series/") || path.contains("/tv/") || path.contains("/shows/") ||
            path.contains("season") || path.contains("episode")
}

private fun isMovie(item: MediaItem): Boolean {
    // 1. Exclude TV series and clips/recordings first
    if (isTVSeries(item) || isClipsAndRecordings(item) || isShorts(item)) return false

    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    // 2. Duration check (> 40 mins)
    val isLong = item.durationMs >= 2_400_000L

    // 3. Dedicated movie folders (can be movies even if length is slightly ambiguous)
    val inDedicatedMovieFolder = path.contains("/movies/") ||
            path.contains("/movie/") ||
            path.contains("/cinema/")

    // 4. General folders/paths (download, downloads, videos) - these REQUIRE the video to be long (> 40 mins)
    val inGeneralFolder = path.contains("/videos/") ||
            path.contains("/download/") ||
            path.contains("/downloads/")

    // A video is a movie if:
    // - It's explicitly in a movie/cinema folder, OR
    // - It's in a general folder (download/videos) AND is actually long enough to be a movie (>= 40 mins)
    return inDedicatedMovieFolder || (inGeneralFolder && isLong)
}

private fun isMovieOrShow(item: MediaItem): Boolean {
    return isMovie(item) || isTVSeries(item)
}

private fun isMusicVideo(item: MediaItem): Boolean {
    val title = (item.title ?: "").lowercase()
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    // 1. Strict duration window (Typical music videos are 1 min to 7 mins).
    // Avoid defaulting 0L unless artist metadata explicitly confirms it.
    val hasValidDuration = item.durationMs in 60_000L..420_000L
    val hasUnknownDurationButArtist = item.durationMs == 0L && (!item.artist.isNullOrBlank() && item.artist != "<unknown>")

    if (!hasValidDuration && !hasUnknownDurationButArtist) return false

    // 2. Aspect ratio check (Most official music videos are widescreen horizontal)
    val isHorizontal = (item.width >= item.height && item.width > 0) || item.aspectRatio >= 1.0f
    if (!isHorizontal) return false

    // 3. Strong Music Indicators (Path, Metadata, or Title Keywords)
    val hasArtist = !item.artist.isNullOrBlank() && item.artist != "<unknown>"
    val hasAlbumOrGenre = !item.album.isNullOrBlank() || !item.genre.isNullOrBlank()

    val musicPathKeywords = listOf("/music/", "/songs/", "/mv/", "/mvs/", "/audio/", "vevo", "soundtrack")
    val matchesMusicPath = musicPathKeywords.any { path.contains(it) }

    val musicTitleKeywords = listOf("official video", "audio", "lyrics", "ft.", "feat", "music video", "remix", "cover")
    val matchesMusicTitle = musicTitleKeywords.any { title.contains(it) }

    // Must pass structural rules PLUS at least one solid music indicator
    return hasArtist || hasAlbumOrGenre || matchesMusicPath || matchesMusicTitle
}

private fun isDownloaded(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    return path.contains("/download") || path.contains("/chrome/")
}
private fun isSocialMediaVideo(item: MediaItem): Boolean {
    val title = (item.title ?: "").lowercase()
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    // 0. Social media platform identifiers/names in the title
    val socialNames = listOf(
        "whatsapp", "telegram", "instagram", "insta", "facebook", "fb", "tiktok", "snapchat", "snap", "pinterest", "twitter", "reddit"
    )
    val matchesTitle = socialNames.any { title.contains(it) }

    // 1. Specific public folder paths
    val targetFolders = listOf(
        "whatsapp/media",
        "pictures/whatsapp images",
        "telegram",
        "pictures/instagram",
        "download/instagram",
        "dcim/instagram",
        "pictures/facebook",
        "movies/tiktok",
        "pictures/tiktok",
        "dcim/tiktok",
        "pictures/snapchat",
        "dcim/snapchat",
        "pictures/pinterest",
        "pictures/twitter",
        "pictures/x",
        "pictures/reddit"
    )
    val matchesFolder = targetFolders.any { path.contains(it) }

    // 2. App packages in /Android/data/ or /Android/media/
    val socialPackages = listOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.twitter.android"
    )

    val matchesAndroidDir = socialPackages.any { pkg ->
        path.contains("/android/data/$pkg") || path.contains("/android/media/$pkg")
    }

    return matchesTitle || matchesFolder || matchesAndroidDir
}

private fun formatFolderSize(totalBytes: Long): String {
    if (totalBytes <= 0) return "0 MB"
    val gb = totalBytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
        val mb = totalBytes / (1024.0 * 1024.0)
        String.format(java.util.Locale.US, "%.0f MB", mb.coerceAtLeast(1.0))
    }
}

