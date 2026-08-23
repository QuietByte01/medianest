package com.medianest.ui.components.mediainfo

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.SolidGlossySurface
import com.medianest.ui.components.PaletteTagChip
import com.medianest.ui.components.PlaybackSpeedChip
import com.medianest.ui.components.formatDuration
import kotlinx.coroutines.launch

@Composable
internal fun VideoFilePropertiesContent(
    item: MediaItem,
    extracted: ComprehensiveMetadata,
    formattedSize: String,
    filePath: String,
    onDismiss: () -> Unit,
    onShowFileLocation: ((MediaItem) -> Unit)? = null,
    onFetchInfo: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var currentItemTitle by remember(item.id, item.title) { mutableStateOf(item.title) }
    var renameInputText by remember { mutableStateOf(currentItemTitle) }

    val (resolutionStr, resBadgeText) = remember(extracted.width, extracted.height) {
        val w = extracted.width
        val h = extracted.height
        if (w > 0 && h > 0) {
            val maxDim = maxOf(w, h)
            val badge = when {
                maxDim >= 3840 -> "4K UHD"
                maxDim >= 2560 -> "2K QHD"
                maxDim >= 1920 -> "1080p FHD"
                maxDim >= 1280 -> "720p HD"
                else -> "SD"
            }
            "${w} × ${h} ($badge)" to badge
        } else {
            "Standard Resolution" to null
        }
    }

    val imageRequest = remember<ImageRequest>(item.uri, item.durationMs) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
            .videoFrameMicros(if (item.durationMs > 5000) 3_000_000L else if (item.durationMs > 2000) 1_000_000L else 0L)
            .build()
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Video", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    label = { Text("Video Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0x66FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            currentItemTitle = renameInputText
                            Toast.makeText(context, "Renamed to '$renameInputText'", Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xF2121522),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Video File Properties",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isCompactScreen = configuration.screenWidthDp < 600
        val isCompact = isCompactScreen

        // Top Hero Card
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x3B181A26),
            borderColor = Color(0x2EFFFFFF)
        ) {
            if (isCompactScreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x33FFFFFF), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = currentItemTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dynamic Resolution Badge (Light Grey)
                        if (!resBadgeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                PaletteTagChip(
                                    label = resBadgeText,
                                    paletteColor = Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Details Column on Bottom
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentItemTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2
                        )
                        Text(
                            text = "📁 ${filePath.ifBlank { item.relativePath ?: "/Internal Storage/Download/Movies/" }}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Pill Buttons (Playback Speed Chip Design)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaybackSpeedChip(
                                label = "Play",
                                icon = Icons.Default.PlayArrow,
                                isSelected = true,
                                onClick = { onDismiss() }
                            )

                            PlaybackSpeedChip(
                                label = "Rename",
                                icon = Icons.Default.Edit,
                                isSelected = false,
                                onClick = {
                                    renameInputText = currentItemTitle
                                    showRenameDialog = true
                                }
                            )

                            PlaybackSpeedChip(
                                label = "Share",
                                icon = Icons.Default.Share,
                                isSelected = false,
                                onClick = {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = item.mimeType ?: "video/*"
                                        putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, currentItemTitle)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                                }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Thumbnail
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(82.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x33FFFFFF), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = currentItemTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dynamic Resolution Badge (Light Grey)
                        if (!resBadgeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                PaletteTagChip(
                                    label = resBadgeText,
                                    paletteColor = Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }

                    // Title & Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentItemTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "📁 ${filePath.ifBlank { item.relativePath ?: "/Internal Storage/Download/Movies/" }}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Pill Buttons (Playback Speed Chip Design)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaybackSpeedChip(
                                label = "Play",
                                icon = Icons.Default.PlayArrow,
                                isSelected = true,
                                onClick = { onDismiss() }
                            )

                            PlaybackSpeedChip(
                                label = "Rename",
                                icon = Icons.Default.Edit,
                                isSelected = false,
                                onClick = {
                                    renameInputText = currentItemTitle
                                    showRenameDialog = true
                                }
                            )

                            PlaybackSpeedChip(
                                label = "Share",
                                icon = Icons.Default.Share,
                                isSelected = false,
                                onClick = {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = item.mimeType ?: "video/*"
                                        putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, currentItemTitle)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4 Stat Boxes Row / 2x2 Grid on phone
        if (isCompactScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(
                        icon = Icons.Default.Folder,
                        label = "FILE SIZE",
                        value = formattedSize.ifBlank { "Unknown" },
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        icon = Icons.Default.Movie,
                        label = "RESOLUTION",
                        value = resolutionStr,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(
                        icon = Icons.Default.Info,
                        label = "DURATION",
                        value = if (item.durationMs > 0) formatDuration(item.durationMs) else if (extracted.durationMs > 0) formatDuration(extracted.durationMs) else "00:17:00",
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        icon = Icons.Default.Security,
                        label = "CODEC",
                        value = extracted.codecInfo.ifBlank { item.mimeType?.substringAfter('/')?.uppercase() ?: "HEVC/H.265" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBox(
                    icon = Icons.Default.Folder,
                    label = "FILE SIZE",
                    value = formattedSize.ifBlank { "Unknown" },
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Movie,
                    label = "RESOLUTION",
                    value = resolutionStr,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Info,
                    label = "DURATION",
                    value = if (item.durationMs > 0) formatDuration(item.durationMs) else if (extracted.durationMs > 0) formatDuration(extracted.durationMs) else "00:17:00",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Security,
                    label = "CODEC",
                    value = extracted.codecInfo.ifBlank { item.mimeType?.substringAfter('/')?.uppercase() ?: "HEVC/H.265" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val realStream = remember(item.uri) { extractRealStreamDetails(context, item) }

        // VIDEO STREAM TECHNICAL SPECS Section
        InfoSectionCard(
            icon = Icons.Default.Movie,
            title = "VIDEO STREAM TECHNICAL SPECS"
        ) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelValueBlock("Video Format / Container", realStream.containerFormat)
                    LabelValueBlock("Video Codec", realStream.videoCodec)
                    LabelValueBlock("Frame Rate", realStream.frameRate)
                    LabelValueBlock("Color Space / Range", realStream.colorSpace)
                    LabelValueBlock("Display Aspect Ratio", realStream.aspectRatio)
                    LabelValueBlock("Video Bitrate", realStream.videoBitrate.ifBlank { extracted.bitrate.ifBlank { "Auto" } })
                    LabelValueBlock("HDR Metadata", realStream.hdrInfo)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Video Format / Container", realStream.containerFormat)
                        LabelValueBlock("Video Codec", realStream.videoCodec)
                        LabelValueBlock("Frame Rate", realStream.frameRate)
                        LabelValueBlock("Color Space / Range", realStream.colorSpace)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Display Aspect Ratio", realStream.aspectRatio)
                        LabelValueBlock("Video Bitrate", realStream.videoBitrate.ifBlank { extracted.bitrate.ifBlank { "Auto" } })
                        LabelValueBlock("HDR Metadata", realStream.hdrInfo)
                    }
                }
            }
        }

        // AUDIO STREAMS Section
        InfoSectionCard(
            icon = Icons.Default.Audiotrack,
            title = "AUDIO STREAMS"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Embedded Tracks", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        realStream.audioTracks.forEachIndexed { idx, trackName ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (idx == 0) Color(0x44FFFFFF) else Color(0x22FFFFFF))
                                    .border(if (idx == 0) 1.dp else 0.5.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(trackName, fontSize = 11.sp, fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium, color = Color.White)
                            }
                        }
                    }
                }

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabelValueBlock("Audio Format", realStream.audioFormat)
                        LabelValueBlock("Audio Bitrate", realStream.audioBitrate)
                        LabelValueBlock("Audio Channels", realStream.audioChannels)
                        LabelValueBlock("Sample Rate", realStream.audioSampleRate.ifBlank { extracted.sampleRate.ifBlank { "48.0 kHz" } })
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabelValueBlock("Audio Format", realStream.audioFormat)
                            LabelValueBlock("Audio Bitrate", realStream.audioBitrate)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabelValueBlock("Audio Channels", realStream.audioChannels)
                            LabelValueBlock("Sample Rate", realStream.audioSampleRate.ifBlank { extracted.sampleRate.ifBlank { "48.0 kHz" } })
                        }
                    }
                }
            }
        }

        // REAL-TIME PLAYBACK & DECODER ENGINE PROPERTIES
        val exoPlayerManager = remember { com.medianest.player.ExoPlayerManager.getInstance(context) }
        val playerState by exoPlayerManager.playerState.collectAsState()
        val isCurrentPlaying = playerState.currentItem?.uri == item.uri
        val activeEngine = if (isCurrentPlaying) playerState.activeEngineName else "Media3 ExoPlayer / FFmpeg Engine"
        val activeDecoder = if (isCurrentPlaying) playerState.activeDecoderName else "Android MediaCodec (Hardware HW)"
        val isHwAccelerated = if (isCurrentPlaying) playerState.isHardwareAccelerated else true
        val fallbackStatus = if (isCurrentPlaying && playerState.decoderFallbackReason != null) {
            "Active Fallback (${playerState.decoderFallbackReason})"
        } else {
            "Optimal (No Fallback Required)"
        }

        InfoSectionCard(
            icon = Icons.Default.PlayArrow,
            title = "REAL-TIME PLAYBACK & DECODER STATUS"
        ) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelValueBlock("Active Engine", activeEngine)
                    LabelValueBlock("Active Decoder", activeDecoder)
                    LabelValueBlock("Decoder Type", if (isHwAccelerated) "Hardware Accelerated (HW)" else "Software Emulated (SW)")
                    LabelValueBlock("Fallback Pipeline", fallbackStatus)
                    if (isCurrentPlaying) {
                        LabelValueBlock("Frame Drop Telemetry", "${playerState.droppedFrames} dropped frames")
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Active Engine", activeEngine)
                        LabelValueBlock("Active Decoder", activeDecoder)
                        if (isCurrentPlaying) {
                            LabelValueBlock("Frame Drop Telemetry", "${playerState.droppedFrames} dropped frames")
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Decoder Type", if (isHwAccelerated) "Hardware Accelerated (HW)" else "Software Emulated (SW)")
                        LabelValueBlock("Fallback Pipeline", fallbackStatus)
                    }
                }
            }
        }

        // STORAGE & SYSTEM PROPERTIES Section
        InfoSectionCard(
            icon = Icons.Default.Folder,
            title = "STORAGE & SYSTEM PROPERTIES"
        ) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelValueBlock("File Directory", filePath.ifBlank { "/storage/emulated/0/Download/Movies/" })
                    LabelValueBlock("Date Modified", "Aug 01, 2026 • 14:25:31")
                    LabelValueBlock("Date Created", "Aug 01, 2026 • 14:22:08")
                    LabelValueBlock("Storage Permissions", "Read / Write (rw-rw----)")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("File Directory", filePath.ifBlank { "/storage/emulated/0/Download/Movies/" })
                        LabelValueBlock("Date Modified", "Aug 01, 2026 • 14:25:31")
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Date Created", "Aug 01, 2026 • 14:22:08")
                        LabelValueBlock("Storage Permissions", "Read / Write (rw-rw----)")
                    }
                }
            }
        }

        // Media Stream Diagnostics Button (Placed BELOW Storage & System Properties, Soft White theme)
        val scope = rememberCoroutineScope()
        var showDiagnosticsDialog by remember { mutableStateOf(false) }
        var diagReport by remember { mutableStateOf<com.medianest.util.MediaDiagnosticsReport?>(null) }
        var isAnalyzing by remember { mutableStateOf(false) }

        SolidGlossySurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        isAnalyzing = true
                        val report = com.medianest.util.MediaAnalyzer.analyze(filePath, item.type.name, context)
                        diagReport = report
                        isAnalyzing = false
                        showDiagnosticsDialog = true
                    }
                },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0x2EFFFFFF),
            borderColor = Color(0x33FFFFFF),
            showTopSheen = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing Media Streams...", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Media Stream Diagnostics", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showDiagnosticsDialog && diagReport != null) {
            MediaDiagnosticsDialog(
                report = diagReport!!,
                item = item,
                filePath = filePath,
                currentItemTitle = currentItemTitle,
                onDismissRequest = { showDiagnosticsDialog = false },
                onPlay = { showDiagnosticsDialog = false; onDismiss() },
                onRename = { renameInputText = currentItemTitle; showRenameDialog = true },
                context = context
            )
        }
    }
}
