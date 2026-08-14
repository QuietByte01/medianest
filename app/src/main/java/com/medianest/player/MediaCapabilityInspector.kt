package com.medianest.player

import android.net.Uri
import android.util.Log
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
                
                Log.d("MediaInspector", "Probe: Container=$container, Video=$vcodec, Audio=$acodec")
                
                // Only force FFmpeg for containers/codecs Media3/MediaCodec definitively struggles with
                val isAvi = container.contains("AVI")
                val isMpeg4 = vcodec.contains("mpeg4") || vcodec.contains("xvid") || vcodec.contains("msmpeg4")
                val isAc3 = acodec.contains("ac3")
                
                val needsFFmpeg = isAvi && (isMpeg4 || isAc3) || 
                                  vcodec.contains("flv") || 
                                  vcodec.contains("wmv") ||
                                  acodec.contains("wma")
                
                if (needsFFmpeg) {
                    Log.i("MediaInspector", "FFmpeg fallback triggered by probe: $probeResult")
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
        // Common formats (.mp3, .flac, .wav, .m4a, .aac) are handled fine by hardware decoders.
        val ffmpegOnlyExtensions = listOf(".ogg", ".opus")
        if (ffmpegOnlyExtensions.any { fileName.endsWith(it) }) {
            return MediaProfile(
                container = "Audio (Native)",
                videoCodec = "NONE",
                audioCodec = fileName.substringAfterLast(".").uppercase(),
                requiresFFmpegFallback = true
            )
        }

        // Fallback to extension check if probe is missing or clean
        val isAvi = fileName.endsWith(".avi")
        if (isAvi) {
            Log.i("MediaInspector", "FFmpeg fallback triggered by extension: $fileName")
            return MediaProfile(
                container = "AVI (Ext)",
                videoCodec = "MPEG-4 / XVID",
                audioCodec = "AC-3",
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
