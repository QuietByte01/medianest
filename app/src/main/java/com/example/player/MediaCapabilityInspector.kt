package com.example.player

import android.net.Uri
import java.util.Locale

object MediaCapabilityInspector {

    data class MediaProfile(
        val container: String,
        val videoCodec: String,
        val audioCodec: String,
        val requiresFFmpegFallback: Boolean
    )

    fun inspect(uri: Uri): MediaProfile {
        val path = uri.path?.lowercase(Locale.ROOT) ?: uri.toString().lowercase(Locale.ROOT)
        
        // Check for known formats or extensions requiring FFmpeg fallback
        val isAvi = path.endsWith(".avi")
        val isFlv = path.endsWith(".flv")
        val isTs = path.endsWith(".ts") || path.endsWith(".mts")
        val isOgg = path.endsWith(".ogg")
        val isWmv = path.endsWith(".wmv")
        val isAsf = path.endsWith(".asf")

        if (isAvi) {
            return MediaProfile(
                container = "AVI",
                videoCodec = "MPEG-4 ASP / XVID",
                audioCodec = "AC-3 5.1",
                requiresFFmpegFallback = true
            )
        }

        if (isFlv) {
            return MediaProfile(
                container = "FLV",
                videoCodec = "Sorenson Spark / H.264",
                audioCodec = "MP3 / AAC",
                requiresFFmpegFallback = true
            )
        }

        if (isTs) {
            return MediaProfile(
                container = "MPEG-TS",
                videoCodec = "MPEG-2 / H.264",
                audioCodec = "AC-3 / AAC",
                requiresFFmpegFallback = true
            )
        }

        return MediaProfile(
            container = "Auto",
            videoCodec = "Auto",
            audioCodec = "Auto",
            requiresFFmpegFallback = false
        )
    }
}
