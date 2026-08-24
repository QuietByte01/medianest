package com.medianest.ui.videoplayer.panels

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.R
import com.medianest.data.model.MediaItem
import com.medianest.player.PlayerState
import com.medianest.ui.components.AmbientGlassSurface
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.extractBaseHueFromArt
import com.medianest.ui.components.media.*
import com.medianest.ui.components.media.MediaEffect
import com.medianest.ui.components.media.FilterCarouselStyle
import com.medianest.ui.components.media.MediaFilterCarousel
import com.medianest.ui.components.media.MediaTrimTimeline
import com.medianest.ui.components.media.MediaAspectRatio
import com.medianest.ui.components.media.MediaAspectRatioSelector
import com.medianest.ui.components.media.AspectRatioSelectorStyle
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.ui.videoplayer.getBreadcrumbParts
import com.medianest.ui.videoplayer.safeFormatDuration
import com.medianest.ui.components.formatDuration
import kotlinx.coroutines.withContext


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
    isAbRepeatActive: Boolean = false,
    onAbRepeat: () -> Unit = {},
    onVideoFx: () -> Unit,
    onAudioTracks: () -> Unit,
    onCast: () -> Unit,
    onSettings: () -> Unit,
    onShowInfo: () -> Unit = {},
    backgroundImage: Any? = null,
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
        AmbientGlassSurface(
            modifier = modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 56.dp, end = 16.dp)
                .width(220.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            backgroundImage = backgroundImage,
            borderWidth = 0.5.dp
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
                    text = {
                        Text(
                            text = if (isAbRepeatActive) "A-B Repeat: Active" else "A-B Repeat",
                            color = if (isAbRepeatActive) Color(0xFF4FC3F7) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isAbRepeatActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.RepeatOne,
                            null,
                            tint = if (isAbRepeatActive) Color(0xFF4FC3F7) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { onAbRepeat(); onDismiss() }
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
    currentMode: MediaAspectRatio,
    onModeChange: (MediaAspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val bgRes = R.drawable.bg_596

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
            borderWidth = 0.5.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Aspect Ratio", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                
                MediaAspectRatioSelector(
                    options = listOf(
                        MediaAspectRatio.FIT, MediaAspectRatio.CROP, MediaAspectRatio.P_16_9,
                        MediaAspectRatio.P_16_10, MediaAspectRatio.P_9_16, MediaAspectRatio.P_4_3,
                        MediaAspectRatio.P_1_1, MediaAspectRatio.P_4_5, MediaAspectRatio.P_21_9,
                        MediaAspectRatio.ORIGINAL, MediaAspectRatio.STRETCH
                    ),
                    selected = currentMode,
                    onSelect = onModeChange,
                    style = AspectRatioSelectorStyle.PLAYER_LIST
                )
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
    val isDark = LocalDarkTheme.current

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
            borderWidth = 0.5.dp
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
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(speeds) { speed ->
                        val isSelected = speed == currentSpeed
                        val rawLabel = if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "$speed"
                        val displayLabel = if (isSelected) "${rawLabel}x" else if (speed == 1.0f) "1.0x" else rawLabel
                        com.medianest.ui.components.PlaybackSpeedChip(
                            label = displayLabel,
                            isSelected = isSelected,
                            onClick = { onSpeedChange(speed); onDismiss() },
                            fontSize = 14.sp,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoPostProcessingPanel(
    currentEffect: MediaEffect,
    onEffectChange: (MediaEffect) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalDarkTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            borderWidth = 0.5.dp
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

                MediaFilterCarousel(
                    filters = listOf(
                        MediaEffect.OFF, MediaEffect.NATURAL, MediaEffect.TRUE_COLOR, MediaEffect.BALANCED,
                        MediaEffect.CINEMA, MediaEffect.VIVID, MediaEffect.WARM,
                        MediaEffect.COOL, MediaEffect.CYBERPUNK, MediaEffect.DREAMY,
                        MediaEffect.BW, MediaEffect.SEPIA, MediaEffect.SHARPEN,
                        MediaEffect.HIGH_CONTRAST, MediaEffect.NIGHT_VISION, MediaEffect.VINTAGE_CRT
                    ),
                    activeFilter = currentEffect,
                    onFilterChange = onEffectChange,
                    style = FilterCarouselStyle.PLAYER_CHIP
                )
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
        thumbnails = withContext(kotlinx.coroutines.Dispatchers.IO) {
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

            MediaTrimTimeline(
                thumbnails = thumbnails,
                isExtracting = isExtracting,
                videoDurationMs = totalDur,
                startMs = startMs,
                endMs = endMs,
                onRangeChange = { newStart, newEnd ->
                    startMs = newStart
                    endMs = newEnd
                },
                accentColor = Color(0xFF38BDF8),
                showSprockets = true,
                showDurationLabels = true
            )

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

