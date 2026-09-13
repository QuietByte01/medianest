package com.medianest.ui.library.audio

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import coil.request.ImageRequest
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.AlphabetScroller
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.components.MediaLoadingAnimation
import com.medianest.ui.components.formatDuration
import com.medianest.ui.library.MoveOrCopyFileDialog
import com.medianest.ui.components.translucentScrollBar
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.FolderHiddenUtils
import com.medianest.util.formatBytesReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SongsList(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (List<MediaItem>, Int) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    isLoading: Boolean = false,
    showDeleteOption: Boolean = true,
    onRemoveFromPlaylist: ((MediaItem) -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?, targetSongUri: String?) -> Unit = { _, _, _, _, _ -> },
    onAddToPlaylist: (MediaItem) -> Unit = {},
    onShowInfo: (MediaItem) -> Unit = {},
    showInGallery: Boolean = false,
    targetSongUri: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exoPlayerManager = remember { com.medianest.player.ExoPlayerManager.getInstance(context) }
    val playerState by exoPlayerManager.playerState.collectAsState()
    val currentlyPlayingUri = playerState.currentItem?.uri?.toString()

    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var editMetadataItem by remember { mutableStateOf<MediaItem?>(null) }
    var itemToMove by remember { mutableStateOf<MediaItem?>(null) }
    var itemToCopy by remember { mutableStateOf<MediaItem?>(null) }
    var songToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var highlightedSongUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(targetSongUri, songs) {
        if (!targetSongUri.isNullOrBlank() && songs.isNotEmpty()) {
            val index = songs.indexOfFirst { it.uri.toString() == targetSongUri }
            if (index != -1) {
                highlightedSongUri = targetSongUri
                listState.animateScrollToItem(index)
                kotlinx.coroutines.delay(2500)
                highlightedSongUri = null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MediaLoadingAnimation(
                    mediaType = MediaType.AUDIO,
                    iconSize = 52.dp
                )
            }
        } else if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Audio Files Found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val isAlphabetical = remember(songs) {
                if (songs.size < 30) false 
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
                        end = if (isAlphabetical) 20.dp else 4.dp
                    )
                ) {
                    items(songs, key = { it.id }) { item ->
                        SongRow(
                            item = item,
                            currentlyPlayingUri = currentlyPlayingUri,
                            isAlphabetical = isAlphabetical,
                            isSelected = selectedUris.contains(item.uri.toString()),
                            isHighlighted = item.uri.toString() == highlightedSongUri,
                            onSongClick = {
                                val idx = songs.indexOf(it)
                                if (idx != -1) onSongClick(songs, idx)
                            },
                            onAddToPlaylist = onAddToPlaylist,
                            onRemoveFromPlaylist = onRemoveFromPlaylist,
                            onInfoClick = onShowInfo,
                            onEditMetadataClick = { editMetadataItem = it },
                            onMoveClick = { itemToMove = it },
                            onCopyClick = { itemToCopy = it },
                            onDeleteClick = { songToDelete = it },
                            onNavigateSubTab = onNavigateSubTab,
                            showDeleteOption = showDeleteOption,
                            showInGallery = showInGallery,
                            context = context
                        )
                    }
                }
                if (isAlphabetical) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                        AlphabetScroller(
                            items = remember(songs) { songs.map { it.title } },
                            onScrollTo = { targetIndex ->
                                scope.launch { listState.scrollToItem(targetIndex) }
                            }
                        )
                    }
                }
            }
        }

        if (itemToMove != null) {
            MoveOrCopyFileDialog(
                item = itemToMove!!,
                allItems = songs,
                isCopy = false,
                onDismiss = { itemToMove = null }
            )
        }

        if (itemToCopy != null) {
            MoveOrCopyFileDialog(
                item = itemToCopy!!,
                allItems = songs,
                isCopy = true,
                onDismiss = { itemToCopy = null }
            )
        }

        /* MediaInfoBottomSheet handled at top level in LibraryScreen */

        if (songToDelete != null) {
            val target = songToDelete!!
            com.medianest.ui.components.DeleteConfirmationDialog(
                title = "Delete Audio File",
                itemTitle = target.title,
                onDismiss = { songToDelete = null },
                onConfirm = {
                    songToDelete = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, target.uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
}

@Composable
private fun SongRow(
    item: MediaItem,
    currentlyPlayingUri: String?,
    isAlphabetical: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean = false,
    onSongClick: (MediaItem) -> Unit,
    onAddToPlaylist: (MediaItem) -> Unit,
    onRemoveFromPlaylist: ((MediaItem) -> Unit)?,
    onInfoClick: (MediaItem) -> Unit,
    onEditMetadataClick: (MediaItem) -> Unit,
    onMoveClick: (MediaItem) -> Unit = {},
    onCopyClick: (MediaItem) -> Unit = {},
    onDeleteClick: (MediaItem) -> Unit,
    onNavigateSubTab: (tabIndex: Int, album: String?, artist: String?, folder: String?, targetSongUri: String?) -> Unit,
    showDeleteOption: Boolean,
    showInGallery: Boolean,
    context: android.content.Context
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val albumArtModel = item.albumArtUri ?: item.uri
    val isCurrentlyPlaying = item.uri.toString() == currentlyPlayingUri

    // Only create infinite transition for the actively playing song — avoids running
    // an infiniteRepeatable on every list row which compounds badly on large libraries.
    val pulseAlpha: Float = if (isCurrentlyPlaying) {
        val highlightPulse = rememberInfiniteTransition(label = "SongHighlightPulse")
        highlightPulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAlpha"
        ).value
    } else {
        0.70f // static — border never shown on non-playing rows
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isAlphabetical) 20.dp else 16.dp,
                end = if (isAlphabetical) 0.dp else 16.dp,
                top = 5.dp,
                bottom = 5.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable { onSongClick(item) },
        shape = RoundedCornerShape(20.dp),
        enableBlur = false,
        backgroundColor = when {
            isHighlighted -> Color.White.copy(alpha = 0.22f)
            isCurrentlyPlaying -> Color(0x33FFFFFF)
            else -> Color(0x221C1F2B)
        },
        borderColor = when {
            isHighlighted -> Color.White.copy(alpha = 0.70f * pulseAlpha)
            isCurrentlyPlaying -> Color(0x66FFFFFF)
            else -> Color(0x2EFFFFFF)
        },
        borderWidth = if (isHighlighted) 2.dp else 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            // Artwork Container with Frosty Fallback
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder fallback drawn underneath
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    enableBlur = false,
                    backgroundColor = Color.Transparent,
                    borderColor = Color(0x22FFFFFF)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                val imageRequest = remember(albumArtModel) {
                    ImageRequest.Builder(context)
                        .data(albumArtModel)
                        .crossfade(false)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

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
                    val m = item.mimeType.lowercase()
                    val ext = item.uri.toString().substringAfterLast('.').lowercase().substringBefore('?')
                    when {
                        m.contains("flac") || ext == "flac" -> "FLAC"
                        m.contains("wav") || ext == "wav" -> "WAV"
                        m.contains("m4a") || m.contains("mp4a") || ext == "m4a" -> "M4A"
                        m.contains("aac") || ext == "aac" -> "AAC"
                        m.contains("ogg") || ext == "ogg" -> "OGG"
                        m.contains("opus") || ext == "opus" -> "OPUS"
                        m.contains("mpeg") || m.contains("mp3") || ext == "mp3" -> "MP3"
                        else -> ext.uppercase().ifEmpty { "AUDIO" }
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
                GlassDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(20.dp),
                    backgroundImage = item.albumArtUri ?: item.uri
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDark) Color.White else Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    HorizontalDivider(color = if (isDark) Color(0x1AFFFFFF) else Color(0x1A000000))

                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist(item)
                        }
                    )
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Playlist", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                onRemoveFromPlaylist(item)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("File Info", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onInfoClick(item)
                        }
                    )
                    if (showDeleteOption) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showMenu = false
                                onDeleteClick(item)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit Tag & Metadata", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onEditMetadataClick(item)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Folder", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onMoveClick(item)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to Folder", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onCopyClick(item)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Show Album", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Album, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onNavigateSubTab(3, item.album ?: "Unknown Album", null, null, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Show Artist", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            showMenu = false
                            onNavigateSubTab(4, null, item.artist ?: "Unknown Artist", null, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (showInGallery) "Open with" else "Show in Folder", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { 
                            Icon(
                                if (showInGallery) Icons.Default.MusicNote else Icons.Default.Folder, 
                                contentDescription = null,
                                tint = if (isDark) Color.White else Color.Black,
                                modifier = Modifier.size(20.dp)
                            ) 
                        },
                        onClick = {
                            showMenu = false
                            if (showInGallery) {
                                com.medianest.util.IntentUtils.openInGallery(context, item)
                            } else {
                                val targetFolderKey = item.relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: (item.bucketName ?: "Music")
                                onNavigateSubTab(5, null, null, targetFolderKey, item.uri.toString())
                            }
                        }
                    )
                }
            }
        }
    }
}
