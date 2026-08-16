package com.medianest.player

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
import com.medianest.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
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
    val activeEngineName: String = "Media3",
    // DSP Settings
    val bassBoostPercent: Int = 0,
    val volumeBoostPercent: Int = 0,
    val isEqEnabled: Boolean = false,
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f), // 5 bands default
    val isDolbyEnabled: Boolean = false,
    val pitchSemitones: Int = 0,
    val isVocalMuteEnabled: Boolean = false,
    val isLoudnessNormalizerEnabled: Boolean = false
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
            val settings = com.medianest.MediaNestApp.instance.settingsManager
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
                    // Grab the live session ID from the underlying ExoPlayer instance.
                    // This ensures the correct (non-zero) session ID is always exposed
                    // to AudioVisualizer, which requires it on Android 11+.
                    val liveSessionId = (engine as? Media3PlaybackEngine)?.player?.audioSessionId ?: 0
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
                        isHardwareAccelerated = diag.isHardwareAccelerated,
                        audioSessionId = if (liveSessionId != 0 && liveSessionId != C.AUDIO_SESSION_ID_UNSET) liveSessionId else _playerState.value.audioSessionId
                    )
                }
                delay(200)
            }
        }
    }

    private var androidEqualizer: android.media.audiofx.Equalizer? = null
    private var androidBassBoost: android.media.audiofx.BassBoost? = null
    private var androidLoudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    private fun updateAndroidAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        try {
            if (androidEqualizer == null || androidEqualizer?.hasControl() == false) {
                androidEqualizer?.release()
                androidEqualizer = android.media.audiofx.Equalizer(0, audioSessionId).apply {
                    enabled = _playerState.value.isEqEnabled
                }
            }
            if (androidBassBoost == null || androidBassBoost?.hasControl() == false) {
                androidBassBoost?.release()
                androidBassBoost = android.media.audiofx.BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
            }
            if (androidLoudnessEnhancer == null || androidLoudnessEnhancer?.hasControl() == false) {
                androidLoudnessEnhancer?.release()
                androidLoudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                    enabled = true
                }
            }
            applyExoAudioEffects()
        } catch (e: Exception) {
            Log.w("ExoPlayerManager", "AudioFX init warning: ${e.message}")
        }
    }

    private fun applyExoAudioEffects() {
        val state = _playerState.value
        try {
            androidBassBoost?.let { bb ->
                if (state.bassBoostPercent > 0) {
                    bb.enabled = true
                    bb.setStrength((state.bassBoostPercent * 10).toShort().coerceIn(0, 1000))
                } else {
                    bb.enabled = false
                }
            }
            androidEqualizer?.let { eq ->
                eq.enabled = state.isEqEnabled
                if (state.isEqEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    val bandLevelRange = eq.bandLevelRange
                    val minMb = bandLevelRange.getOrNull(0) ?: -1000
                    val maxMb = bandLevelRange.getOrNull(1) ?: 1000
                    for (i in 0 until numBands) {
                        val gainDb = state.eqBands.getOrElse(i) { 0f }
                        val levelMb = (gainDb * 100).toInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                        eq.setBandLevel(i.toShort(), levelMb)
                    }
                }
            }
            androidLoudnessEnhancer?.let { le ->
                if (state.volumeBoostPercent > 0) {
                    le.enabled = true
                    le.setTargetGain((state.volumeBoostPercent * 30).coerceIn(0, 3000))
                } else {
                    le.enabled = false
                }
            }
        } catch (e: Exception) {
            Log.w("ExoPlayerManager", "Apply AudioFX error: ${e.message}")
        }
    }

    private fun initializeMedia3Engine() {
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(15000, 50000, 1500, 3000).build()
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true).setMediaCodecSelector(customMediaCodecSelector)
        val player = ExoPlayer.Builder(context, renderersFactory).setMediaSourceFactory(mediaSourceFactory).setLoadControl(loadControl).setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), false).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("ExoPlayerManager", "Player error encountered: ${error.message}", error)
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                updateAndroidAudioEffects(audioSessionId)
                // Propagate the real session ID to playerState so AudioVisualizer
                // receives a valid (non-zero) session ID on Android 11+.
                if (audioSessionId != 0 && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    _playerState.value = _playerState.value.copy(audioSessionId = audioSessionId)
                }
            }
        })
        updateAndroidAudioEffects(player.audioSessionId)
        // Seed playerState immediately with the session ID assigned at player creation.
        if (player.audioSessionId != 0 && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            _playerState.value = _playerState.value.copy(audioSessionId = player.audioSessionId)
        }
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
                if (activeEngine == ffmpegEngine) {
                    activeEngine?.setSurface(lastSurface)
                    updateNativeFilters()
                }
                
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
            val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.medianest.data.db.MediaType.VIDEO
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
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleCallState(state)
                }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
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

                    if (currentTarget.type == com.medianest.data.db.MediaType.AUDIO && (currentTarget.artist == null || currentTarget.title.startsWith("Track") || currentTarget.title == "Media")) {
                        scope.launch(Dispatchers.IO) {
                            val enriched = com.medianest.util.AudioMetadataUtils.extractMetadata(context, currentTarget.uri, rawTitleHint = currentTarget.title, mimeTypeHint = currentTarget.mimeType)
                            withContext(Dispatchers.Main) {
                                val currentQ = _playerState.value.queue.toMutableList()
                                val curIndex = _playerState.value.queueIndex
                                if (curIndex in currentQ.indices) {
                                    currentQ[curIndex] = enriched
                                }
                                _playerState.value = _playerState.value.copy(queue = currentQ, currentItem = enriched)
                                com.medianest.player.FloatingPlayerService.startOrUpdateService(
                                    context = context,
                                    title = enriched.title,
                                    artist = enriched.artist ?: "Unknown Artist",
                                    isPlaying = _playerState.value.isPlaying,
                                    artworkUri = enriched.albumArtUri?.toString() ?: "",
                                    isVideo = false
                                )
                            }
                        }
                    }

                    if (activeEngine == ffmpegEngine) {
                        activeEngine?.setSurface(lastSurface)
                        updateNativeFilters() // Apply DSP settings to FFmpeg
                    }
                    activeEngine?.prepare(currentTarget.uri, true)
                    if (startPosMs > 0) activeEngine?.seekTo(startPosMs)
                    if (requestAudioFocus()) activeEngine?.play()
                } catch (e: Exception) { Log.e("ExoPlayerManager", "Failed to prepare playback", e) }
            }
        }
    }

    fun playSingleUri(uri: Uri?, title: String = "Media", mimeType: String = "") {
        if (uri == null) return
        scope.launch(Dispatchers.IO) {
            val item = com.medianest.util.AudioMetadataUtils.extractMetadata(context, uri, rawTitleHint = title, mimeTypeHint = mimeType)
            withContext(Dispatchers.Main) {
                playMediaList(listOf(item), 0, 0L)
            }
        }
    }

    fun playSingleUri(ctx: Context, uri: Uri?, title: String = "Media", mimeType: String = "") = playSingleUri(uri, title, mimeType)
    fun seekTo(positionMs: Long) = activeEngine?.seekTo(positionMs)
    fun seekForward(offsetMs: Long = 10000L) = seekTo(((activeEngine?.currentPositionMs ?: 0L) + offsetMs).coerceAtMost(activeEngine?.durationMs ?: 0L))
    fun seekBackward(offsetMs: Long = 10000L) = seekTo(((activeEngine?.currentPositionMs ?: 0L) - offsetMs).coerceAtLeast(0L))
    fun next() = _playerState.value.let { if (it.queue.isNotEmpty()) playMediaList(it.queue, (it.queueIndex + 1) % it.queue.size, 0L) }
    fun previous() = _playerState.value.let { if (it.queue.isNotEmpty()) { if ((activeEngine?.currentPositionMs ?: 0L) > 3000L) { activeEngine?.seekTo(0L) } else { playMediaList(it.queue, if (it.queueIndex - 1 < 0) it.queue.size - 1 else it.queueIndex - 1, 0L) } } }
    fun play() {
        if (requestAudioFocus()) {
            activeEngine?.play()
            // State is confirmed by the 200ms polling loop; no optimistic override here
        }
    }

    fun pause() {
        activeEngine?.pause()
        abandonAudioFocus()
        // State is confirmed by the 200ms polling loop
    }

    fun stop() {
        activeEngine?.pause()
        activeEngine?.seekTo(0L)
        abandonAudioFocus()
    }

    val isPlaying: Boolean
        get() = activeEngine?.isPlaying == true

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }
    fun onEnginePlaybackEnded() {
        scope.launch(Dispatchers.Main) {
            val state = _playerState.value
            when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> {
                    seekTo(0L)
                    play()
                }
                Player.REPEAT_MODE_ALL -> {
                    next()
                }
                Player.REPEAT_MODE_OFF -> {
                    if (state.queueIndex < state.queue.size - 1) {
                        next()
                    } else {
                        pause()
                        seekTo(0L)
                    }
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) { activeEngine?.setPlaybackSpeed(speed); _playerState.value = _playerState.value.copy(playbackSpeed = speed) }
    fun setRepeatMode(repeatMode: Int) { 
        activeEngine?.setRepeatMode(repeatMode)
        _playerState.value = _playerState.value.copy(
            repeatMode = repeatMode,
            audioRepeatMode = repeatMode,
            videoRepeatMode = repeatMode
        )
    }
    fun setShuffleMode(enabled: Boolean) { _playerState.value = _playerState.value.copy(isShuffle = enabled) }
    
    fun setVideoSurface(surface: Surface?) { 
        lastSurface = surface
        // Manual surface steering is only for FFmpeg.
        // Media3 handles its surface internally via PlayerView.
        if (activeEngine == ffmpegEngine) {
            activeEngine?.setSurface(surface)
        }
    }
    
    fun release() {
        activeEngine?.pause()
        media3Engine?.release()
        ffmpegEngine.release()
        androidEqualizer?.release(); androidEqualizer = null
        androidBassBoost?.release(); androidBassBoost = null
        androidLoudnessEnhancer?.release(); androidLoudnessEnhancer = null
        abandonAudioFocus()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        instance = null
    }
    
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

    fun setAudioBoost(percent: Int) {
        setVolumeBoost(percent)
    }
    fun getAvailableTextTracks(): List<TextTrackInfo> = emptyList()
    fun selectTextTrack(index: Int) {}
    fun addExternalSubtitle(uri: Uri, name: String = "Subtitle") {}
    
    fun setBassBoost(percent: Int) {
        _playerState.value = _playerState.value.copy(bassBoostPercent = percent)
        updateNativeFilters()
    }

    fun setVolumeBoost(percent: Int) {
        _playerState.value = _playerState.value.copy(volumeBoostPercent = percent)
        updateNativeFilters()
    }

    fun setEqBands(bands: List<Float>) {
        _playerState.value = _playerState.value.copy(eqBands = bands)
        updateNativeFilters()
    }

    fun setEqEnabled(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isEqEnabled = enabled)
        updateNativeFilters()
    }

    fun setPitchSemitones(semitones: Int) {
        _playerState.value = _playerState.value.copy(pitchSemitones = semitones)
        updateNativeFilters()
    }

    fun setVocalMute(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isVocalMuteEnabled = enabled)
        updateNativeFilters()
    }

    fun setLoudnessNormalizer(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isLoudnessNormalizerEnabled = enabled)
        updateNativeFilters()
    }

    private fun updateNativeFilters() {
        val state = _playerState.value
        val filterList = mutableListOf<String>()
        
        // 1. Vocal Mute / Center Channel Cancellation
        if (state.isVocalMuteEnabled) {
            filterList.add("pan=stereo|c0=c0-c1|c1=c1-c0")
        }

        // 2. Pitch Shifter (Semitones via rubberband or asetrate)
        if (state.pitchSemitones != 0) {
            val factor = 2.0.pow(state.pitchSemitones / 12.0)
            filterList.add("asetrate=48000*$factor,aresample=48000")
        }

        // 3. EBU R128 Loudness Normalizer
        if (state.isLoudnessNormalizerEnabled) {
            filterList.add("dynaudnorm=f=150:g=15")
        }

        // 4. Bass Boost
        if (state.bassBoostPercent > 0) {
            val gain = (state.bassBoostPercent / 100f) * 15 // Max 15dB
            filterList.add("bass=g=$gain:f=100:w=0.5")
        }
        
        // 5. EQ Bands (5-band)
        if (state.isEqEnabled) {
            val freqs = listOf(60, 230, 910, 3600, 14000)
            state.eqBands.forEachIndexed { index, gain ->
                if (index < freqs.size && gain != 0f) {
                    filterList.add("equalizer=f=${freqs[index]}:t=q:w=1:g=$gain")
                }
            }
        }
        
        // 6. Volume Boost
        if (state.volumeBoostPercent > 0) {
            val multiplier = 1.0f + (state.volumeBoostPercent / 100f) * 2.0f // Up to 3.0x (300%)
            filterList.add("volume=$multiplier")
        }
        
        val fullFilterDesc = if (filterList.isEmpty()) "anull" else filterList.joinToString(",")
        ffmpegEngine.setAudioFilters(fullFilterDesc)
        applyExoAudioEffects()
    }

    fun setVideoBackgroundPlayEnabled(enabled: Boolean) { _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = enabled) }
    fun clearAllCache(onComplete: () -> Unit = {}) { onComplete() }
    fun addToQueue(items: List<MediaItem>) {}
    fun stopPlayback() { activeEngine?.pause(); abandonAudioFocus(); FloatingPlayerService.stopService(context); _playerState.value = PlayerState() }
}
