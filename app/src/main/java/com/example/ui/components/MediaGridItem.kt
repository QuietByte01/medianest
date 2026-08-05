package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import coil.decode.VideoFrameDecoder
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import java.util.concurrent.TimeUnit

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
    onMoreClick: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRemoveFromCategory: (() -> Unit)? = null,
    showRemoveOption: Boolean = false
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val calculatedRatio = item.aspectRatio.coerceIn(0.45f, 2.2f)
    val itemShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)

    val settingsManager = com.example.MediaNestApp.instance.settingsManager
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = itemShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val imageRequest = remember(item.uri, item.type, item.durationMs, context) {
            val builder = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
            if (item.type == com.example.data.db.MediaType.VIDEO) {
                builder.decoderFactory(VideoFrameDecoder.Factory())
                val seekMicros = if (item.durationMs > 2000) 1_000_000L else if (item.durationMs > 500) 250_000L else 0L
                builder.videoFrameMicros(seekMicros)
            }
            builder.build()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(calculatedRatio)
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
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val state = painter.state
                    if (state is AsyncImagePainter.State.Loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            MediaLoadingAnimation(
                                mediaType = item.type,
                                iconSize = 26.dp,
                                showLabel = false
                            )
                        }
                    } else if (state is AsyncImagePainter.State.Error) {
                        if (item.type == MediaType.VIDEO) {
                            LaunchedEffect(item.uri) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val retriever = android.media.MediaMetadataRetriever()
                                        retriever.setDataSource(context, item.uri)
                                        val frame = retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                            ?: retriever.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        retriever.release()
                                        fallbackBitmap = frame
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val errorIcon = when (item.type) {
                                MediaType.AUDIO -> Icons.Default.Audiotrack
                                MediaType.VIDEO -> Icons.Default.Movie
                                else -> Icons.Default.Image
                            }
                            Icon(
                                imageVector = errorIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }

            // Video overlay badge
            if (item.type == MediaType.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatDuration(item.durationMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Selection checkbox overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.5f)
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

                    val isDark = com.example.ui.theme.LocalDarkTheme.current
                    val menuBg = if (isDark) Color(0xBF0F1015) else Color(0xA6FFFFFF)

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = menuBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(180.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play / View", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        if (onInfo != null) {
                            DropdownMenuItem(
                                text = { Text("File Info", color = if (isDark) Color.White else Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                                onClick = {
                                    showMenu = false
                                    onInfo()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Select", color = if (isDark) Color.White else Color.Black) },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = if (isDark) Color.White else Color.Black) },
                            onClick = {
                                showMenu = false
                                onLongClick()
                            }
                        )
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

fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
