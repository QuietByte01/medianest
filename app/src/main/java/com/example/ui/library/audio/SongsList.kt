package com.example.ui.library.audio

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.ui.components.AlphabetScroller
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.MediaLoadingAnimation
import com.example.ui.components.formatDuration
import com.example.ui.components.translucentScrollBar
import com.example.ui.components.GlassSurface
import com.example.ui.theme.LocalDarkTheme
import com.example.util.FolderHiddenUtils
import com.example.util.formatBytesReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SongsList(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    isLoading: Boolean = false,
    showDeleteOption: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?) -> Unit = { _, _, _, _ -> },
    onAddToPlaylist: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.example.MediaNestApp.instance.database }
    val recentlyPlayedStates by db.playbackStateDao().getRecentlyPlayed().collectAsState(initial = emptyList())
    val currentlyPlayingUri = recentlyPlayedStates.firstOrNull()?.mediaUri

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
        val isAlphabetical = remember(songs) {
            if (songs.size < 10) false 
            else {
                // Check if list is sorted alphabetically by title (A-Z)
                var sorted = true
                for (i in 0 until (songs.size - 1)) {
                    if (songs[i].title.lowercase() > songs[i+1].title.lowercase()) {
                        sorted = false
                        break
                    }
                }
                sorted
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isAlphabetical) Modifier else Modifier.translucentScrollBar(listState)),
                contentPadding = PaddingValues(
                    bottom = 80.dp,
                    end = if (isAlphabetical) 16.dp else 4.dp // Reduced space
                )
            ) {
                items(songs, key = { it.id }) { item ->
                    var showMenu by remember { mutableStateOf(false) }
                    val albumArtModel = item.albumArtUri
                    val isCurrentlyPlaying = item.uri.toString() == currentlyPlayingUri

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = if (isAlphabetical) 4.dp else 16.dp, top = 5.dp, bottom = 5.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSongClick(item) },
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isCurrentlyPlaying) Color(0x33FFFFFF) else Color(0x221C1F2B),
                        borderColor = if (isCurrentlyPlaying) Color(0x66FFFFFF) else Color(0x2EFFFFFF)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Artwork Container with Frosty Fallback
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val state = painter.state
                                    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error || albumArtModel == null) {
                                        GlassSurface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(14.dp),
                                            backgroundColor = Color.Transparent, // Transparent as requested
                                            borderColor = Color(0x22FFFFFF)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(28.dp) // Increased from 22.dp
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
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = item.title,
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
                                val sizeStr = if (item.size > 0) formatBytesReport(item.size) else formatDuration(item.durationMs)

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
                                    containerColor = if (LocalDarkTheme.current) Color(0xBF0F1015) else Color(0xA6FFFFFF),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.border(1.dp, if (LocalDarkTheme.current) Color(0x28FFFFFF) else Color(0x33000000), RoundedCornerShape(16.dp))
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
            if (isAlphabetical) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    AlphabetScroller(songs = songs, listState = listState)
                }
            }
        }
    }

    if (infoItem != null) {
        MediaInfoBottomSheet(
            item = infoItem!!,
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
                        scope.launch(Dispatchers.IO) {
                            try {
                                FolderHiddenUtils.deleteMediaUri(context, target.uri)
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
