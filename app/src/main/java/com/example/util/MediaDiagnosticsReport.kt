package com.example.util

/**
 * Complete structured output of a media file analysis pass (FFprobe + FFmpeg error check).
 * Designed to be logged, shared via share sheet, or sent to analytics.
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
    val diagnostics: DiagnosticsInfo
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"fileName\": \"${fileName.esc()}\",")
        appendLine("  \"filePath\": \"${filePath.esc()}\",")
        appendLine("  \"fileSize\": $fileSize,")
        appendLine("  \"mediaType\": \"$mediaType\",")
        appendLine("  \"analysisTimestampMs\": $analysisTimestampMs,")
        appendLine("  \"analysisElapsedMs\": $analysisElapsedMs,")
        format?.let { f ->
            appendLine("  \"format\": {")
            appendLine("    \"container\": \"${f.containerFormat.esc()}\",")
            appendLine("    \"name\": \"${f.formatLongName.esc()}\",")
            appendLine("    \"duration\": ${f.duration},")
            appendLine("    \"bitrate\": ${f.bitrate},")
            appendLine("    \"streams\": ${f.streamCount}")
            appendLine("  },")
        }
        videoStream?.let { v ->
            appendLine("  \"videoStream\": {")
            appendLine("    \"codec\": \"${v.codecName.esc()}\",")
            appendLine("    \"profile\": \"${v.profile.orEmpty().esc()}\",")
            appendLine("    \"resolution\": \"${v.width}x${v.height}\",")
            appendLine("    \"pixelFormat\": \"${v.pixelFormat.orEmpty().esc()}\",")
            appendLine("    \"colorSpace\": \"${v.colorSpace.orEmpty().esc()}\",")
            appendLine("    \"colorPrimaries\": \"${v.colorPrimaries.orEmpty().esc()}\",")
            appendLine("    \"colorTransfer\": \"${v.colorTransfer.orEmpty().esc()}\",")
            appendLine("    \"frameRate\": \"${v.avgFrameRate}\",")
            appendLine("    \"fpsDecimal\": ${String.format("%.3f", v.avgFpsDecimal)},")
            appendLine("    \"aspectRatio\": \"${v.aspectRatio.orEmpty().esc()}\",")
            appendLine("    \"bitrate\": ${v.bitrate ?: 0},")
            appendLine("    \"rotation\": ${v.rotation ?: 0}")
            appendLine("  },")
        }
        if (audioStreams.isNotEmpty()) {
            appendLine("  \"audioStreams\": [")
            audioStreams.forEachIndexed { i, a ->
                val comma = if (i < audioStreams.lastIndex) "," else ""
                appendLine("    {\"index\":${a.index},\"codec\":\"${a.codecName.esc()}\",\"sampleRate\":${a.sampleRate},\"channels\":${a.channels},\"layout\":\"${a.channelLayout.orEmpty().esc()}\",\"bitrate\":${a.bitrate ?: 0},\"bitsPerSample\":${a.bitsPerSample},\"language\":\"${a.language.orEmpty().esc()}\"}$comma")
            }
            appendLine("  ],")
        }
        imageInfo?.let { img ->
            appendLine("  \"imageInfo\": {")
            appendLine("    \"format\": \"${img.format.esc()}\",")
            appendLine("    \"dimensions\": \"${img.width}x${img.height}\",")
            appendLine("    \"colorDepth\": \"${img.colorDepth.orEmpty().esc()}\",")
            appendLine("    \"pixelFormat\": \"${img.pixelFormat.orEmpty().esc()}\",")
            appendLine("    \"orientation\": ${img.orientation ?: 0},")
            appendLine("    \"hasAlpha\": ${img.hasAlpha}")
            appendLine("  },")
        }
        appendLine("  \"diagnostics\": {")
        appendLine("    \"hasErrors\": ${diagnostics.hasErrors},")
        appendLine("    \"errors\": [${diagnostics.errors.joinToString(",") { "\"${it.esc()}\"" }}],")
        appendLine("    \"warnings\": [${diagnostics.warnings.joinToString(",") { "\"${it.esc()}\"" }}],")
        appendLine("    \"corruptedFrames\": ${diagnostics.corruptedFramesDetected},")
        appendLine("    \"timestampIssues\": ${diagnostics.timestampIssues},")
        appendLine("    \"note\": \"${diagnostics.analysisNote.esc()}\"")
        appendLine("  }")
        appendLine("}")
    }

    fun toShareText(): String = buildString {
        appendLine("📊 MediaNest — Media Diagnostics")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("📁 File: $fileName")
        appendLine("💾 Size: ${formatBytesReport(fileSize)}")
        appendLine("⏱  Analysis: ${analysisElapsedMs}ms")
        appendLine()
        format?.let { f ->
            appendLine("📦 Container: ${f.formatLongName.ifBlank { f.containerFormat }}")
            appendLine("   Duration: ${formatDurationReport(f.duration)}")
            appendLine("   Bitrate: ${formatBitrateReport(f.bitrate)}")
            appendLine("   Streams: ${f.streamCount}")
            appendLine()
        }
        videoStream?.let { v ->
            appendLine("🎬 Video: ${v.codecName.uppercase()}${v.profile?.let { " ($it)" } ?: ""}")
            appendLine("   Resolution: ${v.width}×${v.height}${v.aspectRatio?.let { " ($it)" } ?: ""}")
            appendLine("   Frame Rate: ${String.format("%.2f", v.avgFpsDecimal)} fps")
            v.bitrate?.let { appendLine("   Bitrate: ${formatBitrateReport(it)}") }
            v.pixelFormat?.let { appendLine("   Pixel Format: $it") }
            v.colorSpace?.let { appendLine("   Color Space: $it") }
            v.colorPrimaries?.let { appendLine("   Color Primaries: $it") }
            appendLine()
        }
        audioStreams.forEachIndexed { i, a ->
            appendLine("🎵 Audio${if (audioStreams.size > 1) " #${i+1}" else ""}: ${a.codecName.uppercase()}")
            appendLine("   ${a.sampleRate} Hz · ${a.channelLayout ?: "${a.channels}ch"}")
            a.bitrate?.let { appendLine("   Bitrate: ${formatBitrateReport(it)}") }
            a.language?.let { appendLine("   Language: $it") }
            appendLine()
        }
        imageInfo?.let { img ->
            appendLine("🖼  Image: ${img.format.uppercase()} ${img.width}×${img.height}")
            img.colorDepth?.let { appendLine("   Color Depth: $it") }
            img.pixelFormat?.let { appendLine("   Pixel Format: $it") }
            if (img.hasAlpha) appendLine("   ✅ Alpha channel present")
            appendLine()
        }
        appendLine("🔍 Diagnostics: ${diagnostics.analysisNote}")
        if (diagnostics.corruptedFramesDetected) appendLine("   ❌ Corrupted frames detected")
        if (diagnostics.timestampIssues) appendLine("   ⚠  Timestamp irregularities")
        diagnostics.errors.take(5).forEach { appendLine("   ❌ $it") }
        diagnostics.warnings.take(3).forEach { appendLine("   ⚠  $it") }
        if (!diagnostics.hasErrors && !diagnostics.corruptedFramesDetected) appendLine("   ✅ No issues detected")
        appendLine()
        appendLine("Generated by MediaNest")
    }

    private fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

data class FormatInfo(val containerFormat: String, val formatLongName: String, val duration: Double, val bitrate: Long, val size: Long, val streamCount: Int, val startTime: Double?)
data class VideoStreamInfo(val index: Int, val codecName: String, val codecLongName: String, val profile: String?, val level: Int?, val width: Int, val height: Int, val pixelFormat: String?, val colorSpace: String?, val colorPrimaries: String?, val colorTransfer: String?, val colorRange: String?, val frameRate: String, val avgFrameRate: String, val avgFpsDecimal: Double, val aspectRatio: String?, val bitrate: Long?, val duration: Double?, val isDefault: Boolean, val rotation: Int?)
data class AudioStreamInfo(val index: Int, val codecName: String, val codecLongName: String, val profile: String?, val sampleRate: Int, val channels: Int, val channelLayout: String?, val sampleFormat: String?, val bitsPerSample: Int, val bitrate: Long?, val duration: Double?, val isDefault: Boolean, val language: String?)
data class ImageAnalysisInfo(val format: String, val width: Int, val height: Int, val colorDepth: String?, val pixelFormat: String?, val orientation: Int?, val colorProfile: String?, val hasAlpha: Boolean)
data class SubtitleStreamInfo(val index: Int, val codecName: String, val language: String?)
data class DiagnosticsInfo(val hasErrors: Boolean, val errors: List<String>, val warnings: List<String>, val corruptedFramesDetected: Boolean, val timestampIssues: Boolean, val missingStreams: Boolean, val decodingErrorLines: List<String>, val analysisNote: String)

internal fun formatBytesReport(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> String.format("%.2f MB", bytes / 1_048_576.0)
    bytes >= 1024L          -> String.format("%.1f KB", bytes / 1024.0)
    else                    -> "$bytes B"
}
internal fun formatBitrateReport(bps: Long): String = when {
    bps >= 1_000_000L -> String.format("%.1f Mbps", bps / 1_000_000.0)
    bps >= 1_000L     -> String.format("%.0f kbps", bps / 1_000.0)
    else              -> "$bps bps"
}
internal fun formatDurationReport(seconds: Double): String {
    val totalSec = seconds.toLong()
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
