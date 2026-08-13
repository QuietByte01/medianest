package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.data.model.MediaItem
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
    val audioBoostPercent: Int = 0,
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

        var activeManager: ExoPlayerManager?
            get() = instance
            set(value) { instance = value }
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentDecoderPreference = "AUTO"
    private var isHwAccelEnabled = true
    private var isUninterruptedMode = false
    private var isAutoResumeOnBluetooth = false

    private val customMediaCodecSelector by lazy {
        MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            try {
                val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                val sorted = if (!isHwAccelEnabled || currentDecoderPreference == "SOFTWARE") {
                    decoders.sortedWith(compareByDescending { it.softwareOnly || !it.hardwareAccelerated })
                } else if (currentDecoderPreference == "HARDWARE") {
                    decoders.sortedWith(compareByDescending { it.hardwareAccelerated })
                } else {
                    decoders.sortedWith(compareByDescending<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> { it.hardwareAccelerated }.thenByDescending { it.vendor }.thenBy { it.softwareOnly })
                }
                sorted.ifEmpty { MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling) }
            } catch (e: Exception) {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
            }
        }
    }

    private val ffmpegEngine = FFmpegPlaybackEngine(context)
    private var media3Engine: Media3PlaybackEngine? = null
    private var activeEngine: PlaybackEngine? = null
    private var lastSurface: Surface? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var isCallActive = false
    private var playOnFocusGain = false
    private var isPausedByCall = false
    private var focusRequest: AudioFocusRequest? = null

    init {
        initializeMedia3Engine()
        
        scope.launch {
            val settings = com.example.MediaNestApp.instance.settingsManager
            launch { settings.decoderMode.collectLatest { if (currentDecoderPreference != it) { currentDecoderPreference = it; recreatePlayerBySettings() } } }
            launch { settings.hardwareAccelerationEnabled.collectLatest { if (isHwAccelEnabled != it) { isHwAccelEnabled = it; recreatePlayerBySettings() } } }
            launch { settings.uninterruptedMode.collectLatest { isUninterruptedMode = it } }
            launch { settings.autoResumeOnBluetooth.collectLatest { isAutoResumeOnBluetooth = it } }
            launch { settings.audioBackgroundPlay.collectLatest { _playerState.value = _playerState.value.copy(isAudioBackgroundPlayEnabled = it) } }
            launch { settings.videoBackgroundPlay.collectLatest { _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = it) } }
            launch {
                combine(settings.showPlaybackNotification, settings.showVideoNotification, _playerState) { a, v, s -> Triple(a, v, s) }
                    .collectLatest { (audioNotif, videoNotif, state) -> handleNotificationUpdate(audioNotif, videoNotif, state) }
            }
        }

        setupTelephonyListener()
        setupBluetoothReceiver()

        scope.launch {
            while (isActive) {
                val engine = activeEngine
                if (engine != null) {
                    val diag = engine.diagnosticState.value
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = engine.currentPositionMs,
                        durationMs = engine.durationMs,
                        isPlaying = engine.isPlaying,
                        droppedFrames = diag.droppedFrames,
                        audioDecodeErrors = diag.audioDecodeErrors,
                        timestampRecoveryCount = diag.timestampRecoveryCount,
                        containerName = diag.containerName,
                        videoCodec = diag.videoCodec,
                        audioCodec = diag.audioCodec,
                        activeDecoderName = diag.decoderName,
                        isHardwareAccelerated = diag.isHardwareAccelerated
                    )
                }
                delay(200)
            }
        }
    }

    private fun initializeMedia3Engine() {
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(2000, 30000, 250, 1500).build()
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true).setMediaCodecSelector(customMediaCodecSelector)
        val player = ExoPlayer.Builder(context, renderersFactory).setMediaSourceFactory(mediaSourceFactory).setLoadControl(loadControl).setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), false).build()
        media3Engine = Media3PlaybackEngine(context, player)
        if (activeEngine == null) activeEngine = media3Engine
    }

    private fun recreatePlayerBySettings() {
        val wasPlaying = activeEngine?.isPlaying ?: false
        val pos = activeEngine?.currentPositionMs ?: 0L
        val item = _playerState.value.currentItem

        activeEngine?.pause()
        media3Engine?.release()
        initializeMedia3Engine()
        
        if (item != null) {
            scope.launch {
                val probeResult = withContext(Dispatchers.IO) { ffmpegEngine.probe(item.uri) }
                val profile = MediaCapabilityInspector.inspect(item.uri, probeResult)
                val forceHW = currentDecoderPreference == "HARDWARE"
                
                activeEngine = if (profile.requiresFFmpegFallback && !forceHW) ffmpegEngine else media3Engine
                _playerState.value = _playerState.value.copy(activeEngineName = if (activeEngine == ffmpegEngine) "FFmpeg" else "Media3")
                
                // If we switched to FFmpeg, apply the surface manually.
                // If we are on Media3, the VideoPlayerScreen's AndroidView will attach the surface itself.
                if (activeEngine == ffmpegEngine) activeEngine?.setSurface(lastSurface)
                
                activeEngine?.prepare(item.uri, wasPlaying)
                activeEngine?.seekTo(pos)
            }
        }
    }

    val exoPlayer: ExoPlayer
        get() = media3Engine?.player ?: throw IllegalStateException("Media3 Engine not initialized")

    private fun handleNotificationUpdate(audioNotif: Boolean, videoNotif: Boolean, state: PlayerState) {
        val currentItem = state.currentItem ?: return
        val engine = activeEngine ?: return
        if (engine.currentPositionMs < engine.durationMs || state.isPlaying) {
            val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.example.data.db.MediaType.VIDEO
            val bgPlayEnabled = if (isVideo) state.isVideoBackgroundPlayEnabled else state.isAudioBackgroundPlayEnabled
            if (!bgPlayEnabled && !state.isPlaying) { FloatingPlayerService.stopService(context); return }
            if (if (isVideo) videoNotif else audioNotif) {
                FloatingPlayerService.startOrUpdateService(context, currentItem.title, currentItem.artist ?: currentItem.album ?: "MediaNest", state.isPlaying, currentItem.albumArtUri?.toString() ?: currentItem.uri.toString(), isVideo)
            } else FloatingPlayerService.stopService(context)
        } else FloatingPlayerService.stopService(context)
    }

    private fun setupTelephonyListener() {
        try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = handleCallState(state)
                })
            }
        } catch (_: Exception) {}
    }

    private fun handleCallState(state: Int) {
        isCallActive = state != TelephonyManager.CALL_STATE_IDLE
        if (isCallActive) {
            if (activeEngine?.isPlaying == true) { isPausedByCall = true; activeEngine?.pause() }
        } else if (isPausedByCall) { isPausedByCall = false; activeEngine?.play() }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) {
                if (isAutoResumeOnBluetooth && activeEngine?.isPlaying == false && _playerState.value.currentItem != null) activeEngine?.play()
            }
        }
    }

    private fun setupBluetoothReceiver() {
        try {
            val filter = IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(bluetoothReceiver, filter)
        } catch (_: Exception) {}
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> if (playOnFocusGain) { activeEngine?.play(); playOnFocusGain = false }
            AudioManager.AUDIOFOCUS_LOSS -> { playOnFocusGain = false; activeEngine?.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (!(isUninterruptedMode && !isCallActive)) { playOnFocusGain = activeEngine?.isPlaying == true; activeEngine?.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (!isUninterruptedMode) activeEngine?.setVolume(0.2f)
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AndroidAudioAttributes.Builder().setUsage(AndroidAudioAttributes.USAGE_MEDIA).setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC).build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attr).setAcceptsDelayedFocusGain(true).setOnAudioFocusChangeListener(audioFocusChangeListener).setWillPauseWhenDucked(false).build()
            return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return true
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    fun playMediaList(items: List<MediaItem>, startIndex: Int = 0, startPosMs: Long = 0L) {
        if (items.isEmpty()) return
        val targetIndex = startIndex.coerceIn(0, items.size - 1)
        val currentTarget = items[targetIndex]
        scope.launch {
            withContext(Dispatchers.Main) {
                try {
                    _playerState.value = _playerState.value.copy(queue = items, queueIndex = targetIndex, currentItem = currentTarget)
                    val probeResult = withContext(Dispatchers.IO) { ffmpegEngine.probe(currentTarget.uri) }
                    val profile = MediaCapabilityInspector.inspect(currentTarget.uri, probeResult)
                    val forceHW = currentDecoderPreference == "HARDWARE"
                    
                    val newEngine = if (profile.requiresFFmpegFallback && !forceHW) ffmpegEngine else media3Engine
                    val newEngineName = if (newEngine == ffmpegEngine) "FFmpeg" else "Media3"
                    
                    activeEngine?.pause()
                    activeEngine = newEngine
                    
                    Log.i("ExoPlayerManager", "Selected engine $newEngineName for ${currentTarget.title}")
                    
                    _playerState.value = _playerState.value.copy(
                        activeEngineName = newEngineName,
                        isHardwareAccelerated = newEngine == media3Engine
                    )

                    if (activeEngine == ffmpegEngine) activeEngine?.setSurface(lastSurface)
                    activeEngine?.prepare(currentTarget.uri, true)
                    if (startPosMs > 0) activeEngine?.seekTo(startPosMs)
                    if (requestAudioFocus()) activeEngine?.play()
                } catch (e: Exception) { Log.e("ExoPlayerManager", "Failed to prepare playback", e) }
            }
        }
    }

    fun playSingleUri(uri: Uri?, title: String = "Media", mimeType: String = "") {
        if (uri == null) return
        playMediaList(listOf(MediaItem(id = uri.hashCode().toLong(), title = title, uri = uri, mimeType = mimeType, type = com.example.data.db.MediaType.VIDEO)), 0, 0L)
    }

    fun playSingleUri(context: Context, uri: Uri?, title: String = "Media", mimeType: String = "") = playSingleUri(uri, title, mimeType)
    fun seekTo(positionMs: Long) = activeEngine?.seekTo(positionMs)
    fun seekForward(offsetMs: Long = 10000L) = seekTo(((activeEngine?.currentPositionMs ?: 0L) + offsetMs).coerceAtMost(activeEngine?.durationMs ?: 0L))
    fun seekBackward(offsetMs: Long = 10000L) = seekTo(((activeEngine?.currentPositionMs ?: 0L) - offsetMs).coerceAtLeast(0L))
    fun next() = _playerState.value.let { if (it.queue.isNotEmpty()) playMediaList(it.queue, (it.queueIndex + 1) % it.queue.size, 0L) }
    fun previous() = _playerState.value.let { if (it.queue.isNotEmpty()) playMediaList(it.queue, if (it.queueIndex - 1 < 0) it.queue.size - 1 else it.queueIndex - 1, 0L) }
    fun togglePlayPause() {
        if (activeEngine?.isPlaying == true) { activeEngine?.pause(); _playerState.value = _playerState.value.copy(isPlaying = false) }
        else if (requestAudioFocus()) { activeEngine?.play(); _playerState.value = _playerState.value.copy(isPlaying = true) }
    }
    fun setPlaybackSpeed(speed: Float) { activeEngine?.setPlaybackSpeed(speed); _playerState.value = _playerState.value.copy(playbackSpeed = speed) }
    fun setRepeatMode(repeatMode: Int) { activeEngine?.setRepeatMode(repeatMode); _playerState.value = _playerState.value.copy(repeatMode = repeatMode) }
    fun setShuffleMode(enabled: Boolean) { _playerState.value = _playerState.value.copy(isShuffle = enabled) }
    
    fun setVideoSurface(surface: Surface?) { 
        lastSurface = surface
        // Manual surface steering is only for FFmpeg.
        // Media3 handles its surface internally via PlayerView.
        if (activeEngine == ffmpegEngine) {
            activeEngine?.setSurface(surface)
        }
    }
    
    fun release() { activeEngine?.pause(); media3Engine?.release(); ffmpegEngine.release(); instance = null }
    
    data class AudioTrackInfo(val index: Int, val name: String, val isSelected: Boolean)
    data class TextTrackInfo(val index: Int, val name: String, val isSelected: Boolean)

    fun getAvailableAudioTracks() = media3Engine?.player?.currentTracks?.let { t -> 
        val list = mutableListOf<AudioTrackInfo>()
        var idx = 0
        t.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g -> 
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                list.add(AudioTrackInfo(idx++, f.language ?: f.label ?: "Track $idx", g.isTrackSelected(i)))
            }
        }
        list
    } ?: emptyList()

    fun selectAudioTrack(index: Int) {
        try {
            val player = media3Engine?.player ?: return
            val tracks = player.currentTracks
            var targetGroup: androidx.media3.common.Tracks.Group? = null
            var trackIndexInGroup = -1
            var currentIndex = 0
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        if (currentIndex == index) { targetGroup = group; trackIndexInGroup = i; break }
                        currentIndex++
                    }
                }
                if (targetGroup != null) break
            }
            if (targetGroup != null) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(androidx.media3.common.TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIndexInGroup))
                    .build()
            }
        } catch (_: Exception) {}
    }

    fun setAudioBoost(percent: Int) {}
    fun getAvailableTextTracks(): List<TextTrackInfo> = emptyList()
    fun selectTextTrack(index: Int) {}
    fun addExternalSubtitle(uri: Uri, name: String = "Subtitle") {}
    fun setVideoBackgroundPlayEnabled(enabled: Boolean) { _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = enabled) }
    fun clearAllCache(onComplete: () -> Unit = {}) { onComplete() }
    fun addToQueue(items: List<MediaItem>) {}
    fun stopPlayback() { activeEngine?.pause(); abandonAudioFocus(); FloatingPlayerService.stopService(context); _playerState.value = PlayerState() }
}
