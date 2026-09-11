package com.medianest.util

import java.util.Locale

/**
 * Complete structured developer telemetry & diagnostic report generated from FFmpeg/FFprobe analysis.
 * Can be logged to crash/analytics tools, exported as JSON, or shared via system share sheet.
 */
data class MediaDiagnosticsReport(
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val mediaType: String,           // "VIDEO" | "AUDIO" | "IMAGE" | "UNKNOWN"
    val analysisTimestampMs: Long,
    val analysisElapsedMs: Long,
    val format: FormatInfo? = null,
    val videoStream: VideoStreamInfo? = null,
    val audioStreams: List<AudioStreamInfo> = emptyList(),
    val imageInfo: ImageAnalysisInfo? = null,
    val subtitleStreams: List<SubtitleStreamInfo> = emptyList(),
    val diagnostics: DiagnosticsInfo,
    val techBadges: List<String> = emptyList()
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"fileName\": \"${fileName.esc()}\",")
        appendLine("  \"filePath\": \"${filePath.esc()}\",")
        appendLine("  \"fileSize\": $fileSize,")
        appendLine("  \"mediaType\": \"$mediaType\",")
        appendLine("  \"analysisTimestampMs\": $analysisTimestampMs,")
        appendLine("  \"analysisElapsedMs\": $analysisElapsedMs,")
        appendLine("  \"techBadges\": [${techBadges.joinToString(",") { "\"${it.esc()}\"" }}],")
        format?.let { f ->
            appendLine("  \"format\": {")
            appendLine("    \"container\": \"${f.containerFormat.esc()}\",")
            appendLine("    \"name\": \"${f.formatLongName.esc()}\",")
            appendLine("    \"duration\": ${f.duration},")
            appendLine("    \"bitrate\": ${f.bitrate},")
            appendLine("    \"streamCount\": ${f.streamCount},")
            appendLine("    \"startTime\": ${f.startTime ?: 0.0},")
            appendLine("    \"probeScore\": ${f.probeScore}")
            appendLine("  },")
        }
        videoStream?.let { v ->
            appendLine("  \"videoStream\": {")
            appendLine("    \"codec\": \"${v.codecName.esc()}\",")
            appendLine("    \"codecLongName\": \"${v.codecLongName.esc()}\",")
            appendLine("    \"profile\": \"${v.profile.orEmpty().esc()}\",")
            appendLine("    \"level\": ${v.level ?: 0},")
            appendLine("    \"resolution\": \"${v.width}x${v.height}\",")
            appendLine("    \"pixelFormat\": \"${v.pixelFormat.orEmpty().esc()}\",")
            appendLine("    \"colorSpace\": \"${v.colorSpace.orEmpty().esc()}\",")
            appendLine("    \"colorPrimaries\": \"${v.colorPrimaries.orEmpty().esc()}\",")
            appendLine("    \"colorTransfer\": \"${v.colorTransfer.orEmpty().esc()}\",")
            appendLine("    \"colorRange\": \"${v.colorRange.orEmpty().esc()}\",")
            appendLine("    \"frameRate\": \"${v.frameRate.esc()}\",")
            appendLine("    \"fpsDecimal\": ${String.format(Locale.US, "%.3f", v.avgFpsDecimal)},")
            appendLine("    \"aspectRatio\": \"${v.aspectRatio.orEmpty().esc()}\",")
            appendLine("    \"bitrate\": ${v.bitrate ?: 0},")
            appendLine("    \"isHdr\": ${v.isHdr},")
            appendLine("    \"isDolbyVision\": ${v.isDolbyVision},")
            appendLine("    \"rotation\": ${v.rotation ?: 0}")
            appendLine("  },")
        }
        if (audioStreams.isNotEmpty()) {
            appendLine("  \"audioStreams\": [")
            audioStreams.forEachIndexed { i, a ->
                val comma = if (i < audioStreams.lastIndex) "," else ""
                appendLine("    {\"index\":${a.index},\"codec\":\"${a.codecName.esc()}\",\"sampleRate\":${a.sampleRate},\"channels\":${a.channels},\"layout\":\"${a.channelLayout.orEmpty().esc()}\",\"sampleFormat\":\"${a.sampleFormat.orEmpty().esc()}\",\"bitsPerSample\":${a.bitsPerSample},\"bitrate\":${a.bitrate ?: 0},\"isSpatial\":${a.isSpatialAudio},\"language\":\"${a.language.orEmpty().esc()}\"}$comma")
            }
            appendLine("  ],")
        }
        imageInfo?.let { img ->
            appendLine("  \"imageInfo\": {")
            appendLine("    \"format\": \"${img.format.esc()}\",")
            appendLine("    \"dimensions\": \"${img.width}x${img.height}\",")
            appendLine("    \"colorDepth\": \"${img.colorDepth.orEmpty().esc()}\",")
            appendLine("    \"pixelFormat\": \"${img.pixelFormat.orEmpty().esc()}\",")
            appendLine("    \"colorProfile\": \"${img.colorProfile.orEmpty().esc()}\",")
            appendLine("    \"orientation\": ${img.orientation ?: 0},")
            appendLine("    \"hasAlpha\": ${img.hasAlpha},")
            appendLine("    \"isAnimated\": ${img.isAnimated}")
            appendLine("  },")
        }
        appendLine("  \"diagnostics\": {")
        appendLine("    \"hasErrors\": ${diagnostics.hasErrors},")
        appendLine("    \"errors\": [${diagnostics.errors.joinToString(",") { "\"${it.esc()}\"" }}],")
        appendLine("    \"warnings\": [${diagnostics.warnings.joinToString(",") { "\"${it.esc()}\"" }}],")
        appendLine("    \"corruptedFrames\": ${diagnostics.corruptedFramesDetected},")
        appendLine("    \"timestampIssues\": ${diagnostics.timestampIssues},")
        appendLine("    \"muxingIssues\": ${diagnostics.muxingIssues},")
        appendLine("    \"missingStreams\": ${diagnostics.missingStreams},")
        appendLine("    \"analysisNote\": \"${diagnostics.analysisNote.esc()}\"")
        appendLine("  }")
        appendLine("}")
    }

    fun toShareText(): String = buildString {
        appendLine("📊 MediaNest — Media Diagnostics & Telemetry")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("📁 File: $fileName")
        appendLine("💾 Size: ${formatBytesReport(fileSize)}")
        appendLine("⏱  Probe Time: ${analysisElapsedMs}ms")
        if (techBadges.isNotEmpty()) {
            appendLine("🏷  Badges: ${techBadges.joinToString(" · ")}")
        }
        appendLine()
        format?.let { f ->
            appendLine("📦 Container: ${f.formatLongName.ifBlank { f.containerFormat }}")
            appendLine("   Duration: ${formatDurationReport(f.duration)}")
            appendLine("   Overall Bitrate: ${formatBitrateReport(f.bitrate)}")
            appendLine("   Streams: ${f.streamCount} (Score: ${f.probeScore})")
            appendLine()
        }
        videoStream?.let { v ->
            appendLine("🎬 Video Stream:")
            appendLine("   Codec: ${v.codecName.uppercase()}${if (!v.profile.isNullOrBlank()) " (${v.profile})" else ""}")
            appendLine("   Resolution: ${v.width}×${v.height}${if (!v.aspectRatio.isNullOrBlank()) " (${v.aspectRatio})" else ""}")
            appendLine("   Frame Rate: ${String.format(Locale.US, "%.2f", v.avgFpsDecimal)} fps (${v.frameRate})")
            v.bitrate?.let { appendLine("   Bitrate: ${formatBitrateReport(it)}") }
            v.pixelFormat?.let { appendLine("   Pixel Format: $it") }
            v.colorSpace?.let { appendLine("   Color Space: $it (Primaries: ${v.colorPrimaries ?: "N/A"}, Transfer: ${v.colorTransfer ?: "N/A"})") }
            if (v.isDolbyVision) appendLine("   ✨ Dolby Vision Metadata Detected")
            else if (v.isHdr) appendLine("   🌈 High Dynamic Range (HDR)")
            appendLine()
        }
        audioStreams.forEachIndexed { i, a ->
            appendLine("🎵 Audio Stream${if (audioStreams.size > 1) " #${i + 1}" else ""}:")
            appendLine("   Codec: ${a.codecName.uppercase()}${if (!a.profile.isNullOrBlank()) " (${a.profile})" else ""}")
            appendLine("   Specs: ${a.sampleRate} Hz · ${a.channelLayout ?: "${a.channels}ch"} · ${a.bitsPerSample}-bit")
            a.bitrate?.let { appendLine("   Bitrate: ${formatBitrateReport(it)}") }
            if (a.isSpatialAudio) appendLine("   🎧 Spatial / Immersive Audio")
            if (!a.language.isNullOrBlank() && a.language != "und") appendLine("   Language: ${a.language}")
            appendLine()
        }
        imageInfo?.let { img ->
            appendLine("🖼  Image Specs:")
            appendLine("   Format: ${img.format.uppercase()} (${img.width}×${img.height})")
            img.colorDepth?.let { appendLine("   Color Depth: $it") }
            img.pixelFormat?.let { appendLine("   Pixel Format: $it") }
            img.colorProfile?.let { appendLine("   Color Profile: $it") }
            if (img.hasAlpha) appendLine("   ✅ Alpha Channel Present")
            if (img.isAnimated) appendLine("   🎞 Animated Image")
            appendLine()
        }
        appendLine("🔍 Diagnostics:")
        appendLine("   ${diagnostics.analysisNote}")
        if (diagnostics.corruptedFramesDetected) appendLine("   ❌ Bitstream corruption / invalid packets detected")
        if (diagnostics.timestampIssues) appendLine("   ⚠ Timestamp PTS/DTS discontinuities")
        if (diagnostics.muxingIssues) appendLine("   ⚠ Container muxing / header missing issues")
        diagnostics.errors.take(5).forEach { appendLine("   ❌ $it") }
        diagnostics.warnings.take(3).forEach { appendLine("   ⚠ $it") }
        if (!diagnostics.hasErrors && !diagnostics.corruptedFramesDetected && !diagnostics.timestampIssues) {
            appendLine("   ✅ No integrity issues found")
        }
        appendLine()
        appendLine("Generated by MediaNest Diagnostics Engine")
    }

    private fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

data class FormatInfo(
    val containerFormat: String,
    val formatLongName: String,
    val duration: Double,
    val bitrate: Long,
    val size: Long,
    val streamCount: Int,
    val videoStreamCount: Int = 1,
    val audioStreamCount: Int = 1,
    val subtitleStreamCount: Int = 0,
    val startTime: Double?,
    val probeScore: Int = 100,
    val creationTime: String = "",
    val encoderTags: String = "encoder=FFmpeg v6.1",
    val containerFlags: String = "faststart, seekable"
)

data class VideoStreamInfo(
    val index: Int,
    val streamId: String = "#0:0",
    val codecName: String,
    val codecLongName: String,
    val profile: String?,
    val level: Int?,
    val width: Int,
    val height: Int,
    val pixelFormat: String?,
    val colorSpace: String?,
    val colorPrimaries: String?,
    val colorTransfer: String?,
    val colorRange: String?,
    val frameRate: String,
    val avgFrameRate: String,
    val avgFpsDecimal: Double,
    val aspectRatio: String?,
    val bitrate: Long?,
    val maxBitrate: Long? = null,
    val duration: Double?,
    val totalFrames: Long = 0L,
    val isInterlaced: Boolean = false,
    val isDefault: Boolean,
    val rotation: Int?,
    val isHdr: Boolean = false,
    val isDolbyVision: Boolean = false,
    val isHdr10Plus: Boolean = false,
    val hdrInfo: String = "SDR"
)

data class AudioStreamInfo(
    val index: Int,
    val streamId: String = "#0:1",
    val codecName: String,
    val codecLongName: String,
    val profile: String?,
    val sampleRate: Int,
    val channels: Int,
    val channelLayout: String?,
    val sampleFormat: String?,
    val bitsPerSample: Int,
    val bitrate: Long?,
    val duration: Double?,
    val isDefault: Boolean,
    val language: String?,
    val isSpatialAudio: Boolean = false,
    val title: String = ""
)

data class ImageAnalysisInfo(
    val format: String,
    val width: Int,
    val height: Int,
    val colorDepth: String?,
    val pixelFormat: String?,
    val orientation: Int?,
    val colorProfile: String?,
    val hasAlpha: Boolean,
    val isAnimated: Boolean = false
)

data class SubtitleStreamInfo(
    val index: Int,
    val codecName: String,
    val language: String?
)

data class DiagnosticsInfo(
    val hasErrors: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val corruptedFramesDetected: Boolean,
    val corruptedVideoFramesCount: Int = 0,
    val corruptedVideoFramesPct: Double = 0.0,
    val droppedVideoFramesCount: Int = 0,
    val isVideoCorrupted: Boolean = false,
    val corruptedAudioSamplesCount: Int = 0,
    val corruptedAudioSamplesPct: Double = 0.0,
    val audioBufferUnderrunsCount: Int = 0,
    val isAudioCorrupted: Boolean = false,
    val avSyncOffsetMs: Int = 0,
    val concealedMacroblocksCount: Int = 0,
    val keyframeLossCount: Int = 0,
    val demuxerDiscontinuity: Boolean = false,
    val timestampIssues: Boolean,
    val muxingIssues: Boolean = false,
    val missingStreams: Boolean,
    val decodingErrorLines: List<String>,
    val analysisNote: String
)

internal fun formatBytesReport(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
    bytes >= 1024L          -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else                    -> "$bytes B"
}

internal fun formatBitrateReport(bps: Long): String = when {
    bps >= 1_000_000L -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
    bps >= 1_000L     -> String.format(Locale.US, "%.0f kbps", bps / 1_000.0)
    else              -> "$bps bps"
}

internal fun formatDurationReport(seconds: Double): String {
    val totalSec = seconds.toLong()
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

