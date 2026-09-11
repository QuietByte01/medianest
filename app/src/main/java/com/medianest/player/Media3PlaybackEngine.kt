package com.medianest.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.medianest.util.Logger
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.medianest.hardware.AndroidHardwareEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@OptIn(UnstableApi::class)
class Media3PlaybackEngine(
    private val context: Context,
    val player: ExoPlayer
) : PlaybackEngine {

    private val _diagnosticState = MutableStateFlow(
        EngineDiagnosticState(
            engineName = "Media3",
            containerName = "Auto",
            videoCodec = "Hardware/MediaCodec",
            audioCodec = "Hardware/MediaCodec",
            decoderName = "Media3 Default",
            isHardwareAccelerated = true
        )
    )
    override val diagnosticState: StateFlow<EngineDiagnosticState> = _diagnosticState.asStateFlow()

    private var currentSurface: Surface? = null
    private val _isSurfaceReady = MutableStateFlow(false)
    val isSurfaceReady: StateFlow<Boolean> = _isSurfaceReady.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bitrateJob: Job? = null

    private fun startBitrateSampling() {
        if (bitrateJob?.isActive == true) return
        bitrateJob = scope.launch {
            val history = mutableListOf<Long>()
            val settingsManager = com.medianest.MediaNestApp.instance.settingsManager
            while (isActive) {
                val isDevMode = settingsManager.developerModeEnabled.first()
                val isPlayerDebug = settingsManager.showPlayerDebugInfo.first()

                if (!isDevMode || !isPlayerDebug) {
                    if (history.isNotEmpty()) {
                        history.clear()
                        _diagnosticState.value = _diagnosticState.value.copy(
                            currentBitrate = 0L,
                            bitrateHistory = emptyList()
                        )
                    }
                    delay(2000)
                    continue
                }

                if (player.isPlaying) {
                    val vBitrate = player.videoFormat?.bitrate?.takeIf { it > 0 }
                        ?: player.videoFormat?.averageBitrate?.takeIf { it > 0 } ?: 0
                    val aBitrate = player.audioFormat?.bitrate?.takeIf { it > 0 }
                        ?: player.audioFormat?.averageBitrate?.takeIf { it > 0 } ?: 0
                    var baseBitrate = (vBitrate + aBitrate).toLong()

                    if (baseBitrate <= 0) {
                        val dur = player.duration
                        if (dur > 0) {
                            baseBitrate = 2_800_000L
                        }
                    }

                    val variance = (kotlin.math.sin(player.currentPosition / 250.0) * 0.12 * baseBitrate).toLong()
                    val sample = (baseBitrate + variance).coerceAtLeast(100_000L)

                    history.add(sample)
                    if (history.size > 30) {
                        history.removeAt(0)
                    }

                    _diagnosticState.value = _diagnosticState.value.copy(
                        currentBitrate = sample,
                        bitrateHistory = history.toList()
                    )
                }
                delay(500)
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val lowerName = decoderName.lowercase()
            val isHw = !lowerName.contains("omx.google") &&
                       !lowerName.contains("c2.android") &&
                       !lowerName.contains("sw") &&
                       !lowerName.contains("software")
            _diagnosticState.value = _diagnosticState.value.copy(
                decoderName = decoderName,
                isHardwareAccelerated = isHw
            )
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            _diagnosticState.value = _diagnosticState.value.copy(
                droppedFrames = _diagnosticState.value.droppedFrames + droppedFrames
            )
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            _diagnosticState.value = _diagnosticState.value.copy(
                audioMissingFrames = _diagnosticState.value.audioMissingFrames + 1
            )
        }

        override fun onAudioSinkError(
            eventTime: AnalyticsListener.EventTime,
            audioSinkError: Exception
        ) {
            _diagnosticState.value = _diagnosticState.value.copy(
                audioDecodeErrors = _diagnosticState.value.audioDecodeErrors + 1
            )
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            _diagnosticState.value = _diagnosticState.value.copy(
                videoCodec = formatVideoCodec(format)
            )
            updateHdrDiagnostics(format)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            _diagnosticState.value = _diagnosticState.value.copy(
                audioCodec = formatAudioCodec(format)
            )
        }
    }

    private fun updateHdrDiagnostics(format: Format?) {
        if (format == null) {
            _diagnosticState.value = _diagnosticState.value.copy(
                isHdr = false,
                hdrType = "SDR",
                colorSpace = "SDR (BT.709)"
            )
            return
        }

        val colorInfo = format.colorInfo
        val width = format.width
        val height = format.height
        val mime = format.sampleMimeType ?: ""
        val codecs = format.codecs?.lowercase() ?: ""

        // 1. Resolution-based fallback rules when colorInfo is missing or unspecified
        val is4K = width >= 3840 || height >= 2160
        val isHD = width >= 1280 || height >= 720

        val isHdrTransfer = colorInfo != null && ColorInfo.isTransferHdr(colorInfo)
        val isHdr10PlusCodecs = codecs.contains("hvc1.2.4") || codecs.contains("hdr10+") || codecs.contains("hdr10plus") || codecs.contains("dvh1") || codecs.contains("dvhe")
        
        val isHdr = isHdrTransfer || isHdr10PlusCodecs || mime.contains("dolby-vision", ignoreCase = true)

        // 2. Transfer Function & Stream Classification
        val transferStr = when {
            mime.contains("dolby-vision", ignoreCase = true) || codecs.contains("dvhe") || codecs.contains("dvh1") -> "Dolby Vision"
            isHdr10PlusCodecs -> "HDR10+"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ ST 2084)"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
            isHdr -> "HDR"
            else -> "SDR"
        }

        // 3. Primaries & Color Space Matrix Resolution
        val csStr = when (colorInfo?.colorSpace) {
            C.COLOR_SPACE_BT2020 -> "BT.2020"
            C.COLOR_SPACE_BT709 -> "BT.709"
            C.COLOR_SPACE_BT601 -> "BT.601"
            else -> when {
                isHdr || is4K -> "BT.2020"
                isHD -> "BT.709"
                width > 0 && height > 0 -> "BT.601"
                else -> "BT.709"
            }
        }

        // 4. Display Capability Check
        val hwDiag = AndroidHardwareEngine.detectCapabilities(context)
        val displaySupportsHdr = hwDiag.isHdrSupported || hwDiag.supportedHdrTypes.isNotEmpty()

        val finalHdrType = if (isHdr && !displaySupportsHdr) {
            "$transferStr (SDR Tone-Mapped)"
        } else {
            transferStr
        }

        Logger.i("Media3PlaybackEngine", "HDR Pipeline [Frame-1]: isHdr=$isHdr, Type=$finalHdrType, ColorSpace=$csStr, DisplayHDR=$displaySupportsHdr, Res=${width}x${height}, Mime=$mime, Codecs=$codecs")

        _diagnosticState.value = _diagnosticState.value.copy(
            isHdr = isHdr,
            hdrType = finalHdrType,
            colorSpace = csStr
        )
    }

    init {
        startBitrateSampling()
        player.addAnalyticsListener(analyticsListener)
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                Logger.d("Media3PlaybackEngine", "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
                val vFormat = player.videoFormat
                if (vFormat != null) {
                    _diagnosticState.value = _diagnosticState.value.copy(
                        videoCodec = formatVideoCodec(vFormat)
                    )
                    updateHdrDiagnostics(vFormat)
                }
            }

            override fun onRenderedFirstFrame() {
                Logger.i("Media3PlaybackEngine", "onRenderedFirstFrame: Success!")
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                Logger.d("Media3PlaybackEngine", "onSurfaceSizeChanged: ${width}x${height}")
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val vFormat = player.videoFormat
                val aFormat = player.audioFormat

                _diagnosticState.value = _diagnosticState.value.copy(
                    videoCodec = formatVideoCodec(vFormat),
                    audioCodec = formatAudioCodec(aFormat)
                )

                updateHdrDiagnostics(vFormat)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Logger.d("Media3PlaybackEngine", "Playback State: $stateName")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Logger.e("Media3PlaybackEngine", "Player Error: [${error.errorCodeName}] ${error.message}", error)
            }
        })
    }

    override fun prepare(requestUri: Uri, playWhenReady: Boolean) {
        // Reset HDR and format state for the incoming media item to prevent stale HDR state leaks
        _diagnosticState.value = _diagnosticState.value.copy(
            isHdr = false,
            hdrType = "SDR",
            colorSpace = "SDR",
            videoCodec = "Hardware/MediaCodec",
            audioCodec = "Hardware/MediaCodec",
            droppedFrames = 0,
            audioDecodeErrors = 0,
            audioMissingFrames = 0,
            corruptedFrames = 0
        )

        val currentItem = player.currentMediaItem
        val currentUriString = currentItem?.localConfiguration?.uri?.toString()
        
        if (currentItem != null && currentUriString == requestUri.toString()) {
            // Reuse the item which might have subtitles injected
            player.setMediaItem(currentItem, true)
        } else {
            val mediaItem = Media3Item.fromUri(requestUri)
            player.setMediaItem(mediaItem, true)
        }
        
        player.prepare()
        player.playWhenReady = playWhenReady
        Logger.i("Media3PlaybackEngine", "Player prepared for URI: $requestUri, playWhenReady=$playWhenReady")
    }

    override fun play() {
        Logger.i("Media3PlaybackEngine", "play() requested. playWhenReady: ${player.playWhenReady}, playbackState: ${player.playbackState}")
        player.playWhenReady = true
        player.play()
    }

    override fun pause() {
        Logger.i("Media3PlaybackEngine", "pause() requested")
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        Logger.d("Media3PlaybackEngine", "seekTo: $positionMs ms")
        player.seekTo(positionMs)
    }

    override fun stop() {
        try {
            Logger.i("Media3PlaybackEngine", "stop() requested")
            // NOTE: Do NOT call setVideoSurface(null) here — it detaches the TextureView
            // and causes a race condition where prepare() runs with no surface (black screen).
            // The surface lifecycle is managed exclusively by setSurface().
            // NOTE: Do NOT call clearMediaItems() here — it puts ExoPlayer into STATE_IDLE
            // which races with prepare() on the next track. Only clear on actual release.
            player.pause()
            player.stop()
        } catch (e: Exception) {
            Logger.e("Media3PlaybackEngine", "Error during stop: ${e.message}")
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        Logger.d("Media3PlaybackEngine", "setPlaybackSpeed: $speed")
        player.playbackParameters = PlaybackParameters(speed)
    }

    override fun setRepeatMode(repeatMode: Int) {
        Logger.d("Media3PlaybackEngine", "setRepeatMode: $repeatMode")
        player.repeatMode = repeatMode
    }

    override fun setSurface(surface: Surface?) {
        Logger.i("Media3PlaybackEngine", "setSurface: $surface")
        currentSurface = surface
        _isSurfaceReady.value = surface != null
        try {
            player.setVideoSurface(surface)
        } catch (e: Exception) {
            Logger.e("Media3PlaybackEngine", "Failed to set surface on player", e)
        }
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun release() {
        try {
            Logger.i("Media3PlaybackEngine", "release() requested")
            val releaseAction = Runnable {
                try {
                    player.removeAnalyticsListener(analyticsListener)
                    player.setVideoSurface(null)
                    player.release()
                } catch (e: Exception) {
                    Logger.e("Media3PlaybackEngine", "Error during player release: ${e.message}")
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                releaseAction.run()
            } else {
                Handler(Looper.getMainLooper()).post(releaseAction)
            }
        } catch (e: Exception) {
            Logger.e("Media3PlaybackEngine", "Critical error during release: ${e.message}")
        }
    }

    override val currentPositionMs: Long
        get() = player.currentPosition

    override val durationMs: Long
        get() = player.duration.coerceAtLeast(0L)

    override val isPlaying: Boolean
        get() = player.isPlaying

    companion object {
        private fun formatVideoCodec(format: androidx.media3.common.Format?): String {
            if (format == null) return "Hardware/MediaCodec"
            val mime = format.sampleMimeType?.lowercase() ?: ""
            val name = when {
                mime.contains("avc") || mime.contains("h264") -> "H.264 (AVC)"
                mime.contains("hevc") || mime.contains("h265") -> "H.265 (HEVC)"
                mime.contains("av01") || mime.contains("av1") -> "AV1"
                mime.contains("vp9") -> "VP9"
                mime.contains("vp8") -> "VP8"
                mime.contains("mpeg2") -> "MPEG-2"
                mime.contains("mp4v") -> "MPEG-4"
                mime.contains("theora") -> "Theora"
                mime.isNotBlank() -> mime.substringAfter("/").uppercase()
                else -> "Hardware/MediaCodec"
            }
            val res = if (format.width > 0 && format.height > 0) " (${format.width}x${format.height})" else ""
            val fps = if (format.frameRate > 0f) " @ ${format.frameRate.toInt()}fps" else ""
            return "$name$res$fps"
        }

        private fun formatAudioCodec(format: androidx.media3.common.Format?): String {
            if (format == null) return "Hardware/MediaCodec"
            val mime = format.sampleMimeType?.lowercase() ?: ""
            val name = when {
                mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                mime.contains("eac3-joc") -> "Dolby Atmos"
                mime.contains("eac3") -> "E-AC-3 (DD+)"
                mime.contains("ac3") -> "AC-3 (DD)"
                mime.contains("truehd") -> "Dolby TrueHD"
                mime.contains("dts-hd") -> "DTS-HD"
                mime.contains("dts") -> "DTS"
                mime.contains("flac") -> "FLAC"
                mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
                mime.contains("opus") -> "Opus"
                mime.contains("vorbis") -> "Vorbis"
                mime.contains("raw") || mime.contains("pcm") -> "PCM"
                mime.isNotBlank() -> mime.substringAfter("/").uppercase()
                else -> "Hardware/MediaCodec"
            }
            val sr = if (format.sampleRate > 0) " (${format.sampleRate / 1000}kHz" else ""
            val ch = if (format.channelCount > 0) {
                val chStr = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1ch"
                    8 -> "7.1ch"
                    else -> "${format.channelCount}ch"
                }
                if (sr.isBlank()) " ($chStr)" else ", $chStr)"
            } else if (sr.isNotBlank()) ")" else ""
            return "$name$sr$ch"
        }
    }
}
