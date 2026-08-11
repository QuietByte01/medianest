package com.example.ui.library.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MediaNestApp
import com.example.data.db.MediaCategory
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.SortRow
import com.example.ui.components.rememberSortRevealConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll

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
    var subTabState by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var targetAlbum by remember(initialAlbum) { mutableStateOf(initialAlbum) }
    var targetArtist by remember(initialArtist) { mutableStateOf(initialArtist) }
    var targetFolder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var itemToAddToPlaylist by remember { mutableStateOf<MediaItem?>(null) }

    var sortField by remember { mutableStateOf("Name") }
    var isAscending by remember { mutableStateOf(true) }
    val (isSortVisible, nestedScrollConnection) = rememberSortRevealConnection()

    // Separate sort fields for different tabs
    val sortOptions = remember(subTabState) {
        when (subTabState) {
            0 -> listOf("Name", "Date Added", "Artist")
            3 -> listOf("Name", "Release Year", "Artist")
            4 -> listOf("Name", "Date Added")
            5 -> listOf("Name", "Date Added")
            6 -> listOf("Custom Order", "Name", "Date Added", "Date Played")
            else -> listOf("Name", "Date Added")
        }
    }

    // Reset sort field if not available in current tab
    LaunchedEffect(subTabState) {
        if (sortField !in sortOptions) {
            sortField = if (subTabState == 3) "Name" else if (subTabState == 6) "Custom Order" else "Name"
            isAscending = true
        }
    }

    val db = remember { com.example.MediaNestApp.instance.database }
    val cachedMetadataList by db.metadataCacheDao().getAllCacheFlow().collectAsState(initial = emptyList<com.example.data.db.AudioMetadataCache>())
    val cacheMap = remember(cachedMetadataList) { cachedMetadataList.associateBy { it.audioUri } }
    
    val effectiveAudioList = remember(audioList, cacheMap, sortField, isAscending, subTabState) {
        val list = audioList.map { item ->
            val cached = cacheMap[item.uri.toString()]
            if (cached != null) {
                item.copy(
                    title = cached.title ?: item.title,
                    artist = cached.artist.takeIf { !it.isNullOrBlank() } ?: item.artist,
                    album = cached.album.takeIf { !it.isNullOrBlank() } ?: item.album,
                    albumArtUri = cached.albumArtUri?.let { android.net.Uri.parse(it) } ?: item.albumArtUri
                )
            } else {
                item
            }
        }
        
        // Sorting logic based on tab and field (Tracks & Albums & Artists & Folders)
        if (subTabState == 0 || subTabState == 3 || subTabState == 4 || subTabState == 5) {
            val comp = when (sortField) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Artist" -> compareBy<MediaItem> { (it.artist ?: "").lowercase() }
                "Date Added" -> compareBy<MediaItem> { it.dateAdded }
                "Release Year" -> compareBy<MediaItem> { 
                    val cached = cacheMap[it.uri.toString()]
                    cached?.year?.toIntOrNull() ?: 0
                }
                else -> compareBy<MediaItem> { it.title.lowercase() }
            }
            if (isAscending) list.sortedWith(comp) else list.sortedWith(comp).reversed()
        } else {
            list
        }
    }

    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed().collectAsState(initial = emptyList())
    val mostPlayedStates by db.playbackStateDao().getMostPlayed().collectAsState(initial = emptyList())

    val sortedPlaylists = remember(playlists, sortField, isAscending, subTabState, recentlyPlayedStates) {
        if (subTabState != 6) return@remember playlists
        val comp = when (sortField) {
            "Name" -> compareBy<MediaCategory> { it.name.lowercase() }
            "Date Added" -> compareBy<MediaCategory> { it.createdAt }
            "Date Played" -> compareBy<MediaCategory> { 0L }
            else -> compareBy<MediaCategory> { it.sortOrder } // Custom Order
        }
        if (isAscending) playlists.sortedWith(comp) else playlists.sortedWith(comp).reversed()
    }

    val recentSongs = remember(recentlyPlayedStates, effectiveAudioList) {
        val audioMap = effectiveAudioList.associateBy { it.uri.toString() }
        recentlyPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
    }

    val mostPlayedSongs = remember(mostPlayedStates, effectiveAudioList) {
        val audioMap = effectiveAudioList.associateBy { it.uri.toString() }
        mostPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
    }

    BackHandler(enabled = subTabState != 0) {
        subTabState = 0
    }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val subTabs = remember {
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

    val visibleCategories = remember(subTabs, effectiveAudioList, recentSongs, mostPlayedSongs, playlists, subTabState) {
        subTabs.filter { cat ->
            when (cat.index) {
                0 -> true
                1 -> recentSongs.isNotEmpty() || subTabState == 1
                2 -> mostPlayedSongs.isNotEmpty() || subTabState == 2
                3 -> effectiveAudioList.any { !it.album.isNullOrBlank() } || subTabState == 3
                4 -> effectiveAudioList.any { !it.artist.isNullOrBlank() } || subTabState == 4
                5 -> true
                6 -> true
                else -> true
            }
        }
    }

    val contentBlock: @Composable () -> Unit = {
        when (subTabState) {
            0 -> SongsList(
                songs = effectiveAudioList,
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
            3 -> AlbumsGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                gridSizeLevel = gridSizeLevel,
                initialSelectedAlbum = targetAlbum,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                sortField = sortField,
                isAscending = isAscending,
                cacheMap = cacheMap
            )
            4 -> ArtistsGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                gridSizeLevel = gridSizeLevel,
                initialSelectedArtist = targetArtist,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                sortField = sortField,
                isAscending = isAscending
            )
            5 -> FoldersGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                initialSelectedFolder = targetFolder,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                },
                sortField = sortField,
                isAscending = isAscending
            )
            6 -> PlaylistsList(
                playlists = sortedPlaylists,
                audioList = effectiveAudioList,
                recentSongs = recentSongs,
                mostPlayedSongs = mostPlayedSongs,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onSongClick = onSongClick,
                isLoading = isLoading,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab: Int, album: String?, artist: String?, folder: String? ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                }
            )
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassSurface(
                modifier = Modifier
                    .width(200.dp)
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
                    visibleCategories.forEach { item ->
                        val isSelected = subTabState == item.index
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
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
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = item.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF8E95A5)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Column {
                    SortRow(
                        sortField = sortField,
                        onSortFieldChange = { sortField = it },
                        isAscending = isAscending,
                        onIsAscendingChange = { isAscending = it },
                        isVisible = isSortVisible.value && (subTabState in listOf(0, 3, 4, 5, 6)),
                        options = sortOptions,
                        onBack = when (subTabState) {
                            3 -> if (targetAlbum != null) ({ targetAlbum = null }) else null
                            4 -> if (targetArtist != null) ({ targetArtist = null }) else null
                            5 -> if (targetFolder != null) ({ targetFolder = null }) else null
                            else -> null
                        },
                        backLabel = when (subTabState) {
                            3 -> targetAlbum
                            4 -> targetArtist
                            5 -> targetFolder?.substringAfterLast('/')
                            else -> null
                        }
                    )
                    contentBlock()
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(visibleCategories, key = { it.index }) { item ->
                    val isSelected = subTabState == item.index
                    GlassSurface(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
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
            
            SortRow(
                sortField = sortField,
                onSortFieldChange = { sortField = it },
                isAscending = isAscending,
                onIsAscendingChange = { isAscending = it },
                isVisible = isSortVisible.value && (subTabState in listOf(0, 3, 4, 5, 6)),
                options = sortOptions,
                onBack = when (subTabState) {
                    3 -> if (targetAlbum != null) ({ targetAlbum = null }) else null
                    4 -> if (targetArtist != null) ({ targetArtist = null }) else null
                    5 -> if (targetFolder != null) ({ targetFolder = null }) else null
                    else -> null
                },
                backLabel = when (subTabState) {
                    3 -> targetAlbum
                    4 -> targetArtist
                    5 -> targetFolder?.substringAfterLast('/')
                    else -> null
                }
            )

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
