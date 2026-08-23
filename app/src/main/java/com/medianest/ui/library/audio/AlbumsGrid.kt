package com.medianest.ui.library.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.AlphabetScroller
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.formatDuration
import com.medianest.ui.components.translucentScrollBarGrid
import kotlinx.coroutines.launch

@Composable
fun AlbumsGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (List<MediaItem>, Int) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    gridSizeLevel: Int = 1,
    initialSelectedAlbum: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?, targetSongUri: String?) -> Unit = { _, _, _, _, _ -> },
    sortField: String = "Name",
    isAscending: Boolean = true,
    cacheMap: Map<String, com.medianest.data.db.AudioMetadataCache> = emptyMap(),
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedAlbum by remember(initialSelectedAlbum) { mutableStateOf(initialSelectedAlbum) }

    BackHandler(enabled = selectedAlbum != null) {
        selectedAlbum = null
    }

    val db = remember { com.medianest.MediaNestApp.instance.database }
    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed("AUDIO").collectAsState(initial = emptyList())
    val currentlyPlayingUri = recentlyPlayedStates.firstOrNull()?.mediaUri
    val currentlyPlayingAlbum = remember(currentlyPlayingUri, songs) {
        songs.firstOrNull { it.uri.toString() == currentlyPlayingUri }?.album
    }

    val albumsMap = remember(songs) {
        songs.groupBy { it.album ?: "Unknown Album" }
    }

    val sortedAlbumNames = remember(albumsMap, sortField, isAscending, cacheMap) {
        val keys = albumsMap.keys.toList()
        
        val comp = when (sortField) {
            "Name" -> compareBy<String> { it.lowercase() }
            "Artist" -> compareBy<String> { albumName ->
                albumsMap[albumName]?.firstOrNull()?.artist?.lowercase() ?: ""
            }
            "Release Year" -> compareBy<String> { albumName ->
                val firstSong = albumsMap[albumName]?.firstOrNull()
                cacheMap[firstSong?.uri?.toString()]?.year?.toIntOrNull() ?: 0
            }
            else -> compareBy<String> { it.lowercase() }
        }
        
        if (isAscending) keys.sortedWith(comp) else keys.sortedWith(comp).reversed()
    }

    val isAlphabetical = remember(sortedAlbumNames, sortField, isAscending) {
        sortField == "Name" && isAscending && sortedAlbumNames.size >= 30
    }

    if (selectedAlbum != null) {
        val albumSongs = albumsMap[selectedAlbum] ?: emptyList()
        val totalDurationMs = remember(albumSongs) { albumSongs.sumOf { it.durationMs } }
        val artistName = remember(albumSongs) { albumSongs.firstOrNull { !it.artist.isNullOrBlank() }?.artist ?: "Unknown Artist" }
        val coverUri = remember(albumSongs) { albumSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: albumSongs.firstOrNull()?.uri }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(coverUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = painter.state
                        if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error || coverUri == null) {
                                    GlassSurface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(16.dp),
                                        backgroundColor = Color.Transparent,
                                        borderColor = Color(0x22FFFFFF)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(64.dp) // Increased from 52.dp
                                            )
                                        }
                                    }
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = selectedAlbum!!,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$artistName • ${albumSongs.size} Songs • ${formatDuration(totalDurationMs)}",
                    fontSize = 13.sp,
                    color = Color(0xFF9EA3B0),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { if (albumSongs.isNotEmpty()) onSongClick(albumSongs, 0) },
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x3338BDF8),
                        borderColor = Color(0x6638BDF8)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Play All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    GlassSurface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (albumSongs.isNotEmpty()) {
                                    val shuffled = albumSongs.shuffled()
                                    onSongClick(shuffled, 0)
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x221C1F2B),
                        borderColor = Color(0x28FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Shuffle",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            SongsList(
                songs = albumSongs,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = onAddToPlaylist,
                onNavigateSubTab = onNavigateSubTab
            )
        }
    } else {
        val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val baseColumns = if (screenWidthDp >= 840) 5 else if (screenWidthDp >= 600) 4 else 2
        val gridColumns = baseColumns
        
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarGrid(gridState),
                contentPadding = PaddingValues(
                    bottom = 90.dp, 
                    start = if (isAlphabetical) 20.dp else 16.dp, 
                    end = if (isAlphabetical) 20.dp else 16.dp, 
                    top = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedAlbumNames, key = { it }) { albumName ->
                    val albumSongs = albumsMap[albumName] ?: emptyList()
                    val coverItem = albumSongs.firstOrNull()
                    val isCurrentlyPlaying = currentlyPlayingAlbum != null && currentlyPlayingAlbum == albumName

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAlbum = albumName },
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = if (isCurrentlyPlaying) Color(0x33FFFFFF) else Color(0x221C1F2B),
                        borderColor = if (isCurrentlyPlaying) Color(0x66FFFFFF) else Color(0x2EFFFFFF)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val albumArtModel = coverItem?.albumArtUri
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                    contentDescription = albumName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val state = painter.state
                                    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error || albumArtModel == null) {
                                        GlassSurface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(16.dp),
                                            backgroundColor = Color.Transparent,
                                            borderColor = Color(0x22FFFFFF)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Album,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(48.dp) // Increased from 32.dp
                                                )
                                            }
                                        }
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }

                                if (isCurrentlyPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x77000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Currently Playing",
                                            tint = Color(0xEEFFFFFF),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = albumName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = coverItem?.artist ?: "Unknown Artist",
                                fontSize = 11.5.sp,
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
                                color = Color(0xFF686C7A),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            if (isAlphabetical) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    AlphabetScroller(
                        items = sortedAlbumNames,
                        onScrollTo = { targetIndex ->
                            scope.launch { gridState.scrollToItem(targetIndex) }
                        }
                    )
                }
            }
        }
    }
}
