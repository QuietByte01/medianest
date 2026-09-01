package com.medianest.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * CompositionLocal providing whether video auto-previews are enabled in library grids.
 */
val LocalAutoPlayVideoPreviews = compositionLocalOf { true }

/**
 * CompositionLocal providing whether GIF auto-play is enabled in library grids.
 */
val LocalAutoPlayGifPreviews = compositionLocalOf { true }

/**
 * Coordinates video previews to only play one at a time,
 * starting from the 1st video in the visible area and smoothly advancing sequentially.
 */
object SlideShowVideoPreviewCoordinator {
    private val visibleUris = mutableListOf<Uri>()
    private val _activeUri = MutableStateFlow<Uri?>(null)
    val activeUri: StateFlow<Uri?> = _activeUri
    
    private val _isAudioMuted = MutableStateFlow(true)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun register(uri: Uri) {
        if (!visibleUris.contains(uri)) {
            visibleUris.add(uri)
        }
        // Always start with the first item in the visible area if not currently playing a valid item
        if (_activeUri.value == null || !visibleUris.contains(_activeUri.value)) {
            _activeUri.value = visibleUris.firstOrNull()
            startTimer()
        } else if (timerJob == null || timerJob?.isActive != true) {
            startTimer()
        }
    }

    fun unregister(uri: Uri) {
        visibleUris.remove(uri)
        if (_activeUri.value == uri) {
            // Pick the next available visible video or start from the top
            _activeUri.value = visibleUris.firstOrNull()
            if (_activeUri.value != null) {
                startTimer()
            } else {
                timerJob?.cancel()
            }
        }
    }

    fun onVideoEnded(uri: Uri) {
        if (_activeUri.value == uri) {
            next()
        }
    }

    fun toggleAudio() {
        _isAudioMuted.value = !_isAudioMuted.value
    }

    fun next() {
        if (visibleUris.isEmpty()) {
            _activeUri.value = null
            timerJob?.cancel()
            return
        }
        val currentIdx = _activeUri.value?.let { visibleUris.indexOf(it) } ?: -1
        val nextIdx = if (currentIdx != -1 && currentIdx + 1 < visibleUris.size) currentIdx + 1 else 0
        _activeUri.value = visibleUris[nextIdx]
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(12_000L) // Advance every 12 seconds if video hasn't ended sooner
            if (visibleUris.size > 1) {
                next()
            }
        }
    }
}

/**
 * Lightweight, isolated in-place video-only preview player.
 *
 * Uses SlideShowVideoPreviewCoordinator to ensure one video plays at a time starting from the
 * 1st visible item, advancing automatically to the next item in the viewport.
 *
 * @param uri Video media URI to play.
 * @param modifier Layout modifier.
 */
@OptIn(UnstableApi::class)
@Composable
fun LibraryVideoPreviewView(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriString = remember(uri) { uri.toString() }

    val activeUri by SlideShowVideoPreviewCoordinator.activeUri.collectAsState()
    val isAudioMuted by SlideShowVideoPreviewCoordinator.isAudioMuted.collectAsState()
    val isActive = activeUri == uri

    DisposableEffect(uriString) {
        SlideShowVideoPreviewCoordinator.register(uri)
        onDispose {
            SlideShowVideoPreviewCoordinator.unregister(uri)
        }
    }

    if (isActive) {
        var isFirstFrameRendered by remember(uriString) { mutableStateOf(false) }

        val exoPlayer = remember(uriString) {
            ExoPlayer.Builder(context)
                .setLoadControl(
                    androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(500, 1500, 250, 500)
                        .build()
                )
                .build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = if (isAudioMuted) 0f else 1f
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, isAudioMuted)
                        .build()
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            isFirstFrameRendered = true
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                SlideShowVideoPreviewCoordinator.onVideoEnded(uri)
                            }
                        }
                    })
                    prepare()
                }
        }

        LaunchedEffect(isAudioMuted) {
            exoPlayer.volume = if (isAudioMuted) 0f else 1f
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, isAudioMuted)
                .build()
        }

        DisposableEffect(uriString) {
            onDispose {
                exoPlayer.release()
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx).inflate(com.medianest.R.layout.player_view_texture, null, false) as PlayerView
                    view.apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isFirstFrameRendered) 1f else 0f
                    }
            )
        }
    }
}
