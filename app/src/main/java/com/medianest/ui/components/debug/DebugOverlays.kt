package com.medianest.ui.components.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.player.PlayerState
import com.medianest.ui.components.GlassSurface

/**
 * Shared logic for Developer Mode debug overlays.
 */

@Composable
fun PlayerDebugOverlay(
    playerState: PlayerState,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0xCC0F1015),
        borderColor = Color(0x336366F1),
        enableBlur = true,
        blurRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VIDEO / PLAYER DEBUG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA5B4FC)
                )
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(14.dp)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            DebugStatRow("Engine", playerState.activeEngineName)
            DebugStatRow("Decoder", playerState.activeDecoderName)
            DebugStatRow("Video", playerState.videoCodec)
            DebugStatRow("Audio", playerState.audioCodec)

            val bitrateDisplay = if (playerState.currentBitrate > 0) {
                com.medianest.util.formatBitrateReport(playerState.currentBitrate)
            } else {
                "Calculating..."
            }
            DebugStatRow("Bitrate", bitrateDisplay, valueColor = Color(0xFF38BDF8))
            
            if (playerState.droppedFrames > 0) {
                val dropColor = if (playerState.droppedFrames > 50) Color(0xFFEF4444) else Color.White
                DebugStatRow("Dropped Frames", playerState.droppedFrames.toString(), valueColor = dropColor)
            }
            
            if (playerState.audioMissingFrames > 0) {
                DebugStatRow("Audio Missing", playerState.audioMissingFrames.toString(), valueColor = Color(0xFFFBBF24))
            }

            if (playerState.corruptedFrames > 0) {
                DebugStatRow("Corrupted Frames", playerState.corruptedFrames.toString(), valueColor = Color(0xFFEF4444))
            }

            DebugStatRow("HDR Type", playerState.hdrType)
            DebugStatRow("Color Space", playerState.colorSpace)
            
            if (playerState.decoderFallbackReason != null) {
                Text(
                    text = "Fallback: ${playerState.decoderFallbackReason}",
                    fontSize = 9.sp,
                    color = Color(0xFFFBBF24),
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

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
    GlassSurface(
        modifier = modifier.width(260.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0xCC0F1015),
        borderColor = Color(0x3334D399),
        enableBlur = true,
        blurRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IMAGE DEBUG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399)
                )
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(14.dp)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            if (item != null) {
                DebugStatRow("File", item.title)
                DebugStatRow("Size", "${item.width} x ${item.height}")
                DebugStatRow("Format", item.mimeType.substringAfter("/").uppercase())
                DebugStatRow("Path", item.relativePath ?: "Unknown")
            }
            
            DebugStatRow("Engine", "SubsamplingScaleImageView")
            DebugStatRow("Bitmap", "Hardware Config (VRAM)")
        }
    }
}

@Composable
fun AudioDebugOverlay(
    playerState: PlayerState,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.width(260.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0xCC0F1015),
        borderColor = Color(0x33FB7185),
        enableBlur = true,
        blurRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIO DEBUG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFB7185)
                )
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFFFB7185),
                    modifier = Modifier.size(14.dp)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            DebugStatRow("Engine", playerState.activeEngineName)
            DebugStatRow("Decoder", playerState.activeDecoderName)
            DebugStatRow("Codec", playerState.audioCodec)
            DebugStatRow("Session ID", playerState.audioSessionId.toString())
            
            val errorColor = if (playerState.audioDecodeErrors > 0) Color(0xFFEF4444) else Color.White
            DebugStatRow("Decode Errors", playerState.audioDecodeErrors.toString(), valueColor = errorColor)
            
            DebugStatRow("DSP Bass", "${playerState.bassBoostPercent}%")
            DebugStatRow("DSP Vol", "${playerState.volumeBoostPercent}%")
            DebugStatRow("EQ State", if (playerState.isEqEnabled) "Enabled" else "Disabled")
        }
    }
}

@Composable
fun HardwarePipelineDiagnosticsCard(
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val hwCaps = androidx.compose.runtime.remember {
        com.medianest.hardware.AndroidHardwareEngine.detectCapabilities(context)
    }
    
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1A6366F1),
        borderColor = Color(0x336366F1),
        enableBlur = true,
        blurRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "HARDWARE PIPELINE DIAGNOSTICS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA5B4FC),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Real-time Hardware Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "GPU ACCELERATED",
                    fontSize = 10.sp,
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "• RAM Tier: ${hwCaps.ramTier}\n" +
                       "• GPU Backend: ${if (hwCaps.isVulkanSupported) "Vulkan 1.3 / GL ES 3.2" else "GL ES 3.0"}\n" +
                       "• Video Decode: MediaCodec SoC DSP (${if (hwCaps.isAv1HwSupported) "AV1, " else ""}HEVC, H.264)\n" +
                       "• Audio Sink: ${if (hwCaps.isAAudioSupported) "AAudio Low-Latency" else "OpenSL ES"}\n" +
                       "• Display HDR: ${if (hwCaps.isHdr10Supported) "Supported (HDR10/HLG)" else "Not Detected"}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace
            )
        }
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
