package com.medianest.player

import android.net.Uri
import com.medianest.util.Logger
import java.util.Locale

object MediaCapabilityInspector {

    data class MediaProfile(
        val container: String,
        val videoCodec: String,
        val audioCodec: String,
        val requiresFFmpegFallback: Boolean
    )

    fun inspect(uri: Uri, probeResult: String? = null): MediaProfile {
        val path = uri.path?.lowercase(Locale.ROOT) ?: uri.toString().lowercase(Locale.ROOT)
        val fileName = path.substringAfterLast('/')
        
        // Use Probe Result if available (format: "container|vcodec|acodec")
        if (probeResult != null) {
            val parts = probeResult.split("|")
            if (parts.size >= 3) {
                val container = parts[0].uppercase()
                val vcodec = parts[1].lowercase()
                val acodec = parts[2].lowercase()
                
                Logger.d("MediaInspector", "Probe for $fileName: Container=$container, Video=$vcodec, Audio=$acodec")
                
                // Force FFmpeg for formats hardware definitively struggles with
                val isAvi = container.contains("AVI", ignoreCase = true)
                val isMpeg4 = vcodec.contains("mpeg4") || vcodec.contains("xvid") || vcodec.contains("msmpeg4")
                val isWmv = vcodec.contains("wmv") || acodec.contains("wma")
                val isFlv = vcodec.contains("flv") || container.contains("FLV")
                
                val needsFFmpeg = isAvi || isMpeg4 || isWmv || isFlv
                
                if (needsFFmpeg) {
                    Logger.i("MediaInspector", "FFmpeg fallback triggered by probe for $fileName. Reason: container=$container, vcodec=$vcodec, acodec=$acodec")
                    return MediaProfile(
                        container = container,
                        videoCodec = vcodec.uppercase(),
                        audioCodec = acodec.uppercase(),
                        requiresFFmpegFallback = true
                    )
                }
            }
        }

        // Only route to FFmpeg/Oboe for formats ExoPlayer/MediaCodec struggles with.
        val ffmpegOnlyExtensions = listOf(".ogg", ".opus", ".avi", ".wmv", ".flv", ".divx")
        if (ffmpegOnlyExtensions.any { fileName.endsWith(it) }) {
            Logger.i("MediaInspector", "FFmpeg fallback triggered by extension: $fileName")
            return MediaProfile(
                container = if (fileName.endsWith(".avi")) "AVI" else "Native Fallback",
                videoCodec = "NONE",
                audioCodec = fileName.substringAfterLast(".").uppercase(),
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
