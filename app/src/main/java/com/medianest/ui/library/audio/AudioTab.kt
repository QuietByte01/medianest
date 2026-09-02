package com.medianest.ui.library.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import com.medianest.MediaNestApp
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.SortRow
import com.medianest.ui.components.rememberSortRevealConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch

@Composable
fun AudioTab(
    audioList: List<MediaItem>,
    playlists: List<MediaCategory>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridSizeLevel: Int = 1,
    isLoading: Boolean = false,
    isScanningHidden: Boolean = false,
    onSongClick: (List<MediaItem>, Int) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    initialSubTab: Int = 0,
    initialAlbum: String? = null,
    initialArtist: String? = null,
    initialFolder: String? = null,
    initialTargetSongUri: String? = null,
    onBackToDashboard: () -> Unit = {},
    onRescanHiddenMedia: () -> Unit = {},
    onShowInfo: (MediaItem) -> Unit = {},
    viewModel: com.medianest.ui.MediaViewModel = viewModel()
) {
    var subTabState by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var previousSubTabState by remember { mutableStateOf<Int?>(null) }
    var targetAlbum by remember(initialAlbum) { mutableStateOf(initialAlbum) }
    var targetArtist by remember(initialArtist) { mutableStateOf(initialArtist) }
    var targetFolder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var targetSongUri by remember(initialTargetSongUri) { mutableStateOf(initialTargetSongUri) }
    var targetPlaylist by remember { mutableStateOf<MediaCategory?>(null) }
    var itemToAddToPlaylist by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(initialTargetSongUri) {
        if (!initialTargetSongUri.isNullOrBlank()) {
            targetSongUri = initialTargetSongUri
        }
    }

    val settingsManager = MediaNestApp.instance.settingsManager
    val showHiddenSetting by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val persistedSortField by settingsManager.audioSortField.collectAsState(initial = "Name")
    val persistedSortAscending by settingsManager.audioSortAscending.collectAsState(initial = true)
    
    val sortField = persistedSortField
    val isAscending = persistedSortAscending
    
    LaunchedEffect(sortField, isAscending) {
        viewModel.updateAudioSort(sortField, isAscending)
    }

    LaunchedEffect(subTabState) {
        val tabId = when (subTabState) {
            7 -> "EXCLUDED"
            else -> "ALL" // Default to all if not excluded, as other tabs are handled differently
        }
        viewModel.updateAudioFilter(tabId)
    }
    
    val (isSortVisible, nestedScrollConnection) = rememberSortRevealConnection()
    val scope = rememberCoroutineScope()

    // Persistent Scroll States
    val songsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val albumsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val artistsGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val foldersGridState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Smooth Scroll on Sort Change
    LaunchedEffect(sortField, isAscending) {
        when (subTabState) {
            0, 1, 2 -> songsListState.animateScrollToItem(0)
            3 -> albumsGridState.animateScrollToItem(0)
            4 -> artistsGridState.animateScrollToItem(0)
            5 -> foldersGridState.animateScrollToItem(0)
        }
    }

    // Separate sort fields for different tabs
    val sortOptions = remember(subTabState) {
        when (subTabState) {
            0 -> listOf("Name", "Date Added", "Artist")
            3 -> listOf("Name", "Release Year", "Artist")
            4 -> listOf("Name", "Date Added")
            5 -> listOf("Name", "Date Added", "Size")
            6 -> listOf("Custom Order", "Name", "Date Added", "Date Played")
            else -> listOf("Name", "Date Added")
        }
    }

    // Reset sort field if not available in current tab
    LaunchedEffect(subTabState) {
        if (persistedSortField !in sortOptions) {
            settingsManager.setAudioSortField(if (subTabState == 3) "Name" else if (subTabState == 6) "Custom Order" else "Name")
            settingsManager.setAudioSortAscending(true)
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()

    val db = remember { com.medianest.MediaNestApp.instance.database }
    val cachedMetadataList by db.metadataCacheDao().getAllCacheFlow().collectAsState(initial = emptyList<com.medianest.data.db.AudioMetadataCache>())
    val cacheMap = remember(cachedMetadataList) { cachedMetadataList.associateBy { it.audioUri } }
    
    val effectiveAudioList = remember(audioList, cacheMap, sortField, isAscending, subTabState, showHiddenSetting, searchQuery) {
        val baseList = when (subTabState) {
            7 -> audioList.filter { com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
            8 -> audioList.filter { com.medianest.util.FolderHiddenUtils.isItemHidden(it) && !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
            5 -> audioList.filter { !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
            else -> audioList.filter { item ->
                !com.medianest.util.FolderHiddenUtils.isItemExcluded(item) &&
                (showHiddenSetting || !com.medianest.util.FolderHiddenUtils.isItemHidden(item))
            }
        }

        val list = baseList.map { item ->
            val cached = cacheMap[item.uri.toString()]
            if (cached != null) {
                item.copy(
                    title = cached.title ?: item.title,
                    artist = cached.artist.takeIf { !it.isNullOrBlank() } ?: item.artist,
                    album = cached.album.takeIf { !it.isNullOrBlank() } ?: item.album,
                    albumArtUri = cached.albumArtUri?.let { android.net.Uri.parse(it) } ?: item.albumArtUri,
                    genre = cached.genre ?: item.genre,
                    year = cached.year ?: item.year,
                    composer = cached.composer ?: item.composer,
                    albumArtist = cached.albumArtist ?: item.albumArtist,
                    trackNumber = cached.trackNumber ?: item.trackNumber
                )
            } else {
                item
            }
        }

        val filteredBySearch = if (searchQuery.isNotBlank()) {
            list.filter { item ->
                item.title.contains(searchQuery, ignoreCase = true) ||
                (item.artist?.contains(searchQuery, ignoreCase = true) == true) ||
                (item.album?.contains(searchQuery, ignoreCase = true) == true)
            }
        } else {
            list
        }
        
        // Sorting logic based on tab and field (Tracks & Albums & Artists & Folders & Excluded & Hidden)
        if (subTabState == 0 || subTabState == 3 || subTabState == 4 || subTabState == 5 || subTabState == 7 || subTabState == 8) {
            val comp = when (sortField) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Artist" -> compareBy<MediaItem> { (it.artist ?: "").lowercase() }
                "Date Added", "Date" -> compareBy<MediaItem> { maxOf(it.dateAdded, it.dateCreated, it.dateModified) }
                "Size" -> compareBy<MediaItem> { if (it.size > 0) it.size else Long.MAX_VALUE }
                "Duration" -> compareBy<MediaItem> { it.durationMs }
                "Release Year" -> compareBy<MediaItem> { 
                    val cached = cacheMap[it.uri.toString()]
                    cached?.year?.toIntOrNull() ?: 0
                }
                else -> compareBy<MediaItem> { it.title.lowercase() }
            }
            if (isAscending) filteredBySearch.sortedWith(comp) else filteredBySearch.sortedWith(comp).reversed()
        } else {
            filteredBySearch
        }
    }

    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed("AUDIO").collectAsState(initial = emptyList())
    val mostPlayedStates by db.playbackStateDao().getMostPlayed("AUDIO").collectAsState(initial = emptyList())

    val sortedPlaylists = remember(playlists, sortField, isAscending, subTabState, recentlyPlayedStates, searchQuery) {
        if (subTabState != 6) return@remember playlists
        val basePlaylists = if (searchQuery.isNotBlank()) {
            playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            playlists
        }
        val comp = when (sortField) {
            "Name" -> compareBy<MediaCategory> { it.name.lowercase() }
            "Date Added" -> compareBy<MediaCategory> { it.createdAt }
            "Date Played" -> compareBy<MediaCategory> { 0L }
            else -> compareBy<MediaCategory> { it.sortOrder } // Custom Order
        }
        if (isAscending) basePlaylists.sortedWith(comp) else basePlaylists.sortedWith(comp).reversed()
    }

    val recentSongs = remember(recentlyPlayedStates, effectiveAudioList) {
        val audioMap = effectiveAudioList.associateBy { it.uri.toString() }
        recentlyPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
            .filter { !it.isExcluded }
    }

    val mostPlayedSongs = remember(mostPlayedStates, effectiveAudioList) {
        val audioMap = effectiveAudioList.associateBy { it.uri.toString() }
        mostPlayedStates.mapNotNull { state -> audioMap[state.mediaUri] }
            .filter { !it.isExcluded }
    }

    BackHandler(enabled = subTabState != 0 || targetAlbum != null || targetArtist != null || targetFolder != null || targetPlaylist != null || previousSubTabState != null) {
        if (targetPlaylist != null) {
            targetPlaylist = null
        } else if (targetAlbum != null || targetArtist != null || targetFolder != null) {
            targetAlbum = null
            targetArtist = null
            targetFolder = null
        } else if (previousSubTabState != null) {
            subTabState = previousSubTabState!!
            previousSubTabState = null
        } else {
            subTabState = 0
        }
    }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val subTabs = remember(showHiddenSetting) {
        val base = mutableListOf(
            SubTabInfo(0, "All Songs", Icons.Default.MusicNote),
            SubTabInfo(3, "Albums", Icons.Default.Album),
            SubTabInfo(4, "Artists", Icons.Default.Person),
            SubTabInfo(5, "Folders", Icons.Default.Folder),
            SubTabInfo(6, "Playlists", Icons.Default.QueueMusic),
            SubTabInfo(1, "Recent", Icons.Default.Schedule),
            SubTabInfo(2, "Most Played", Icons.Default.LocalFireDepartment)
        )
        if (showHiddenSetting) {
            base.add(SubTabInfo(7, "Excluded", Icons.Default.VisibilityOff))
            base.add(SubTabInfo(8, "Hidden Folders", Icons.Default.FolderZip))
        }
        base
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
                7 -> audioList.any { it.isExcluded } || subTabState == 7
                8 -> audioList.any { it.isHidden && !it.isExcluded } || subTabState == 8
                else -> true
            }
        }
    }

    val contentBlock: @Composable () -> Unit = {
        if ((isLoading && (audioList.isEmpty() || (effectiveAudioList.isEmpty() && subTabState !in listOf(1, 2, 6)))) ||
            (isScanningHidden && (subTabState == 8 || subTabState == 7) && effectiveAudioList.isEmpty())) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.MediaLoadingAnimation(
                    mediaType = com.medianest.data.db.MediaType.AUDIO,
                    iconSize = 52.dp,
                    showLabel = isScanningHidden && (subTabState == 8 || subTabState == 7),
                    customMessage = when {
                        isScanningHidden && subTabState == 8 -> "Scanning hidden audio..."
                        isScanningHidden && subTabState == 7 -> "Scanning excluded audio..."
                        else -> null
                    }
                )
            }
        } else {
            when (subTabState) {
                0 -> SongsList(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                isLoading = isLoading,
                listState = songsListState,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onShowInfo = onShowInfo,
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                }
            )
            1 -> SongsList(
                songs = recentSongs,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                isLoading = isLoading,
                listState = songsListState,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onShowInfo = onShowInfo,
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                }
            )
            2 -> SongsList(
                songs = mostPlayedSongs,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                isLoading = isLoading,
                listState = songsListState,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onShowInfo = onShowInfo,
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                }
            )
            7 -> FoldersGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                initialSelectedFolder = targetFolder,
                targetSongUri = targetSongUri,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                },
                isLoading = isLoading,
                isScanningHidden = isScanningHidden,
                onRescanHiddenMedia = onRescanHiddenMedia,
                sortField = sortField,
                isAscending = isAscending
            )
            8 -> FoldersGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                initialSelectedFolder = targetFolder,
                targetSongUri = targetSongUri,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                },
                isLoading = isLoading,
                isScanningHidden = isScanningHidden,
                onRescanHiddenMedia = onRescanHiddenMedia,
                sortField = sortField,
                isAscending = isAscending
            )
            3 -> AlbumsGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                initialSelectedAlbum = targetAlbum,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                },
                sortField = sortField,
                isAscending = isAscending,
                cacheMap = cacheMap,
                gridState = albumsGridState
            )
            4 -> ArtistsGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                initialSelectedArtist = targetArtist,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                },
                sortField = sortField,
                isAscending = isAscending,
                gridState = artistsGridState
            )
            5 -> FoldersGrid(
                songs = effectiveAudioList,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                initialSelectedFolder = targetFolder,
                targetSongUri = targetSongUri,
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab, album, artist, folder, targetUri ->
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                },
                isLoading = isLoading,
                isScanningHidden = isScanningHidden,
                onRescanHiddenMedia = onRescanHiddenMedia,
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
                initialSelectedPlaylist = targetPlaylist,
                onSelectPlaylist = { targetPlaylist = it },
                onAddToPlaylist = { itemToAddToPlaylist = it },
                onNavigateSubTab = { tab: Int, album: String?, artist: String?, folder: String?, targetUri: String? ->
                    previousSubTabState = subTabState
                    subTabState = tab
                    if (album != null) targetAlbum = album
                    if (artist != null) targetArtist = artist
                    if (folder != null) targetFolder = folder
                    if (targetUri != null) targetSongUri = targetUri
                }
            )
        }
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
                                .clickable { 
                                    subTabState = item.index
                                    previousSubTabState = null
                                    targetAlbum = null
                                    targetArtist = null
                                    targetFolder = null
                                    targetPlaylist = null
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0x1AFFFFFF) else Color.Transparent
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
                        onSortFieldChange = { scope.launch { settingsManager.setAudioSortField(it) } },
                        isAscending = isAscending,
                        onIsAscendingChange = { scope.launch { settingsManager.setAudioSortAscending(it) } },
                        isVisible = isSortVisible.value && (subTabState in listOf(0, 3, 4, 5, 6) || previousSubTabState != null),
                        options = sortOptions,
                        onBack = when {
                            targetPlaylist != null -> ({ targetPlaylist = null })
                            targetAlbum != null -> ({ targetAlbum = null })
                            targetArtist != null -> ({ targetArtist = null })
                            targetFolder != null -> ({ targetFolder = null })
                            previousSubTabState != null -> ({
                                subTabState = previousSubTabState!!
                                previousSubTabState = null
                            })
                            subTabState != 0 -> ({ subTabState = 0 })
                            else -> onBackToDashboard
                        },
                        backLabel = when {
                            targetPlaylist != null -> targetPlaylist?.name
                            targetAlbum != null -> targetAlbum
                            targetArtist != null -> targetArtist
                            targetFolder != null -> targetFolder?.substringAfterLast('/')
                            previousSubTabState == 6 -> "Playlists"
                            previousSubTabState != null -> visibleCategories.firstOrNull { it.index == previousSubTabState }?.label
                            subTabState != 0 -> "All Songs"
                            else -> "Dashboard"
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
                            .clickable { 
                                subTabState = item.index
                                previousSubTabState = null
                                targetAlbum = null
                                targetArtist = null
                                targetFolder = null
                                targetPlaylist = null
                            },
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
                onSortFieldChange = { scope.launch { settingsManager.setAudioSortField(it) } },
                isAscending = isAscending,
                onIsAscendingChange = { scope.launch { settingsManager.setAudioSortAscending(it) } },
                isVisible = isSortVisible.value && (subTabState in listOf(0, 3, 4, 5, 6) || previousSubTabState != null),
                options = sortOptions,
                onBack = when {
                    targetPlaylist != null -> ({ targetPlaylist = null })
                    targetAlbum != null -> ({ targetAlbum = null })
                    targetArtist != null -> ({ targetArtist = null })
                    targetFolder != null -> ({ targetFolder = null })
                    previousSubTabState != null -> ({
                        subTabState = previousSubTabState!!
                        previousSubTabState = null
                    })
                    subTabState != 0 -> ({ subTabState = 0 })
                    else -> onBackToDashboard
                },
                backLabel = when {
                    targetPlaylist != null -> targetPlaylist?.name
                    targetAlbum != null -> targetAlbum
                    targetArtist != null -> targetArtist
                    targetFolder != null -> targetFolder?.substringAfterLast('/')
                    previousSubTabState == 6 -> "Playlists"
                    previousSubTabState != null -> visibleCategories.firstOrNull { it.index == previousSubTabState }?.label
                    subTabState != 0 -> "All Songs"
                    else -> "Dashboard"
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
