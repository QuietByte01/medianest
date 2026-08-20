package com.medianest.ui.videoeditor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.medianest.ui.videoeditor.data.ThumbnailRepository
import com.medianest.ui.videoeditor.model.VideoTimelineState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(VideoTimelineState())
    val state = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var thumbnailRepository: ThumbnailRepository? = null
    private var playbackProgressJob: Job? = null

    fun initialize(uri: Uri) {
        if (player != null) return

        player = ExoPlayer.Builder(getApplication()).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _state.update { it.copy(
                            durationMs = duration,
                            trimEndMs = duration
                        ) }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        startPlaybackProgressTracker()
                    } else {
                        playbackProgressJob?.cancel()
                    }
                }
            })
        }

        thumbnailRepository = ThumbnailRepository(getApplication(), uri)
    }

    private fun startPlaybackProgressTracker() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (true) {
                val currentPos = player?.currentPosition ?: 0L
                _state.update { it.copy(positionMs = currentPos) }
                
                // Handle trim boundaries during playback
                val stateValue = _state.value
                if (currentPos >= stateValue.trimEndMs) {
                    player?.pause()
                    seekTo(stateValue.trimStartMs)
                }
                
                delay(16) // ~60fps
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clampedPos = positionMs.coerceIn(0, _state.value.durationMs)
        player?.seekTo(clampedPos)
        _state.update { it.copy(positionMs = clampedPos) }
    }

    fun togglePlayback() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun setTrimStart(timeMs: Long) {
        _state.update { 
            val newStart = timeMs.coerceIn(0L, it.trimEndMs - 100L)
            it.copy(trimStartMs = newStart) 
        }
        seekTo(timeMs)
    }

    fun setTrimEnd(timeMs: Long) {
        _state.update { 
            val newEnd = timeMs.coerceIn(it.trimStartMs + 100L, it.durationMs)
            it.copy(trimEndMs = newEnd) 
        }
        seekTo(timeMs)
    }

    fun setZoom(pixelsPerSecond: Float) {
        _state.update { it.copy(pixelsPerSecond = pixelsPerSecond.coerceIn(30f, 1000f)) }
    }

    fun getThumbnailRepository(): ThumbnailRepository? = thumbnailRepository

    fun getPlayer(): Player? = player

    override fun onCleared() {
        super.onCleared()
        player?.release()
        thumbnailRepository?.release()
        playbackProgressJob?.cancel()
    }
}
