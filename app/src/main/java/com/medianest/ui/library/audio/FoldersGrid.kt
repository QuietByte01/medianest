package com.medianest.ui.library.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.mediainfo.getFilePathFromUri
import com.medianest.ui.library.MoveFolderDialog
import com.medianest.ui.library.RenameFolderDialog
import com.medianest.util.formatBytesReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FoldersGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (List<MediaItem>, Int) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    initialSelectedFolder: String? = null,
    targetSongUri: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?, targetSongUri: String?) -> Unit = { _, _, _, _, _ -> },
    isLoading: Boolean = false,
    isScanningHidden: Boolean = false,
    sortField: String = "Name",
    isAscending: Boolean = true
) {
    var showAllFoldersMode by remember { mutableStateOf(false) }
    val subfoldersListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Folder Actions State
    var folderToRename by remember { mutableStateOf<String?>(null) }
    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { com.medianest.MediaNestApp.instance.settingsManager }

    BackHandler(enabled = showAllFoldersMode) {
        showAllFoldersMode = false
    }

    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val folderMap = remember(songs, showHiddenSetting) {
        songs.groupBy { item ->
            val relPath = item.relativePath?.trim('/')
            val dotSegment = relPath?.split('/')?.firstOrNull { it.startsWith(".") && it.length > 1 }
            when {
                showHiddenSetting && dotSegment != null -> dotSegment
                !relPath.isNullOrBlank() -> relPath
                else -> item.bucketName ?: "Music"
            }
        }
    }

    val appHiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())
    val db = remember { com.medianest.MediaNestApp.instance.database }
    val selectiveHiddenFolders by db.selectiveHiddenFolderDao().getAllHiddenFolders().collectAsState(initial = emptyList())

    val selectiveAudioHidden = remember(selectiveHiddenFolders) {
        selectiveHiddenFolders.filter { it.mediaType == "AUDIO" && it.isHidden }
            .flatMap { listOf(it.folderPath, it.folderName) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    val allHiddenAudioFolders = remember(appHiddenFolders, selectiveAudioHidden) {
        (appHiddenFolders + selectiveAudioHidden).map { it.lowercase() }.toSet()
    }

    fun isFolderExcluded(folderKey: String, items: List<MediaItem>?): Boolean {
        val lowerKey = folderKey.lowercase()
        val folderName = lowerKey.substringAfterLast('/')
        if (com.medianest.util.FolderHiddenUtils.isFolderExcludedByDefault(folderKey, folderName)) return true
        if (lowerKey in allHiddenAudioFolders || folderName in allHiddenAudioFolders) return true
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

    val sortedFolderNames = remember(folderMap, sortField, isAscending) {
        val keys = folderMap.keys.toList()
        val comp = when (sortField) {
            "Name" -> compareBy<String> { it.lowercase() }
            "Date Added" -> compareBy<String> { folderName ->
                folderMap[folderName]?.maxOfOrNull { maxOf(it.dateAdded, it.dateCreated) } ?: 0L
            }
            "Size" -> compareBy<String> { folderName ->
                folderMap[folderName]?.sumOf { it.size } ?: 0L
            }
            else -> compareBy<String> { it.lowercase() }
        }
        
        val baseSorted = if (isAscending) keys.sortedWith(comp) else keys.sortedWith(comp).reversed()
        baseSorted.sortedBy { fn -> isFolderHidden(fn, folderMap[fn]) && !showAllFoldersMode }
    }

    val isExcludedFeed = remember(songs) { songs.isNotEmpty() && songs.all { it.isExcluded || com.medianest.util.FolderHiddenUtils.isItemHidden(it) } }
    val isHiddenFeed = remember(songs) { songs.isNotEmpty() && songs.all { it.isHidden && !it.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(it) } }

    val visibleFolderNames = remember(sortedFolderNames, folderMap, isExcludedFeed, isHiddenFeed, showHiddenSetting, allHiddenAudioFolders) {
        when {
            isExcludedFeed -> sortedFolderNames.filter { fn -> isFolderExcluded(fn, folderMap[fn]) }
            isHiddenFeed -> sortedFolderNames.filter { fn -> isFolderSystemHidden(fn, folderMap[fn]) }
            else -> sortedFolderNames.filter { fn ->
                val items = folderMap[fn]
                !isFolderExcluded(fn, items) && (showHiddenSetting || !isFolderSystemHidden(fn, items))
            }
        }
    }

    var selectedFolder by remember(initialSelectedFolder, visibleFolderNames) {
        mutableStateOf<String?>(
            initialSelectedFolder?.let { target ->
                visibleFolderNames.firstOrNull { 
                    it.equals(target, ignoreCase = true) ||
                    it.lowercase().endsWith(target.lowercase()) ||
                    target.lowercase().endsWith(it.lowercase())
                } ?: target
            } ?: visibleFolderNames.firstOrNull()
        )
    }

    LaunchedEffect(initialSelectedFolder) {
        if (!initialSelectedFolder.isNullOrBlank()) {
            selectedFolder = visibleFolderNames.firstOrNull { 
                it.equals(initialSelectedFolder, ignoreCase = true) ||
                it.lowercase().endsWith(initialSelectedFolder.lowercase()) ||
                initialSelectedFolder.lowercase().endsWith(it.lowercase())
            } ?: initialSelectedFolder
        }
    }

    LaunchedEffect(selectedFolder, visibleFolderNames) {
        if (selectedFolder != null) {
            val idx = visibleFolderNames.indexOf(selectedFolder)
            if (idx != -1) {
                subfoldersListState.animateScrollToItem(idx)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if ((isLoading && visibleFolderNames.isEmpty()) || (isScanningHidden && isHiddenFeed && visibleFolderNames.isEmpty())) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.MediaLoadingAnimation(
                    mediaType = com.medianest.data.db.MediaType.AUDIO,
                    iconSize = 52.dp,
                    showLabel = isScanningHidden && isHiddenFeed,
                    customMessage = if (isScanningHidden && isHiddenFeed) "Scanning hidden folders..." else null
                )
            }
        } else if (visibleFolderNames.isEmpty()) {
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isHiddenFeed) Icons.Default.VisibilityOff else Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = Color(0xFFC0C5D0),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isHiddenFeed) "No Hidden Audio Folders" else if (isExcludedFeed) "No Excluded Audio Folders" else "No Audio Folders Found",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isHiddenFeed) "Folders marked with .nomedia or hidden in Settings will appear here." else "No audio directories match your current filter.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (showAllFoldersMode) {
            // Full Folders View (Show All)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALL FOLDERS (${visibleFolderNames.size})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E95A5),
                    letterSpacing = 0.8.sp
                )

                GlassSurface(
                    modifier = Modifier.clickable { showAllFoldersMode = false },
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x33FFFFFF),
                    borderColor = Color(0x44FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Subdirectories View", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleFolderNames, key = { "all_folder_$it" }) { folderName ->
                    val folderSongs = folderMap[folderName] ?: emptyList()
                    val isHidden = isFolderHidden(folderName, folderSongs)
                    val isExcluded = isFolderExcluded(folderName, folderSongs)
                    val totalSize = remember(folderSongs) { folderSongs.sumOf { it.size } }
                    val displaySizeStr = if (totalSize > 0L) com.medianest.util.formatBytesReport(totalSize) else "0 B"

                    val isSelected = (selectedFolder == folderName)
                    var showFolderMenu by remember { mutableStateOf(false) }

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFolder = folderName
                                showAllFoldersMode = false
                            },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x2EFFFFFF)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x55FFFFFF) else Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExcluded || isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isExcluded) "$folderName (Excluded)" else if (isHidden) "$folderName (Hidden)" else folderName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${folderSongs.size} Songs • $displaySizeStr",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E95A5)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showFolderMenu = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color(0xFF8E95A5),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                GlassDropdownMenu(
                                    expanded = showFolderMenu,
                                    onDismissRequest = { showFolderMenu = false },
                                    backgroundImage = folderSongs.firstOrNull()?.let { it.albumArtUri ?: it.uri }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename Folder", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showFolderMenu = false
                                            folderToRename = folderName
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move Folder", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showFolderMenu = false
                                            folderToMove = folderName
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Folder Info", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showFolderMenu = false
                                            folderForInfo = folderName
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isExcluded) "Include Folder" else "Exclude Folder", color = Color.White) },
                                        leadingIcon = { Icon(if (isExcluded) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showFolderMenu = false
                                            scope.launch {
                                                val current = settingsManager.hiddenFolders.first()
                                                val folderPathKey = folderSongs.firstOrNull()?.relativePath?.trim('/') ?: folderName
                                                if (isExcluded) {
                                                    settingsManager.setHiddenFolders(current - folderName - folderPathKey)
                                                    db.selectiveHiddenFolderDao().unhideFolder(folderName, "AUDIO")
                                                    db.selectiveHiddenFolderDao().unhideFolder(folderPathKey, "AUDIO")
                                                } else {
                                                    settingsManager.setHiddenFolders(current + folderPathKey + folderName)
                                                    db.selectiveHiddenFolderDao().insertOrUpdate(
                                                        com.medianest.data.db.SelectiveHiddenFolder(
                                                            folderPath = folderPathKey,
                                                            folderName = folderName,
                                                            mediaType = "AUDIO",
                                                            isHidden = true
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showFolderMenu = false
                                            folderToDelete = folderName
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Restored original glossy path header
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x221C1F2B)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        tint = Color(0xFF9EA3B0),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Internal Storage",
                        fontSize = 13.sp,
                        color = Color(0xFF9EA3B0)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF9EA3B0),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Music",
                        fontSize = 13.sp,
                        color = Color(0xFF9EA3B0)
                    )
                    if (selectedFolder != null) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF9EA3B0),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = selectedFolder!!,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (sortedFolderNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SUBDIRECTORIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E95A5),
                        letterSpacing = 0.8.sp
                    )

                    Text(
                        text = "Show All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.clickable { showAllFoldersMode = true }
                    )
                }

                LazyRow(
                    state = subfoldersListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleFolderNames, key = { "subfolder_$it" }) { folderName ->
                        val folderSongs = folderMap[folderName] ?: emptyList()
                        val isHidden = isFolderHidden(folderName, folderSongs)
                        val totalSize = remember(folderSongs) { folderSongs.sumOf { it.size } }
                        val displaySizeStr = if (totalSize > 0L) com.medianest.util.formatBytesReport(totalSize) else "0 B"
                        val isSelected = (selectedFolder == folderName)

                        GlassSurface(
                            modifier = Modifier.clickable { selectedFolder = folderName },
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                            borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x2EFFFFFF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0x55FFFFFF) else Color(0x33FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isHidden) "$folderName (Hidden)" else folderName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folderSongs.size} Files • $displaySizeStr",
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(0xFFD0D5E0) else Color(0xFF9EA3B0)
                                    )
                                }
                            }
                        }
                    }
                }

                val targetFolderName = selectedFolder ?: sortedFolderNames.firstOrNull() ?: ""
                Text(
                    text = if (targetFolderName.isNotEmpty()) "AUDIO FILES IN ${targetFolderName.uppercase()}" else "AUDIO FILES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E95A5),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val activeFolderSongs = remember(selectedFolder, sortedFolderNames, songs, folderMap) {
                val target = selectedFolder ?: sortedFolderNames.firstOrNull()
                if (target != null) {
                    folderMap[target]
                        ?: folderMap.entries.firstOrNull { (k, _) ->
                            val normKey = k.trim('/').lowercase()
                            val normTarget = target.trim('/').lowercase()
                            normKey == normTarget ||
                            normKey.substringAfterLast('/') == normTarget ||
                            normTarget.substringAfterLast('/') == normKey ||
                            normKey.endsWith("/$normTarget") ||
                            normTarget.endsWith("/$normKey")
                        }?.value
                        ?: emptyList()
                } else {
                    emptyList()
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                SongsList(
                    songs = activeFolderSongs,
                    selectedUris = selectedUris,
                    isSelectionMode = isSelectionMode,
                    onSongClick = onSongClick,
                    onSongLongClick = onSongLongClick,
                    onNavigateSubTab = onNavigateSubTab,
                    onAddToPlaylist = onAddToPlaylist,
                    showInGallery = true,
                    targetSongUri = targetSongUri
                )
            }
        }
    }

            // Folder Action Dialog: Rename Folder
            if (folderToRename != null) {
                val srcFolder = folderToRename!!
                val itemsInFolder = folderMap[srcFolder] ?: emptyList()
                RenameFolderDialog(
                    folderName = srcFolder,
                    itemsInFolder = itemsInFolder,
                    defaultMediaType = com.medianest.data.db.MediaType.AUDIO,
                    scope = scope,
                    onDismiss = { folderToRename = null },
                    onRenameComplete = { folderToRename = null }
                )
            }

            // Folder Action Dialog: Move Folder
            if (folderToMove != null) {
                MoveFolderDialog(
                    folderName = folderToMove!!,
                    folderGroups = folderMap,
                    mediaType = com.medianest.data.db.MediaType.AUDIO,
                    scope = scope,
                    onDismiss = { folderToMove = null }
                )
            }

            // Folder Action Dialog: Delete Folder
            if (folderToDelete != null) {
                val srcFolder = folderToDelete
                val itemsToDelete = folderMap[srcFolder] ?: emptyList()
                AlertDialog(
                    onDismissRequest = { folderToDelete = null },
                    containerColor = if (com.medianest.ui.theme.LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                shape = RoundedCornerShape(24.dp),
                    title = { Text("Delete Folder: $srcFolder") },
                    text = {
                        Text("Are you sure you want to delete this folder and all ${itemsToDelete.size} tracks inside? This action cannot be undone.")
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            onClick = {
                                folderToDelete = null
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    itemsToDelete.forEach { item ->
                                        try {
                                            com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, item.uri)
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
                val items = folderMap[srcFolder] ?: emptyList()
                val totalSize = items.sumOf { it.size }
                val folderPath = items.firstOrNull()?.let {
                    val p = getFilePathFromUri(context, it.uri)
                    if (p.contains('/')) p.substringBeforeLast('/') else it.relativePath ?: srcFolder
                } ?: srcFolder

                AlertDialog(
                    onDismissRequest = { folderForInfo = null },
                    containerColor = if (com.medianest.ui.theme.LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                    shape = RoundedCornerShape(24.dp),
                    title = { Text("Folder Info", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Folder Name: ${srcFolder.substringAfterLast('/')}", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Folder Path: $folderPath", fontSize = 13.sp, color = Color(0xFF9EA3B0))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Total Tracks: ${items.size}", color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Total Size: ${android.text.format.Formatter.formatFileSize(context, totalSize)}", color = Color.White)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { folderForInfo = null }) {
                            Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
