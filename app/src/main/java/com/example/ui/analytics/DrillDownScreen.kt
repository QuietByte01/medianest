@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.analytics

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem

import com.example.ui.components.GlassSurface

enum class AnalyticsSortType {
    DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, NAME_ASC
}

private val KNOWN_EXTENSIONS = setOf(
    "MP3", "FLAC", "WAV", "AAC", "OGG", "M4A", "WMA", "AIFF", "OPUS", "MID", "MIDI",
    "MP4", "MKV", "AVI", "WEBM", "MOV", "3GP", "FLV", "WMV", "M4V",
    "JPG", "JPEG", "PNG", "WEBP", "HEIC", "HEIF", "GIF", "BMP"
)

private fun getEffectiveExtension(item: MediaItem, filterFormat: String? = null): String {
    if (filterFormat != null) return filterFormat.uppercase()

    // 1. Check path / uri extension first
    val path = item.relativePath ?: item.uri.path ?: item.uri.toString()
    val pathExt = path.substringAfterLast('.', "").substringBefore('?').substringBefore('#').trim().uppercase()
    if (pathExt in KNOWN_EXTENSIONS) {
        return pathExt
    }

    // 2. Check title extension only if it's in KNOWN_EXTENSIONS
    if (item.title.contains('.')) {
        val titleExt = item.title.substringAfterLast('.', "").trim().uppercase()
        if (titleExt in KNOWN_EXTENSIONS) {
            return titleExt
        }
    }

    // 3. Fallback to MIME type
    if (item.mimeType.isNotBlank()) {
        val sub = item.mimeType.substringAfter('/').lowercase()
        when {
            sub.contains("mpeg") || sub.contains("mp3") -> return "MP3"
            sub.contains("flac") -> return "FLAC"
            sub.contains("wav") || sub.contains("wave") || sub.contains("x-wav") -> return "WAV"
            sub.contains("aac") -> return "AAC"
            sub.contains("ogg") || sub.contains("vorbis") || sub.contains("opus") -> return "OGG"
            sub.contains("m4a") -> return "M4A"
            sub.contains("mp4") -> return if (item.type == com.example.data.db.MediaType.AUDIO) "M4A" else "MP4"
            sub.contains("jpeg") || sub.contains("jpg") -> return "JPG"
            sub.contains("png") -> return "PNG"
            sub.contains("webp") -> return "WEBP"
            sub.contains("heic") || sub.contains("heif") -> return "HEIC"
            sub.contains("matroska") || sub.contains("mkv") -> return "MKV"
            sub.contains("webm") -> return "WEBM"
            sub.contains("quicktime") || sub.contains("mov") -> return "MOV"
            else -> {
                val clean = sub.filter { it.isLetterOrDigit() }.take(4)
                if (clean.isNotBlank()) return clean.uppercase()
            }
        }
    }

    // 4. Default by MediaType
    return when (item.type) {
        com.example.data.db.MediaType.AUDIO -> "MP3"
        com.example.data.db.MediaType.VIDEO -> "MP4"
        com.example.data.db.MediaType.IMAGE -> "JPG"
    }
}

@Composable
fun DrillDownScreen(
    title: String,
    filterCategory: String?, // IMAGE, VIDEO, AUDIO or null
    filterFormat: String?,   // JPG, MP4, MP3, etc. or null
    allImages: List<MediaItem>,
    allVideos: List<MediaItem>,
    allAudio: List<MediaItem>,
    onBack: () -> Unit,
    onOpenQuickView: (MediaItem) -> Unit,
    onOpenVideoPlayer: (MediaItem) -> Unit,
    onOpenAudioPlayer: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var isGridView by remember { mutableStateOf(true) }
    var sortType by remember { mutableStateOf(AnalyticsSortType.DATE_DESC) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedUris.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    BackHandler {
        if (isSelectionMode) {
            selectedUris = emptySet()
        } else {
            onBack()
        }
    }

    // Filter matching files
    val filteredFiles = remember(allImages, allVideos, allAudio, filterCategory, filterFormat) {
        val candidates = when (filterCategory) {
            "IMAGE" -> allImages
            "VIDEO" -> allVideos
            "AUDIO" -> allAudio
            else -> allImages + allVideos + allAudio
        }

        if (filterFormat == null) {
            candidates
        } else {
            val targetFmt = filterFormat.uppercase()
            candidates.filter { item ->
                val name = item.title.lowercase()
                val path = (item.relativePath ?: "").lowercase()
                val uriStr = item.uri.toString().lowercase()
                val mime = item.mimeType.lowercase()
                val combined = "$name $path $uriStr"

                when (targetFmt) {
                    "MP3" -> combined.contains(".mp3") || mime.contains("mpeg") || mime.contains("mp3") || targetFmt.lowercase() in mime
                    "MP4" -> combined.contains(".mp4") || mime.contains("mp4")
                    "JPG", "JPEG" -> combined.contains(".jpg") || combined.contains(".jpeg") || mime.contains("jpeg") || mime.contains("jpg")
                    "PNG" -> combined.contains(".png") || mime.contains("png")
                    "FLAC" -> combined.contains(".flac") || mime.contains("flac")
                    "WAV" -> combined.contains(".wav") || mime.contains("wav")
                    "AAC" -> combined.contains(".aac") || mime.contains("aac")
                    "M4A" -> combined.contains(".m4a") || mime.contains("m4a")
                    else -> combined.contains(targetFmt.lowercase()) || mime.contains(targetFmt.lowercase())
                }
            }
        }
    }

    // Sort files
    val sortedFiles = remember(filteredFiles, sortType) {
        when (sortType) {
            AnalyticsSortType.DATE_DESC -> filteredFiles.sortedByDescending { it.dateAdded }
            AnalyticsSortType.DATE_ASC -> filteredFiles.sortedBy { it.dateAdded }
            AnalyticsSortType.SIZE_DESC -> filteredFiles.sortedByDescending { it.size }
            AnalyticsSortType.SIZE_ASC -> filteredFiles.sortedBy { it.size }
            AnalyticsSortType.NAME_ASC -> filteredFiles.sortedBy { it.title }
        }
    }

    val totalSize = remember(sortedFiles) { sortedFiles.sumOf { it.size } }

    val fixedDarkGradient = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E222A),
                Color(0xFF121419),
                Color(0xFF0C0E12)
            )
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(fixedDarkGradient),
        topBar = {
            GlassSurface(
                shape = RoundedCornerShape(0.dp),
                backgroundColor = Color(0x3B181A24),
                borderColor = Color(0x28FFFFFF),
                enableBlur = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column {
                            Text(text = title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                            Text(
                                text = "${sortedFiles.size} items • ${AnalyticsColors.formatBytes(totalSize)}",
                                fontSize = 12.sp,
                                color = Color(0xFF9EA3AF)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View Mode",
                                tint = Color.White
                            )
                        }

                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor = Color(0xDC141722),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date (Newest First)") },
                                    onClick = { sortType = AnalyticsSortType.DATE_DESC; showSortMenu = false },
                                    leadingIcon = { if (sortType == AnalyticsSortType.DATE_DESC) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest First)") },
                                    onClick = { sortType = AnalyticsSortType.DATE_ASC; showSortMenu = false },
                                    leadingIcon = { if (sortType == AnalyticsSortType.DATE_ASC) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (Largest First)") },
                                    onClick = { sortType = AnalyticsSortType.SIZE_DESC; showSortMenu = false },
                                    leadingIcon = { if (sortType == AnalyticsSortType.SIZE_DESC) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (Smallest First)") },
                                    onClick = { sortType = AnalyticsSortType.SIZE_ASC; showSortMenu = false },
                                    leadingIcon = { if (sortType == AnalyticsSortType.SIZE_ASC) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A to Z)") },
                                    onClick = { sortType = AnalyticsSortType.NAME_ASC; showSortMenu = false },
                                    leadingIcon = { if (sortType == AnalyticsSortType.NAME_ASC) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sortedFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No files found matching criteria", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 8.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedFiles, key = { it.uri.toString() }) { item ->
                        val isSelected = selectedUris.contains(item.uri.toString())
                        val ext = getEffectiveExtension(item, filterFormat)
                        val formatColor = AnalyticsColors.getFormatColor(
                            ext,
                            if (allImages.contains(item)) "IMAGE" else if (allVideos.contains(item)) "VIDEO" else "AUDIO"
                        )

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable {
                                    if (isSelectionMode) {
                                        selectedUris = if (isSelected) selectedUris - item.uri.toString() else selectedUris + item.uri.toString()
                                    } else {
                                        when {
                                            allImages.contains(item) -> onOpenQuickView(item)
                                            allVideos.contains(item) -> onOpenVideoPlayer(item)
                                            else -> onOpenAudioPlayer(item)
                                        }
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.albumArtUri ?: item.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Checkbox overlay if selected
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                // Format pill badge
                                Surface(
                                    color = formatColor,
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = ext.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Size pill badge
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(topStart = 8.dp),
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                ) {
                                    Text(
                                        text = AnalyticsColors.formatBytes(item.size),
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(sortedFiles, key = { it.uri.toString() }) { item ->
                        val isSelected = selectedUris.contains(item.uri.toString())
                        val ext = getEffectiveExtension(item, filterFormat)
                        val formatColor = AnalyticsColors.getFormatColor(
                            ext,
                            if (allImages.contains(item)) "IMAGE" else if (allVideos.contains(item)) "VIDEO" else "AUDIO"
                        )

                        ListItem(
                            headlineContent = {
                                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text("${AnalyticsColors.formatBytes(item.size)} • ${item.bucketName ?: "Storage"}", fontSize = 12.sp)
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(item.albumArtUri ?: item.uri).crossfade(true).build(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = formatColor,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            text = ext.take(3).uppercase(),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                if (isSelectionMode) {
                                    selectedUris = if (isSelected) selectedUris - item.uri.toString() else selectedUris + item.uri.toString()
                                } else {
                                    when {
                                        allImages.contains(item) -> onOpenQuickView(item)
                                        allVideos.contains(item) -> onOpenVideoPlayer(item)
                                        else -> onOpenAudioPlayer(item)
                                    }
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }

            // Multi-Select Batch Action Bar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedUris.size} selected",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val allUris = sortedFiles.map { it.uri.toString() }.toSet()
                                selectedUris = if (selectedUris.size == allUris.size && allUris.isNotEmpty()) {
                                    emptySet()
                                } else {
                                    allUris
                                }
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            }

                            IconButton(onClick = {
                                val urisToShare = selectedUris.map { Uri.parse(it) }
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }

                            IconButton(onClick = {
                                showDeleteConfirmDialog = true
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }

                            IconButton(onClick = { selectedUris = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                        }
                    }
                }
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    containerColor = androidx.compose.ui.graphics.Color(0xDC141722),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    title = { Text("Delete Selected Items?") },
                    text = { Text("Are you sure you want to delete ${selectedUris.size} item(s)?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirmDialog = false
                            selectedUris.forEach { uriStr ->
                                try {
                                    com.example.util.FolderHiddenUtils.deleteMediaUri(context, Uri.parse(uriStr))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            selectedUris = emptySet()
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
