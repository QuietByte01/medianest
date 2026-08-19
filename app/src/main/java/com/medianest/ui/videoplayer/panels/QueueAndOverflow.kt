package com.medianest.ui.videoplayer.panels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.model.MediaItem
import com.medianest.player.PlayerState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.videoplayer.getBreadcrumbParts
import com.medianest.ui.videoplayer.safeFormatDuration
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SidebarQueueDrawer(
    playerState: PlayerState,
    onVideoClick: (MediaItem) -> Unit,
    onClose: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0xF20E111A),
        borderColor = Color(0x33FFFFFF),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    val breadcrumbParts = remember(playerState.currentItem) {
                        getBreadcrumbParts(context, playerState.currentItem)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        breadcrumbParts.forEachIndexed { index, part ->
                            Text(
                                text = part,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (index < breadcrumbParts.lastIndex) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0x99FFFFFF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playerState.queue) { video ->
                    val isCurrent = video.uri == playerState.currentItem?.uri
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onVideoClick(video)
                            },
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = if (isCurrent) Color(0x33FFFFFF) else Color(0x1AFFFFFF),
                        borderColor = if (isCurrent) Color.White else Color(0x22FFFFFF)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFF581C87), Color(0xFF0F172A))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = video.albumArtUri ?: video.uri,
                                    contentDescription = video.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = safeFormatDuration(context, video),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = video.title,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoPlayerOverflowMenu(
    onDismiss: () -> Unit,
    onOpenWith: () -> Unit,
    onShowInFolder: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    isBackgroundPlayEnabled: Boolean,
    onToggleBackgroundPlay: (Boolean) -> Unit,
    isAutoRepeatEnabled: Boolean,
    onToggleAutoRepeat: (Boolean) -> Unit,
    onVideoFx: () -> Unit,
    onAudioTracks: () -> Unit,
    onCast: () -> Unit,
    onSettings: () -> Unit,
    onShowInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        GlassSurface(
            modifier = modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 56.dp, end = 16.dp)
                .width(220.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0xF20E111A),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DropdownMenuItem(
                    text = { Text("File Info", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onShowInfo(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Open with", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onOpenWith(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Show in folder", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onShowInFolder(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                    onClick = { onDelete(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Share", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onShare(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Edit Video", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onEdit(); onDismiss() }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                
                DropdownMenuItem(
                    text = { Text("Background Play: ${if (isBackgroundPlayEnabled) "On" else "Off"}", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onToggleBackgroundPlay(!isBackgroundPlayEnabled) }
                )
                DropdownMenuItem(
                    text = { Text("Auto Repeat: ${if (isAutoRepeatEnabled) "On" else "Off"}", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Repeat, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onToggleAutoRepeat(!isAutoRepeatEnabled) }
                )
                DropdownMenuItem(
                    text = { Text("Audio Tracks", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Audiotrack, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onAudioTracks(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Cast to TV", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Tv, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onCast(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Video FX & Filters", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Tune, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onVideoFx(); onDismiss() }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                
                DropdownMenuItem(
                    text = { Text("Settings", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onSettings(); onDismiss() }
                )
            }
        }
    }
}

@Composable
internal fun AspectRatioModal(
    currentMode: String,
    onModeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF2121522),
            borderColor = Color(0x33FFFFFF),
            enableBlur = true,
            blurRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Aspect Ratio", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                val aspectRatios = listOf(
                    "FIT" to "Fit", "CROP" to "Crop", "16:9" to "16:9",
                    "16:10" to "16:10", "4:3" to "4:3", "1:1" to "1:1",
                    "ORIGINAL" to "Original (100%)", "STRETCH" to "Stretch"
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(aspectRatios) { (code, label) ->
                        val isSelected = currentMode.equals(code, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clickable { onModeChange(code); onDismiss() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, color = if (isSelected) Color.White else Color(0x80FFFFFF), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaybackSpeedModal(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF2121522),
            borderColor = Color(0x33FFFFFF),
            enableBlur = true,
            blurRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Playback Speed", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(speeds) { speed ->
                        val isSelected = currentSpeed == speed
                        val rawLabel = if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "$speed"
                        val displayLabel = if (isSelected) "${rawLabel}x" else if (speed == 1.0f) "1.0x" else rawLabel
                        Box(
                            modifier = Modifier
                                .clickable { onSpeedChange(speed); onDismiss() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = displayLabel, color = if (isSelected) Color.White else Color(0x80FFFFFF), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoPostProcessingPanel(
    currentEffect: String,
    onEffectChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF2121522),
            borderColor = Color(0x33FFFFFF),
            enableBlur = true,
            blurRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Video FX & Post-Processing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val effects = listOf(
                    "NORMAL" to "Normal",
                    "TRUE_COLOR" to "Natural Balance",
                    "BW" to "B&W",
                    "SEPIA" to "Sepia Film",
                    "CINEMATIC" to "Cinematic",
                    "VIVID" to "Vivid",
                    "BALANCED" to "Balanced",
                    "SHARPEN" to "Sharpen",
                    "HIGH_CONTRAST" to "High Contrast",
                    "NIGHT_VISION" to "Night Vision",
                    "VINTAGE_CRT" to "Vintage CRT"
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(effects) { (code, label) ->
                        val isSelected = currentEffect.equals(code, ignoreCase = true)
                        Surface(
                            onClick = { onEffectChange(code) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xF5FFFFFF) else Color(0x1AFFFFFF),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFFFFF) else Color(0x14FFFFFF))
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xCCFFFFFF),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilmstripTrack(
    thumbnails: List<Bitmap>,
    isExtracting: Boolean,
    videoDurationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val durationFloat = videoDurationMs.toFloat().coerceAtLeast(1000f)

    val startFrac = (startMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / durationFloat).coerceIn(0f, 1f)

    val handleWidthDp = 18.dp
    val handleWidthPx = with(density) { handleWidthDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
    ) {
        if (thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 8) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(Color.White.copy(alpha = if (isExtracting) 0.08f + (i % 2) * 0.04f else 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(16) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(16) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
            }
        }

        if (trackWidthPx > 0f) {
            val usableWidth = trackWidthPx - (handleWidthPx * 2)
            val startLeftPx = (startFrac * usableWidth).coerceAtLeast(0f)
            val endRightPx = (handleWidthPx + endFrac * usableWidth).coerceAtMost(trackWidthPx)
            val selectionWidthPx = (endRightPx - startLeftPx).coerceAtLeast(handleWidthPx * 2)

            if (startLeftPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { startLeftPx.toDp() })
                        .align(Alignment.CenterStart)
                        .background(Color.Black.copy(alpha = 0.68f))
                )
            }

            val rightDimWidthPx = (trackWidthPx - (startLeftPx + selectionWidthPx)).coerceAtLeast(0f)
            if (rightDimWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { rightDimWidthPx.toDp() })
                        .align(Alignment.CenterEnd)
                        .background(Color.Black.copy(alpha = 0.68f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = with(density) { startLeftPx.toDp() })
                    .width(with(density) { selectionWidthPx.toDp() })
                    .background(Color(0x2238BDF8))
                    .border(
                        border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .pointerInput(usableWidth, durationFloat, startMs, endMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (usableWidth > 0f) {
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val curDuration = endMs - startMs
                                val newStart = (startMs + deltaMs).coerceIn(0L, videoDurationMs - curDuration)
                                val newEnd = newStart + curDuration
                                onRangeChange(newStart, newEnd)
                            }
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = with(density) { startLeftPx.toDp() })
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color(0xFF38BDF8))
                    .pointerInput(usableWidth, durationFloat, startMs, endMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (usableWidth > 0f) {
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val minGapMs = 500L
                                val newStart = (startMs + deltaMs).coerceIn(0L, endMs - minGapMs)
                                onRangeChange(newStart, endMs)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = with(density) { (startLeftPx + selectionWidthPx - handleWidthPx).toDp() })
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFF38BDF8))
                    .pointerInput(usableWidth, durationFloat, startMs, endMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (usableWidth > 0f) {
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val minGapMs = 500L
                                val newEnd = (endMs + deltaMs).coerceIn(startMs + minGapMs, videoDurationMs)
                                onRangeChange(startMs, newEnd)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoTrimmerBottomSheet(
    videoUri: Uri? = null,
    videoDurationMs: Long,
    currentPositionMs: Long,
    onTrim: (startMs: Long, endMs: Long, exportGif: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalDur = videoDurationMs.coerceAtLeast(1000L)
    var startMs by remember { mutableLongStateOf((currentPositionMs - 3000L).coerceAtLeast(0L)) }
    var endMs by remember { mutableLongStateOf((currentPositionMs + 5000L).coerceAtMost(totalDur)) }
    if (endMs <= startMs) {
        endMs = (startMs + 4000L).coerceAtMost(totalDur)
    }

    var exportFormat by remember { mutableStateOf("GIF") }

    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }

    LaunchedEffect(videoUri, videoDurationMs) {
        if (videoUri == null || videoDurationMs <= 0L) return@LaunchedEffect
        isExtracting = true
        val frames = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<Bitmap>()
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val count = 9
                val stepUs = (videoDurationMs * 1000L) / count
                for (i in 0 until count) {
                    val timeUs = (i * stepUs).coerceAtLeast(0L)
                    val bmp = retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, 140, 90, true)
                        list.add(scaled)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoTrimmer", "Thumbnail extraction notice: ${e.message}")
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            list
        }
        thumbnails = frames
        isExtracting = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF00A0F1D),
        scrimColor = Color.Black.copy(alpha = 0.40f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Video Trimmer & GIF Creator",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            FilmstripTrack(
                thumbnails = thumbnails,
                isExtracting = isExtracting,
                videoDurationMs = totalDur,
                startMs = startMs,
                endMs = endMs,
                onRangeChange = { newStart, newEnd ->
                    startMs = newStart
                    endMs = newEnd
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start: ${com.medianest.ui.components.formatDuration(startMs)}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Clip: ${com.medianest.ui.components.formatDuration((endMs - startMs).coerceAtLeast(0L))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "End: ${com.medianest.ui.components.formatDuration(endMs)}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formats = listOf(
                    "GIF" to "Animated GIF",
                    "VIDEO" to "Trim MP4 Video",
                    "WEBP" to "Animated WebP"
                )
                formats.forEach { (key, label) ->
                    val isSelected = exportFormat == key
                    Surface(
                        onClick = { exportFormat = key },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF38BDF8) else Color(0x1FFFFFFF),
                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0x22FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.35f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        onTrim(startMs, endMs, exportFormat == "GIF" || exportFormat == "WEBP")
                        onDismiss()
                    },
                    modifier = Modifier.weight(0.65f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Icon(
                        imageVector = if (exportFormat == "VIDEO") Icons.Default.ContentCut else Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (exportFormat) {
                            "GIF" -> "Create GIF Clip"
                            "WEBP" -> "Create WebP Clip"
                            else -> "Save Trimmed Video"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
