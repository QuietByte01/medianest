package com.medianest.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    }

    init {
        player.addAnalyticsListener(analyticsListener)
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0) {
                    _diagnosticState.value = _diagnosticState.value.copy(
                        videoCodec = "${videoSize.width}x${videoSize.height}"
                    )
                }
            }
        })
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        val mediaItem = Media3Item.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    override fun setRepeatMode(repeatMode: Int) {
        player.repeatMode = repeatMode
    }

    override fun setSurface(surface: Surface?) {
        Log.i("Media3PlaybackEngine", "setSurface: $surface")
        currentSurface = surface
        player.setVideoSurface(surface)
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun release() {
        player.removeAnalyticsListener(analyticsListener)
        player.release()
    }

    override val currentPositionMs: Long
        get() = player.currentPosition

    override val durationMs: Long
        get() = player.duration.coerceAtLeast(0L)

    override val isPlaying: Boolean
        get() = player.isPlaying
}
