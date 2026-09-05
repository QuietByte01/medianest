package com.medianest.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.util.setDataSourceSafe
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import coil.decode.VideoFrameDecoder
import com.medianest.data.model.MediaItem
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.ThumbnailManager
import com.medianest.util.formatBytesReport
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WideVideoCard(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeName: String? = null,
    isLocationFallback: Boolean = false,
    onDelete: () -> Unit,
    onRemoveFromCategory: (() -> Unit)? = null,
    onAddToCategory: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onShowInfo: (() -> Unit)? = null,
    useRealRatio: Boolean = false
) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    val timeMillis = if (item.dateAdded > 10_000_000_000L) item.dateAdded else item.dateAdded * 1000L
    val timeStr = if (item.dateAdded > 0) timeFormat.format(Date(timeMillis)) else "N/A"
    val context = androidx.compose.ui.platform.LocalContext.current
    val autoPlayVideoPreviews = LocalAutoPlayVideoPreviews.current

    var fallbackBitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var rebuildToken by remember(item.uri) { mutableStateOf(0) }

    LaunchedEffect(item.uri, rebuildToken) {
        if (fallbackBitmap == null) {
            val bmp = ThumbnailManager.getThumbnail(context, item.uri, rebuildToken)
            if (bmp != null) {
                fallbackBitmap = bmp
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val rebuildOffsets = remember { listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.25f, 0.05f) }
    val selectedFactor = rebuildOffsets[rebuildToken % rebuildOffsets.size]

    val videoSeekMicros = remember(item.durationMs, rebuildToken) {
        if (item.durationMs > 1_000) {
            (item.durationMs * 1000L * selectedFactor).toLong()
        } else {
            0L
        }
    }

    val imageRequest = remember(item.uri, item.durationMs, rebuildToken) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .diskCacheKey("wide_${item.uri}_${item.size}_${item.dateAdded}_$rebuildToken")
            .memoryCacheKey("wide_${item.uri}_${item.size}_${item.dateAdded}_$rebuildToken")
            .crossfade(true)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMicros(videoSeekMicros)
            .build()
    }

    GlassSurface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
        borderColor = if (isSelected) Color.White else Color(0x28FFFFFF),
        borderWidth = if (isSelected) 1.0.dp else 0.5.dp
    ) {
        Box {
            Column(modifier = Modifier.padding(10.dp)) {
                val thumbModifier = if (useRealRatio) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(item.aspectRatio.coerceIn(0.3f, 2.5f))
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                }
                Box(
                    modifier = thumbModifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (LocalDarkTheme.current) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f))
                ) {
                    if (fallbackBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = fallbackBitmap!!.asImageBitmap(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = {
                                if (fallbackBitmap == null) {
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        val bmp = wideVideoCardSemaphore.withPermit {
                                            extractVideoThumbnailWide(context, item)
                                        }
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            fallbackBitmap = bmp
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // Live In-Place Video Auto-Preview when visible in viewport
                    if (autoPlayVideoPreviews) {
                        LibraryVideoPreviewView(
                            uri = item.uri,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    val activeUri by SlideShowVideoPreviewCoordinator.activeUri.collectAsState()
                    val isAudioMuted by SlideShowVideoPreviewCoordinator.isAudioMuted.collectAsState()
                    val isCurrentlyPreviewing = autoPlayVideoPreviews && activeUri == item.uri

                    // Speaker toggle on the left side (BottomStart)
                    if (isCurrentlyPreviewing) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clickable {
                                    SlideShowVideoPreviewCoordinator.toggleAudio()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAudioMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isAudioMuted) "Turn Audio On" else "Mute Audio",
                                    tint = if (isAudioMuted) Color.White.copy(alpha = 0.9f) else Color(0xFF4ADE80),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    if (!placeName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xDD0F1015),
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLocationFallback) Icons.Default.Folder else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = placeName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Compact Duration badge on bottom-right (BottomEnd)
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.30f))
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            RoundedPlayIcon(
                                modifier = Modifier.size(10.dp),
                                tint = Color.White
                            )
                            Text(
                                text = formatDuration(item.durationMs),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false
                                    ),
                                    lineHeight = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🕒 $timeStr",
                        fontSize = 11.5.sp,
                        color = Color(0xFF9EA3B0)
                    )
                    Text(
                        text = if (item.size > 0) formatBytesReport(item.size) else "0 B",
                        fontSize = 11.5.sp,
                        color = Color(0xFF9EA3B0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Action Menu
            var menuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                val isDark = LocalDarkTheme.current
                GlassDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
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
                        text = { Text("File Info", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            if (onShowInfo != null) onShowInfo()
                        }
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                            onClick = { 
                                menuExpanded = false
                                onDelete() 
                            }
                        )
                    }
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                            onClick = { 
                                menuExpanded = false
                                onRename() 
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rebuild Thumbnail", color = if (isDark) Color.White else Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            fallbackBitmap = null
                            val nextToken = rebuildToken + 1
                            rebuildToken = nextToken
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val newFrame = extractVideoThumbnailWide(context, item, nextToken)
                                if (newFrame != null) {
                                    fallbackBitmap = newFrame
                                }
                            }
                        }
                    )
                    if (onAddToCategory != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Category", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                menuExpanded = false
                                onAddToCategory()
                            }
                        )
                    }
                    if (onRemoveFromCategory != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Category", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                            onClick = { 
                                menuExpanded = false
                                onRemoveFromCategory() 
                            }
                        )
                    }
                    if (onMove != null) {
                        DropdownMenuItem(
                            text = { Text("Move to Folder", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                            onClick = { 
                                menuExpanded = false
                                onMove() 
                            }
                        )
                    }
                    if (onCopy != null) {
                        DropdownMenuItem(
                            text = { Text("Copy to Folder", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                            onClick = { 
                                menuExpanded = false
                                onCopy() 
                            }
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                            onClick = { 
                                menuExpanded = false
                                onDelete() 
                            }
                        )
                    }
                }
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 1.0.dp else 0.5.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.25f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

private val wideVideoCardSemaphore = kotlinx.coroutines.sync.Semaphore(1)

private fun extractVideoThumbnailWide(
    context: android.content.Context,
    item: com.medianest.data.model.MediaItem,
    attemptOffset: Int = 0
): android.graphics.Bitmap? {
    var retriever: android.media.MediaMetadataRetriever? = null
    return try {
        retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSourceSafe(context, item.uri)
        val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: item.durationMs

        val baseOffsets = listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.25f, 0.05f)
        val shiftedOffsets = if (attemptOffset > 0) {
            val shift = attemptOffset % baseOffsets.size
            baseOffsets.drop(shift) + baseOffsets.take(shift)
        } else {
            baseOffsets
        }

        val candidates = shiftedOffsets.map { factor ->
            if (durationMs > 1_000) (durationMs * 1000L * factor).toLong() else 0L
        }.distinct()

        var result: android.graphics.Bitmap? = null
        try {
            for (seekMicros in candidates) {
                val frame = retriever.getFrameAtTime(seekMicros, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val readableBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        frame.config == android.graphics.Bitmap.Config.HARDWARE) {
                        frame.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: frame
                    } else {
                        frame
                    }

                    var darkCount = 0
                    var total = 0
                    val step = (readableBitmap.width / 5).coerceAtLeast(1)
                    val stepY = (readableBitmap.height / 5).coerceAtLeast(1)
                    var xi = 0
                    while (xi < readableBitmap.width) {
                        var yi = 0
                        while (yi < readableBitmap.height) {
                            val px = readableBitmap.getPixel(xi, yi)
                            val luma = 0.299 * android.graphics.Color.red(px) + 0.587 * android.graphics.Color.green(px) + 0.114 * android.graphics.Color.blue(px)
                            if (luma < 15.0) darkCount++
                            total++
                            yi += stepY
                        }
                        xi += step
                    }

                    val isBlack = total > 0 && (darkCount.toFloat() / total) > 0.88f
                    if (!isBlack) {
                        result = frame
                        break
                    }
                    if (result == null) result = frame
                }
            }
        } catch (_: Exception) {}
        result
    } catch (_: Exception) { null } finally {
        try { retriever?.release() } catch (_: Exception) {}
    }
}

