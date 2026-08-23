package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import coil.size.Precision
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit


import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 8,
    roundedCornersEnabled: Boolean = true,
    isHighlighted: Boolean = false,
    onMoreClick: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRemoveFromCategory: (() -> Unit)? = null,
    showRemoveOption: Boolean = false,
    onOpenFolder: ((String, String?) -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    showInGallery: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var showMenu by remember { mutableStateOf(false) }
    var dynamicRatio by remember(item.id, item.width, item.height) {
        mutableFloatStateOf(item.aspectRatio.coerceIn(0.45f, 2.2f))
    }
    val itemShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)

    val highlightPulse = rememberInfiniteTransition(label = "MediaHighlightPulse")
    val pulseAlpha by highlightPulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val settingsManager = com.medianest.MediaNestApp.instance.settingsManager
    val pictureModeEnabled by settingsManager.pictureModeEnabled.collectAsState(initial = true)
    val applyToThumbnails by settingsManager.applyPictureModeToThumbnails.collectAsState(initial = false)
    val pictureMode by settingsManager.pictureMode.collectAsState(initial = "BALANCED")
    val customSat by settingsManager.customSaturation.collectAsState(initial = 1.18f)
    val customCon by settingsManager.customContrast.collectAsState(initial = 1.06f)
    val customWarmth by settingsManager.customWarmth.collectAsState(initial = 0.03f)

    val colorFilter = remember(pictureModeEnabled, applyToThumbnails, pictureMode, customSat, customCon, customWarmth) {
        PictureModeUtils.getComposeColorFilter(
            modeKey = pictureMode,
            customSat = customSat,
            customCon = customCon,
            customWarmth = customWarmth,
            enabled = pictureModeEnabled && applyToThumbnails
        )
    }

    var fallbackBitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Rebuild trigger: incrementing this forces the image request to be recreated
    var rebuildToken by remember(item.uri) { mutableStateOf(0) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) Modifier.border(BorderStroke(2.5.dp, Color(0xFF38BDF8).copy(alpha = pulseAlpha)), itemShape)
                else Modifier
            )
            .clip(itemShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) Color(0x3338BDF8)
            else if (com.medianest.ui.theme.LocalDarkTheme.current) 
                Color.White.copy(alpha = 0.10f) 
            else 
                Color.Black.copy(alpha = 0.10f)
        )
    ) {
    val rebuildOffsets = remember { listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.25f, 0.05f) }
    val selectedFactor = rebuildOffsets[rebuildToken % rebuildOffsets.size]

    val videoSeekMicros = remember(item.durationMs, rebuildToken) {
        if (item.durationMs > 1_000) {
            (item.durationMs * 1000L * selectedFactor).toLong()
        } else {
            0L
        }
    }

    val imageRequest = remember(item.uri, item.type, item.durationMs, context, item.size, item.dateAdded, isTablet, rebuildToken) {
        val builder = ImageRequest.Builder(context)
            .data(item.uri)
            // Include rebuildToken so invalidation works on demand
            .diskCacheKey("${item.uri}_${item.size}_${item.dateAdded}_$rebuildToken")
            .memoryCacheKey("${item.uri}_${item.size}_${item.dateAdded}_$rebuildToken")
            .crossfade(true)
            .precision(Precision.INEXACT)

        if (isTablet) {
            builder.size(600)
        } else {
            builder.size(400)
        }

        if (item.type == MediaType.VIDEO) {
            builder.decoderFactory(VideoFrameDecoder.Factory())
            builder.videoFrameMicros(videoSeekMicros)
        }
        builder.build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(dynamicRatio)
    ) {
        if (fallbackBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = fallbackBitmap!!.asImageBitmap(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            coil.compose.AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { success ->
                    val intrinsicSize = success.painter.intrinsicSize
                    if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                        val loadedRatio = (intrinsicSize.width / intrinsicSize.height).coerceIn(0.45f, 2.2f)
                        if (kotlin.math.abs(loadedRatio - dynamicRatio) > 0.04f) {
                            dynamicRatio = loadedRatio
                        }
                    }
                },
                onError = {
                    // Coil VideoFrameDecoder failed — fall back to MediaMetadataRetriever
                    if (item.type == MediaType.VIDEO && fallbackBitmap == null) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            fallbackBitmap = extractVideoThumbnail(context, item, rebuildToken)
                        }
                    }
                }
            )
        }

            // Video duration overlay badge
            if (item.type == MediaType.VIDEO) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.30f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = formatDuration(item.durationMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Selection checkbox overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 1.0.dp else 0.5.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.2f)
                                else Color.Black.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (onMoreClick != null || onInfo != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
                    val menuBg = if (com.medianest.ui.theme.LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF)

                    GlassDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(180.dp),
                        backgroundImage = item.albumArtUri ?: item.uri
                    ) {
                        DropdownMenuItem(
                            text = { Text("File Info", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showMenu = false
                                if (onInfo != null) onInfo()
                            }
                        )
                        if (onOpenFolder != null) {
                            DropdownMenuItem(
                                text = { Text(if (showInGallery) "Open with" else "Show in Folder", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = if (showInGallery) Icons.Default.Image else Icons.Default.Folder, 
                                        contentDescription = null, 
                                        tint = if (isDark) Color.White else Color.Black
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    if (showInGallery) {
                                        com.medianest.util.IntentUtils.openInGallery(context, item)
                                    } else {
                                        val folderKey = item.relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: (item.bucketName ?: "Folder")
                                        onOpenFolder(folderKey, item.uri.toString())
                                    }
                                }
                            )
                        }
                        // Rebuild thumbnail option for video items
                        if (item.type == MediaType.VIDEO) {
                            DropdownMenuItem(
                                text = { Text("Rebuild Thumbnail", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                                onClick = {
                                    showMenu = false
                                    fallbackBitmap = null
                                    val nextToken = rebuildToken + 1
                                    rebuildToken = nextToken
                                    // Proactively extract non-black progressive frame on background thread
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val newFrame = extractVideoThumbnail(context, item, nextToken)
                                        if (newFrame != null) {
                                            fallbackBitmap = newFrame
                                        }
                                    }
                                }
                            )
                        }
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                        }
                        if (showRemoveOption && onRemoveFromCategory != null) {
                            DropdownMenuItem(
                                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromCategory()
                                }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
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

            // Bottom title banner for Audio
            if (item.type == MediaType.AUDIO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(6.dp)
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun extractVideoThumbnail(
    context: android.content.Context,
    item: MediaItem,
    attemptOffset: Int = 0
): android.graphics.Bitmap? {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, item.uri)

        // Read actual duration from retriever if item.durationMs is unreliable
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
        for (seekMicros in candidates) {
            val frame = retriever.getFrameAtTime(seekMicros, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null && !isFrameBlack(frame)) {
                result = frame
                break
            }
            // Keep last non-null frame as fallback even if dark
            if (frame != null && result == null) result = frame
        }

        retriever.release()
        result
    } catch (_: Exception) { null }
}

/**
 * Returns true when a bitmap is essentially all-black (e.g. the frame decoder decoded a blank frame).
 * Safely handles Hardware Bitmaps and samples luminance across a grid.
 */
private fun isFrameBlack(bitmap: android.graphics.Bitmap): Boolean {
    return try {
        val readableBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
            bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }
        if (readableBitmap.width < 4 || readableBitmap.height < 4) return true
        val step = (readableBitmap.width / 5).coerceAtLeast(1)
        val stepY = (readableBitmap.height / 5).coerceAtLeast(1)
        var darkCount = 0
        var total = 0
        var x = 0
        while (x < readableBitmap.width) {
            var y = 0
            while (y < readableBitmap.height) {
                val pixel = readableBitmap.getPixel(x, y)
                val luma = (0.299 * android.graphics.Color.red(pixel) +
                            0.587 * android.graphics.Color.green(pixel) +
                            0.114 * android.graphics.Color.blue(pixel))
                if (luma < 15.0) darkCount++
                total++
                y += stepY
            }
            x += step
        }
        total > 0 && (darkCount.toFloat() / total) > 0.88f
    } catch (_: Exception) {
        false
    }
}

fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
