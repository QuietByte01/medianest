package com.medianest.ui.library.audio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
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
import com.medianest.ui.components.GlassDropdownMenu
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface

@Composable
fun UserPlaylistCard(
    pl: MediaCategory,
    audioList: List<MediaItem>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { com.medianest.MediaNestApp.instance.database }
    var artUri by remember { mutableStateOf<Uri?>(null) }
    var trackCount by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(pl.id, audioList) {
        db.categoryDao().getMediaUrisForCategory(pl.id).collect { uris ->
            trackCount = uris.size
            artUri = audioList.firstOrNull { uris.contains(it.uri.toString()) }?.let { it.albumArtUri ?: it.uri }
        }
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
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
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(artUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = painter.state
                        if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
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
                GlassDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    backgroundImage = artUri
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Playlist") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Playlist (.m3u)") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            exportPlaylistToM3u(context, pl, audioList)
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

fun exportPlaylistToM3u(context: android.content.Context, category: MediaCategory, audioList: List<MediaItem>) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    scope.launch {
        try {
            val db = com.medianest.MediaNestApp.instance.database
            val uris = db.categoryDao().getMediaUrisForCategorySync(category.id)
            val matchedSongs = audioList.filter { uris.contains(it.uri.toString()) }

            val m3uContent = StringBuilder()
            m3uContent.appendLine("#EXTM3U")
            m3uContent.appendLine("#PLAYLIST:${category.name}")
            for (song in matchedSongs) {
                val durationSec = (song.durationMs / 1000).coerceAtLeast(0)
                val artistStr = song.artist ?: "Unknown Artist"
                m3uContent.appendLine("#EXTINF:$durationSec,$artistStr - ${song.title}")
                val path = song.uri.path ?: song.uri.toString()
                m3uContent.appendLine(path)
            }

            val safeName = category.name.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            val fileName = "$safeName.m3u"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val exportFile = java.io.File(downloadsDir, fileName)
            exportFile.writeText(m3uContent.toString())

            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/x-mpegurl"
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Exported to Downloads/$fileName", android.widget.Toast.LENGTH_LONG).show()
                runCatching {
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Playlist M3U"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
