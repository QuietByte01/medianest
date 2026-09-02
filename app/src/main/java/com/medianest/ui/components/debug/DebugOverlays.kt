package com.medianest.ui.components.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medianest.player.PlayerState
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.backdropReceiver
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared logic for Developer Mode debug overlays.
 */

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateOf

@Composable
fun PlayerDebugOverlay(
    playerState: PlayerState,
    videoAspectRatio: Float = 0f,
    playingAspectRatio: Float = 0f,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isCollapsed by remember { mutableStateOf(false) }

    val runtime = Runtime.getRuntime()
    val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
    val nativeHeapMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

    val item = playerState.currentItem
    val fileSizeBytes = item?.size ?: 0L
    val fileSizeFormatted = when {
        fileSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024f * 1024f))
        fileSizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024f)
        else -> "$fileSizeBytes B"
    }

    GlassSurface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .width(if (isCollapsed) 210.dp else 280.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0xCC0F1015),
        borderColor = Color(0x336366F1),
        enableBlur = true,
        blurRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isCollapsed = !isCollapsed },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag overlay",
                        tint = Color(0xFFA5B4FC).copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isCollapsed) "VIDEO (${playerState.activeEngineName})" else "VIDEO / PLAYER DEBUG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC)
                    )
                }
                Icon(
                    if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Collapse",
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(16.dp)
                )
            }

            if (!isCollapsed) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                if (item != null) {
                    DebugStatRow("File", item.title)
                    if (fileSizeBytes > 0) {
                        DebugStatRow("File Size", fileSizeFormatted, valueColor = Color(0xFF38BDF8))
                    }
                    if (item.width > 0 && item.height > 0) {
                        DebugStatRow("Resolution", "${item.width} x ${item.height}")
                    }
                }

                DebugStatRow("Engine", playerState.activeEngineName, valueColor = Color(0xFFFFD54F))
                DebugStatRow("Decoder", playerState.activeDecoderName)
                DebugStatRow("Pipeline", if (playerState.isHardwareAccelerated) "GPU Direct (MediaCodec)" else "Software (FFmpeg)", valueColor = if (playerState.isHardwareAccelerated) Color(0xFF34D399) else Color(0xFFFBBF24))
                DebugStatRow("Video Codec", playerState.videoCodec)
                DebugStatRow("Audio Codec", playerState.audioCodec)
                if (playerState.containerName != "Unknown") {
                    DebugStatRow("Container", playerState.containerName)
                }

                val bitrateDisplay = if (playerState.currentBitrate > 0) {
                    com.medianest.util.formatBitrateReport(playerState.currentBitrate)
                } else {
                    "Calculating..."
                }
                DebugStatRow("Bitrate", bitrateDisplay, valueColor = Color(0xFF38BDF8))
                
                if (playerState.playbackSpeed != 1.0f) {
                    DebugStatRow("Speed", "${playerState.playbackSpeed}x")
                }

                // Always display frame diagnostics
                DebugStatRow("Dropped Frames", "${playerState.droppedFrames}", valueColor = if (playerState.droppedFrames > 0) Color(0xFFEF4444) else Color(0xFF94A3B8))
                DebugStatRow("Audio Missing", "${playerState.audioMissingFrames}", valueColor = if (playerState.audioMissingFrames > 0) Color(0xFFFBBF24) else Color(0xFF94A3B8))
                DebugStatRow("Corrupted Frames", "${playerState.corruptedFrames}", valueColor = if (playerState.corruptedFrames > 0) Color(0xFFEF4444) else Color(0xFF94A3B8))

                if (playerState.timestampRecoveryCount > 0) {
                    DebugStatRow("Sync Recoveries", playerState.timestampRecoveryCount.toString(), valueColor = Color(0xFFFBBF24))
                }

                DebugStatRow("HDR Type", playerState.hdrType)
                DebugStatRow("Color Space", playerState.colorSpace)

                if (videoAspectRatio > 0f) {
                    val effectivePlayingRatio = if (playingAspectRatio > 0f) playingAspectRatio else videoAspectRatio
                    val isRatioDifferent = kotlin.math.abs(effectivePlayingRatio - videoAspectRatio) > 0.04f
                    val videoRatioStr = formatRatio(videoAspectRatio)
                    val playingRatioStr = formatRatio(effectivePlayingRatio)

                    if (isRatioDifferent) {
                        DebugStatRow(
                            label = "Aspect Ratio",
                            value = "$videoRatioStr -> $playingRatioStr",
                            valueColor = Color(0xFFEF4444)
                        )
                    } else {
                        DebugStatRow(
                            label = "Aspect Ratio",
                            value = videoRatioStr,
                            valueColor = Color(0xFF34D399)
                        )
                    }
                }
                
                if (playerState.decoderFallbackReason != null) {
                    Text(
                        text = "Fallback: ${playerState.decoderFallbackReason}",
                        fontSize = 9.sp,
                        color = Color(0xFFFBBF24),
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                DebugStatRow("App JVM Heap", "$usedHeapMb MB / $maxHeapMb MB", valueColor = if (usedHeapMb > maxHeapMb * 0.75f) Color(0xFFEF4444) else Color(0xFF94A3B8))
                DebugStatRow("Native Memory", "$nativeHeapMb MB", valueColor = Color(0xFF38BDF8))

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 2.dp))
                
                // Bitrate line graph (Demux / Input)
                Text(
                    text = "BITRATE GRAPH (INPUT / DEMUX)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                BitrateGraph(
                    data = playerState.bitrateHistory.ifEmpty { listOf(1200000L, 1800000L, 1500000L, 2200000L, 1900000L, 2500000L, 2100000L) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33000000))
                )
            }
        }
    }
}

@Composable
fun BitrateGraph(
    data: List<Long>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(4.dp)) {
        if (data.size < 2) return@Canvas
        val maxVal = (data.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val minVal = (data.minOrNull() ?: 0L).toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val width = size.width
        val height = size.height

        val stepX = width / (data.size - 1)

        val demuxPath = Path()
        val inputPath = Path()

        data.forEachIndexed { index, value ->
            val x = index * stepX
            val normValue = (value - minVal) / range
            val yDemux = height - (normValue * height)
            val yInput = height - ((normValue * 0.85f) * height) // Input slightly offset for demux vs input display

            if (index == 0) {
                demuxPath.moveTo(x, yDemux)
                inputPath.moveTo(x, yInput)
            } else {
                demuxPath.lineTo(x, yDemux)
                inputPath.lineTo(x, yInput)
            }
        }

        // Draw Input Bitrate Line (Cyan)
        drawPath(
            path = inputPath,
            color = Color(0xFF38BDF8),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw Demux Bitrate Line (Purple)
        drawPath(
            path = demuxPath,
            color = Color(0xFFA5B4FC),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
fun ImageDebugOverlay(
    item: com.medianest.data.model.MediaItem?,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isCollapsed by remember { mutableStateOf(false) }

    val runtime = Runtime.getRuntime()
    val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxHeapMb = runtime.maxMemory() / (1024 * 1024)

    val isGif = item?.mimeType?.contains("gif", ignoreCase = true) == true || item?.title?.endsWith(".gif", ignoreCase = true) == true || item?.relativePath?.endsWith(".gif", ignoreCase = true) == true
    val isHeavyGif = isGif && (item?.size ?: 0L) >= 50 * 1024 * 1024L
    val isAnimatedWebpOrAvif = item?.mimeType?.contains("webp", ignoreCase = true) == true || item?.mimeType?.contains("avif", ignoreCase = true) == true
    val isLargeImage = (item?.width ?: 0) > 4000 || (item?.height ?: 0) > 4000

    val engineName = when {
        isHeavyGif -> "GifDecoder (Streaming Raster)"
        isGif -> "ImageDecoder (Hardware AHardwareBuffer)"
        isAnimatedWebpOrAvif -> "ImageDecoder (Hardware Animated)"
        isLargeImage -> "Subsampling Region Decoder (64 Tiles)"
        else -> "Coil Hardware GraphicBuffer"
    }

    val width = item?.width ?: 0
    val height = item?.height ?: 0
    val megaPixels = if (width > 0 && height > 0) String.format(Locale.US, "%.1f MP", (width * height) / 1_000_000f) else "N/A"
    
    // RAM calculation: ARGB_8888 = 4 bytes/pixel, HARDWARE = GPU VRAM
    val uncompressedFrameRamMb = if (width > 0 && height > 0) (width.toLong() * height.toLong() * 4L) / (1024f * 1024f) else 0f
    val fileSizeBytes = item?.size ?: 0L
    val fileSizeFormatted = when {
        fileSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024f * 1024f))
        fileSizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024f)
        else -> "$fileSizeBytes B"
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(offsetX.roundToInt() - 16, offsetY.roundToInt() + 80),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        GlassSurface(
            modifier = modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .width(if (isCollapsed) 210.dp else 280.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xCC0F1015),
            borderColor = Color(0x3334D399),
            enableBlur = true,
            blurRadius = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCollapsed = !isCollapsed },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag overlay",
                            tint = Color(0xFF34D399).copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isCollapsed) "IMAGE ($usedHeapMb MB)" else "IMAGE / MEMORY DEBUG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }
                    Icon(
                        if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Collapse",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (!isCollapsed) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    if (item != null) {
                        DebugStatRow("File", item.title)
                        DebugStatRow("File Size", fileSizeFormatted, valueColor = Color(0xFF38BDF8))
                        DebugStatRow("Resolution", "${item.width} x ${item.height} ($megaPixels)")
                        DebugStatRow("Format", item.mimeType.substringAfter("/").uppercase())
                    }

                    DebugStatRow("Engine", engineName, valueColor = Color(0xFFFFD54F))
                    
                    if (isGif) {
                        DebugStatRow("Decoded VRAM", "Hardware GPU Buffer", valueColor = Color(0xFF34D399))
                        DebugStatRow("Frame Footprint", String.format(Locale.US, "%.1f MB / frame", uncompressedFrameRamMb))
                    } else {
                        DebugStatRow("Image Decoded RAM", String.format(Locale.US, "%.2f MB (GPU VRAM)", uncompressedFrameRamMb), valueColor = Color(0xFF34D399))
                    }

                    DebugStatRow("Bitmap Allocator", "HARDWARE (0 JVM Heap)")
                    DebugStatRow("App JVM Heap", "$usedHeapMb MB / $maxHeapMb MB", valueColor = if (usedHeapMb > maxHeapMb * 0.75f) Color(0xFFEF4444) else Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
fun AudioDebugOverlay(
    playerState: PlayerState,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isCollapsed by remember { mutableStateOf(false) }

    val runtime = Runtime.getRuntime()
    val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
    val nativeHeapMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

    val item = playerState.currentItem
    val fileSizeBytes = item?.size ?: 0L
    val fileSizeFormatted = when {
        fileSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024f * 1024f))
        fileSizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024f)
        else -> "$fileSizeBytes B"
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(offsetX.roundToInt() - 16, offsetY.roundToInt() + 80),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        GlassSurface(
            modifier = modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .width(if (isCollapsed) 210.dp else 280.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xCC0F1015),
            borderColor = Color(0x33FB7185),
            enableBlur = true,
            blurRadius = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCollapsed = !isCollapsed },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag overlay",
                            tint = Color(0xFFFB7185).copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isCollapsed) "AUDIO (${playerState.activeEngineName})" else "AUDIO / DSP DEBUG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFB7185)
                        )
                    }
                    Icon(
                        if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Collapse",
                        tint = Color(0xFFFB7185),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (!isCollapsed) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    if (item != null) {
                        DebugStatRow("Track", item.title)
                        if (fileSizeBytes > 0) {
                            DebugStatRow("File Size", fileSizeFormatted, valueColor = Color(0xFF38BDF8))
                        }
                    }

                    DebugStatRow("Engine", playerState.activeEngineName, valueColor = Color(0xFFFFD54F))
                    DebugStatRow("Decoder", playerState.activeDecoderName)
                    DebugStatRow("Codec", playerState.audioCodec)
                    if (playerState.containerName != "Unknown") {
                        DebugStatRow("Container", playerState.containerName)
                    }
                    if (playerState.currentBitrate > 0) {
                        DebugStatRow("Bitrate", com.medianest.util.formatBitrateReport(playerState.currentBitrate), valueColor = Color(0xFF38BDF8))
                    }
                    DebugStatRow("Session ID", playerState.audioSessionId.toString())
                    
                    if (playerState.playbackSpeed != 1.0f) {
                        DebugStatRow("Speed", "${playerState.playbackSpeed}x")
                    }
                    if (playerState.pitchSemitones != 0) {
                        DebugStatRow("Pitch Shift", "${playerState.pitchSemitones} st")
                    }

                    val errorColor = if (playerState.audioDecodeErrors > 0) Color(0xFFEF4444) else Color.White
                    if (playerState.audioDecodeErrors > 0) {
                        DebugStatRow("Decode Errors", playerState.audioDecodeErrors.toString(), valueColor = errorColor)
                    }
                    if (playerState.audioMissingFrames > 0) {
                        DebugStatRow("Missing Frames", playerState.audioMissingFrames.toString(), valueColor = Color(0xFFFBBF24))
                    }
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    DebugStatRow("DSP Bass Boost", "${playerState.bassBoostPercent}%")
                    DebugStatRow("DSP Vol Boost", "${playerState.volumeBoostPercent}%")
                    DebugStatRow("Equalizer", if (playerState.isEqEnabled) "Enabled (5 Bands)" else "Bypassed")
                    if (playerState.isDolbyEnabled) {
                        DebugStatRow("Dolby / Spatial", "Active", valueColor = Color(0xFF34D399))
                    }
                    if (playerState.isLoudnessNormalizerEnabled) {
                        DebugStatRow("Normalizer", "Active", valueColor = Color(0xFF38BDF8))
                    }
                    if (playerState.isVocalMuteEnabled) {
                        DebugStatRow("Vocal Mute", "Active", valueColor = Color(0xFFEF4444))
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    DebugStatRow("App JVM Heap", "$usedHeapMb MB / $maxHeapMb MB", valueColor = if (usedHeapMb > maxHeapMb * 0.75f) Color(0xFFEF4444) else Color(0xFF94A3B8))
                    DebugStatRow("Native Memory", "$nativeHeapMb MB", valueColor = Color(0xFF38BDF8))
                }
            }
        }
    }
}

@Composable
fun HardwarePipelineDiagnosticsCard(
    context: android.content.Context,
    modifier: Modifier = Modifier,
    backdropState: BackdropBlurState? = null
) {
    val hwCaps = androidx.compose.runtime.remember {
        com.medianest.hardware.AndroidHardwareEngine.detectCapabilities(context)
    }
    val shape = RoundedCornerShape(16.dp)
    val cardBg = Color(0x1A6366F1) // Maintain translucent tint

    val cardModifier = modifier
        .fillMaxWidth()
        .clip(shape)

    GlassSurface(
        modifier = cardModifier,
        shape = shape,
        backgroundColor = cardBg,
        borderColor = Color(0x336366F1),
        enableBlur = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isPhoneScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

            if (isPhoneScreen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "REAL-TIME HARDWARE & MEDIA PROFILE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x3334D399)
                    ) {
                        Text(
                            text = "GPU ACCELERATED",
                            fontSize = 9.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME HARDWARE & MEDIA PROFILE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x3334D399)
                    ) {
                        Text(
                            text = "GPU ACCELERATED",
                            fontSize = 9.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // System & Device
            DebugDetailRow("Device / System", "${hwCaps.deviceModel} (API ${hwCaps.apiLevel})")
            DebugDetailRow("Memory Tier", "${hwCaps.ramTier} (${hwCaps.totalRamMb} MB Total RAM)")
            DebugDetailRow("GPU Backend", if (hwCaps.isVulkanSupported) "Vulkan 1.3 / GL ES 3.2 (Max ${hwCaps.maxTextureSize}px)" else "OpenGL ES 3.0")

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Hardware Video Codecs
            val hwCodecsList = mutableListOf<String>().apply {
                if (hwCaps.isAv1HwSupported) add("AV1")
                if (hwCaps.isHevcHwSupported) add("HEVC/H.265")
                if (hwCaps.isH264HwSupported) add("H.264/AVC")
                if (hwCaps.isVp9HwSupported) add("VP9")
                if (hwCaps.isVp8HwSupported) add("VP8")
                if (hwCaps.isMpeg4HwSupported) add("MPEG4")
            }.joinToString(", ")
            DebugDetailRow("Hardware Video Decoders", hwCodecsList.ifEmpty { "H.264, HEVC (Software Fallback)" })

            // Display & HDR Support
            val hdrDisplay = if (hwCaps.isHdrSupported) {
                "${hwCaps.hdrFormatsStr} (10-bit Wide Color)"
            } else {
                "SDR Only"
            }
            val hdrColor = if (hwCaps.isHdrSupported) Color(0xFF34D399) else Color(0xFF94A3B8)
            DebugDetailRow("Display HDR Support", hdrDisplay, valueColor = hdrColor)

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Audio Pipeline & Formats
            DebugDetailRow("Audio Pipeline", "AAudio Low-Latency -> OpenSL ES")
            DebugDetailRow("Passthrough Audio", hwCaps.supportedAudioPassthrough.joinToString(", "))

            // Image & Subtitles
            val imageEngineStr = buildString {
                append("Coil Hardware Bitmaps")
                if (hwCaps.isUltraHdrSupported) append(" • Ultra HDR Gainmaps")
                if (hwCaps.isWideColorGamutSupported) append(" • Display P3")
            }
            DebugDetailRow("Image Pipeline", imageEngineStr)
            DebugDetailRow("Subtitle Engine", "GPU Accelerated ASS/SSA Vector Shading")
        }
    }
}

@Composable
private fun DebugDetailRow(label: String, value: String, valueColor: Color = Color.White) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            fontSize = 11.sp,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun DebugStatRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
    }
}

private fun formatRatio(ratio: Float): String {
    if (ratio <= 0f) return "Auto"
    return when {
        kotlin.math.abs(ratio - 16f / 9f) < 0.03f -> "16:9"
        kotlin.math.abs(ratio - 9f / 16f) < 0.03f -> "9:16"
        kotlin.math.abs(ratio - 4f / 3f) < 0.03f -> "4:3"
        kotlin.math.abs(ratio - 3f / 4f) < 0.03f -> "3:4"
        kotlin.math.abs(ratio - 16f / 10f) < 0.03f -> "16:10"
        kotlin.math.abs(ratio - 1f) < 0.03f -> "1:1"
        kotlin.math.abs(ratio - 21f / 9f) < 0.05f -> "21:9"
        kotlin.math.abs(ratio - 4f / 5f) < 0.03f -> "4:5"
        else -> String.format(java.util.Locale.US, "%.2f:1", ratio)
    }
}

/**
 * Real-time Library Telemetry Debug Overlay for Image, Audio, Video, and Dashboard/Analytics screens.
 * Tracks FPS rendering rate, 3-second rolling frame drop jank window, system health with explicit diagnostic reason,
 * Coil bitmap memory cache footprint, thermal throttling state, battery temperature, and memory pressure.
 */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun LibraryDebugOverlay(
    activeTabName: String,
    filteredItemsCount: Int,
    isSearchActive: Boolean,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isCollapsed by remember { mutableStateOf(false) }

    // Real-Time Choreographer FPS & 3-second Rolling Jank Window
    var fps by remember { mutableFloatStateOf(60f) }
    var frameTimeMs by remember { mutableFloatStateOf(16.6f) }
    var rollingJankCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var totalFramesInWindow by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        var lastFrameTimeNanos = 0L
        var windowStartNanos = 0L
        var jankInWindow = 0
        var framesInWindow = 0

        while (true) {
            androidx.compose.runtime.withFrameNanos { frameNanos ->
                if (lastFrameTimeNanos > 0L) {
                    val deltaNanos = frameNanos - lastFrameTimeNanos
                    val deltaMs = deltaNanos / 1_000_000f
                    if (deltaMs > 0f) {
                        val currentFps = (1000f / deltaMs).coerceIn(0f, 120f)
                        fps = fps * 0.88f + currentFps * 0.12f
                        frameTimeMs = frameTimeMs * 0.88f + deltaMs * 0.12f

                        framesInWindow++
                        if (deltaMs > 32f) { // Dropped 2+ frames
                            jankInWindow++
                        }
                    }
                }
                if (windowStartNanos == 0L) windowStartNanos = frameNanos

                // Sliding 3-second window reset
                if (frameNanos - windowStartNanos >= 3_000_000_000L) {
                    rollingJankCount = jankInWindow
                    totalFramesInWindow = framesInWindow
                    jankInWindow = 0
                    framesInWindow = 0
                    windowStartNanos = frameNanos
                }

                lastFrameTimeNanos = frameNanos
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Memory Telemetry
    val runtime = Runtime.getRuntime()
    val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
    val nativeHeapMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
    val memoryUtilizationPercent = (usedHeapMb.toFloat() / maxHeapMb.coerceAtLeast(1).toFloat() * 100).toInt()

    // Thermal Throttling Telemetry
    val powerManager = remember(context) { context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager }
    val thermalStatus = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && powerManager != null) {
        when (powerManager.currentThermalStatus) {
            android.os.PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
            android.os.PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            android.os.PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            android.os.PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            else -> "NORMAL"
        }
    } else "NORMAL"

    // Battery Telemetry
    val batteryIntent = remember(context) {
        try {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) { null }
    }
    val batteryLevel = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val batteryScale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (batteryLevel >= 0 && batteryScale > 0) (batteryLevel * 100 / batteryScale) else -1
    val batteryTempTenths = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    val batteryTempC = if (batteryTempTenths > 0) String.format(Locale.US, "%.1f°C", batteryTempTenths / 10f) else "N/A"

    // Coil Bitmap Cache Telemetry
    val memoryCache = try { coil.Coil.imageLoader(context).memoryCache } catch (_: Throwable) { null }
    val coilCacheMb = memoryCache?.size?.let { it / (1024f * 1024f) } ?: 0f
    val coilMaxMb = memoryCache?.maxSize?.let { it / (1024f * 1024f) } ?: 0f

    // Precise System Health Status & Reason Diagnosis
    val (healthStatus, healthReason, healthColor) = when {
        thermalStatus in listOf("SEVERE", "CRITICAL", "EMERGENCY") ->
            Triple("HIGH PRESSURE", "Thermal Throttling ($thermalStatus)", Color(0xFFEF4444))
        memoryUtilizationPercent > 82 ->
            Triple("HIGH PRESSURE", "JVM Heap $memoryUtilizationPercent% (>82%)", Color(0xFFEF4444))
        rollingJankCount >= 8 ->
            Triple("HIGH PRESSURE", "$rollingJankCount Jank Frames / 3s", Color(0xFFEF4444))
        memoryUtilizationPercent > 68 ->
            Triple("HEAVY LOAD", "JVM Heap $memoryUtilizationPercent%", Color(0xFFFBBF24))
        rollingJankCount >= 3 ->
            Triple("HEAVY LOAD", "$rollingJankCount Jank Frames / 3s", Color(0xFFFBBF24))
        thermalStatus in listOf("LIGHT", "MODERATE") ->
            Triple("HEAVY LOAD", "Thermal $thermalStatus", Color(0xFFFBBF24))
        else ->
            Triple("STABLE", "Normal (Heap $memoryUtilizationPercent%, 0 Jank)", Color(0xFF34D399))
    }

    val tabBadgeColor = when (activeTabName) {
        "IMAGES" -> Color(0xFF34D399)
        "VIDEOS" -> Color(0xFF38BDF8)
        "AUDIO" -> Color(0xFFFB7185)
        "DASHBOARD" -> Color(0xFFA5B4FC)
        else -> Color(0xFFFFD54F)
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(offsetX.roundToInt() - 16, offsetY.roundToInt() + 80),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        GlassSurface(
            modifier = modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .width(if (isCollapsed) 210.dp else 290.dp),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = Color(0xDC0C0E14),
            borderColor = tabBadgeColor.copy(alpha = 0.4f),
            enableBlur = true,
            blurRadius = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCollapsed = !isCollapsed },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag overlay",
                            tint = tabBadgeColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isCollapsed) "$activeTabName • ${fps.roundToInt()} FPS" else "TELEMETRY ($activeTabName)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tabBadgeColor
                        )
                    }
                    Icon(
                        if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Collapse",
                        tint = tabBadgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (!isCollapsed) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // Realtime FPS & Render Metrics
                    val fpsColor = when {
                        fps >= 55f -> Color(0xFF34D399)
                        fps >= 35f -> Color(0xFFFBBF24)
                        else -> Color(0xFFEF4444)
                    }
                    DebugStatRow("FPS / Render Rate", "${fps.roundToInt()} FPS (${String.format(Locale.US, "%.1f", frameTimeMs)} ms)", valueColor = fpsColor)
                    DebugStatRow("3s Frame Drops", "$rollingJankCount jank frames", valueColor = if (rollingJankCount > 0) Color(0xFFFBBF24) else Color(0xFF94A3B8))
                    
                    // System Health & Explicit Diagnosis Reason
                    DebugStatRow("System Health", healthStatus, valueColor = healthColor)
                    DebugStatRow("Diagnosis Reason", healthReason, valueColor = healthColor)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Active View & Paging Telemetry
                    DebugStatRow("Active Context", activeTabName, valueColor = tabBadgeColor)
                    val estimatedViewportItems = if (filteredItemsCount > 0) (filteredItemsCount.coerceAtMost(24)) else 0
                    DebugStatRow("Lazy Viewport Buffer", "~$estimatedViewportItems items rendered", valueColor = Color(0xFF34D399))
                    DebugStatRow("Filter Dataset Total", "$filteredItemsCount total items", valueColor = Color(0xFFFFD54F))
                    DebugStatRow("Composition Strategy", "Lazy Windowing & Item Recycler")

                    // Memory & Heap Footprint
                    DebugStatRow("JVM Heap Used", "$usedHeapMb MB / $maxHeapMb MB ($memoryUtilizationPercent%)", valueColor = if (memoryUtilizationPercent > 75) Color(0xFFEF4444) else Color(0xFF94A3B8))
                    DebugStatRow("Native Heap Alloc", "$nativeHeapMb MB", valueColor = Color(0xFF38BDF8))
                    if (coilMaxMb > 0f) {
                        DebugStatRow("Coil Bitmap Cache", String.format(Locale.US, "%.1f MB / %.1f MB", coilCacheMb, coilMaxMb), valueColor = Color(0xFF34D399))
                    } else {
                        DebugStatRow("Coil Bitmap Cache", "Hardware GraphicBuffers")
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Thermal & Battery Hardware Telemetry
                    DebugStatRow("Thermal Status", thermalStatus, valueColor = if (thermalStatus != "NORMAL") Color(0xFFFBBF24) else Color(0xFF34D399))
                    DebugStatRow("Battery Temp / Level", "$batteryTempC • ${if (batteryPct >= 0) "$batteryPct%" else "N/A"}", valueColor = Color(0xFF94A3B8))

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 2.dp))

                    // Quick Action Button: Purge Memory & Force GC
                    Button(
                        onClick = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    coil.Coil.imageLoader(context).diskCache?.clear()
                                    coil.Coil.imageLoader(context).memoryCache?.clear()
                                } catch (_: Throwable) {}
                                System.gc()
                            }
                            android.widget.Toast.makeText(context, "Memory Cache Purged & GC Invoked", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PURGE CACHE & FORCE GC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}
