package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.data.model.MediaItem
import com.example.util.ContentUriUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerState(
    val currentItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioBoostPercent: Int = 0, // 0 to 100%
    val queue: List<MediaItem> = emptyList(),
    val queueIndex: Int = 0,
    val isShuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val audioRepeatMode: Int = Player.REPEAT_MODE_ALL,
    val videoRepeatMode: Int = Player.REPEAT_MODE_OFF,
    val isAudioShuffleEnabled: Boolean = false,
    val isVideoShuffleEnabled: Boolean = false,
    val isAudioBackgroundPlayEnabled: Boolean = true,
    val isVideoBackgroundPlayEnabled: Boolean = false,
    val activeDecoderName: String = "Hardware Default",
    val isHardwareAccelerated: Boolean = true,
    val droppedFrames: Int = 0,
    val audioDecodeErrors: Int = 0,
    val timestampRecoveryCount: Int = 0,
    val containerName: String = "Unknown",
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val decoderFallbackReason: String? = null,
    val audioSessionId: Int = 0,
    val activeEngineName: String = "Media3"
)

@OptIn(UnstableApi::class)
class ExoPlayerManager private constructor(private val context: Context) {

    companion object {
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ExoPlayerManager? = null

        fun getInstance(context: Context): ExoPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: ExoPlayerManager(context.applicationContext).also { instance = it }
            }
        }

        // Backwards compatibility property
        var activeManager: ExoPlayerManager?
            get() = instance
            set(value) {
                instance = value
            }
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val media3Engine = Media3PlaybackEngine(context)
    private val ffmpegEngine = FFmpegPlaybackEngine(context)
    private var activeEngine: PlaybackEngine = media3Engine

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentDecoderPreference = "AUTO"
    private var isHwAccelEnabled = true
    private var isUninterruptedMode = false
    private var isAutoResumeOnBluetooth = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var isCallActive = false
    private var playOnFocusGain = false
    private var isPausedByCall = false
    private var focusRequest: AudioFocusRequest? = null

    init {
        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.decoderMode.collectLatest { mode ->
                currentDecoderPreference = mode
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.hardwareAccelerationEnabled.collectLatest { enabled ->
                isHwAccelEnabled = enabled
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.uninterruptedMode.collectLatest { enabled ->
                isUninterruptedMode = enabled
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.autoResumeOnBluetooth.collectLatest { enabled ->
                isAutoResumeOnBluetooth = enabled
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.audioBackgroundPlay.collectLatest { enabled ->
                _playerState.value = _playerState.value.copy(isAudioBackgroundPlayEnabled = enabled)
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.videoBackgroundPlay.collectLatest { enabled ->
                _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = enabled)
            }
        }

        setupTelephonyListener()
        setupBluetoothReceiver()

        scope.launch {
            combine(
                com.example.MediaNestApp.instance.settingsManager.showPlaybackNotification,
                com.example.MediaNestApp.instance.settingsManager.showVideoNotification,
                _playerState
            ) { audioNotif, videoNotif, state ->
                Triple(audioNotif, videoNotif, state)
            }.collectLatest { (audioNotif, videoNotif, state) ->
                val currentItem = state.currentItem
                if (currentItem != null && (activeEngine.currentPositionMs < activeEngine.durationMs || state.isPlaying)) {
                    val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.example.data.db.MediaType.VIDEO
                    val bgPlayEnabled = if (isVideo) state.isVideoBackgroundPlayEnabled else state.isAudioBackgroundPlayEnabled
                    
                    if (!bgPlayEnabled && !state.isPlaying) {
                        FloatingPlayerService.stopService(context)
                        return@collectLatest
                    }

                    val shouldShow = if (isVideo) videoNotif else audioNotif
                    if (shouldShow) {
                        FloatingPlayerService.startOrUpdateService(
                            context = context,
                            title = currentItem.title,
                            artist = currentItem.artist ?: currentItem.album ?: currentItem.bucketName ?: "MediaNest",
                            isPlaying = state.isPlaying,
                            artworkUri = currentItem.albumArtUri?.toString() ?: currentItem.uri.toString(),
                            isVideo = isVideo
                        )
                    } else {
                        FloatingPlayerService.stopService(context)
                    }
                } else {
                    FloatingPlayerService.stopService(context)
                }
            }
        }

        // Diagnostic sync job
        scope.launch {
            while (isActive) {
                val diag = activeEngine.diagnosticState.value
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = activeEngine.currentPositionMs,
                    durationMs = activeEngine.durationMs,
                    isPlaying = activeEngine.isPlaying,
                    droppedFrames = diag.droppedFrames,
                    audioDecodeErrors = diag.audioDecodeErrors,
                    timestampRecoveryCount = diag.timestampRecoveryCount,
                    containerName = diag.containerName,
                    videoCodec = diag.videoCodec,
                    audioCodec = diag.audioCodec,
                    activeDecoderName = diag.decoderName,
                    isHardwareAccelerated = diag.isHardwareAccelerated
                )
                delay(200)
            }
        }
    }

    // Expose underlying exoPlayer for backwards compatibility with existing UI / Service bindings
    val exoPlayer: ExoPlayer
        get() = media3Engine.player

    private val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val sorted = if (!isHwAccelEnabled || currentDecoderPreference == "SOFTWARE") {
            decoders.sortedWith(compareByDescending { it.softwareOnly || !it.hardwareAccelerated })
        } else if (currentDecoderPreference == "HARDWARE") {
            decoders.sortedWith(compareByDescending { it.hardwareAccelerated })
        } else {
            decoders.sortedWith(
                compareByDescending<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> { it.hardwareAccelerated }
                    .thenByDescending { it.vendor }
                    .thenBy { it.softwareOnly }
            )
        }
        if (sorted.isEmpty()) {
            MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        } else {
            sorted
        }
    }

    private fun setupTelephonyListener() {
        try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallState(state)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Error setting up telephony listener", e)
        }
    }

    private fun handleCallState(state: Int) {
        val wasCallActive = isCallActive
        isCallActive = state != TelephonyManager.CALL_STATE_IDLE
        
        if (isCallActive && !wasCallActive) {
            if (activeEngine.isPlaying) {
                isPausedByCall = true
                activeEngine.pause()
            }
        } else if (!isCallActive && wasCallActive) {
            if (isPausedByCall) {
                isPausedByCall = false
                activeEngine.play()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) {
                if (isAutoResumeOnBluetooth && !activeEngine.isPlaying && _playerState.value.currentItem != null) {
                    activeEngine.play()
                }
            }
        }
    }

    private fun setupBluetoothReceiver() {
        try {
            val filter = IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(bluetoothReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Error registering bluetooth receiver", e)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (playOnFocusGain) {
                    activeEngine.play()
                    playOnFocusGain = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                playOnFocusGain = false
                activeEngine.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isUninterruptedMode && !isCallActive) {
                    // Ignore transient loss
                } else {
                    playOnFocusGain = activeEngine.isPlaying
                    activeEngine.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!isUninterruptedMode) {
                    activeEngine.setVolume(0.2f)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(false)
                .build()
            
            return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun recordPlay(uriStr: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = com.example.MediaNestApp.instance.database
                val existing = db.playbackStateDao().getPlaybackState(uriStr)
                if (existing != null) {
                    db.playbackStateDao().savePlaybackState(
                        existing.copy(
                            lastPlayedAt = System.currentTimeMillis(),
                            playCount = existing.playCount + 1
                        )
                    )
                } else {
                    db.playbackStateDao().savePlaybackState(
                        com.example.data.db.PlaybackState(
                            mediaUri = uriStr,
                            lastPlayedAt = System.currentTimeMillis(),
                            playCount = 1
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Track and Audio helper methods for UI compatibility
    data class AudioTrackInfo(val index: Int, val name: String, val isSelected: Boolean)

    fun getAvailableAudioTracks(): List<AudioTrackInfo> {
        val tracks = exoPlayer.currentTracks
        val list = mutableListOf<AudioTrackInfo>()
        var index = 0
        for (group in tracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val name = format.language ?: format.label ?: "Audio Track ${index + 1}"
                    list.add(AudioTrackInfo(index, name, group.isTrackSelected(i)))
                    index++
                }
            }
        }
        return list
    }

    fun selectAudioTrack(index: Int) {
        // Track selection logic via ExoPlayer TrackSelector
        try {
            val trackSelector = (exoPlayer as? androidx.media3.exoplayer.ExoPlayer)?.trackSelector
            // Default track selection parameters override
            val currentParameters = trackSelector?.parameters ?: return
            val newParameters = currentParameters.buildUpon()
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                .build()
            trackSelector.setParameters(newParameters)
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Error selecting audio track", e)
        }
    }

    fun setAudioBoost(percent: Int) {
        _playerState.value = _playerState.value.copy(audioBoostPercent = percent)
        // Apply loudness enhancement if supported
    }
    fun playMediaList(items: List<MediaItem>, startIndex: Int = 0, startPosMs: Long = 0L) {
        if (items.isEmpty()) return

        val targetIndex = startIndex.coerceIn(0, items.size - 1)
        val currentTarget = items[targetIndex]

        scope.launch {
            withContext(Dispatchers.Main) {
                try {
                    _playerState.value = _playerState.value.copy(
                        queue = items,
                        queueIndex = targetIndex,
                        currentItem = currentTarget
                    )

                    val profile = MediaCapabilityInspector.inspect(currentTarget.uri)
                    if (profile.requiresFFmpegFallback) {
                        Log.i("ExoPlayerManager", "Routing to FFmpegFallback engine for ${currentTarget.uri} due to profile: $profile")
                        activeEngine.pause()
                        activeEngine = ffmpegEngine
                        _playerState.value = _playerState.value.copy(
                            activeEngineName = "FFmpeg",
                            activeDecoderName = "FFmpeg Software Decoder (${profile.videoCodec} / ${profile.audioCodec})",
                            isHardwareAccelerated = false
                        )
                    } else {
                        Log.i("ExoPlayerManager", "Routing to Media3 hardware engine for ${currentTarget.uri}")
                        activeEngine.pause()
                        activeEngine = media3Engine
                        _playerState.value = _playerState.value.copy(
                            activeEngineName = "Media3",
                            activeDecoderName = "Hardware Default",
                            isHardwareAccelerated = true
                        )
                    }

                    activeEngine.prepare(currentTarget.uri, playWhenReady = true)
                    if (startPosMs > 0) {
                        activeEngine.seekTo(startPosMs)
                    }
                    if (requestAudioFocus()) {
                        activeEngine.play()
                    }
                } catch (e: Exception) {
                    Log.e("ExoPlayerManager", "Failed to prepare playback", e)
                }
            }
        }

        recordPlay(currentTarget.uri.toString())
    }

    fun playSingleUri(uri: Uri?, title: String = "Media", mimeType: String = "") {
        if (uri == null) return
        val item = MediaItem(
            id = uri.hashCode().toLong(),
            title = title,
            uri = uri,
            mimeType = mimeType,
            type = com.example.data.db.MediaType.VIDEO
        )
        playMediaList(listOf(item), 0, 0L)
    }

    // Overload for Context, Uri, Title, MimeType to support legacy callers
    fun playSingleUri(context: Context, uri: Uri?, title: String = "Media", mimeType: String = "") {
        playSingleUri(uri, title, mimeType)
    }

    data class TextTrackInfo(val index: Int, val name: String, val isSelected: Boolean)

    fun getAvailableTextTracks(): List<TextTrackInfo> = emptyList()
    fun selectTextTrack(index: Int) {}
    fun addExternalSubtitle(uri: Uri, name: String = "Subtitle") {}
    fun setVideoBackgroundPlayEnabled(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = enabled)
    }
    fun clearAllCache(onComplete: () -> Unit = {}) {
        onComplete()
    }

    fun addToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val currentQueue = _playerState.value.queue.toMutableList()
        currentQueue.addAll(items)
        _playerState.value = _playerState.value.copy(queue = currentQueue)
    }

    fun seekTo(positionMs: Long) {
        activeEngine.seekTo(positionMs)
    }

    fun seekForward(offsetMs: Long = 10000L) {
        seekTo((activeEngine.currentPositionMs + offsetMs).coerceAtMost(activeEngine.durationMs))
    }

    fun seekBackward(offsetMs: Long = 10000L) {
        seekTo((activeEngine.currentPositionMs - offsetMs).coerceAtLeast(0L))
    }

    fun next() {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        val nextIndex = (state.queueIndex + 1) % state.queue.size
        playMediaList(state.queue, nextIndex, 0L)
    }

    fun previous() {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        val prevIndex = if (state.queueIndex - 1 < 0) state.queue.size - 1 else state.queueIndex - 1
        playMediaList(state.queue, prevIndex, 0L)
    }

    fun togglePlayPause() {
        if (activeEngine.isPlaying) {
            activeEngine.pause()
        } else {
            if (requestAudioFocus()) {
                activeEngine.play()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        activeEngine.setPlaybackSpeed(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun setRepeatMode(repeatMode: Int) {
        activeEngine.setRepeatMode(repeatMode)
        _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
    }

    fun setShuffleMode(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isShuffle = enabled)
    }

    fun setVideoSurface(surface: Surface?) {
        activeEngine.setSurface(surface)
    }

    fun stopPlayback() {
        activeEngine.pause()
        abandonAudioFocus()
        FloatingPlayerService.stopService(context)
        _playerState.value = PlayerState()
    }

    fun release() {
        stopPlayback()
        media3Engine.release()
        ffmpegEngine.release()
        instance = null
    }
}
