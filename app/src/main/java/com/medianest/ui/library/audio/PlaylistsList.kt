package com.medianest.ui.library.audio

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
import com.medianest.data.model.ArtistInfo
import com.medianest.ui.components.ArtistInfoPanel
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MediaLoadingAnimation
import com.medianest.util.ArtistImageUtils
import com.medianest.util.rememberArtistImageUrl
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.medianest.MediaNestApp
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.util.formatDurationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

private fun formatPlaylistDuration(totalMs: Long): String {
    if (totalMs <= 0) return "0s"
    val totalSec = totalMs / 1000
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60

    return when {
        hours > 0 -> "${hours} hr ${mins} mins"
        mins > 0 && secs > 0 -> "${mins} mins ${secs}s"
        mins > 0 -> "${mins} mins"
        else -> "${secs}s"
    }
}

@Composable
private fun FavoriteArtistCard(
    artistName: String,
    favCount: Int,
    coverUri: Uri?,
    onClick: () -> Unit
) {
    val artistImgUrl = rememberArtistImageUrl(artistName)
    val cardShape = RoundedCornerShape(16.dp)

    GlassSurface(
        shape = cardShape,
        enableBlur = false,
        backgroundColor = Color(0x1F24293A),
        borderColor = Color(0x2BFFFFFF),
        modifier = Modifier
            .width(110.dp)
            .height(120.dp)
            .clip(cardShape)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(cardShape)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp)
                )
            }

            val context = LocalContext.current
            val primaryModel = remember(artistImgUrl, coverUri, artistName) {
                artistImgUrl ?: coverUri ?: ArtistImageUtils.getFallbackArtistImageUrl(artistName)
            }
            val imageRequest = remember(primaryModel) {
                ImageRequest.Builder(context)
                    .data(primaryModel)
                    .crossfade(false)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(cardShape)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = artistName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsList(
    playlists: List<MediaCategory>,
    audioList: List<MediaItem>,
    recentSongs: List<MediaItem> = emptyList(),
    mostPlayedSongs: List<MediaItem> = emptyList(),
    onCreatePlaylistClick: () -> Unit,
    onSongClick: (List<MediaItem>, Int) -> Unit,
    isLoading: Boolean = false,
    initialSelectedPlaylist: MediaCategory? = null,
    onSelectPlaylist: (MediaCategory?) -> Unit = {},
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?, targetSongUri: String?) -> Unit = { _, _, _, _, _ -> },
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onShowInfo: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPlaylist by remember(initialSelectedPlaylist) { mutableStateOf(initialSelectedPlaylist) }
    var selectedArtistForInfo by remember { mutableStateOf<String?>(null) }
    var artistInfoObject by remember { mutableStateOf<ArtistInfo?>(null) }
    var isArtistInfoLoading by remember { mutableStateOf(false) }
    var browsingAlbumName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialSelectedPlaylist) {
        selectedPlaylist = initialSelectedPlaylist
    }

    var playlistUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistToDelete by remember { mutableStateOf<MediaCategory?>(null) }

    BackHandler(enabled = selectedArtistForInfo != null) {
        if (browsingAlbumName != null) {
            browsingAlbumName = null
        } else {
            selectedArtistForInfo = null
        }
    }

    BackHandler(enabled = selectedPlaylist != null && selectedArtistForInfo == null) {
        selectedPlaylist = null
        onSelectPlaylist(null)
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
                val favTracks = audioList.filter { uris.contains(it.uri.toString()) }
                favArtUri = favTracks.maxByOrNull { it.dateAdded }?.let { it.albumArtUri ?: it.uri }
            }
        } else {
            favArtUri = null
            favCount = 0
        }
    }

    val mostPlayedArtUri = remember(mostPlayedSongs) {
        mostPlayedSongs.firstOrNull()?.let { it.albumArtUri ?: it.uri }
    }

    val recentlyPlayedArtUri = remember(recentSongs) {
        recentSongs.firstOrNull()?.let { it.albumArtUri ?: it.uri }
    }

    val recentlyAddedArtUri = remember(audioList) {
        audioList.maxByOrNull { it.dateAdded }?.let { it.albumArtUri ?: it.uri }
    }

    val userPlaylists = remember(playlists) {
        playlists.filter { 
            !it.name.equals("Favourites", ignoreCase = true) && 
            !it.name.equals("Favorites", ignoreCase = true) 
        }
    }

    var showRecognizedPlaylistsSheet by remember { mutableStateOf(false) }

    val artistRepo = remember { com.medianest.MediaNestApp.instance.artistMetadataRepository }

    val db = remember { com.medianest.MediaNestApp.instance.database }
    val allCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())
    val artistCategories by db.categoryDao().getCategoriesByType("ARTIST").collectAsState(initial = emptyList())

    val followedCategory = remember(artistCategories) {
        artistCategories.find { it.name.equals("Followed Artists", ignoreCase = true) }
    }

    val followedArtistNames = remember(allCrossRefs, followedCategory) {
        if (followedCategory == null) emptyList<String>()
        else allCrossRefs.filter { it.categoryId == followedCategory.id }.map { it.mediaUri }
    }

    LaunchedEffect(selectedArtistForInfo) {
        browsingAlbumName = null
        val name = selectedArtistForInfo
        if (name != null) {
            isArtistInfoLoading = true
            val info = artistRepo.getArtistInfo(name)
            val artistSongs = audioList.filter { it.artist.equals(name, ignoreCase = true) }
            val localAlbums = artistSongs.groupBy { it.album ?: "Unknown Album" }.map { (title, songs) ->
                com.medianest.data.model.LocalAlbumInfo(
                    title = title,
                    artworkUri = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: songs.firstOrNull()?.uri,
                    songCount = songs.size
                )
            }
            artistInfoObject = info.copy(localAlbums = localAlbums)
            isArtistInfoLoading = false
        } else {
            artistInfoObject = null
            isArtistInfoLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedArtistForInfo != null) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isArtistInfoLoading || artistInfoObject == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MediaLoadingAnimation(mediaType = MediaType.AUDIO, iconSize = 52.dp)
                    }
                } else {
                    ArtistInfoPanel(
                        artistInfo = artistInfoObject!!,
                        browsingAlbumName = browsingAlbumName,
                        onPopularAlbumClick = { albumName ->
                            browsingAlbumName = albumName
                        },
                        onLocalAlbumClick = { albumName ->
                            browsingAlbumName = albumName
                        },
                        onBackToArtist = {
                            browsingAlbumName = null
                        },
                        onArtistClick = { newArtist ->
                            browsingAlbumName = null
                            selectedArtistForInfo = newArtist
                        },
                        allAudioItems = audioList,
                        useCardShape = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else if (selectedPlaylist != null) {
            val playlistSongs = audioList.filter { playlistUris.contains(it.uri.toString()) }

            val isFavoritesPlaylist = remember(selectedPlaylist) {
                selectedPlaylist?.name.equals("Favourites", ignoreCase = true) ||
                selectedPlaylist?.name.equals("Favorites", ignoreCase = true)
            }

            val followedArtistsWithCount = remember(followedArtistNames, playlistSongs, audioList, isFavoritesPlaylist) {
                if (!isFavoritesPlaylist) {
                    emptyList()
                } else {
                    followedArtistNames.map { artistName ->
                        val favCount = playlistSongs.count { song ->
                            val rawArtist = song.artist ?: "Unknown Artist"
                            rawArtist.contains(artistName, ignoreCase = true)
                        }
                        val artistSongs = audioList.filter { it.artist?.contains(artistName, ignoreCase = true) == true }
                        val coverUri = artistSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: artistSongs.firstOrNull()?.uri
                        Triple(artistName, favCount, coverUri)
                    }.sortedByDescending { it.second }
                }
            }

        var showPlaylistDetailMenu by remember { mutableStateOf(false) }

        val headerGradient = remember(selectedPlaylist?.id, selectedPlaylist?.name) {
            val pl = selectedPlaylist
            val idHash = if (pl != null) abs(pl.name.hashCode() + pl.id.toInt()) else 0
            val hue = (idHash % 360).toFloat()
            listOf(
                Color.hsv(hue, 0.45f, 0.28f, 0.85f),
                Color.hsv((hue + 20f) % 360f, 0.50f, 0.38f, 0.85f),
                Color.hsv(hue, 0.45f, 0.28f, 0.85f)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFavoritesPlaylist) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    enableBlur = false,
                    backgroundBrush = Brush.horizontalGradient(colors = headerGradient),
                    borderColor = Color(0x44FFFFFF)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "PLAYLIST",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.70f),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = selectedPlaylist?.name ?: "Playlist",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val totalDurationMs = remember(playlistSongs) { playlistSongs.sumOf { it.durationMs } }
                            val formattedDuration = remember(totalDurationMs) { formatPlaylistDuration(totalDurationMs) }
                            Text(
                                text = "${playlistSongs.size} tracks • $formattedDuration",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            IconButton(
                                onClick = { showPlaylistDetailMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Playlist Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            GlassDropdownMenu(
                                expanded = showPlaylistDetailMenu,
                                onDismissRequest = { showPlaylistDetailMenu = false },
                                modifier = Modifier.width(200.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export Playlist (.m3u)", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showPlaylistDetailMenu = false
                                        selectedPlaylist?.let { pl ->
                                            exportPlaylistToM3u(context, pl, audioList)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showPlaylistDetailMenu = false
                                        playlistToDelete = selectedPlaylist
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isFavoritesPlaylist && followedArtistsWithCount.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(followedArtistsWithCount, key = { it.first }) { (artistName, count, coverUri) ->
                            FavoriteArtistCard(
                                artistName = artistName,
                                favCount = count,
                                coverUri = coverUri,
                                onClick = {
                                    selectedArtistForInfo = artistName
                                }
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (isPlaylistLoading || (isLoading && playlistSongs.isEmpty())) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MediaLoadingAnimation(
                        mediaType = MediaType.AUDIO,
                        iconSize = 52.dp
                    )
                }
            } else if (playlistSongs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No Songs in Playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SongsList(
                        songs = playlistSongs,
                        selectedUris = emptySet(),
                        isSelectionMode = false,
                        onSongClick = onSongClick,
                        onSongLongClick = {},
                        onNavigateSubTab = onNavigateSubTab,
                        onAddToPlaylist = onAddToPlaylist,
                        onShowInfo = onShowInfo,
                        onRemoveFromPlaylist = { item ->
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val db = com.medianest.MediaNestApp.instance.database
                                db.categoryDao().removeMediaFromCategory(selectedPlaylist!!.id, item.uri.toString())
                            }
                        }
                    )
                }
            }
        }
    } else {
        if (isLoading && playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MediaLoadingAnimation(
                    mediaType = MediaType.AUDIO,
                    iconSize = 52.dp
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
                                    onClick = { 
                                        if (favPlaylist != null) {
                                            selectedPlaylist = favPlaylist
                                            onSelectPlaylist(favPlaylist)
                                        } else {
                                            onNavigateSubTab(2, null, null, null, null)
                                        }
                                    }
                                )
                            }
                            item {
                                QuickAccessCard(
                                    title = "Most Played",
                                    subtitle = "${mostPlayedSongs.size} tracks",
                                    icon = Icons.Default.LocalFireDepartment,
                                    artUri = mostPlayedArtUri,
                                    gradientColors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
                                    onClick = { onNavigateSubTab(2, null, null, null, null) }
                                )
                            }
                            item {
                                QuickAccessCard(
                                    title = "Recently Played",
                                    subtitle = "${recentSongs.size} tracks",
                                    icon = Icons.Default.History,
                                    artUri = recentlyPlayedArtUri,
                                    gradientColors = listOf(Color(0xFFE91E63), Color(0xFFFF4081)),
                                    onClick = { onNavigateSubTab(1, null, null, null, null) }
                                )
                            }
                            item {
                                QuickAccessCard(
                                    title = "Recently Added",
                                    subtitle = "${audioList.size} tracks",
                                    icon = Icons.Default.Schedule,
                                    artUri = recentlyAddedArtUri,
                                    gradientColors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF)),
                                    onClick = { onNavigateSubTab(0, null, null, null, null) }
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showRecognizedPlaylistsSheet = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Import",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
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
                                onClick = {
                                    selectedPlaylist = pl
                                    onSelectPlaylist(pl)
                                },
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
        com.medianest.ui.components.DeleteConfirmationDialog(
            title = "Delete Playlist",
            message = "Are you sure you want to delete the playlist '${target.name}'? The songs will not be deleted from your device.",
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                val catToDelete = target
                playlistToDelete = null
                if (selectedPlaylist?.id == catToDelete.id || selectedPlaylist?.name.equals(catToDelete.name, ignoreCase = true)) {
                    selectedPlaylist = null
                    onSelectPlaylist(null)
                }
                scope.launch(Dispatchers.IO) {
                    val db = MediaNestApp.instance.database
                    db.categoryDao().clearCategoryMedia(catToDelete.id)
                    db.categoryDao().deleteCategoryById(catToDelete.id)
                    db.categoryDao().deleteCategory(catToDelete)
                    db.categoryDao().deleteCategoryByNameAndType(catToDelete.name, catToDelete.type)

                    try {
                        // Delete original source file if stored in coverUri
                        if (!catToDelete.coverUri.isNullOrBlank()) {
                            val originalFile = File(catToDelete.coverUri)
                            if (originalFile.exists() && originalFile.isFile) {
                                originalFile.delete()
                            }
                        }

                        // Also clean up any exported or scanned files in standard media folders
                        val safeName = catToDelete.name.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                        listOf(
                            File(downloadsDir, "$safeName.m3u"),
                            File(downloadsDir, "$safeName.m3u8"),
                            File(downloadsDir, "${catToDelete.name}.m3u"),
                            File(downloadsDir, "${catToDelete.name}.m3u8"),
                            File(musicDir, "$safeName.m3u"),
                            File(musicDir, "$safeName.m3u8"),
                            File(musicDir, "${catToDelete.name}.m3u"),
                            File(musicDir, "${catToDelete.name}.m3u8")
                        ).forEach { f ->
                            if (f.exists()) f.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
}
