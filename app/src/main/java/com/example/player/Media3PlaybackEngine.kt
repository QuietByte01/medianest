package com.example.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class Media3PlaybackEngine(private val context: Context) : PlaybackEngine {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

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

    init {
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _diagnosticState.value = _diagnosticState.value.copy(
                    videoCodec = if (videoSize.width > 0) "Decoded (${videoSize.width}x${videoSize.height})" else "Hardware/MediaCodec"
                )
            }
        })
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        val mediaItem = Media3Item.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        currentSurface?.let { player.setVideoSurface(it) }
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
        currentSurface = surface
        player.setVideoSurface(surface)
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun release() {
        player.release()
    }

    override val currentPositionMs: Long
        get() = player.currentPosition

    override val durationMs: Long
        get() = player.duration.coerceAtLeast(0L)

    override val isPlaying: Boolean
        get() = player.isPlaying
}
