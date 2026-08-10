package com.example.ui.library.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.formatDuration

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
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUri != null) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
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
                    Button(
                        onClick = { if (albumSongs.isNotEmpty()) onSongClick(albumSongs.first()) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { if (albumSongs.isNotEmpty()) onSongClick(albumSongs.shuffled().first()) },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            SongsList(
                songs = albumSongs,
                selectedUris = selectedUris,
                isSelectionMode = isSelectionMode,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToPlaylist = onAddToPlaylist
            )
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
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF282C38)),
                            contentAlignment = Alignment.Center
                        ) {
                            val albumArtModel = coverItem?.albumArtUri
                            if (albumArtModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                    contentDescription = albumName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(32.dp)
                                )
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
    }
}
