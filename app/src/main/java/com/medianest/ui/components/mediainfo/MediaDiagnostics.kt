package com.medianest.ui.components.mediainfo

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.medianest.R
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.util.MediaDiagnosticsReport
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun MediaDiagnosticsDialog(
    item: MediaItem,
    report: MediaDiagnosticsReport,
    filePath: String,
    currentItemTitle: String,
    onDismissRequest: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    context: Context
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isHealthClean = !report.diagnostics.corruptedFramesDetected &&
            !report.diagnostics.timestampIssues &&
            !report.diagnostics.hasErrors &&
            !report.diagnostics.isVideoCorrupted &&
            !report.diagnostics.isAudioCorrupted &&
            report.diagnostics.corruptedVideoFramesCount == 0 &&
            report.diagnostics.corruptedAudioSamplesCount == 0
    var selectedAudioTrackIndex by remember { mutableIntStateOf(0) }
    val isAudioMedia = item.type == MediaType.AUDIO
    val isImageMedia = item.type == MediaType.IMAGE
    val isVideoMedia = item.type == MediaType.VIDEO

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
                ?: (dialogView.context as? Activity)?.window
            window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    w.isNavigationBarContrastEnforced = false
                }
                WindowInsetsControllerCompat(w, w.decorView).let { controller ->
                    controller.isAppearanceLightNavigationBars = false
                }
            }
            onDispose {}
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC05070E))
                .padding(if (isLandscape) 24.dp else 10.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.88f else 1f)
                    .fillMaxHeight(0.94f),
                shape = RoundedCornerShape(24.dp),
                borderColor = Color(0x33384260),
                backgroundBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF05070A),
                        Color(0xFF0C101A)
                    )
                ),
                enableBlur = false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isLandscape) 20.dp else 14.dp, vertical = 14.dp)
                ) {
                    // Dialog Top Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33A855F7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = if (isAudioMedia) "Audio Stream Diagnostics" else if (isImageMedia) "Image Format Diagnostics" else "Complete Media Diagnostics",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { onDismissRequest() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Diagnostics Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Hero Card: Media Thumbnail, Title, Path, Chips & Action Buttons
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = Color(0x1A1E2438),
                            borderColor = Color(0x333F4A6A)
                        ) {
                            if (isLandscape) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Thumbnail Box
                                    Box(
                                        modifier = Modifier
                                            .width(105.dp)
                                            .height(95.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x2E252D48))
                                            .border(1.dp, Color(0x336366F1), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.albumArtUri != null) {
                                            AsyncImage(
                                                model = item.albumArtUri,
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isAudioMedia) Icons.Default.MusicNote else if (isImageMedia) Icons.Default.Image else Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = Color(0xFFA5B4FC),
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }

                                        /*
                                        val thumbBadge = when {
                                            isVideoMedia && (report.videoStream?.width ?: 0) >= 3840 -> "4K HDR"
                                            isVideoMedia && (report.videoStream?.width ?: 0) >= 2000 -> "2K QHD"
                                            isVideoMedia -> "FHD"
                                            isAudioMedia && (report.audioStreams.firstOrNull()?.isSpatialAudio == true) -> "ATMOS"
                                            isAudioMedia -> report.audioStreams.firstOrNull()?.codecName?.take(6) ?: "AUDIO"
                                            isImageMedia -> report.imageInfo?.format ?: "IMAGE"
                                            else -> "MEDIA"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(5.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xCC000000))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(thumbBadge, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        */
                                    }

                                    // Metadata & Chips & Actions
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = report.fileName,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "📁 ${filePath.ifBlank { "/storage/emulated/0/Media/" }}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8E9CB2),
                                            maxLines = 1
                                        )

                                        // Colored Tech Chips Flow Row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            report.techBadges.forEach { badge ->
                                                DiagnosticTechChip(badge)
                                            }

                                            // Share Button
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0x28A78BFA))
                                                    .border(1.dp, Color(0x66A78BFA), RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(android.content.Intent.EXTRA_TEXT, report.toShareText())
                                                        }
                                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Share", fontSize = 8.5.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        /*
                                        DiagActionButtonsRow(
                                            report = report,
                                            currentItemTitle = currentItemTitle,
                                            onPlay = onPlay,
                                            onRename = onRename,
                                            context = context
                                        )
                                        */
                                    }
                                }
                            } else {
                                // Phone Portrait Stack
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x2E252D48))
                                                .border(1.dp, Color(0x336366F1), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.albumArtUri != null) {
                                                AsyncImage(
                                                    model = item.albumArtUri,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isAudioMedia) Icons.Default.MusicNote else if (isImageMedia) Icons.Default.Image else Icons.Default.Movie,
                                                    contentDescription = null,
                                                    tint = Color(0xFFA5B4FC),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = report.fileName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "📁 ${filePath.ifBlank { "/storage/emulated/0/Media/" }}",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF8E9CB2),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        report.techBadges.forEach { badge ->
                                            DiagnosticTechChip(badge)
                                        }

                                        // Share Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x28A78BFA))
                                                .border(1.dp, Color(0x66A78BFA), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, report.toShareText())
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                                                }
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Share", fontSize = 8.5.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                                            }
                                        }
                                    }

                                    /*
                                    DiagActionButtonsRow(
                                        report = report,
                                        currentItemTitle = currentItemTitle,
                                        onPlay = onPlay,
                                        onRename = onRename,
                                        context = context
                                    )
                                    */
                                }
                            }
                        }

                        // 2. Four Stat Cards Grid/Row tailored by media type
                        if (isAudioMedia) {
                            val audio = report.audioStreams.firstOrNull()
                            if (isLandscape) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DiagStatCard(icon = Icons.Default.Folder, label = "FILE SIZE", value = com.medianest.util.formatBytesReport(report.fileSize), modifier = Modifier.weight(1f))
                                    DiagStatCard(icon = Icons.Default.GraphicEq, label = "CHANNELS", value = "${audio?.channels ?: 2} Channels (${audio?.channelLayout ?: "Stereo"})", modifier = Modifier.weight(1f))
                                    DiagStatCard(icon = Icons.Default.Info, label = "DURATION", value = com.medianest.util.formatDurationReport(report.format?.duration ?: 0.0), modifier = Modifier.weight(1f))
                                    DiagStatCard(icon = Icons.Default.MusicNote, label = "CODEC", value = audio?.codecName ?: "AUDIO", modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DiagStatCard(icon = Icons.Default.Folder, label = "FILE SIZE", value = com.medianest.util.formatBytesReport(report.fileSize), modifier = Modifier.weight(1f))
                                        DiagStatCard(icon = Icons.Default.GraphicEq, label = "CHANNELS", value = "${audio?.channels ?: 2} Channels", modifier = Modifier.weight(1f))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DiagStatCard(icon = Icons.Default.Info, label = "DURATION", value = com.medianest.util.formatDurationReport(report.format?.duration ?: 0.0), modifier = Modifier.weight(1f))
                                        DiagStatCard(icon = Icons.Default.MusicNote, label = "CODEC", value = audio?.codecName ?: "AUDIO", modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else if (isImageMedia) {
                            val img = report.imageInfo
                            if (isLandscape) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DiagStatCard(icon = Icons.Default.Folder, label = "FILE SIZE", value = com.medianest.util.formatBytesReport(report.fileSize), modifier = Modifier.weight(1f))
                                    DiagStatCard(
                                        modifier = Modifier.weight(1f),
                                        leadingContent = { com.medianest.ui.components.RoundedPlayIcon(modifier = Modifier.size(16.dp), tint = Color(0xFFA5B4FC)) },
                                        label = "RESOLUTION", 
                                        value = img?.let { "${it.width} × ${it.height}" } ?: "N/A"
                                    )
                                    DiagStatCard(icon = Icons.Default.Info, label = "COLOR DEPTH", value = img?.colorDepth ?: "8-bit", modifier = Modifier.weight(1f))
                                    DiagStatCard(icon = Icons.Default.Image, label = "FORMAT", value = img?.format ?: "IMAGE", modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DiagStatCard(icon = Icons.Default.Folder, label = "FILE SIZE", value = com.medianest.util.formatBytesReport(report.fileSize), modifier = Modifier.weight(1f))
                                        DiagStatCard(
                                        modifier = Modifier.weight(1f),
                                        leadingContent = { com.medianest.ui.components.RoundedPlayIcon(modifier = Modifier.size(16.dp), tint = Color(0xFFA5B4FC)) },
                                        label = "RESOLUTION", 
                                        value = img?.let { "${it.width} × ${it.height}" } ?: "N/A"
                                    )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DiagStatCard(icon = Icons.Default.Info, label = "COLOR DEPTH", value = img?.colorDepth ?: "8-bit", modifier = Modifier.weight(1f))
                                        DiagStatCard(icon = Icons.Default.Image, label = "FORMAT", value = img?.format ?: "IMAGE", modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            // Video Stats
                            if (isLandscape) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DiagStatCard(icon = Icons.Default.Folder, label = "FILE SIZE", value = com.medianest.util.formatBytesReport(report.fileSize), modifier = Modifier.weight(1f))
                                    DiagStatCard(
                                        leadingContent = { com.medianest.ui.components.RoundedPlayIcon(modifier = Modifier.size(16.dp), tint = Color(0xFFA5B4FC)) },
                                        label = "RESOLUTION", 
                                        value = report.videoStream?.let { "${it.width} × ${it.height}" } ?: "N/A", 
                                        modifier = Modifier.weight(1f)
                                    )
                                    DiagStatCard(icon = Icons.Default.Info, label = "DURATION", value = com.medianest.util.formatDurationReport(report.format?.duration ?: 0.0), modifier = Modifier.weight(1f))
                                    DiagStatCard(icon = Icons.Default.Movie, label = "CODEC", value = report.videoStream?.codecName ?: "N/A", modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DiagStatCard(icon = Icons.Default.Folder, label = "FILE SIZE", value = com.medianest.util.formatBytesReport(report.fileSize), modifier = Modifier.weight(1f))
                                        DiagStatCard(
                                            modifier = Modifier.weight(1f),
                                            leadingContent = { com.medianest.ui.components.RoundedPlayIcon(modifier = Modifier.size(16.dp), tint = Color(0xFFA5B4FC)) },
                                            label = "RESOLUTION", 
                                            value = report.videoStream?.let { "${it.width} × ${it.height}" } ?: "N/A"
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DiagStatCard(icon = Icons.Default.Info, label = "DURATION", value = com.medianest.util.formatDurationReport(report.format?.duration ?: 0.0), modifier = Modifier.weight(1f))
                                        DiagStatCard(icon = Icons.Default.Movie, label = "CODEC", value = report.videoStream?.codecName ?: "N/A", modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // 3. Stream Integrity & Health Card
                        val hasVideoErrors = report.diagnostics.isVideoCorrupted || report.diagnostics.corruptedVideoFramesCount > 0
                        val hasAudioErrors = report.diagnostics.isAudioCorrupted || report.diagnostics.corruptedAudioSamplesCount > 0 || report.diagnostics.audioBufferUnderrunsCount > 0

                        val healthHeaderTitle = when {
                            isHealthClean -> "✔ STREAM INTEGRITY & HEALTH (CLEAN)"
                            hasVideoErrors && hasAudioErrors -> "⚠ STREAM INTEGRITY — VIDEO & AUDIO ISSUES DETECTED"
                            hasVideoErrors -> "⚠ STREAM INTEGRITY — VIDEO STREAM ISSUES DETECTED"
                            hasAudioErrors -> "⚠ STREAM INTEGRITY — AUDIO STREAM ISSUES DETECTED"
                            report.diagnostics.timestampIssues -> "⚠ STREAM INTEGRITY — PTS/DTS TIMESTAMP GAP DETECTED"
                            else -> "⚠ STREAM INTEGRITY — CONTAINER HEADER ISSUES DETECTED"
                        }

                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = if (isHealthClean) Color(0x1810B981) else Color(0x22EF4444),
                            borderColor = if (isHealthClean) Color(0x4410B981) else Color(0x55EF4444)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = healthHeaderTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHealthClean) Color(0xFF34D399) else Color(0xFFF87171)
                                    )
                                }

                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isVideoMedia || report.videoStream != null) {
                                        DiagHealthRow(
                                            label = "Video Stream Integrity",
                                            value = if (hasVideoErrors) "${report.diagnostics.corruptedVideoFramesCount} corrupted video frames" else "0 corrupted video frames (Clean)",
                                            isError = hasVideoErrors
                                        )
                                    }
                                    if (isAudioMedia || report.audioStreams.isNotEmpty()) {
                                        DiagHealthRow(
                                            label = "Audio Stream Integrity",
                                            value = if (hasAudioErrors) {
                                                val sampleCount = report.diagnostics.corruptedAudioSamplesCount
                                                val underruns = report.diagnostics.audioBufferUnderrunsCount
                                                if (sampleCount > 0) "$sampleCount corrupted audio samples"
                                                else if (underruns > 0) "$underruns audio buffer underruns"
                                                else "Audio stream issue detected"
                                            } else "0 corrupted audio samples (Clean)",
                                            isError = hasAudioErrors
                                        )
                                    }
                                    DiagHealthRow(
                                        label = "Demuxer Timestamp Continuity",
                                        value = if (report.diagnostics.timestampIssues || report.diagnostics.demuxerDiscontinuity) "PTS/DTS Discontinuity Warning" else "Monotonic Timestamps (Passed)",
                                        isError = report.diagnostics.timestampIssues || report.diagnostics.demuxerDiscontinuity
                                    )
                                    DiagHealthRow(
                                        label = "Container Header Integrity",
                                        value = if (report.diagnostics.muxingIssues) "Muxing / Header Warning" else "Passed (Clean Header)",
                                        isError = report.diagnostics.muxingIssues
                                    )
                                    DiagHealthRow(
                                        label = "A/V Sync Status",
                                        value = if (report.diagnostics.avSyncOffsetMs != 0) "${report.diagnostics.avSyncOffsetMs} ms offset" else "In Sync (0 ms offset)",
                                        isError = abs(report.diagnostics.avSyncOffsetMs) > 100
                                    )
                                }
                            }
                        }

                        // 4. Video Information Card (Shown ONLY for Video files)
                        if (isVideoMedia && report.videoStream != null) {
                            val v = report.videoStream
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                backgroundColor = Color(0x1A181E30),
                                borderColor = Color(0x333F4A6A)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                                        Text("VIDEO INFORMATION", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                    }

                                    if (isLandscape) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Codec", "${v.codecName} (${v.codecLongName})")
                                                DiagInfoRow("Resolution", "${v.width} × ${v.height} (${v.aspectRatio ?: "16:9"})")
                                                DiagInfoRow("Frame Rate", "${String.format(Locale.US, "%.3f", v.avgFpsDecimal)} fps (Constant)")
                                                DiagInfoRow("Duration", com.medianest.util.formatDurationReport(v.duration ?: 0.0))
                                                DiagInfoRow("Interlaced / Progressive", if (v.isInterlaced) "Interlaced" else "Progressive")
                                                DiagInfoRow("Color Range", v.colorRange ?: "Full Range (0-255)")
                                                DiagInfoRow("HDR Information", v.hdrInfo)
                                                DiagInfoRow("Video Stream ID", v.streamId)
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Codec Profile / Level", v.profile ?: "Main 10@L5.1@High")
                                                DiagInfoRow("Pixel Format", v.pixelFormat ?: "yuv420p10le")
                                                DiagInfoRow("Avg / Max Bitrate", "${com.medianest.util.formatBitrateReport(v.bitrate ?: 0L)} / ${com.medianest.util.formatBitrateReport(v.maxBitrate ?: (v.bitrate ?: 0L))}")
                                                DiagInfoRow("Frame Count", String.format(Locale.US, "%,d frames", v.totalFrames))
                                                DiagInfoRow("Color Space", v.colorSpace ?: "BT.2020 (bt2020nc)")
                                                DiagInfoRow("Primaries / Transfer", "${v.colorPrimaries ?: "BT.2020"} / ${v.colorTransfer ?: "SMPTE ST 2086"}")
                                                DiagInfoRow("Rotation / Orientation", "${v.rotation ?: 0}° (Normal)")
                                            }
                                        }
                                    } else {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            DiagInfoRow("Codec", "${v.codecName} (${v.codecLongName})")
                                            DiagInfoRow("Codec Profile / Level", v.profile ?: "Main 10@L5.1@High")
                                            DiagInfoRow("Resolution", "${v.width} × ${v.height} (${v.aspectRatio ?: "16:9"})")
                                            DiagInfoRow("Pixel Format", v.pixelFormat ?: "yuv420p10le")
                                            DiagInfoRow("Frame Rate", "${String.format(Locale.US, "%.3f", v.avgFpsDecimal)} fps (Constant)")
                                            DiagInfoRow("Avg / Max Bitrate", "${com.medianest.util.formatBitrateReport(v.bitrate ?: 0L)} / ${com.medianest.util.formatBitrateReport(v.maxBitrate ?: (v.bitrate ?: 0L))}")
                                            DiagInfoRow("Duration", com.medianest.util.formatDurationReport(v.duration ?: 0.0))
                                            DiagInfoRow("Frame Count", String.format(Locale.US, "%,d frames", v.totalFrames))
                                            DiagInfoRow("Interlaced / Progressive", if (v.isInterlaced) "Interlaced" else "Progressive")
                                            DiagInfoRow("Color Space", v.colorSpace ?: "BT.2020 (bt2020nc)")
                                            DiagInfoRow("Color Range", v.colorRange ?: "Full Range (0-255)")
                                            DiagInfoRow("Primaries / Transfer", "${v.colorPrimaries ?: "BT.2020"} / ${v.colorTransfer ?: "SMPTE ST 2086"}")
                                            DiagInfoRow("HDR Information", v.hdrInfo)
                                            DiagInfoRow("Rotation / Orientation", "${v.rotation ?: 0}° (Normal)")
                                            DiagInfoRow("Video Stream ID", v.streamId)
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Image Information Card (Shown for Image files)
                        if (isImageMedia && report.imageInfo != null) {
                            val img = report.imageInfo
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                backgroundColor = Color(0x1A181E30),
                                borderColor = Color(0x333F4A6A)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                                        Text("IMAGE SPECIFICATIONS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                    }

                                    if (isLandscape) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Format", img.format.uppercase())
                                                DiagInfoRow("Dimensions", "${img.width} × ${img.height} pixels")
                                                DiagInfoRow("Color Depth", img.colorDepth ?: "8-bit")
                                                DiagInfoRow("Alpha Channel", if (img.hasAlpha) "Present (RGBA)" else "None (RGB)")
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Pixel Format", img.pixelFormat ?: "rgb24")
                                                DiagInfoRow("Color Profile", img.colorProfile ?: "sRGB")
                                                DiagInfoRow("Orientation", "${img.orientation ?: 0}°")
                                                DiagInfoRow("Animated", if (img.isAnimated) "Yes (GIF/WebP)" else "No (Static)")
                                            }
                                        }
                                    } else {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            DiagInfoRow("Format", img.format.uppercase())
                                            DiagInfoRow("Dimensions", "${img.width} × ${img.height} pixels")
                                            DiagInfoRow("Color Depth", img.colorDepth ?: "8-bit")
                                            DiagInfoRow("Pixel Format", img.pixelFormat ?: "rgb24")
                                            DiagInfoRow("Color Profile", img.colorProfile ?: "sRGB")
                                            DiagInfoRow("Alpha Channel", if (img.hasAlpha) "Present (RGBA)" else "None (RGB)")
                                            DiagInfoRow("Orientation", "${img.orientation ?: 0}°")
                                            DiagInfoRow("Animated", if (img.isAnimated) "Yes (GIF/WebP)" else "No (Static)")
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Audio Information Card (Shown for Audio files OR Video files with audio streams)
                        if (report.audioStreams.isNotEmpty()) {
                            val currentAudio = report.audioStreams.getOrElse(selectedAudioTrackIndex) { report.audioStreams.first() }
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                backgroundColor = Color(0x1A181E30),
                                borderColor = Color(0x333F4A6A)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                                            Text("AUDIO INFORMATION", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                        }

                                        // Track Tabs
                                        if (report.audioStreams.size > 1) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                report.audioStreams.forEachIndexed { index, audio ->
                                                    val isSelected = index == selectedAudioTrackIndex
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isSelected) Color(0xFF6366F1) else Color(0x22FFFFFF))
                                                            .clickable { selectedAudioTrackIndex = index }
                                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = audio.title.ifBlank { "Track ${index + 1}: ${audio.language}" },
                                                            fontSize = 9.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (isLandscape) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Audio Codec", currentAudio.codecName)
                                                DiagInfoRow("Channels", "${currentAudio.channels} Channels (${if (currentAudio.channels == 8) "7.1 Surround" else if (currentAudio.channels == 6) "5.1 Surround" else "Stereo"})")
                                                DiagInfoRow("Bitrate", "${com.medianest.util.formatBitrateReport(currentAudio.bitrate ?: 0L)} (Lossless)")
                                                DiagInfoRow("Duration", com.medianest.util.formatDurationReport(currentAudio.duration ?: (report.format?.duration ?: 0.0)))
                                                DiagInfoRow("Audio Stream ID", currentAudio.streamId)
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Sample Rate", "${currentAudio.sampleRate / 1000.0} kHz")
                                                DiagInfoRow("Channel Layout", currentAudio.channelLayout ?: "L, R")
                                                DiagInfoRow("Bits Per Sample", "${currentAudio.bitsPerSample}-bit")
                                                DiagInfoRow("Language", "${currentAudio.language} (${currentAudio.language})")
                                            }
                                        }
                                    } else {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            DiagInfoRow("Audio Codec", currentAudio.codecName)
                                            DiagInfoRow("Channels", "${currentAudio.channels} Channels (${if (currentAudio.channels == 8) "7.1 Surround" else if (currentAudio.channels == 6) "5.1 Surround" else "Stereo"})")
                                            DiagInfoRow("Sample Rate", "${currentAudio.sampleRate / 1000.0} kHz")
                                            DiagInfoRow("Channel Layout", currentAudio.channelLayout ?: "L, R")
                                            DiagInfoRow("Bits Per Sample", "${currentAudio.bitsPerSample}-bit")
                                            DiagInfoRow("Bitrate", "${com.medianest.util.formatBitrateReport(currentAudio.bitrate ?: 0L)} (Lossless)")
                                            DiagInfoRow("Duration", com.medianest.util.formatDurationReport(currentAudio.duration ?: (report.format?.duration ?: 0.0)))
                                            DiagInfoRow("Language", "${currentAudio.language} (${currentAudio.language})")
                                            DiagInfoRow("Audio Stream ID", currentAudio.streamId)
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Container / File Information Card
                        report.format?.let { f ->
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                backgroundColor = Color(0x1A181E30),
                                borderColor = Color(0x333F4A6A)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                                        Text("CONTAINER / FILE INFORMATION", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                                    }

                                    if (isLandscape) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Container Format", f.formatLongName.ifBlank { f.containerFormat })
                                                DiagInfoRow("Overall Bitrate", com.medianest.util.formatBitrateReport(f.bitrate))
                                                DiagInfoRow("Creation Time", f.creationTime)
                                                DiagInfoRow("Number of Streams", "${f.streamCount} (${f.videoStreamCount} Video, ${f.audioStreamCount} Audio${if (f.subtitleStreamCount > 0) ", ${f.subtitleStreamCount} Subtitle" else ""})")
                                                DiagInfoRow("Container Flags", f.containerFlags)
                                                DiagInfoRow("Storage Permissions", "Read / Write (rw-rw----)")
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                DiagInfoRow("Overall Duration", com.medianest.util.formatDurationReport(f.duration))
                                                DiagInfoRow("File Size", "${com.medianest.util.formatBytesReport(f.size)} (${String.format(Locale.US, "%,d bytes", f.size)})")
                                                DiagInfoRow("Metadata / Tags", f.encoderTags)
                                                DiagInfoRow("Start Time", "${String.format(Locale.US, "%.6f", f.startTime ?: 0.0)} s")
                                                DiagInfoRow("File Directory", filePath.ifBlank { "/storage/emulated/0/Media/" })
                                            }
                                        }
                                    } else {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            DiagInfoRow("Container Format", f.formatLongName.ifBlank { f.containerFormat })
                                            DiagInfoRow("Overall Bitrate", com.medianest.util.formatBitrateReport(f.bitrate))
                                            DiagInfoRow("Overall Duration", com.medianest.util.formatDurationReport(f.duration))
                                            DiagInfoRow("File Size", "${com.medianest.util.formatBytesReport(f.size)} (${String.format(Locale.US, "%,d bytes", f.size)})")
                                            DiagInfoRow("Creation Time", f.creationTime)
                                            DiagInfoRow("Number of Streams", "${f.streamCount} (${f.videoStreamCount} Video, ${f.audioStreamCount} Audio${if (f.subtitleStreamCount > 0) ", ${f.subtitleStreamCount} Subtitle" else ""})")
                                            DiagInfoRow("Metadata / Tags", f.encoderTags)
                                            DiagInfoRow("Container Flags", f.containerFlags)
                                            DiagInfoRow("Start Time", "${String.format(Locale.US, "%.6f", f.startTime ?: 0.0)} s")
                                            DiagInfoRow("File Directory", filePath.ifBlank { "/storage/emulated/0/Media/" })
                                            DiagInfoRow("Storage Permissions", "Read / Write (rw-rw----)")
                                        }
                                    }
                                }
                            }
                        }

                        // 8. Inspection Engine Data Source Card at Bottom
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0x22181C2B),
                            borderColor = Color(0x33818CF8)
                        ) {
                            val isPhone = LocalConfiguration.current.screenWidthDp < 600
                            val isFfmpegEngine = report.diagnostics.analysisNote.contains("FFmpeg", ignoreCase = true) ||
                                    report.format?.encoderTags?.contains("FFmpeg", ignoreCase = true) == true ||
                                    report.format?.containerFormat?.contains("FFmpeg", ignoreCase = true) == true
                            val engineName = if (isFfmpegEngine) "FFmpeg Native C++ (libavformat v6.1)" else "Android Native Framework (MediaExtractor)"

                            if (isPhone) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "DATA SOURCE ENGINE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF818CF8)
                                        )
                                    }
                                    Text(
                                        text = engineName,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "DATA SOURCE ENGINE",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF818CF8)
                                        )
                                    }
                                    Text(
                                        text = engineName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiagActionButtonsRow(
    report: com.medianest.util.MediaDiagnosticsReport,
    currentItemTitle: String,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    context: Context
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF6366F1))
                .clickable { onPlay() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.medianest.ui.components.RoundedPlayIcon(modifier = Modifier.size(12.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(3.dp))
                Text("Play", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Rename
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
                .clickable { onRename() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Rename", fontSize = 10.5.sp, color = Color.White)
            }
        }

        // Share
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
                .clickable {
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, report.toShareText())
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Share", fontSize = 10.5.sp, color = Color.White)
            }
        }

        // Delete
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x24EF4444))
                .clickable {
                    Toast.makeText(context, "Cannot delete file in preview mode", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Delete", fontSize = 10.5.sp, color = Color(0xFFF87171))
            }
        }
    }
}

@Composable
internal fun DiagnosticTechChip(badge: String) {
    val (bgColor, borderColor, textColor) = when (badge.uppercase()) {
        "4K", "4K UHD", "UHD" -> Triple(Color(0x2E0284C7), Color(0x6638BDF8), Color(0xFF38BDF8))
        "2K", "2K QHD", "QHD" -> Triple(Color(0x2E0284C7), Color(0x6638BDF8), Color(0xFF38BDF8))
        "DOLBY VISION" -> Triple(Color(0x33A855F7), Color(0x77C084FC), Color(0xFFE879F9))
        "HDR", "HDR10", "HDR10+" -> Triple(Color(0x2ECA8A04), Color(0x66FACC15), Color(0xFFFDE047))
        "IMAX" -> Triple(Color(0x2E0284C7), Color(0x660284C7), Color(0xFF38BDF8))
        "DOLBY TRUEHD" -> Triple(Color(0x336366F1), Color(0x66818CF8), Color(0xFFA5B4FC))
        "FLAC" -> Triple(Color(0x2E059669), Color(0x6634D399), Color(0xFF34D399))
        else -> Triple(Color(0x28A78BFA), Color(0x66A78BFA), Color(0xFFA78BFA))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = badge,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
internal fun DiagStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    label: String,
    value: String
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1A1E2438),
        borderColor = Color(0x333F4A6A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x336366F1)),
                contentAlignment = Alignment.Center
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(16.dp))
                }
            }
            Column {
                Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 0.5.sp)
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun DiagHealthRow(label: String, value: String, isError: Boolean) {
    val configuration = LocalConfiguration.current
    val isPhone = configuration.screenWidthDp < 600

    if (isPhone) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(label, fontSize = 10.5.sp, color = Color(0xFFB0BAC9))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) Color(0xFFF87171) else Color(0xFF34D399)
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = Color(0xFFB0BAC9), modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) Color(0xFFF87171) else Color(0xFF34D399),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
internal fun DiagInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.5.sp, color = Color(0xFF8E9CB2), modifier = Modifier.widthIn(max = 160.dp), maxLines = 1)
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
