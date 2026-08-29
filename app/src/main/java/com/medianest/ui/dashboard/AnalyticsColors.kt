package com.medianest.ui.dashboard

import androidx.compose.ui.graphics.Color
import java.text.DecimalFormat

object AnalyticsColors {

    val CategoryImages = Color(0xFFF77F00)
    val CategoryVideos = Color(0xFF06A77D)
    val CategoryAudio = Color(0xFFFF006E)

    val Jpg = Color(0xFF00B4D8)
    val Png = Color(0xFFF77F00)
    val Webp = Color(0xEFE0A96D)
    val Gif = Color(0xFF9B5DE5)
    
    val Mp4 = Color(0xFF06A77D)
    val Webm = Color(0xFF8338EC)
    val Mkv = Color(0xFF00B4D8)
    val Avi = Color(0xFF0077B6)
    val Mov = Color(0xFF0284C7)

    val Mp3 = Color(0xFFFF006E)
    val Flac = Color(0xFFFFBE0B)
    val Wav = Color(0xFF3A86EF)
    val Aac = Color(0xFFF15BB5)
    val M4a = Color(0xFFE63946)
    val Opus = Color(0xFF10B981)
    val Ogg = Color(0xFF6366F1)

    private val FallbackColors = listOf(
        Color(0xFF457B9D),
        Color(0xFF2A9D8F),
        Color(0xFFE76F51),
        Color(0xFF118AB2),
        Color(0xFF06D6A0),
        Color(0xFFFFD166),
        Color(0xFFEF476F)
    )

    fun getFormatColor(ext: String, category: String? = null): Color {
        return when (ext.uppercase()) {
            "JPG", "JPEG" -> Jpg
            "PNG" -> Png
            "WEBP" -> Webp
            "GIF" -> Gif
            "MP4" -> Mp4
            "WEBM" -> Webm
            "MKV" -> Mkv
            "AVI" -> Avi
            "MOV" -> Mov
            "MP3" -> Mp3
            "FLAC" -> Flac
            "WAV" -> Wav
            "AAC" -> Aac
            "M4A" -> M4a
            "OPUS" -> Opus
            "OGG" -> Ogg
            else -> {
                when (category) {
                    "IMAGE" -> CategoryImages
                    "VIDEO" -> CategoryVideos
                    "AUDIO" -> CategoryAudio
                    else -> {
                        val index = kotlin.math.abs(ext.hashCode()) % FallbackColors.size
                        FallbackColors[index]
                    }
                }
            }
        }
    }

    fun formatNumber(number: Int): String {
        return DecimalFormat("#,##0").format(number)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        val df = DecimalFormat("#,##0.#")
        return "${df.format(value)} ${units[index]}"
    }

    fun formatTimeAgo(timestamp: Long): String {
        if (timestamp <= 0) return "Never scanned"
        val diffMs = System.currentTimeMillis() - timestamp
        if (diffMs < 60_000L) return "Just now"
        val diffMins = diffMs / 60_000L
        if (diffMins < 60) return "Scanned $diffMins minute${if (diffMins > 1) "s" else ""} ago"
        val diffHours = diffMins / 60
        if (diffHours < 24) return "Scanned $diffHours hour${if (diffHours > 1) "s" else ""} ago"
        val diffDays = diffHours / 24
        return "Scanned $diffDays day${if (diffDays > 1) "s" else ""} ago"
    }
}
