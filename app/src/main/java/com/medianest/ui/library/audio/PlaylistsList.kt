package com.medianest.ui.library.audio

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MediaLoadingAnimation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PlaylistsList(
    playlists: List<MediaCategory>,
    audioList: List<MediaItem>,
    recentSongs: List<MediaItem> = emptyList(),
    mostPlayedSongs: List<MediaItem> = emptyList(),
    onCreatePlaylistClick: () -> Unit,
    onSongClick: (MediaItem) -> Unit,
    isLoading: Boolean = false,
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?) -> Unit = { _, _, _, _ -> },
    onAddToPlaylist: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPlaylist by remember { mutableStateOf<MediaCategory?>(null) }
    var playlistUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistToDelete by remember { mutableStateOf<MediaCategory?>(null) }

    BackHandler(enabled = selectedPlaylist != null) {
        selectedPlaylist = null
    }

    var isPlaylistLoading by remember(selectedPlaylist?.id) { mutableStateOf(selectedPlaylist != null) }

    LaunchedEffect(selectedPlaylist?.id) {
        if (selectedPlaylist != null) {
            isPlaylistLoading = true
            val db = com.medianest.MediaNestApp.instance.database
            db.categoryDao().getMediaUrisForCategory(selectedPlaylist!!.id).collectLatest { uris ->
                playlistUris = uris
                isPlaylistLoading = false
            }
        } else {
            playlistUris = emptyList()
            isPlaylistLoading = false
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
            val db = com.medianest.MediaNestApp.instance.database
            db.categoryDao().getMediaUrisForCategory(favPlaylist.id).collectLatest { uris ->
                favCount = uris.size
                val favTracksWithArt = audioList.filter { uris.contains(it.uri.toString()) && it.albumArtUri != null }
                favArtUri = favTracksWithArt.maxByOrNull { it.dateAdded }?.albumArtUri ?: latestAddedArtUri
            }
        } else {
            favArtUri = latestAddedArtUri
            favCount = 0
        }
    }

    val mostPlayedArtUri = remember(mostPlayedSongs) {
        mostPlayedSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri
    }

    val recentlyPlayedArtUri = remember(recentSongs) {
        recentSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri
    }

    val recentlyAddedArtUri = remember(audioList) {
        audioList.sortedByDescending { it.dateAdded }
            .firstOrNull { it.albumArtUri != null }?.albumArtUri
    }

    val userPlaylists = remember(playlists) {
        playlists.filter { 
            !it.name.equals("Favourites", ignoreCase = true) && 
            !it.name.equals("Favorites", ignoreCase = true) 
        }
    }

    var showRecognizedPlaylistsSheet by remember { mutableStateOf(false) }

    if (selectedPlaylist != null) {
        val playlistSongs = audioList.filter { playlistUris.contains(it.uri.toString()) }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedPlaylist!!.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                IconButton(
                    onClick = {
                        exportPlaylistToM3u(context, selectedPlaylist!!, audioList)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export Playlist",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }

            if (isPlaylistLoading || (isLoading && playlistSongs.isEmpty())) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MediaLoadingAnimation(
                        mediaType = MediaType.AUDIO,
                        iconSize = 52.dp,
                        showLabel = true
                    )
                }
            } else if (playlistSongs.isEmpty()) {
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
    } else {
        if (isLoading && playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MediaLoadingAnimation(
                    mediaType = MediaType.AUDIO,
                    iconSize = 52.dp,
                    showLabel = true
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
                                    subtitle = "${mostPlayedSongs.size} tracks",
                                    icon = Icons.Default.LocalFireDepartment,
                                    artUri = mostPlayedArtUri,
                                    gradientColors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
                                    onClick = { onNavigateSubTab(2, null, null, null) }
                                )
                            }
                            item {
                                QuickAccessCard(
                                    title = "Recently Played",
                                    subtitle = "${recentSongs.size} tracks",
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
                                    onClick = { onNavigateSubTab(0, null, null, null) }
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
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showRecognizedPlaylistsSheet = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Import Playlist File",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                                onDelete = { playlistToDelete = pl }
                            )
                        }
                    }
                }
            }
        }
    }

    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete the playlist '${target.name}'? The songs will not be deleted from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete = null
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val db = com.medianest.MediaNestApp.instance.database
                            db.categoryDao().deleteCategory(target)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRecognizedPlaylistsSheet) {
        RecognizedPlaylistsSheet(
            audioList = audioList,
            onDismiss = { showRecognizedPlaylistsSheet = false }
        )
    }
}
