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
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.getFilePathFromUri
import com.medianest.util.formatBytesReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FoldersGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    initialSelectedFolder: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?) -> Unit = { _, _, _, _ -> },
    sortField: String = "Name",
    isAscending: Boolean = true
) {
    var showAllFoldersMode by remember { mutableStateOf(false) }

    // Folder Actions State
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
        appHiddenFolders + selectiveAudioHidden
    }

    fun isFolderHidden(folderKey: String, items: List<MediaItem>?): Boolean {
        val folderName = folderKey.substringAfterLast('/')
        if (folderKey.startsWith(".") || folderName.startsWith(".")) return true
        if (folderKey in allHiddenAudioFolders || folderName in allHiddenAudioFolders) return true
        if (items.isNullOrEmpty()) return false
        return items.any { item ->
            (item.relativePath != null && (
                allHiddenAudioFolders.any { hidden ->
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

    var selectedFolder by remember(initialSelectedFolder) {
        mutableStateOf<String?>(initialSelectedFolder)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showAllFoldersMode) {
            // Full Folders View (Show All)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALL FOLDERS (${sortedFolderNames.size})",
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
                items(sortedFolderNames, key = { "all_folder_$it" }) { folderName ->
                    val folderSongs = folderMap[folderName] ?: emptyList()
                    val isHidden = isFolderHidden(folderName, folderSongs)
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
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x3DFFFFFF) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88FFFFFF) else Color(0x2EFFFFFF)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0x55FFFFFF) else Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHidden) "${folderName.substringAfterLast('/')} (Hidden)" else folderName.substringAfterLast('/'),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${folderSongs.size} Audio Files • $displaySizeStr",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9EA3B0)
                                )
                            }

                            Box {
                                IconButton(onClick = { showFolderMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Folder Options",
                                        tint = Color(0xFF9EA3B0),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showFolderMenu,
                                    onDismissRequest = { showFolderMenu = false },
                                    containerColor = if (com.medianest.ui.theme.LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Folder Info", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showFolderMenu = false
                                            folderForInfo = folderName
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isHidden) "Unhide Folder" else "Hide Folder", color = Color.White) },
                                        leadingIcon = { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showFolderMenu = false
                                            scope.launch {
                                                val current = settingsManager.hiddenFolders.first()
                                                val folderPathKey = folderSongs.firstOrNull()?.relativePath?.trim('/') ?: folderName
                                                if (isHidden) {
                                                    settingsManager.setHiddenFolders(current - folderName - folderPathKey)
                                                    db.selectiveHiddenFolderDao().unhideFolder(folderName, "AUDIO")
                                                    db.selectiveHiddenFolderDao().unhideFolder(folderPathKey, "AUDIO")
                                                } else {
                                                    settingsManager.setHiddenFolders(current + folderPathKey)
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
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedFolderNames, key = { "subfolder_$it" }) { folderName ->
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

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (selectedFolder != null) "AUDIO FILES IN ${selectedFolder!!.uppercase()}" else "AUDIO FILES IN DIRECTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E95A5),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val activeFolderSongs = remember(selectedFolder, songs, folderMap) {
                if (selectedFolder != null) {
                    folderMap[selectedFolder] ?: emptyList()
                } else {
                    songs
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
                    showInGallery = true
                )
            }
        }
    }

            // Folder Action Dialog: Move Folder
            if (folderToMove != null) {
                var targetName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { folderToMove = null },
                    containerColor = if (com.medianest.ui.theme.LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
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
                                    val itemsToMove = folderMap[srcFolder] ?: emptyList()
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        itemsToMove.forEach { item ->
                                            try {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                    val values = android.content.ContentValues().apply {
                                                        put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/$target/")
                                                    }
                                                    context.contentResolver.update(item.uri, values, null, null)
                                                } else {
                                                    val root = android.os.Environment.getExternalStorageDirectory()
                                                    val destDir = java.io.File(root, "Music/$target")
                                                    destDir.mkdirs()
                                                    val file = java.io.File(item.uri.path ?: "")
                                                    if (file.exists()) {
                                                        file.copyTo(java.io.File(destDir, file.name), overwrite = true)
                                                        file.delete()
                                                    }
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
