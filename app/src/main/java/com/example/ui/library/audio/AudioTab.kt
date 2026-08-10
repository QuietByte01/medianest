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
    var itemToAddToPlaylist by remember { mutableStateOf<MediaItem?>(null) }

    val db = remember { com.example.MediaNestApp.instance.database }
    val cachedMetadataList by db.metadataCacheDao().getAllCacheFlow().collectAsState(initial = emptyList())
    val cacheMap = remember(cachedMetadataList) { cachedMetadataList.associateBy { it.audioUri } }
    val effectiveAudioList = remember(audioList, cacheMap) {
        audioList.map { item ->
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
    }

    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed().collectAsState(initial = emptyList())
    val mostPlayedStates by db.playbackStateDao().getMostPlayed().collectAsState(initial = emptyList())

    val recentSongs = remember(recentlyPlayedStates, effectiveAudioList) {
        val audioMap = effectiveAudioList.associateBy { it.uri.toString() }
        val fromDb = recentlyPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
        if (fromDb.isNotEmpty()) fromDb else effectiveAudioList.sortedByDescending { it.dateAdded }
    }

    val mostPlayedSongs = remember(mostPlayedStates, effectiveAudioList) {
        val audioMap = effectiveAudioList.associateBy { it.uri.toString() }
        val fromDb = mostPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
        if (fromDb.isNotEmpty()) fromDb else effectiveAudioList.sortedByDescending { it.id }
    }

    BackHandler(enabled = subTabState != 0) {
        subTabState = 0
    }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

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

    val visibleCategories = remember(categories, effectiveAudioList, recentSongs, mostPlayedSongs, playlists, subTabState) {
        categories.filter { cat ->
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
            3 -> AlbumsGrid(effectiveAudioList, selectedUris, isSelectionMode, onSongClick, onSongLongClick, gridSizeLevel = gridSizeLevel, initialSelectedAlbum = targetAlbum, onAddToPlaylist = { itemToAddToPlaylist = it })
            4 -> ArtistsGrid(effectiveAudioList, selectedUris, isSelectionMode, onSongClick, onSongLongClick, gridSizeLevel = gridSizeLevel, initialSelectedArtist = targetArtist, onAddToPlaylist = { itemToAddToPlaylist = it })
            5 -> FoldersGrid(effectiveAudioList, selectedUris, isSelectionMode, onSongClick, onSongLongClick, initialSelectedFolder = targetFolder, onAddToPlaylist = { itemToAddToPlaylist = it }, onNavigateSubTab = { tab, album, artist, folder ->
                subTabState = tab
                if (album != null) targetAlbum = album
                if (artist != null) targetArtist = artist
                if (folder != null) targetFolder = folder
            })
            6 -> PlaylistsList(
                playlists = playlists,
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
