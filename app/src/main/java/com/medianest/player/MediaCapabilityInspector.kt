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

    fun inspect(uri: Uri, probeResult: String? = null, titleHint: String? = null): MediaProfile {
        val path = uri.path?.lowercase(Locale.ROOT) ?: uri.toString().lowercase(Locale.ROOT)
        val fileName = (titleHint ?: path.substringAfterLast('/')).lowercase(Locale.ROOT)
        
        // Use Probe Result if available (format: "container|vcodec|acodec")
        if (probeResult != null) {
            val parts = probeResult.split("|")
            if (parts.size >= 3) {
                val container = parts[0].uppercase()
                val vcodec = parts[1].lowercase()
                val acodec = parts[2].lowercase()
                
                Logger.d("MediaInspector", "Probe for $fileName: Container=$container, Video=$vcodec, Audio=$acodec")
                
                // Codecs/containers that standard Android hardware MediaCodec lacks or Media3 AviExtractor struggles with:
                // 1. AVI files with AC3, DTS, EAC3, or MPEG-4 Part 2 (Xvid/DivX/MSMPEG4)
                // 2. WMV, WMA, FLV, RealVideo
                val isAvi = container.contains("AVI", ignoreCase = true) || fileName.endsWith(".avi", ignoreCase = true)
                val isMpeg4Asp = vcodec.contains("mpeg4") || vcodec.contains("xvid") || vcodec.contains("divx") || vcodec.contains("msmpeg4")
                val isTroublesomeAudio = acodec.contains("ac3") || acodec.contains("dts") || acodec.contains("eac3") || acodec.contains("wma")
                val isWmv = vcodec.contains("wmv") || acodec.contains("wma")
                val isFlv = vcodec.contains("flv") || container.contains("FLV")
                
                val needsFFmpeg = (isAvi && (isMpeg4Asp || isTroublesomeAudio)) || isMpeg4Asp || isWmv || isFlv
                
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

        // Route formats where Media3 lacks robust demuxing/decoding directly to FFmpeg
        val ffmpegOnlyExtensions = listOf(".avi", ".wmv", ".flv", ".divx", ".rmvb", ".xvid", ".asf")
        if (ffmpegOnlyExtensions.any { fileName.endsWith(it, ignoreCase = true) }) {
            Logger.i("MediaInspector", "FFmpeg fallback triggered by extension: $fileName")
            return MediaProfile(
                container = if (fileName.endsWith(".avi", ignoreCase = true)) "AVI" else "Native Fallback",
                videoCodec = "Auto",
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
