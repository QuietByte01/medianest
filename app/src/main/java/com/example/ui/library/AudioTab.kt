package com.example.ui.library

import android.net.Uri
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import com.example.data.db.CategoryMediaCrossRef
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.GlassSurface
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.ui.components.MediaLoadingAnimation
import com.example.ui.components.formatDuration
import com.example.util.ArtistImageUtils
import com.example.util.rememberArtistImageUrl

@Composable
fun AudioTab(
    audioList: List<MediaItem>,
    playlists: List<MediaCategory>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridSizeLevel: Int = 1,
    isLoading: Boolean = false,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    initialSubTab: Int = 0,
    initialAlbum: String? = null,
    initialArtist: String? = null,
    initialFolder: String? = null
) {
    var subTabState by remember(initialSubTab) { mutableIntStateOf(initialSubTab) } // 0: All, 1: Recent, 2: Most Played, 3: Albums, 4: Artists, 5: Folders, 6: Playlists
    var targetAlbum by remember(initialAlbum) { mutableStateOf(initialAlbum) }
    var targetArtist by remember(initialArtist) { mutableStateOf(initialArtist) }
    var targetFolder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var audioToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var itemToAddToPlaylist by remember { mutableStateOf<MediaItem?>(null) }

    val db = remember { com.example.MediaNestApp.instance.database }
    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed().collectAsState(initial = emptyList())
    val mostPlayedStates by db.playbackStateDao().getMostPlayed().collectAsState(initial = emptyList())

    val recentSongs = remember(recentlyPlayedStates, audioList) {
        val audioMap = audioList.associateBy { it.uri.toString() }
        val fromDb = recentlyPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
        if (fromDb.isNotEmpty()) fromDb else audioList.sortedByDescending { it.dateAdded }
    }

    val mostPlayedSongs = remember(mostPlayedStates, audioList) {
        val audioMap = audioList.associateBy { it.uri.toString() }
        val fromDb = mostPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
        if (fromDb.isNotEmpty()) fromDb else audioList.sortedByDescending { it.id }
    }

    BackHandler(enabled = subTabState != 0) {
        subTabState = 0
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val categories = remember {
        listOf(
            SubTabInfo(0, "All Songs", Icons.Default.MusicNote),
            SubTabInfo(3, "Albums", Icons.Default.Album),
            SubTabInfo(4, "Artists", Icons.Default.Person),
            SubTabInfo(5, "Folders", Icons.Default.Folder),
            SubTabInfo(6, "Playlists", Icons.Default.QueueMusic),
            SubTabInfo(1, "Recent", Icons.Default.Schedule),
            SubTabInfo(2, "Most Played", Icons.Default.LocalFireDepartment)
        )
    }

    val contentBlock: @Composable () -> Unit = {
        when (subTabState) {
            0 -> SongsList(
                songs = audioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                isLoading = isLoading,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                }
            )
            1 -> SongsList(
                songs = recentSongs,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                isLoading = isLoading,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                }
            )
            2 -> SongsList(
                songs = mostPlayedSongs,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                isLoading = isLoading,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                }
            )
            3 -> AlbumsGrid(audioList, selectedUris, isSelectionMode, onSongClick, onSongLongClick, gridSizeLevel = gridSizeLevel, initialSelectedAlbum = targetAlbum, onAddToPlaylist = { itemToAddToPlaylist = it })
            4 -> ArtistsGrid(audioList, selectedUris, isSelectionMode, onSongClick, onSongLongClick, gridSizeLevel = gridSizeLevel, initialSelectedArtist = targetArtist, onAddToPlaylist = { itemToAddToPlaylist = it })
            5 -> FoldersGrid(audioList, selectedUris, isSelectionMode, onSongClick, onSongLongClick, initialSelectedFolder = targetFolder, onAddToPlaylist = { itemToAddToPlaylist = it }, onNavigateSubTab = { tab, album, artist, folder ->
                subTabState = tab
                if (album != null) targetAlbum = album
                if (artist != null) targetArtist = artist
                if (folder != null) targetFolder = folder
            })
            6 -> PlaylistsList(playlists, audioList, onCreatePlaylistClick, onSongClick, onAddToPlaylist = { itemToAddToPlaylist = it }, onNavigateSubTab = { tab, album, artist, folder ->
                subTabState = tab
                if (album != null) targetAlbum = album
                if (artist != null) targetArtist = artist
                if (folder != null) targetFolder = folder
            })
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Sidebar Card matching tablet design from screenshots
            GlassSurface(
                modifier = Modifier
                    .width(190.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color(0x221C1F2B),
                borderColor = Color(0x2EFFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { item ->
                        val isSelected = subTabState == item.index
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { subTabState = item.index },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0x3DFFFFFF) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF8E95A5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = item.label,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF8E95A5)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                contentBlock()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories, key = { it.index }) { item ->
                    val isSelected = subTabState == item.index
                    GlassSurface(
                        modifier = Modifier
                            .height(38.dp)
                            .clickable { subTabState = item.index },
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFF9EA3B0),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF9EA3B0)
                            )
                        }
                    }
                }
            }

            contentBlock()
        }
    }

    if (itemToAddToPlaylist != null) {
        AddToPlaylistBottomSheet(
            song = itemToAddToPlaylist!!,
            audioPlaylists = playlists,
            onDismissRequest = { itemToAddToPlaylist = null }
        )
    }
}

private data class SubTabInfo(
    val index: Int,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)


@Composable
fun SongsList(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    isLoading: Boolean = false,
    showDeleteOption: Boolean = false,
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?) -> Unit = { _, _, _, _ -> },
    onAddToPlaylist: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var editMetadataItem by remember { mutableStateOf<MediaItem?>(null) }
    var songToDelete by remember { mutableStateOf<MediaItem?>(null) }

    if (isLoading && songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MediaLoadingAnimation(
                mediaType = MediaType.AUDIO,
                iconSize = 52.dp,
                showLabel = true
            )
        }
    } else if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Audio Files Found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(songs, key = { it.id }) { item ->
                var showMenu by remember { mutableStateOf(false) }
                val albumArtModel = item.albumArtUri

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clickable { onSongClick(item) },
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x2EFFFFFF)
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
                                .background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (albumArtModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val displayTitle = item.title
                            Text(
                                text = displayTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val artistStr = item.artist ?: "Unknown Artist"
                            val formatTag = remember(item) {
                                val t = item.title.lowercase()
                                val u = item.uri.toString().lowercase()
                                val m = item.mimeType.lowercase()
                                when {
                                    t.contains(".flac") || u.contains(".flac") || m.contains("flac") -> "FLAC"
                                    t.contains(".wav") || u.contains(".wav") || m.contains("wav") -> "WAV"
                                    t.contains(".m4a") || u.contains(".m4a") || m.contains("m4a") -> "M4A"
                                    t.contains(".aac") || u.contains(".aac") || m.contains("aac") -> "AAC"
                                    t.contains(".ogg") || u.contains(".ogg") || m.contains("ogg") -> "OGG"
                                    else -> "MP3"
                                }
                            }
                            val qualityStr = formatTag
                            val sizeStr = if (item.size > 0) formatFileSize(item.size) else formatDuration(item.durationMs)
                            Text(
                                text = "$artistStr • $qualityStr • $sizeStr",
                                fontSize = 12.sp,
                                color = Color(0xFF9EA3B0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Song options",
                                    tint = Color(0xFF9EA3B0),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = if (com.example.ui.theme.LocalDarkTheme.current) Color(0xBF0F1015) else Color(0xA6FFFFFF),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.border(1.dp, if (com.example.ui.theme.LocalDarkTheme.current) Color(0x28FFFFFF) else Color(0x33000000), RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist") },
                                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onAddToPlaylist(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("File Info") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        infoItem = item
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Tag & Metadata") },
                                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        editMetadataItem = item
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Show Album") },
                                    leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onNavigateSubTab(3, item.album ?: "Unknown Album", null, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Show Artist") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onNavigateSubTab(4, null, item.artist ?: "Unknown Artist", null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Show in Folder") },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onNavigateSubTab(5, null, null, item.bucketName ?: "Music")
                                    }
                                )
                                if (showDeleteOption) {
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            songToDelete = item
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

    if (infoItem != null) {
        MediaInfoBottomSheet(
            item = infoItem,
            onDismiss = { infoItem = null },
            onShowFileLocation = { item ->
                val folder = item.bucketName ?: "Music"
                onNavigateSubTab(5, null, null, folder)
            },
            onFetchInfo = { item ->
                Toast.makeText(context, "Fetching tags & info for '${item.title}'...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (songToDelete != null) {
        val target = songToDelete!!
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Delete Audio File") },
            text = { Text("Are you sure you want to delete '${target.title}'? This will permanently remove the file from your device storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        songToDelete = null
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                com.example.util.FolderHiddenUtils.deleteMediaUri(context, target.uri)
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
                TextButton(onClick = { songToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editMetadataItem != null) {
        AudioMetadataEditDialog(
            item = editMetadataItem!!,
            onDismiss = { editMetadataItem = null }
        )
    }
}

@Composable
fun AlbumsGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    gridSizeLevel: Int = 1,
    initialSelectedAlbum: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedAlbum by remember(initialSelectedAlbum) { mutableStateOf(initialSelectedAlbum) }

    BackHandler(enabled = selectedAlbum != null) {
        selectedAlbum = null
    }

    val db = remember { com.example.MediaNestApp.instance.database }
    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed().collectAsState(initial = emptyList())
    val currentlyPlayingUri = recentlyPlayedStates.firstOrNull()?.mediaUri
    val currentlyPlayingAlbum = remember(currentlyPlayingUri, songs) {
        songs.firstOrNull { it.uri.toString() == currentlyPlayingUri }?.album
    }

    val albumsMap = remember(songs) {
        songs.groupBy { it.album ?: "Unknown Album" }
    }

    if (selectedAlbum != null) {
        val albumSongs = albumsMap[selectedAlbum] ?: emptyList()
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedAlbum = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Albums", tint = Color.White)
                }
                Text(selectedAlbum!!, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            }
            SongsList(albumSongs, selectedUris, isSelectionMode, onSongClick, onSongLongClick, onAddToPlaylist = onAddToPlaylist)
        }
    } else {
        val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val baseColumns = if (screenWidthDp >= 840) 5 else if (screenWidthDp >= 600) 4 else 2
        val gridColumns = when (gridSizeLevel) {
            0 -> (baseColumns * 1.5f).toInt()
            1 -> baseColumns
            2 -> (baseColumns * 0.75f).toInt().coerceAtLeast(1)
            3 -> (baseColumns * 0.5f).toInt().coerceAtLeast(1)
            else -> baseColumns
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albumsMap.keys.toList(), key = { it }) { albumName ->
                val albumSongs = albumsMap[albumName] ?: emptyList()
                val coverItem = albumSongs.firstOrNull()

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAlbum = albumName },
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x2EFFFFFF)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF282C38)),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            val albumArtModel = coverItem?.albumArtUri

                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                contentDescription = albumName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val state = painter.state
                                if (state is AsyncImagePainter.State.Error || albumArtModel == null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF2F3547), Color(0xFF1B1E2B))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x33FFFFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }

                            val isCurrentlyPlaying = currentlyPlayingAlbum != null && currentlyPlayingAlbum == albumName
                            if (isCurrentlyPlaying) {
                                // Circular play button overlay at bottom right for currently playing album
                                Surface(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(36.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    onClick = {
                                        if (albumSongs.isNotEmpty()) {
                                            onSongClick(albumSongs.first())
                                        }
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Album",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = albumName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            lineHeight = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = coverItem?.artist ?: "Unknown Artist",
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp,
                            color = Color(0xFF9EA3B0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "${albumSongs.size} Tracks",
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            color = Color(0xFF686C7A),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    gridSizeLevel: Int = 1,
    initialSelectedArtist: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedArtist by remember(initialSelectedArtist) { mutableStateOf(initialSelectedArtist) }

    BackHandler(enabled = selectedArtist != null) {
        selectedArtist = null
    }

    val artistsMap = remember(songs) {
        songs.groupBy { it.artist ?: "Unknown Artist" }
    }

    val topArtistEntry = remember(artistsMap) {
        artistsMap.maxByOrNull { it.value.size }
    }

    if (selectedArtist != null) {
        val artistSongs = artistsMap[selectedArtist] ?: emptyList()
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedArtist = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Artists", tint = Color.White)
                }
                Column {
                    Text(text = selectedArtist!!, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text(text = "${artistSongs.size} tracks", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                }
            }
            SongsList(artistSongs, selectedUris, isSelectionMode, onSongClick, onSongLongClick, onAddToPlaylist = onAddToPlaylist)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Featured Artist Header Banner (Exact from Image 2)
            if (topArtistEntry != null) {
                val topName = topArtistEntry.key
                val topSongs = topArtistEntry.value
                val topCover = topSongs.firstOrNull()?.albumArtUri
                val albumCount = remember(topSongs) { topSongs.mapNotNull { it.album }.distinct().size.coerceAtLeast(1) }

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { selectedArtist = topName },
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x2EFFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val topArtistImgUrl = rememberArtistImageUrl(topName)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2E3B)),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(topArtistImgUrl).crossfade(true).build(),
                                contentDescription = topName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val state = painter.state
                                if (state is AsyncImagePainter.State.Error) {
                                    if (topCover != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(topCover).crossfade(true).build(),
                                            contentDescription = topName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("MOST PLAYED ARTIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E95A5), letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(topName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$albumCount Albums • ${topSongs.size} Tracks in Library", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                        }

                        val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
                        if (isTablet) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassSurface(
                                    modifier = Modifier.clickable {
                                        if (topSongs.isNotEmpty()) onSongClick(topSongs.first())
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = Color(0x33FFFFFF),
                                    borderColor = Color(0x3DFFFFFF)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play All",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Play All",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                GlassSurface(
                                    modifier = Modifier.clickable {
                                        if (topSongs.isNotEmpty()) onSongClick(topSongs.shuffled().first())
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = Color(0x22FFFFFF),
                                    borderColor = Color(0x28FFFFFF)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Shuffle",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                color = Color(0xFF282D3A),
                                onClick = {
                                    if (topSongs.isNotEmpty()) onSongClick(topSongs.first())
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val baseColumns = if (screenWidthDp >= 840) 5 else if (screenWidthDp >= 600) 4 else 2
            val gridColumns = when (gridSizeLevel) {
                0 -> (baseColumns * 1.5f).toInt()
                1 -> baseColumns
                2 -> (baseColumns * 0.75f).toInt().coerceAtLeast(1)
                3 -> (baseColumns * 0.5f).toInt().coerceAtLeast(1)
                else -> baseColumns
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(artistsMap.keys.toList(), key = { it }) { artistName ->
                    val artistSongs = artistsMap[artistName] ?: emptyList()
                    val coverItem = artistSongs.firstOrNull()
                    val albumCount = remember(artistSongs) { artistSongs.mapNotNull { it.album }.distinct().size.coerceAtLeast(1) }

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedArtist = artistName },
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x221C1F2B),
                        borderColor = Color(0x2EFFFFFF)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val artistImgUrl = rememberArtistImageUrl(artistName)
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(artistImgUrl).crossfade(true).build(),
                                    contentDescription = artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val state = painter.state
                                    if (state is AsyncImagePainter.State.Error) {
                                        val albumArtModel = coverItem?.albumArtUri
                                        if (albumArtModel != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                                contentDescription = artistName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = artistName,
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = artistName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$albumCount Albums • ${artistSongs.size} Songs",
                                fontSize = 11.sp,
                                color = Color(0xFF9EA3B0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun FoldersGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    initialSelectedFolder: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?) -> Unit = { _, _, _, _ -> }
) {
    var showAllFoldersMode by remember { mutableStateOf(false) }

    // Folder Actions State
    var folderToMove by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderForInfo by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { com.example.data.settings.SettingsManager(context) }

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
    val db = remember { com.example.MediaNestApp.instance.database }
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
            item.title.startsWith(".") ||
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

    val sortedFolderNames = remember(folderMap) {
        folderMap.keys.sortedWith(
            compareByDescending<String> { fn -> isFolderHidden(fn, folderMap[fn]) }
                .thenBy { it.lowercase() }
        )
    }

    var selectedFolder by remember(initialSelectedFolder, sortedFolderNames) {
        mutableStateOf<String?>(initialSelectedFolder ?: sortedFolderNames.firstOrNull())
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
                    val displaySizeStr = if (totalSize > 0L) formatFileSize(totalSize) else "482 MB"
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
                                    containerColor = Color(0xDC141722),
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
                                                        com.example.data.db.SelectiveHiddenFolder(
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
            // Subdirectories Horizontal Scroll + Selected Directory Contents Below
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
                        color = Color(0xFF9EA3B0),
                        modifier = Modifier.clickable { selectedFolder = sortedFolderNames.firstOrNull() }
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
                        color = if (selectedFolder == null) Color.White else Color(0xFF9EA3B0),
                        fontWeight = if (selectedFolder == null) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { selectedFolder = sortedFolderNames.firstOrNull() }
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
                        val displaySizeStr = if (totalSize > 0L) formatFileSize(totalSize) else "482 MB"
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
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }
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
                                    val itemsToMove = folderMap[srcFolder] ?: emptyList()
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val root = android.os.Environment.getExternalStorageDirectory()
                                        val destDir = java.io.File(root, "Music/$target")
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
                val itemsToDelete = folderMap[srcFolder] ?: emptyList()
                AlertDialog(
                    onDismissRequest = { folderToDelete = null },
                    containerColor = Color(0xDC141722),
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
                                            com.example.util.FolderHiddenUtils.deleteMediaUri(context, item.uri)
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
                val items = folderMap[srcFolder] ?: emptyList()
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
                            Text("Total Tracks: ${items.size}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Total Size: ${android.text.format.Formatter.formatFileSize(context, totalSize)}")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { folderForInfo = null }) {
                            Text("OK")
                        }
                    }
                )
            }
        }

@Composable
fun PlaylistsList(
    playlists: List<MediaCategory>,
    audioList: List<MediaItem>,
    onCreatePlaylistClick: () -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?) -> Unit = { _, _, _, _ -> },
    onAddToPlaylist: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<MediaCategory?>(null) }
    var playlistUris by remember { mutableStateOf<List<String>>(emptyList()) }

    val playlistPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else null
                    } ?: uri.lastPathSegment ?: "Imported Playlist"

                    val rawName = fileName.substringBeforeLast('.').ifBlank { "Imported Playlist" }
                    val playlistName = rawName.replace('_', ' ')

                    val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() } ?: emptyList()
                    val extractedPaths = mutableListOf<String>()
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        if (trimmed.contains("File", ignoreCase = true) && trimmed.contains("=")) {
                            val valPart = trimmed.substringAfter('=').trim()
                            if (valPart.isNotEmpty()) extractedPaths.add(valPart)
                        } else {
                            extractedPaths.add(trimmed)
                        }
                    }

                    val db = com.example.MediaNestApp.instance.database
                    val matchedUris = mutableSetOf<String>()
                    for (path in extractedPaths) {
                        val targetName = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
                        val targetBase = targetName.substringBeforeLast('.')
                        val matchedItem = audioList.find { item ->
                            val itemUriStr = item.uri.toString().lowercase()
                            val itemLastSeg = (item.uri.lastPathSegment ?: "").lowercase()
                            val itemTitle = item.title.lowercase()
                            itemUriStr.endsWith(targetName) ||
                            itemLastSeg == targetName ||
                            (targetBase.length >= 3 && (itemTitle.contains(targetBase) || itemLastSeg.contains(targetBase)))
                        }
                        if (matchedItem != null) {
                            matchedUris.add(matchedItem.uri.toString())
                        }
                    }

                    val catId = db.categoryDao().insertCategory(
                        MediaCategory(name = playlistName, type = "AUDIO", iconName = "PlaylistPlay")
                    )
                    matchedUris.forEach { mediaUri ->
                        db.categoryDao().insertCategoryCrossRef(
                            CategoryMediaCrossRef(categoryId = catId, mediaUri = mediaUri)
                        )
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Imported '$playlistName' (${matchedUris.size} tracks matched)", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import playlist file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    BackHandler(enabled = selectedPlaylist != null) {
        selectedPlaylist = null
    }

    LaunchedEffect(selectedPlaylist?.id) {
        if (selectedPlaylist != null) {
            val db = com.example.MediaNestApp.instance.database
            db.categoryDao().getMediaUrisForCategory(selectedPlaylist!!.id).collect { uris ->
                playlistUris = uris
            }
        }
    }

    val latestAddedArtUri = remember(audioList) {
        audioList.sortedByDescending { it.dateAdded }.firstOrNull { it.albumArtUri != null }?.albumArtUri
            ?: audioList.firstOrNull { it.albumArtUri != null }?.albumArtUri
    }

    val favPlaylist = remember(playlists) {
        playlists.firstOrNull { it.name.equals("Favourites", ignoreCase = true) || it.name.equals("Favorites", ignoreCase = true) }
    }

    var favArtUri by remember { mutableStateOf<Uri?>(null) }
    var favCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(favPlaylist?.id, audioList) {
        if (favPlaylist != null) {
            val db = com.example.MediaNestApp.instance.database
            db.categoryDao().getMediaUrisForCategory(favPlaylist.id).collect { uris ->
                favCount = uris.size
                val favTrack = audioList.firstOrNull { uris.contains(it.uri.toString()) && it.albumArtUri != null }
                favArtUri = favTrack?.albumArtUri ?: latestAddedArtUri
            }
        } else {
            favArtUri = latestAddedArtUri
            favCount = 0
        }
    }

    val mostPlayedArtUri = remember(audioList) {
        audioList.sortedByDescending { it.id }.firstOrNull { it.albumArtUri != null }?.albumArtUri
            ?: audioList.sortedByDescending { it.id }.firstOrNull()?.uri
    }

    val recentlyPlayedArtUri = remember(audioList) {
        audioList.sortedByDescending { it.dateAdded }.firstOrNull { it.albumArtUri != null }?.albumArtUri
            ?: audioList.sortedByDescending { it.dateAdded }.firstOrNull()?.uri
    }

    val recentlyAddedArtUri = remember(audioList) {
        audioList.sortedByDescending { it.dateAdded }.firstOrNull { it.albumArtUri != null }?.albumArtUri
            ?: audioList.sortedByDescending { it.dateAdded }.firstOrNull()?.uri
    }
    val userPlaylists = remember(playlists) {
        playlists.filter { 
            !it.name.equals("Favourites", ignoreCase = true) && 
            !it.name.equals("Favorites", ignoreCase = true) 
        }
    }

    if (selectedPlaylist != null) {
        val playlistSongs = audioList.filter { playlistUris.contains(it.uri.toString()) }
        var showPlaylistMenu by remember { mutableStateOf(false) }
        var infoItem by remember { mutableStateOf<MediaItem?>(null) }

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = selectedPlaylist!!.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (playlistSongs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No Songs in Playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SongsList(
                    songs = playlistSongs,
                    selectedUris = emptySet(),
                    isSelectionMode = false,
                    onSongClick = onSongClick,
                    onSongLongClick = {},
                    onNavigateSubTab = onNavigateSubTab,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }

        if (infoItem != null) {
            MediaInfoBottomSheet(
                item = infoItem,
                onDismiss = { infoItem = null },
                onShowFileLocation = { item ->
                    val folder = item.bucketName ?: "Music"
                    onNavigateSubTab(5, null, null, folder)
                },
                onFetchInfo = { item ->
                    Toast.makeText(context, "Fetching tags & info for '${item.title}'...", Toast.LENGTH_SHORT).show()
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            QuickAccessCard(
                                title = "Favourites",
                                subtitle = "${favCount} tracks",
                                icon = Icons.Default.Favorite,
                                artUri = favArtUri,
                                gradientColors = listOf(Color(0xFFFF5252), Color(0xFFFF7A00)),
                                onClick = { if (favPlaylist != null) selectedPlaylist = favPlaylist else onNavigateSubTab(2, null, null, null) }
                            )
                        }
                        item {
                            QuickAccessCard(
                                title = "Most Played",
                                subtitle = "${audioList.size} tracks",
                                icon = Icons.Default.LocalFireDepartment,
                                artUri = mostPlayedArtUri,
                                gradientColors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
                                onClick = { onNavigateSubTab(2, null, null, null) }
                            )
                        }
                        item {
                            QuickAccessCard(
                                title = "Recently Played",
                                subtitle = "${audioList.size} tracks",
                                icon = Icons.Default.History,
                                artUri = recentlyPlayedArtUri,
                                gradientColors = listOf(Color(0xFFE91E63), Color(0xFFFF4081)),
                                onClick = { onNavigateSubTab(1, null, null, null) }
                            )
                        }
                        item {
                            QuickAccessCard(
                                title = "Recently Added",
                                subtitle = "${audioList.size} tracks",
                                icon = Icons.Default.Schedule,
                                artUri = recentlyAddedArtUri,
                                gradientColors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF)),
                                onClick = { onNavigateSubTab(1, null, null, null) }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playlists",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = { onCreatePlaylistClick() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (userPlaylists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No custom playlists yet. Tap + to create one.",
                                fontSize = 13.sp,
                                color = Color(0xFF8E93A3)
                            )
                        }
                    }
                } else {
                    items(userPlaylists, key = { it.id }) { pl ->
                        UserPlaylistCard(
                            pl = pl,
                            audioList = audioList,
                            onClick = { selectedPlaylist = pl },
                            onDelete = {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val db = com.example.MediaNestApp.instance.database
                                    db.categoryDao().deleteCategory(pl)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPlaylistCard(
    pl: MediaCategory,
    audioList: List<MediaItem>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { com.example.MediaNestApp.instance.database }
    var artUri by remember { mutableStateOf<Uri?>(null) }
    var trackCount by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(pl.id, audioList) {
        db.categoryDao().getMediaUrisForCategory(pl.id).collect { uris ->
            trackCount = uris.size
            val matchedTrack = audioList.firstOrNull { uris.contains(it.uri.toString()) }
            artUri = matchedTrack?.albumArtUri ?: matchedTrack?.uri ?: audioList.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: audioList.firstOrNull()?.uri
        }
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x221C1F2B),
        borderColor = Color(0x2EFFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (artUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pl.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (trackCount > 0) "$trackCount tracks" else "Custom Playlist",
                    fontSize = 12.sp,
                    color = Color(0xFF9EA3B0)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFF9EA3B0),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = if (com.example.ui.theme.LocalDarkTheme.current) Color(0xBF0F1015) else Color(0xA6FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, if (com.example.ui.theme.LocalDarkTheme.current) Color(0x28FFFFFF) else Color(0x33000000), RoundedCornerShape(16.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Playlist") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    song: MediaItem,
    audioPlaylists: List<MediaCategory>,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.example.MediaNestApp.instance.database }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val userPlaylists = remember(audioPlaylists) {
        audioPlaylists.filter { 
            !it.name.equals("Favourites", ignoreCase = true) && 
            !it.name.equals("Favorites", ignoreCase = true) 
        }
    }

    val isDark = com.example.ui.theme.LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xBF0F1015) else Color(0xA6FFFFFF)

    AdaptiveBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Add to Playlist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Create New Playlist",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (userPlaylists.isEmpty()) {
                Text(
                    text = "No custom playlists found.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(userPlaylists, key = { it.id }) { playlist ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val existing = db.categoryDao().countCategoryMediaCrossRef(playlist.id, song.uri.toString())
                                        if (existing > 0) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(context, "'${song.title}' is already in '${playlist.name}'", Toast.LENGTH_SHORT).show()
                                                onDismissRequest()
                                            }
                                        } else {
                                            db.categoryDao().insertCategoryCrossRef(
                                                CategoryMediaCrossRef(
                                                    categoryId = playlist.id,
                                                    mediaUri = song.uri.toString()
                                                )
                                            )
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(context, "Added to '${playlist.name}'", Toast.LENGTH_SHORT).show()
                                                onDismissRequest()
                                            }
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = playlist.name,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        val name = newPlaylistName.trim()
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val newCatId = db.categoryDao().insertCategory(
                                MediaCategory(name = name, type = "AUDIO", iconName = "PlaylistPlay")
                            )
                            db.categoryDao().insertCategoryCrossRef(
                                CategoryMediaCrossRef(
                                    categoryId = newCatId,
                                    mediaUri = song.uri.toString()
                                )
                            )
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Created '$name' & added track", Toast.LENGTH_SHORT).show()
                                showCreateDialog = false
                                onDismissRequest()
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    artUri: Uri? = null,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    GlassSurface(
        modifier = modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x221C1F2B),
        borderColor = Color(0x2EFFFFFF)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                if (artUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(artUri).crossfade(true).build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = painter.state
                        if (state is AsyncImagePainter.State.Error) {
                            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = Color(0xFF9EA3B0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMetadataEditDialog(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = com.example.MediaNestApp.instance.database
    val networkRepository = remember { com.example.data.repository.NetworkRepository() }
    val settingsManager = com.example.MediaNestApp.instance.settingsManager
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)

    var editTitle by remember(item.uri) { mutableStateOf(item.title) }
    var editArtist by remember(item.uri) { mutableStateOf(item.artist ?: "") }
    var editAlbum by remember(item.uri) { mutableStateOf(item.album ?: "") }
    var editYear by remember(item.uri) { mutableStateOf("") }
    var editGenre by remember(item.uri) { mutableStateOf("") }
    var editAlbumArtUri by remember(item.uri) { mutableStateOf(item.albumArtUri?.toString()) }
    var isFetching by remember { mutableStateOf(false) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            editAlbumArtUri = uri.toString()
        }
    }

    LaunchedEffect(item.uri) {
        val cached = db.metadataCacheDao().getCache(item.uri.toString())
        if (cached != null) {
            editTitle = cached.title ?: item.title
            editArtist = cached.artist ?: item.artist ?: ""
            editAlbum = cached.album ?: item.album ?: ""
            editYear = cached.year ?: ""
            editGenre = cached.genre ?: ""
            if (!cached.albumArtUri.isNullOrBlank()) {
                editAlbumArtUri = cached.albumArtUri
            }
        }
    }

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(text = "Edit Tag & Metadata", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Album Art Selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!editAlbumArtUri.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = editAlbumArtUri,
                            contentDescription = "Album Art",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Cover", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Album Cover Art", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Tap box to select new artwork from gallery",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Image", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = editArtist,
                onValueChange = { editArtist = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = editAlbum,
                onValueChange = { editAlbum = it },
                label = { Text("Album") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = editYear,
                    onValueChange = { editYear = it },
                    label = { Text("Year") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    label = { Text("Genre") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isFetching = true
                            val info = networkRepository.fetchAudioMetadata(editTitle.ifBlank { item.title }, offlineMode)
                            if (info != null) {
                                if (info.title.isNotBlank()) editTitle = info.title
                                if (info.artist.isNotBlank()) editArtist = info.artist
                                if (info.album.isNotBlank()) editAlbum = info.album
                                if (!info.year.isNullOrBlank()) editYear = info.year
                                if (!info.genre.isNullOrBlank()) editGenre = info.genre
                                if (!info.coverArtUrl.isNullOrBlank()) editAlbumArtUri = info.coverArtUrl
                                android.widget.Toast.makeText(context, "Fetched tags online!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "No online metadata found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            isFetching = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isFetching
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Tags")
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            val cache = com.example.data.db.AudioMetadataCache(
                                audioUri = item.uri.toString(),
                                title = editTitle.ifBlank { item.title },
                                artist = editArtist,
                                album = editAlbum,
                                albumArtUri = editAlbumArtUri,
                                year = editYear.ifBlank { null },
                                genre = editGenre.ifBlank { null }
                            )
                            db.metadataCacheDao().saveCache(cache)
                            android.widget.Toast.makeText(context, "Metadata updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
