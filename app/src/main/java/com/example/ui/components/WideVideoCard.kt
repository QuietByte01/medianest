package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import coil.decode.VideoFrameDecoder
import com.example.data.model.MediaItem
import com.example.ui.theme.LocalDarkTheme
import com.example.util.formatBytesReport
import kotlinx.coroutines.launch
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
    onDelete: () -> Unit,
    onRemoveFromCategory: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null
) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    val timeStr = if (item.dateAdded > 0) timeFormat.format(Date(item.dateAdded * 1000L)) else "N/A"
    val context = androidx.compose.ui.platform.LocalContext.current
    var fallbackBitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    val imageRequest = remember(item.uri, item.durationMs) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMicros(if (item.durationMs > 5000) 3_000_000L else if (item.durationMs > 2000) 1_000_000L else 0L)
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
        borderColor = if (isSelected) Color.White else Color(0x28FFFFFF)
    ) {
        Box {
            Column(modifier = Modifier.padding(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                                        try {
                                            val retriever = android.media.MediaMetadataRetriever()
                                            retriever.setDataSource(context, item.uri)
                                            val seekMicros = if (item.durationMs > 5000) 2_000_000L else if (item.durationMs > 2000) 1_000_000L else 0L
                                            val frame = retriever.getFrameAtTime(seekMicros, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                ?: retriever.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                            retriever.release()
                                            fallbackBitmap = frame
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        )
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
                                Text("📍", fontSize = 10.sp)
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

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xDD0F1015),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = formatDuration(item.durationMs),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 2,
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
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (onRemoveFromCategory != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Category") },
                            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) },
                            onClick = { 
                                menuExpanded = false
                                onRemoveFromCategory() 
                            }
                        )
                    }
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { 
                                menuExpanded = false
                                onRename() 
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            menuExpanded = false
                            onDelete() 
                        }
                    )
                }
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White
                    )
                )
            }
        }
    }
}
