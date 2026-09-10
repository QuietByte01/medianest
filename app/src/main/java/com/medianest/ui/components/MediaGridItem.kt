package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.medianest.util.ThumbnailManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
    onAddToCategory: (() -> Unit)? = null,
    showRemoveOption: Boolean = false,
    onOpenFolder: ((String, String?) -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onMoveToFilter: (() -> Unit)? = null,
    showInGallery: Boolean = false,
    gridSizeLevel: Int = 1
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // NOTE: LocalConfiguration removed from this composable — reading it here caused all grid
    // items to invalidate their imageRequest on rotation, reloading every visible thumbnail.

    var showMenu by remember { mutableStateOf(false) }
    var dynamicRatio by remember(item.id, item.width, item.height) {
        mutableFloatStateOf(item.aspectRatio.coerceIn(0.45f, 2.2f))
    }
    val itemShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)

    // Pulse animation is only created when this item is highlighted — avoids running
    // an infiniteRepeatable on every single grid item (200+ animations tanked FPS).
    val pulseAlpha: Float = if (isHighlighted) {
        val highlightPulse = rememberInfiniteTransition(label = "MediaHighlightPulse")
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
        0.70f // static — border is never shown when !isHighlighted anyway
    }

    val autoPlayVideoPreviews = LocalAutoPlayVideoPreviews.current
    val autoPlayGifPreviews = LocalAutoPlayGifPreviews.current

    var fallbackBitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Rebuild trigger: incrementing this forces the image request to be recreated
    var rebuildToken by remember(item.uri) { mutableStateOf(0) }

    // NOTE: Removed the eager LaunchedEffect that pre-fetched from ThumbnailManager on every
    // item enter. This competed with Coil's own cache, causing dual eviction and double memory
    // pressure. Coil now handles the primary load; ThumbnailManager is only used in onError.

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) Modifier.border(BorderStroke(2.5.dp, Color.White.copy(alpha = 0.70f * pulseAlpha)), itemShape)
                else Modifier
            )
            .clip(itemShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) Color.White.copy(alpha = 0.20f)
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

    val isGif = remember(item.mimeType, item.title, item.uri) {
        item.mimeType == "image/gif" || item.title.endsWith(".gif", ignoreCase = true) || item.uri.toString().endsWith(".gif", ignoreCase = true)
    }

    val isAnimatedWebpOrAvif = remember(item.mimeType, item.title, item.uri) {
        val s = (item.mimeType + " " + item.title + " " + item.uri.toString()).lowercase()
        s.contains("webp") || s.contains("avif")
    }

    // NOTE: isTablet removed from keys — it came from LocalConfiguration which changes on rotation,
    // invalidating every grid item's imageRequest at once. targetSize is derived from gridSizeLevel only.
    // NOTE: context removed from keys — context identity is stable per-composition slot, not a cache key.
    val imageRequest = remember(item.uri, item.type, item.durationMs, item.size, item.dateAdded, rebuildToken, isGif, isAnimatedWebpOrAvif, autoPlayGifPreviews, gridSizeLevel) {
        val targetSize = if (gridSizeLevel >= 3) 800 else 400
        val builder = ImageRequest.Builder(context)
            .data(item.uri)
            .diskCacheKey("${item.uri}_${item.size}_${item.dateAdded}_${rebuildToken}_${gridSizeLevel}_${autoPlayGifPreviews}")
            .memoryCacheKey("${item.uri}_${item.size}_${item.dateAdded}_${rebuildToken}_${gridSizeLevel}_${autoPlayGifPreviews}")
            // crossfade(false): memory-cache hits (0ms latency) were running a 300ms fade animation.
            // Scroll with 50 items entering viewport = 50 concurrent fade animations.
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(targetSize)

        if (item.type == MediaType.VIDEO) {
            builder.decoderFactory(com.medianest.util.SemaphoreVideoFrameDecoder.Factory())
            builder.videoFrameMicros(videoSeekMicros)
        } else if (isGif) {
            if (!autoPlayGifPreviews || item.size >= 25 * 1024 * 1024L) {
                builder.decoderFactory(coil.decode.BitmapFactoryDecoder.Factory())
            } else if (item.size >= 10 * 1024 * 1024L) {
                builder.decoderFactory(coil.decode.GifDecoder.Factory(enforceMinimumFrameDelay = true))
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                builder.decoderFactory(coil.decode.ImageDecoderDecoder.Factory())
            } else {
                builder.decoderFactory(coil.decode.GifDecoder.Factory(enforceMinimumFrameDelay = true))
            }
        } else if (isAnimatedWebpOrAvif && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            builder.decoderFactory(coil.decode.ImageDecoderDecoder.Factory())
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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            coil.compose.AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
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
                    // Coil VideoFrameDecoder failed — fall back to our ThumbnailManager
                    if (item.type == MediaType.VIDEO && fallbackBitmap == null) {
                        coroutineScope.launch {
                            val bitmap = ThumbnailManager.getThumbnail(context, item.uri, rebuildToken)
                            if (bitmap != null) {
                                fallbackBitmap = bitmap
                            }
                        }
                    }
                }
            )
        }

        // Live In-Place Video Auto-Preview when visible in viewport
        if (item.type == MediaType.VIDEO && autoPlayVideoPreviews) {
            LibraryVideoPreviewView(
                uri = item.uri,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Video duration overlay badge (compact) & Speaker Audio Toggle (bottom row)
        if (item.type == MediaType.VIDEO) {
            val activeUri by SlideShowVideoPreviewCoordinator.activeUri.collectAsState()
            val isAudioMuted by SlideShowVideoPreviewCoordinator.isAudioMuted.collectAsState()
            val isCurrentlyPreviewing = autoPlayVideoPreviews && activeUri == item.uri

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker toggle on the left side (BottomStart)
                if (isCurrentlyPreviewing) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier
                            .clickable {
                                SlideShowVideoPreviewCoordinator.toggleAudio()
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAudioMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isAudioMuted) "Turn Audio On" else "Mute Audio",
                                tint = if (isAudioMuted) Color.White.copy(alpha = 0.3f) else Color(0xFF4ADE80),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Compact Duration badge on bottom-right (BottomEnd)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        RoundedPlayIcon(
                            modifier = Modifier.size(9.5.dp),
                            tint = Color.White
                        )
                        Text(
                            text = formatDuration(item.durationMs),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                lineHeight = 9.5.sp
                            )
                        )
                    }
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
            } else if (onMoreClick != null || onInfo != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(
                        onClick = {
                            if (onMoreClick != null) {
                                onMoreClick()
                            } else {
                                showMenu = true
                            }
                        },
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

                    GlassDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.width(200.dp),
                        shape = RoundedCornerShape(20.dp)
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
                                showMenu = false
                                if (onInfo != null) onInfo()
                            }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                        }
                        // Rebuild thumbnail option for video items
                        if (item.type == MediaType.VIDEO) {
                            DropdownMenuItem(
                                text = { Text("Rebuild Thumbnail", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    fallbackBitmap = null
                                    val nextToken = rebuildToken + 1
                                    rebuildToken = nextToken
                                    // Proactively extract non-black progressive frame
                                    coroutineScope.launch {
                                        val newFrame = ThumbnailManager.getThumbnail(context, item.uri, nextToken)
                                        if (newFrame != null) {
                                            fallbackBitmap = newFrame
                                        }
                                    }
                                }
                            )
                        }
                        if (onAddToCategory != null) {
                            DropdownMenuItem(
                                text = { Text("Add to Category", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onAddToCategory()
                                }
                            )
                        }
                        if (showRemoveOption && onRemoveFromCategory != null) {
                            DropdownMenuItem(
                                text = { Text("Remove Category", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromCategory()
                                }
                            )
                        }
                        if (onMove != null) {
                            DropdownMenuItem(
                                text = { Text("Move to Folder", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onMove()
                                }
                            )
                        }
                        if (onCopy != null) {
                            DropdownMenuItem(
                                text = { Text("Copy to Folder", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onCopy()
                                }
                            )
                        }
                        if (onOpenFolder != null) {
                            DropdownMenuItem(
                                text = { Text(if (showInGallery) "Open with" else "Show in Folder", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = if (showInGallery) Icons.Default.Image else Icons.Default.Folder, 
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
                                        val folderKey = item.relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: (item.bucketName ?: "Folder")
                                        onOpenFolder(folderKey, item.uri.toString())
                                    }
                                }
                            )
                        }
                        if (onMoveToFilter != null) {
                            DropdownMenuItem(
                                text = { Text("Move to Filter...", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    showMenu = false
                                    onMoveToFilter()
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
