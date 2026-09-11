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
import com.medianest.util.Logger
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.media.MediaEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow

// @Stable: tells Compose compiler this type's equals() is stable and consistent so
// composables receiving PlayerState can be skipped when their read fields haven't changed.
@androidx.compose.runtime.Stable
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
    val currentBitrate: Long = 0L,
    val droppedFrames: Int = 0,
    val audioDecodeErrors: Int = 0,
    val audioMissingFrames: Int = 0,
    val corruptedFrames: Int = 0,
    val bitrateHistory: List<Long> = emptyList(),
    val timestampRecoveryCount: Int = 0,
    val containerName: String = "Unknown",
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val decoderFallbackReason: String? = null,
    val audioSessionId: Int = 0,
    val activeEngineName: String = "Media3",
    val audioSampleFormat: String = "32-bit Float",
    val audioSharingMode: String = "AAudio Exclusive",
    // DSP Settings
    val bassBoostPercent: Int = 0,
    val volumeBoostPercent: Int = 0,
    val isEqEnabled: Boolean = false,
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f), // 5 bands default
    val isDolbyEnabled: Boolean = false,
    val pitchSemitones: Int = 0,
    val isVocalMuteEnabled: Boolean = false,
    val isLoudnessNormalizerEnabled: Boolean = false,
    val isSystemVolumeMaxed: Boolean = false,
    val queueTitle: String? = null,
    val abRepeatState: AbRepeatState = AbRepeatState(),
    val abRepeatA: Long? = null,
    val abRepeatB: Long? = null,
    val isAbRepeatActive: Boolean = false,
    val media3InstanceId: Long = 0L,
    val isHdrContent: Boolean = false,
    val hdrType: String = "SDR",
    val colorSpace: String = "SDR"
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
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    /**
     * Lightweight playback position state emitted every 200ms during playback.
     * Composables that only need seek-bar progress (position/duration/isPlaying) should
     * collect this instead of [playerState] to avoid recomposing the full player screen
     * 5 times per second.
     */
    @androidx.compose.runtime.Stable
    data class PlaybackPositionState(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val isPlaying: Boolean = false
    )
    private val _playbackPositionState = MutableStateFlow(PlaybackPositionState())
    val playbackPositionState: StateFlow<PlaybackPositionState> = _playbackPositionState.asStateFlow()

    val abRepeatController = AbRepeatController { activeEngine?.seekTo(it) }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settingsManager by lazy { com.medianest.MediaNestApp.instance.settingsManager }
    private var currentDecoderPreference = "AUTO"
    private var isHwAccelEnabled = true
    private var isUninterruptedMode = false
    private var isAutoResumeOnBluetooth = false
    private val hiddenFolderPaths = MutableStateFlow<Set<String>>(emptySet())

    private val customMediaCodecSelector by lazy {
        MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            try {
                val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                if (decoders.isEmpty()) return@MediaCodecSelector decoders
                
                when (currentDecoderPreference) {
                    "SOFTWARE" -> decoders.sortedWith(compareByDescending { it.softwareOnly || !it.hardwareAccelerated })
                    "HARDWARE" -> decoders.sortedWith(compareByDescending { it.hardwareAccelerated })
                    else -> if (!isHwAccelEnabled) {
                        decoders.sortedWith(compareByDescending { it.softwareOnly || !it.hardwareAccelerated })
                    } else {
                        decoders
                    }
                }
            } catch (e: Exception) {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
            }
        }
    }

    private val ffmpegEngine = FFmpegPlaybackEngine(context)
    private var media3Engine: Media3PlaybackEngine? = null
    private var activeEngine: PlaybackEngine? = null
    internal var lastSurface: Surface? = null
    private var activeAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var watchdogJob: kotlinx.coroutines.Job? = null
    private var isHardwareFaulty = false // Flag to disable Media3 if it keeps timing out
    private var av1FailureCount = 0
    private var audioFxFailureCount = 0
    private var instanceIdCounter = 1L
    private val brokenEngines = java.util.Collections.synchronizedList(mutableListOf<Media3PlaybackEngine>())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var telephonyCallback: Any? = null
    private var isCallActive = false
    private var playOnFocusGain = false
    private var isPausedByCall = false
    private var isScrubbing = false
    private var wasPlayingBeforeScrub = false
    private var focusRequest: AudioFocusRequest? = null

    // FIXME: Inefficient 200ms polling loop. Polling for position/duration is resource-intensive.
    // Should consider a more efficient event-based update or a shorter interval only during active playback.
    init {
        initializeMedia3Engine()
        
        scope.launch {
            val settings = com.medianest.MediaNestApp.instance.settingsManager
            launch { settings.decoderMode.collectLatest { if (currentDecoderPreference != it) { currentDecoderPreference = it; recreatePlayerBySettings() } } }
            launch { settings.hardwareAccelerationEnabled.collectLatest { if (isHwAccelEnabled != it) { isHwAccelEnabled = it; recreatePlayerBySettings() } } }
            launch { settings.uninterruptedMode.collectLatest { isUninterruptedMode = it } }
            launch { settings.autoResumeOnBluetooth.collectLatest { isAutoResumeOnBluetooth = it } }
            launch { settings.hiddenFolders.collectLatest { hiddenFolderPaths.value = it } }
            launch { settings.audioBackgroundPlay.collectLatest { _playerState.value = _playerState.value.copy(isAudioBackgroundPlayEnabled = it) } }
            launch { settings.videoBackgroundPlay.collectLatest { _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = it) } }
            launch {
                combine(settings.showPlaybackNotification, settings.showVideoNotification, _playerState) { a, v, s -> Triple(a, v, s) }
                    .distinctUntilChanged { old, new ->
                        old.first == new.first &&
                        old.second == new.second &&
                        old.third.currentItem?.uri == new.third.currentItem?.uri &&
                        old.third.isPlaying == new.third.isPlaying &&
                        old.third.isAudioBackgroundPlayEnabled == new.third.isAudioBackgroundPlayEnabled &&
                        old.third.isVideoBackgroundPlayEnabled == new.third.isVideoBackgroundPlayEnabled
                    }
                    .collectLatest { (audioNotif, videoNotif, state) -> handleNotificationUpdate(audioNotif, videoNotif, state) }
            }
            launch {
                abRepeatController.state.collectLatest { ab ->
                    _playerState.value = _playerState.value.copy(
                        abRepeatState = ab,
                        abRepeatA = ab.pointA,
                        abRepeatB = ab.pointB,
                        isAbRepeatActive = ab.isActive
                    )
                }
            }
        }

        setupTelephonyListener()
        setupBluetoothReceiver()
        setupVolumeReceiver()

        scope.launch {
            var lastSavedPos = -1L
            while (isActive) {
                val engine = activeEngine
                if (engine != null) {
                    val isPlaying = engine.isPlaying
                    val diag = engine.diagnosticState.value
                    // Grab the live session ID from the underlying ExoPlayer instance.
                    // This ensures the correct (non-zero) session ID is always exposed
                    // to AudioVisualizer, which requires it on Android 11+.
                    val liveSessionId = (engine as? Media3PlaybackEngine)?.player?.audioSessionId ?: 0
                    
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val isMaxed = currentVol >= maxVol

                    val newPos = engine.currentPositionMs
                    val newDur = engine.durationMs

                    abRepeatController.checkAndLoop(newPos)

                    val oldState = _playerState.value
                    val stateChanged = isPlaying != oldState.isPlaying ||
                            kotlin.math.abs(newPos - oldState.currentPositionMs) >= 100 ||
                            newDur != oldState.durationMs ||
                            diag.droppedFrames != oldState.droppedFrames ||
                            diag.currentBitrate != oldState.currentBitrate ||
                            diag.videoCodec != oldState.videoCodec ||
                            diag.decoderName != oldState.activeDecoderName ||
                            isMaxed != oldState.isSystemVolumeMaxed ||
                            diag.isHdr != oldState.isHdrContent ||
                            (liveSessionId != 0 && liveSessionId != C.AUDIO_SESSION_ID_UNSET && liveSessionId != oldState.audioSessionId)

                    if (stateChanged) {
                        _playerState.value = oldState.copy(
                            currentPositionMs = newPos,
                            durationMs = newDur,
                            isPlaying = isPlaying,
                            currentBitrate = diag.currentBitrate,
                            droppedFrames = diag.droppedFrames,
                            audioDecodeErrors = diag.audioDecodeErrors,
                            audioMissingFrames = diag.audioMissingFrames,
                            corruptedFrames = diag.corruptedFrames,
                            bitrateHistory = diag.bitrateHistory,
                            timestampRecoveryCount = diag.timestampRecoveryCount,
                            containerName = diag.containerName,
                            videoCodec = diag.videoCodec,
                            audioCodec = diag.audioCodec,
                            activeDecoderName = diag.decoderName,
                            isHardwareAccelerated = diag.isHardwareAccelerated,
                            audioSessionId = if (liveSessionId != 0 && liveSessionId != C.AUDIO_SESSION_ID_UNSET) liveSessionId else oldState.audioSessionId,
                            isSystemVolumeMaxed = isMaxed,
                            isHdrContent = diag.isHdr,
                            hdrType = diag.hdrType,
                            colorSpace = diag.colorSpace,
                            audioSampleFormat = diag.audioSampleFormat,
                            audioSharingMode = diag.audioSharingMode
                        )
                    }

                    // Always keep the lightweight position state up-to-date every tick.
                    // VideoPlayerBottomBar subscribes to this instead of the heavy playerState.
                    val curPosState = _playbackPositionState.value
                    if (newPos != curPosState.positionMs || newDur != curPosState.durationMs || isPlaying != curPosState.isPlaying) {
                        _playbackPositionState.value = PlaybackPositionState(newPos, newDur, isPlaying)
                    }

                    // Periodically save progress to DB (every ~5 seconds while playing)
                    if (isPlaying && (lastSavedPos == -1L || kotlin.math.abs(newPos - lastSavedPos) >= 5000)) {
                        lastSavedPos = newPos
                        savePlaybackProgress()
                    }
                }
                delay(if (activeEngine?.isPlaying == true) 200 else 600)
            }
        }
    }

    private fun savePlaybackProgress() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val db = com.medianest.MediaNestApp.instance.database
        val settings = com.medianest.MediaNestApp.instance.settingsManager
        
        scope.launch(Dispatchers.IO) {
            try {
                val shouldRemember = if (item.type == com.medianest.data.db.MediaType.VIDEO) {
                    settings.rememberVideoPosition.first()
                } else true

                if (!shouldRemember) return@launch

                val existing = db.playbackStateDao().getPlaybackState(item.uri.toString())
                val newState = com.medianest.data.db.PlaybackState(
                    mediaUri = item.uri.toString(),
                    mediaType = item.type.name,
                    positionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    lastPlayedAt = System.currentTimeMillis(),
                    playbackSpeed = state.playbackSpeed,
                    playCount = (existing?.playCount ?: 0) + (if (state.currentPositionMs < 1000) 1 else 0) // Basic play count increment
                )
                db.playbackStateDao().savePlaybackState(newState)
            } catch (e: Exception) {
                Logger.e("ExoPlayerManager", "Failed to save playback progress", e)
            }
        }
    }

    private var androidEqualizer: android.media.audiofx.Equalizer? = null
    private var androidBassBoost: android.media.audiofx.BassBoost? = null
    private var androidLoudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    private fun releaseAudioEffects() {
        try {
            androidEqualizer?.enabled = false
            androidEqualizer?.release()
            androidEqualizer = null
            
            androidBassBoost?.enabled = false
            androidBassBoost?.release()
            androidBassBoost = null
            
            androidLoudnessEnhancer?.enabled = false
            androidLoudnessEnhancer?.release()
            androidLoudnessEnhancer = null
        } catch (e: Exception) {
            Logger.w("ExoPlayerManager", "Error releasing AudioFX: ${e.message}")
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
    }

    private var isRebirthing = false

    @OptIn(DelicateCoroutinesApi::class)
    private fun rebuildEngineDueToDeadlock(reason: String) {
        if (isRebirthing) return
        if (activeEngine == ffmpegEngine) {
            Logger.d("ExoPlayerManager", "rebuildEngineDueToDeadlock ignored: active engine is already FFmpeg")
            return
        }
        isRebirthing = true

        scope.launch(Dispatchers.Main) {
            Logger.e("ExoPlayerManager", "FATAL DEADLOCK ($reason) - Isolated rebirth initiated. Instance: ${_playerState.value.media3InstanceId}")
            
            val currentItem = _playerState.value.currentItem
            val currentPos = activeEngine?.currentPositionMs ?: 0L
            val wasPlaying = activeEngine?.isPlaying ?: false

            // 1. ORPHAN the broken engine immediately. 
            media3Engine?.let {
                try {
                    // SILENCE THE ZOMBIE: Explicitly mute and stop the orphaned player.
                    // Even if the decoder is stuck, the audio renderer might still be playing.
                    try { it.player.volume = 0f } catch(e: Exception) {}
                    try { it.player.playWhenReady = false } catch(e: Exception) {}
                    try { it.stop() } catch(e: Exception) {}
                    Logger.d("ExoPlayerManager", "Orphaned engine silenced: ${it.player}")
                } catch (e: Exception) {
                    Logger.w("ExoPlayerManager", "Failed to silence orphaned engine: ${it.player}")
                }
                brokenEngines.add(it)
            }
            media3Engine = null
            activeEngine = null
            
            // 2. Increment ID immediately to force UI to drop all surface references
            val nextId = instanceIdCounter++
            _playerState.value = _playerState.value.copy(
                media3InstanceId = nextId,
                activeEngineName = "Resetting hardware...",
                isPlaying = false
            )
            
            // 3. "Cool Down" period. 
            // Gives the hardware driver time to clear its interrupt queues.
            delay(4000) 

            // 4. Sprout new engine
            initializeMedia3Engine()
            
            // 5. Restore state with Surface-First synchronization
            if (currentItem != null && media3Engine != null) {
                val profile = MediaCapabilityInspector.inspect(currentItem.uri, null, currentItem.title)
                if (profile.requiresFFmpegFallback || av1FailureCount >= 2) {
                    Logger.w("ExoPlayerManager", "Item requires FFmpeg or persistent deadlock. Switching to FFmpeg.")
                    switchToFFmpegFallback("Item requires FFmpeg")
                    isRebirthing = false
                    return@launch
                }

                // Explicitly set surface before wait
                media3Engine?.setSurface(lastSurface)
                activeEngine = media3Engine
                _playerState.value = _playerState.value.copy(
                    activeEngineName = "Media3 (Recovered)"
                )
                
                // CRITICAL: We wait for the UI to report that the new TextureView is ready 
                // before calling prepare(). This prevents the "Audio Only" black screen.
                Logger.d("ExoPlayerManager", "Waiting for new surface attachment...")
                
                var surfaceWaitCount = 0
                while (media3Engine?.isSurfaceReady?.value == false && surfaceWaitCount < 15) {
                    delay(300)
                    surfaceWaitCount++
                }
                
                Logger.d("ExoPlayerManager", "Surface wait finished. Ready: ${media3Engine?.isSurfaceReady?.value == true}")

                activeEngine?.prepare(currentItem.uri, true)
                activeEngine?.seekTo(currentPos)
                if (requestAudioFocus()) {
                    activeEngine?.play()
                }
            }
            
            isRebirthing = false
        }

        // Background "Hopeful" Cleanup — use scope (not GlobalScope) to stay lifecycle-bound.
        scope.launch(Dispatchers.Main) {
            delay(30000) // Wait 30s before trying to touch the dead ones
            synchronized(brokenEngines) {
                val iterator = brokenEngines.iterator()
                while(iterator.hasNext()) {
                    val deadEngine = iterator.next()
                    try {
                        Logger.d("ExoPlayerManager", "Attempting FINAL RELEASE of dead engine in background")
                        deadEngine.release() // ACTUALLY RELEASE IT
                        iterator.remove()
                    } catch (e: Exception) {
                        Logger.w("ExoPlayerManager", "Failed to release zombie engine: ${e.message}")
                    }
                }
            }
        }
    }

    private fun switchToFFmpegFallback(reason: String) {
        val currentItem = _playerState.value.currentItem ?: return
        val currentPos = activeEngine?.currentPositionMs ?: 0L
        
        scope.launch(Dispatchers.Main) {
            Logger.e("ExoPlayerManager", "ENGINE SWITCH: Performing emergency swap to FFmpeg. Reason: $reason")

            val oldEngine = activeEngine
            activeEngine = ffmpegEngine
            
            _playerState.value = _playerState.value.copy(
                activeEngineName = "FFmpeg (Fallback)",
                isHardwareAccelerated = false,
                decoderFallbackReason = reason
            )

            // CRITICAL: Clear surface and SILENCE the old engine immediately
            try {
                if (oldEngine is Media3PlaybackEngine) {
                    Logger.d("ExoPlayerManager", "Muting dead Media3 engine before swap")
                    oldEngine.player.volume = 0f
                    oldEngine.player.playWhenReady = false
                    oldEngine.stop()
                    brokenEngines.add(oldEngine)
                    media3Engine = null // Discard broken Media3 engine so next Media3 video starts fresh
                } else {
                    oldEngine?.setSurface(null)
                    oldEngine?.stop()
                }
            } catch (e: Exception) {
                Logger.w("ExoPlayerManager", "Non-fatal error clearing old engine during fallback: ${e.message}")
            }

            Logger.i("ExoPlayerManager", "FFmpeg Fallback Engine Ready. Resuming at $currentPos ms")
            ffmpegEngine.setSurface(lastSurface)
            ffmpegEngine.setVolume(1.0f)
            updateNativeFilters()
            ffmpegEngine.prepare(currentItem.uri, true)
            if (currentPos > 0) ffmpegEngine.seekTo(currentPos)
            if (requestAudioFocus()) {
                ffmpegEngine.play()
            }
        }
    }

    private fun updateAndroidAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Logger.d("ExoPlayerManager", "Ignoring invalid AudioSession: $audioSessionId")
            return
        }
        
        // If session ID changed, we MUST release previous effects to avoid system-wide leaks.
            if (activeAudioSessionId != audioSessionId) {
                Logger.i("ExoPlayerManager", "AudioSession changed: $activeAudioSessionId -> $audioSessionId. (Player: $audioSessionId) Recreating effects.")
                releaseAudioEffects()
                activeAudioSessionId = audioSessionId
                
                // Add a 1.5-second stabilization delay for Samsung devices to avoid Error -3 (BAD_VALUE)
                scope.launch {
                    delay(1500)
                    Logger.d("ExoPlayerManager", "Initializing AudioFX for session $audioSessionId after stabilization")
                    initAudioEffectsWithRetry(audioSessionId, 3)
                }
            } else {
                initAudioEffectsInternal(audioSessionId)
            }
    }

    private fun initAudioEffectsWithRetry(audioSessionId: Int, retries: Int) {
        try {
            initAudioEffectsInternal(audioSessionId)
        } catch (e: Exception) {
            if (retries > 0) {
                Logger.w("ExoPlayerManager", "AudioFX init failed, retrying... ($retries left)")
                scope.launch {
                    delay(500)
                    initAudioEffectsWithRetry(audioSessionId, retries - 1)
                }
            } else {
                Logger.e("ExoPlayerManager", "AudioFX init failed after retries for session $audioSessionId")
            }
        }
    }

    private fun initAudioEffectsInternal(audioSessionId: Int) {
        if (audioFxFailureCount > 10) {
            Logger.w("ExoPlayerManager", "AudioFX disabled due to too many failures")
            return
        }
        try {
            if (androidEqualizer == null) {
                androidEqualizer = android.media.audiofx.Equalizer(0, audioSessionId).apply {
                    enabled = _playerState.value.isEqEnabled
                }
            }
            if (androidBassBoost == null) {
                androidBassBoost = android.media.audiofx.BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
            }
            if (androidLoudnessEnhancer == null) {
                androidLoudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                    enabled = true
                }
            }
            applyExoAudioEffects()
        } catch (e: Exception) {
            audioFxFailureCount++
            Logger.w("ExoPlayerManager", "AudioFX init failed for session $audioSessionId (Fail Count: $audioFxFailureCount): ${e.message}")
            releaseAudioEffects()
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
            Logger.w("ExoPlayerManager", "Apply AudioFX error: ${e.message}")
        }
    }

    private var playerListener: Player.Listener? = null

    private fun initializeMedia3Engine() {
        if (media3Engine != null) {
            Logger.d("ExoPlayerManager", "Media3 Engine already exists, skipping re-init")
            return
        }
        
        Logger.i("ExoPlayerManager", "Initializing Singleton Media3 Engine")
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setTextTrackTranscodingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(customMediaCodecSelector)
        val player = ExoPlayer.Builder(context, renderersFactory).setLooper(android.os.Looper.getMainLooper())
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), false)
            .build()
        
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Logger.e("ExoPlayerManager", "Player error encountered [code=${error.errorCode}, name=${error.errorCodeName}]: ${error.message}", error)
                
                // If we get a timeout, switch directly to FFmpeg fallback
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT) {
                    switchToFFmpegFallback("Media3 timeout")
                    return
                }

                // Catch ANY extractor exception, source error, loader exception, parsing error, or decoder error
                val isDecoderError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED ||
                    error.cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException ||
                    error.cause is androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException ||
                    error.cause is androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException ||
                    error.cause is androidx.media3.exoplayer.upstream.Loader.UnexpectedLoaderException ||
                    error.cause is java.lang.IndexOutOfBoundsException ||
                    error.cause is java.lang.RuntimeException

                if (isDecoderError) {
                    val engineName = _playerState.value.activeEngineName
                    
                    if (engineName.contains("Media3", true)) {
                        Logger.w("ExoPlayerManager", "Hardware decoder failure (Error=${error.errorCodeName}). Attempting FFmpeg fallback.")
                        switchToFFmpegFallback(error.message ?: error.errorCodeName)
                    }
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                updateAndroidAudioEffects(audioSessionId)
                // Propagate the real session ID to playerState so AudioVisualizer
                // receives a valid (non-zero) session ID on Android 11+.
                if (audioSessionId != 0 && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    _playerState.value = _playerState.value.copy(audioSessionId = audioSessionId)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    watchdogJob?.cancel()
                    Logger.d("ExoPlayerManager", "Playback READY, watchdog cancelled")
                }
                if (playbackState == Player.STATE_ENDED) {
                    onEnginePlaybackEnded()
                }
            }
        }
        
        playerListener = listener
        player.addListener(listener)
        updateAndroidAudioEffects(player.audioSessionId)
        // Seed playerState immediately with the session ID assigned at player creation.
        if (player.audioSessionId != 0 && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            _playerState.value = _playerState.value.copy(audioSessionId = player.audioSessionId)
        }
        media3Engine = Media3PlaybackEngine(context, player)
        _playerState.value = _playerState.value.copy(media3InstanceId = instanceIdCounter++)
        if (activeEngine == null) activeEngine = media3Engine
    }

    private fun recreatePlayerBySettings() {
        if (media3Engine != null) {
            Logger.i("ExoPlayerManager", "Settings changed, but keeping singleton Media3 instance to avoid driver deadlock.")
            // Most settings can be updated without recreation.
            // If we absolutely need to recreate, we'd do it here, but let's prioritize stability.
            return
        }

        initializeMedia3Engine()
    }

    val exoPlayer: ExoPlayer?
        get() = media3Engine?.player

    private fun handleNotificationUpdate(audioNotif: Boolean, videoNotif: Boolean, state: PlayerState) {
        if (isScrubbing) return
        val currentItem = state.currentItem ?: return
        val engine = activeEngine ?: return
        
        Logger.v("ExoPlayerManager", "Notification Update Check: title=${currentItem.title}, isPlaying=${state.isPlaying}, pos=${engine.currentPositionMs}, dur=${engine.durationMs}")

        // Guard: if Media3 is BUFFERING (playWhenReady=true but not yet playing), treat it as playing
        // to avoid killing the service during the startup phase
        val isBufferingStartup = engine is Media3PlaybackEngine &&
            engine.player.playWhenReady &&
            engine.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING

        val isEffectivelyPlaying = state.isPlaying || isBufferingStartup

        if (engine.currentPositionMs < engine.durationMs || isEffectivelyPlaying) {
            val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.medianest.data.db.MediaType.VIDEO
            val bgPlayEnabled = if (isVideo) state.isVideoBackgroundPlayEnabled else state.isAudioBackgroundPlayEnabled
            
            if (!bgPlayEnabled && !isEffectivelyPlaying) { 
                Logger.d("ExoPlayerManager", "Notification: Background play disabled and not playing for ${currentItem.title}. Stopping service.")
                FloatingPlayerService.stopService(context)
                return 
            }
            
            if (if (isVideo) videoNotif else audioNotif) {
                Logger.v("ExoPlayerManager", "Notification: Updating service for ${currentItem.title} (isPlaying=${state.isPlaying}, buffering=$isBufferingStartup)")
                FloatingPlayerService.startOrUpdateService(context, currentItem.title, currentItem.artist ?: currentItem.album ?: "MediaNest", state.isPlaying, currentItem.albumArtUri?.toString() ?: currentItem.uri.toString(), isVideo)
            } else {
                Logger.d("ExoPlayerManager", "Notification: Hidden by user settings for ${currentItem.title}. Stopping service.")
                FloatingPlayerService.stopService(context)
            }
        } else {
            Logger.d("ExoPlayerManager", "Notification: Playback ended for ${currentItem.title}. Stopping service.")
            FloatingPlayerService.stopService(context)
        }
    }

    private fun setupTelephonyListener() {
        try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = handleCallState(state)
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleCallState(state)
                }
                telephonyCallback = listener
                telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
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

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val isMaxed = currentVol >= maxVol
                _playerState.value = _playerState.value.copy(isSystemVolumeMaxed = isMaxed)
                
                // Reset Super Volume Boost if volume is lowered
                if (!isMaxed && _playerState.value.volumeBoostPercent > 0) {
                    setVolumeBoost(0)
                }
            }
        }
    }

    private fun setupVolumeReceiver() {
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            context.registerReceiver(volumeReceiver, filter)
        } catch (_: Exception) {}
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            // Focus granted (or delayed grant arrived) — resume if we had queued a play
            AudioManager.AUDIOFOCUS_GAIN -> if (playOnFocusGain) { activeEngine?.play(); playOnFocusGain = false }
            // Permanent loss (another app took focus for good) — stop and don't resume
            AudioManager.AUDIOFOCUS_LOSS -> { playOnFocusGain = false; pause() }
            // Transient loss (notification, etc.) — remember playing state and pause
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (!(isUninterruptedMode && !isCallActive)) { playOnFocusGain = activeEngine?.isPlaying == true; activeEngine?.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (!isUninterruptedMode) activeEngine?.setVolume(0.2f)
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                val attr = AndroidAudioAttributes.Builder().setUsage(AndroidAudioAttributes.USAGE_MEDIA).setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC).build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attr)
                    // NOTE: setAcceptsDelayedFocusGain(true) means requestAudioFocus can return
                    // AUDIOFOCUS_REQUEST_DELAYED instead of GRANTED. We handle this by setting
                    // playOnFocusGain = true so the listener resumes when focus eventually arrives.
                    .setAcceptsDelayedFocusGain(true).setOnAudioFocusChangeListener(audioFocusChangeListener).setWillPauseWhenDucked(false).build()
            }
            val result = audioManager.requestAudioFocus(focusRequest!!)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                // Focus will arrive via AUDIOFOCUS_GAIN callback; queue the play for then.
                playOnFocusGain = true
                return false // caller should NOT call play() now — listener will do it
            }
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return true
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private var playbackJob: kotlinx.coroutines.Job? = null

    private fun stopAllEnginesExcept(active: PlaybackEngine?) {
        // 1. Mute, pause and stop Media3 only if it's NOT the active (continuing) engine
        if (media3Engine != null && media3Engine != active) {
            try {
                media3Engine?.player?.volume = 0f
                media3Engine?.player?.playWhenReady = false
                media3Engine?.pause()
                // Only call stop() when Media3 is truly being replaced (not just track-switching on same engine)
                media3Engine?.stop()
                Logger.d("ExoPlayerManager", "stopAllEnginesExcept: Media3 paused+stopped (not active)")
            } catch (e: Exception) {
                Logger.w("ExoPlayerManager", "Error stopping inactive Media3 engine: ${e.message}")
            }
        } else if (active == media3Engine && media3Engine != null) {
            // Same engine continues — only mute briefly to prevent double-audio, do NOT stop
            // The volume will be restored to 1f before prepare() is called
            Logger.d("ExoPlayerManager", "stopAllEnginesExcept: Media3 is continuing — muting only (no stop)")
        }
        
        // 2. Pause/stop FFmpeg if not active
        if (active != ffmpegEngine) {
            try {
                ffmpegEngine.pause()
                ffmpegEngine.stop()
                Logger.d("ExoPlayerManager", "stopAllEnginesExcept: FFmpeg paused+stopped (not active)")
            } catch (e: Exception) {
                Logger.w("ExoPlayerManager", "Error stopping inactive FFmpeg engine: ${e.message}")
            }
        }

        // 3. Clean up broken engines in background without blocking main thread
        val toRelease = synchronized(brokenEngines) {
            val list = brokenEngines.toList()
            brokenEngines.clear()
            list
        }
        if (toRelease.isNotEmpty()) {
            scope.launch(Dispatchers.Main) {
                for (dead in toRelease) {
                    try {
                        dead.release()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun playMediaList(items: List<MediaItem>, startIndex: Int = 0, startPosMs: Long = 0L, queueTitle: String? = null) {
        if (items.isEmpty()) {
            Logger.w("ExoPlayerManager", "playMediaList called with empty list")
            return
        }
        
        playbackJob?.cancel()
        val targetIndex = startIndex.coerceIn(0, items.size - 1)
        val currentTarget = items[targetIndex]
        Logger.i("ExoPlayerManager", "PLAYBACK START: title=${currentTarget.title}, index=$targetIndex, pos=$startPosMs, uri=${currentTarget.uri}")

        playbackJob = scope.launch {
            withContext(Dispatchers.Main) {
                try {
                    val uriString = currentTarget.uri.toString().lowercase(java.util.Locale.ROOT)
                    val fileName = currentTarget.title.lowercase(java.util.Locale.ROOT)
                    val preProfile = MediaCapabilityInspector.inspect(currentTarget.uri, null, currentTarget.title)
                    val preNeedsFFmpeg = preProfile.requiresFFmpegFallback
                    val preEngineName = if (preNeedsFFmpeg && currentDecoderPreference != "HARDWARE") "FFmpeg" else _playerState.value.activeEngineName

                    _playerState.value = _playerState.value.copy(
                        queue = items,
                        queueIndex = targetIndex,
                        currentItem = currentTarget,
                        queueTitle = queueTitle ?: _playerState.value.queueTitle,
                        activeEngineName = preEngineName,
                        abRepeatA = null,
                        abRepeatB = null,
                        isAbRepeatActive = false
                    )
                    val isNetworkUrl = uriString.startsWith("http://") || uriString.startsWith("https://")
                    val isVideoType = currentTarget.type == com.medianest.data.db.MediaType.VIDEO ||
                                     currentTarget.mimeType.startsWith("video/") ||
                                     currentTarget.mimeType.startsWith("audio/")
                    val isStandardContainer = fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".webm") ||
                                             fileName.endsWith(".mov") || fileName.endsWith(".3gp") || fileName.endsWith(".m4v") ||
                                             fileName.endsWith(".mp3") || fileName.endsWith(".flac") || fileName.endsWith(".aac") ||
                                             fileName.endsWith(".m4a") || fileName.endsWith(".wav") || fileName.endsWith(".ogg") ||
                                             uriString.endsWith(".mp4") || uriString.endsWith(".mkv") || uriString.endsWith(".webm")
                    val isStandardFastPath = isNetworkUrl || isStandardContainer || (isVideoType && !fileName.endsWith(".avi") && !fileName.endsWith(".wmv") && !fileName.endsWith(".flv"))
                    
                    val probeResult = if (isStandardFastPath) {
                        null
                    } else {
                        withContext(Dispatchers.IO) { ffmpegEngine.probe(currentTarget.uri) }
                    }
                    Logger.d("ExoPlayerManager", "Probe result for ${currentTarget.title}: $probeResult")
                    val profile = MediaCapabilityInspector.inspect(currentTarget.uri, probeResult, currentTarget.title)
                    Logger.i("ExoPlayerManager", "Media Profile for ${currentTarget.title}: $profile")
                    val forceHW = currentDecoderPreference == "HARDWARE"
                    val needsFFmpeg = profile.requiresFFmpegFallback
                    
                    if (!needsFFmpeg || forceHW) {
                        if (media3Engine == null) {
                            initializeMedia3Engine()
                        }
                    }

                    val willUseFFmpeg = needsFFmpeg && !forceHW
                    val oldEngine = activeEngine

                    if (oldEngine != null && oldEngine is Media3PlaybackEngine && willUseFFmpeg) {
                        Logger.i("ExoPlayerManager", "ENGINE SWAP: Media3 -> FFmpeg. Discarding old Media3 engine.")
                        try {
                            oldEngine.player.volume = 0f
                            oldEngine.player.playWhenReady = false
                            oldEngine.stop()
                        } catch (_: Exception) {}
                        brokenEngines.add(oldEngine)
                        media3Engine = null
                    } else if (oldEngine != null && oldEngine is FFmpegPlaybackEngine && !willUseFFmpeg) {
                        Logger.i("ExoPlayerManager", "ENGINE SWAP: FFmpeg -> Media3. Starting fresh Media3 instance.")
                        try {
                            oldEngine.setSurface(null)
                            oldEngine.stop()
                        } catch (_: Exception) {}
                        if (media3Engine == null) {
                            initializeMedia3Engine()
                        }
                    } else if (oldEngine != null && ((oldEngine == ffmpegEngine && willUseFFmpeg) || (oldEngine == media3Engine && !willUseFFmpeg))) {
                        Logger.i("ExoPlayerManager", "TRACK CHANGE: Same engine continues — pausing only")
                        try { oldEngine.pause() } catch (_: Exception) {}
                    }

                    if (!willUseFFmpeg && media3Engine == null) {
                        initializeMedia3Engine()
                    }

                    val newEngine = if (willUseFFmpeg) ffmpegEngine else (media3Engine ?: run { initializeMedia3Engine(); media3Engine })
                    val newEngineName = if (newEngine == ffmpegEngine) "FFmpeg" else "Media3"

                    stopAllEnginesExcept(newEngine)
                    activeEngine = newEngine
                    
                    _playerState.value = _playerState.value.copy(
                        activeEngineName = newEngineName,
                        isHardwareAccelerated = newEngine == media3Engine
                    )

                    Logger.i("ExoPlayerManager", "ENGINE ACTIVE: $newEngineName (Codec=${profile.videoCodec}, Audio=${profile.audioCodec}, NeedsFFmpeg=$needsFFmpeg, ForceHW=$forceHW, Pos=$startPosMs)")

                    if (currentTarget.type == com.medianest.data.db.MediaType.AUDIO && (currentTarget.artist == null || currentTarget.title.startsWith("Track") || currentTarget.title == "Media")) {
                        scope.launch(Dispatchers.IO) {
                            val enriched = com.medianest.util.AudioMetadataUtils.extractMetadata(context, currentTarget.uri, rawTitleHint = currentTarget.title, mimeTypeHint = currentTarget.mimeType)
                            val stableEnriched = enriched.copy(id = currentTarget.id)
                            withContext(Dispatchers.Main) {
                                val currentQ = _playerState.value.queue.toMutableList()
                                val curIndex = _playerState.value.queueIndex
                                if (curIndex in currentQ.indices) {
                                    currentQ[curIndex] = stableEnriched
                                }
                                _playerState.value = _playerState.value.copy(queue = currentQ, currentItem = stableEnriched)
                                com.medianest.player.FloatingPlayerService.startOrUpdateService(
                                    context = context,
                                    title = stableEnriched.title,
                                    artist = stableEnriched.artist ?: "Unknown Artist",
                                    isPlaying = _playerState.value.isPlaying,
                                    artworkUri = stableEnriched.albumArtUri?.toString() ?: "",
                                    isVideo = false
                                )
                            }
                        }
                    }

                    if (newEngine == media3Engine) {
                        try { media3Engine?.player?.volume = 1.0f } catch (_: Exception) {}
                    } else if (newEngine == ffmpegEngine) {
                        try { ffmpegEngine.setVolume(1.0f) } catch (_: Exception) {}
                        activeEngine?.setSurface(lastSurface)
                        updateNativeFilters() // Apply DSP settings to FFmpeg
                    }
                    Logger.d("ExoPlayerManager", "Preparing engine ($newEngineName) for URI: ${currentTarget.uri}")
                    activeEngine?.prepare(currentTarget.uri, true)
                    if (newEngine == media3Engine && currentVideoEffect != com.medianest.ui.components.media.MediaEffect.OFF) {
                        setVideoEffect(currentVideoEffect)
                    }
                    if (startPosMs > 0) activeEngine?.seekTo(startPosMs)
                    if (requestAudioFocus()) {
                        Logger.d("ExoPlayerManager", "Audio focus granted, starting playback on $newEngineName")
                        activeEngine?.play()
                    } else {
                        Logger.w("ExoPlayerManager", "Audio focus denied")
                    }
                } catch (e: Exception) { 
                    Logger.e("ExoPlayerManager", "Failed to prepare playback for ${currentTarget.title}", e) 
                }
            }
        }
    }

    fun playSingleUri(uri: Uri?, title: String = "Media", mimeType: String = "") {
        if (uri == null) return

        // FAST-PATH: Infer basic type and start playback immediately without waiting for slow I/O
        val pathStr = (uri.path ?: uri.toString()).lowercase(java.util.Locale.ROOT)
        val resolvedMime = when {
            mimeType.isNotBlank() && mimeType != "*/*" -> mimeType
            pathStr.endsWith(".mp4") || pathStr.endsWith(".mkv") || pathStr.endsWith(".webm") ||
            pathStr.endsWith(".mov") || pathStr.endsWith(".3gp") || pathStr.endsWith(".avi") ||
            pathStr.endsWith(".ts") || pathStr.endsWith(".flv") || pathStr.endsWith(".m4v") -> "video/mp4"
            pathStr.endsWith(".mp3") || pathStr.endsWith(".wav") || pathStr.endsWith(".flac") ||
            pathStr.endsWith(".aac") || pathStr.endsWith(".m4a") || pathStr.endsWith(".ogg") || pathStr.endsWith(".opus") -> "audio/mpeg"
            else -> "video/mp4" // Default to video if launched from viewer
        }
        val mediaType = if (resolvedMime.startsWith("audio/")) com.medianest.data.db.MediaType.AUDIO else com.medianest.data.db.MediaType.VIDEO
        val fallbackTitle = if (title.isNotBlank() && title != "Media") title else (uri.lastPathSegment?.substringAfterLast('/') ?: "Media")

        val fastItemId = uri.hashCode().toLong()
        val fastItem = MediaItem(
            id = fastItemId,
            uri = uri,
            title = fallbackTitle,
            mimeType = resolvedMime,
            type = mediaType
        )

        // Immediately start playback on Main thread
        playMediaList(listOf(fastItem), 0, 0L)

        // Asynchronously extract and enrich metadata in background without blocking video startup
        scope.launch(Dispatchers.IO) {
            try {
                val enriched = com.medianest.util.AudioMetadataUtils.extractMetadata(context, uri, rawTitleHint = fallbackTitle, mimeTypeHint = resolvedMime)
                val stableEnriched = enriched.copy(id = fastItemId)
                withContext(Dispatchers.Main) {
                    val currentQ = _playerState.value.queue
                    if (currentQ.isNotEmpty() && currentQ[0].uri == uri) {
                        _playerState.value = _playerState.value.copy(
                            queue = listOf(stableEnriched),
                            currentItem = stableEnriched
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.w("ExoPlayerManager", "Background metadata extraction failed for $uri: ${e.message}")
            }
        }
    }

    fun playSingleUri(ctx: Context, uri: Uri?, title: String = "Media", mimeType: String = "") = playSingleUri(uri, title, mimeType)
    fun setFastSeek(fast: Boolean) {
        if (activeEngine == media3Engine) {
            try {
                media3Engine?.player?.setSeekParameters(
                    if (fast) androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
                    else androidx.media3.exoplayer.SeekParameters.EXACT
                )
            } catch (_: Exception) {}
        }
    }
    /**
     * Called when interactive scrubbing begins.
     * Pauses the active engine so the video decoder renders single preview frames
     * on each seek without audio contention, buffering delays, or decoder stalls.
     */
    fun scrubStart() {
        isScrubbing = true
        wasPlayingBeforeScrub = activeEngine?.isPlaying == true
        setFastSeek(true)
        try {
            activeEngine?.pause()
        } catch (_: Exception) {}
    }

    /**
     * Called on each drag step to seek to the preview frame.
     */
    fun scrubSeek(positionMs: Long) {
        try {
            activeEngine?.seekTo(positionMs)
        } catch (_: Exception) {}
    }

    /**
     * Called when interactive scrubbing ends (user lifts finger).
     * Restores exact seeking, seeks to final target position, and resumes playback
     * cleanly if the video was playing prior to the scrub gesture.
     */
    fun scrubEnd(finalPositionMs: Long) {
        setFastSeek(false)
        try {
            activeEngine?.seekTo(finalPositionMs)
            if (wasPlayingBeforeScrub) {
                activeEngine?.play()
            }
        } catch (_: Exception) {}
        isScrubbing = false
    }
    fun seekTo(positionMs: Long) {
        try {
            val clamped = positionMs.coerceAtLeast(0L)
            _playerState.value = _playerState.value.copy(currentPositionMs = clamped)
            activeEngine?.seekTo(clamped)
        } catch (e: Exception) {
            Logger.e("ExoPlayerManager", "seekTo error: ${e.message}")
        }
    }
    fun seekForward(offsetMs: Long = 10000L) = seekTo(((activeEngine?.currentPositionMs ?: 0L) + offsetMs).coerceAtMost(activeEngine?.durationMs ?: 0L))
    fun seekBackward(offsetMs: Long = 10000L) = seekTo(((activeEngine?.currentPositionMs ?: 0L) - offsetMs).coerceAtLeast(0L))
    fun next() = _playerState.value.let {
        Logger.i("ExoPlayerManager", "next() clicked: queueSize=${it.queue.size}, currentIndex=${it.queueIndex}")
        if (it.queue.isNotEmpty()) {
            val nextIndex = (it.queueIndex + 1) % it.queue.size
            playMediaList(it.queue, nextIndex, 0L, it.queueTitle)
        } else {
            Logger.w("ExoPlayerManager", "next() ignored because queue is empty!")
        }
    }
    fun previous() = _playerState.value.let {
        Logger.i("ExoPlayerManager", "previous() clicked: queueSize=${it.queue.size}, currentIndex=${it.queueIndex}")
        if (it.queue.isNotEmpty()) {
            if ((activeEngine?.currentPositionMs ?: 0L) > 3000L) {
                activeEngine?.seekTo(0L)
            } else {
                val prevIndex = if (it.queueIndex - 1 < 0) it.queue.size - 1 else it.queueIndex - 1
                playMediaList(it.queue, prevIndex, 0L, it.queueTitle)
            }
        }
    }
    fun play() {
        Logger.d("ExoPlayerManager", "play() called. Active Engine: ${_playerState.value.activeEngineName}, isHardwareFaulty: $isHardwareFaulty")
        if (activeEngine == media3Engine) {
            try { media3Engine?.player?.volume = 1.0f } catch (_: Exception) {}
        } else if (activeEngine == ffmpegEngine) {
            try { ffmpegEngine.setVolume(1.0f) } catch (_: Exception) {}
        }
        if (requestAudioFocus()) {
            activeEngine?.play()
        }
    }

    fun pause() {
        Logger.d("ExoPlayerManager", "pause() called")
        try { activeEngine?.pause() } catch (_: Exception) {}
        _playerState.value = _playerState.value.copy(isPlaying = false)
        savePlaybackProgress()
        // NOTE: Do NOT call abandonAudioFocus() here. Abandoning on every user pause fires
        // AUDIOFOCUS_LOSS on the registered listener, which calls pause() again asynchronously
        // right as play() tries to resume — causing video+audio to freeze after pause→play.
        // Audio focus is only abandoned on stop() and release().
    }

    fun stop() {
        Logger.i("ExoPlayerManager", "stop() called — full playback teardown")
        watchdogJob?.cancel()
        playbackJob?.cancel()
        
        try {
            // On explicit stop (user closes player), we DO clear media items
            // since we're actually shutting down, not just switching tracks
            media3Engine?.player?.volume = 0f
            media3Engine?.player?.playWhenReady = false
            media3Engine?.player?.stop()
            media3Engine?.player?.clearMediaItems()
            // Do NOT call setVideoSurface(null) — the TextureView lifecycle cleans that up
        } catch (_: Exception) {}
        try {
            ffmpegEngine.setSurface(null)
            ffmpegEngine.stop()
        } catch (_: Exception) {}
        stopAllEnginesExcept(null)
        _playerState.value = _playerState.value.copy(isPlaying = false)
        abandonAudioFocus()
        FloatingPlayerService.stopService(context)
    }

    fun releaseSurface() {
        try {
            media3Engine?.player?.clearVideoSurface()
            ffmpegEngine.setSurface(null)
        } catch (_: Exception) {}
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

    private fun syncAbRepeatState() {
        val ab = abRepeatController.state.value
        _playerState.value = _playerState.value.copy(
            abRepeatState = ab,
            abRepeatA = ab.pointA,
            abRepeatB = ab.pointB,
            isAbRepeatActive = ab.isActive
        )
    }

    fun setAbRepeatPointA(positionMs: Long? = null) {
        val pos = positionMs ?: activeEngine?.currentPositionMs ?: _playerState.value.currentPositionMs
        abRepeatController.setPointA(pos)
        syncAbRepeatState()
    }

    fun setAbRepeatPointB(positionMs: Long? = null) {
        val pos = positionMs ?: activeEngine?.currentPositionMs ?: _playerState.value.currentPositionMs
        abRepeatController.setPointB(pos, durationMs = _playerState.value.durationMs)
        syncAbRepeatState()
    }

    fun adjustAbRepeatPointA(deltaMs: Long) {
        abRepeatController.adjustPointA(deltaMs, _playerState.value.durationMs)
        syncAbRepeatState()
    }

    fun adjustAbRepeatPointB(deltaMs: Long) {
        abRepeatController.adjustPointB(deltaMs, _playerState.value.durationMs)
        syncAbRepeatState()
    }

    fun toggleAbRepeat(active: Boolean? = null) {
        abRepeatController.toggle(activeEngine?.currentPositionMs ?: _playerState.value.currentPositionMs)
        syncAbRepeatState()
    }

    fun clearAbRepeat() {
        abRepeatController.clear()
        syncAbRepeatState()
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
        if (activeEngine == ffmpegEngine) {
            activeEngine?.setSurface(surface)
        }
    }
    
    @OptIn(DelicateCoroutinesApi::class)
    fun release() {
        Logger.i("ExoPlayerManager", "Full release initiated")
        watchdogJob?.cancel()
        playbackJob?.cancel()
        
        activeEngine?.setSurface(null)
        activeEngine?.pause()
        
        playerListener?.let { media3Engine?.player?.removeListener(it) }
        playerListener = null
        
        val m3 = media3Engine
        val ff = ffmpegEngine
        media3Engine = null
        activeEngine = null
        
        // Use NonCancellable so release() completes even if the manager's scope is being
        // cancelled — this is the correct idiom, not GlobalScope which leaks coroutines.
        scope.launch(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
            try {
                m3?.release()
                ff.release()
                Logger.d("ExoPlayerManager", "Engines released successfully in background")
                synchronized(brokenEngines) { brokenEngines.forEach { try { it.release() } catch(e: Exception) {} }; brokenEngines.clear() }
            } catch (e: Exception) {
                Logger.e("ExoPlayerManager", "Error in background engine release", e)
            }
        }
        
        releaseAudioEffects()
        abandonAudioFocus()
        
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        try {
            context.unregisterReceiver(volumeReceiver)
        } catch (_: Exception) {}
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it as TelephonyCallback) }
            } else {
                telephonyCallback?.let { telephonyManager.listen(it as android.telephony.PhoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: Exception) {}
        telephonyCallback = null
        
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
    fun getAvailableTextTracks(): List<TextTrackInfo> = media3Engine?.player?.currentTracks?.let { t ->
        val list = mutableListOf<TextTrackInfo>()
        var idx = 0
        t.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { g ->
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val lang = f.language?.uppercase()
                val label = f.label
                val displayName = when {
                    !label.isNullOrBlank() && !lang.isNullOrBlank() -> "$label ($lang)"
                    !label.isNullOrBlank() -> label
                    !lang.isNullOrBlank() -> "Subtitle Track ${idx + 1} ($lang)"
                    else -> "Subtitle Track ${idx + 1}"
                }
                list.add(TextTrackInfo(idx++, displayName, g.isTrackSelected(i)))
            }
        }
        list
    } ?: emptyList()

    fun selectTextTrack(index: Int) {
        try {
            val player = media3Engine?.player ?: return
            if (index == -1) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
                Logger.i("ExoPlayerManager", "Text track disabled (OFF)")
                return
            }

            val tracks = player.currentTracks
            var targetGroup: Tracks.Group? = null
            var trackIndexInGroup = -1
            var currentIndex = 0
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        if (currentIndex == index) {
                            targetGroup = group
                            trackIndexInGroup = i
                            break
                        }
                        currentIndex++
                    }
                }
                if (targetGroup != null) break
            }
            if (targetGroup != null) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIndexInGroup))
                    .build()
                Logger.i("ExoPlayerManager", "Selected text track $index")
            }
        } catch (e: Exception) {
            Logger.e("ExoPlayerManager", "Error selecting text track $index", e)
        }
    }

    fun addExternalSubtitle(uri: Uri, name: String = "Subtitle") {
        try {
            val player = media3Engine?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return
            val currentPos = player.currentPosition
            val isPlaying = player.isPlaying

            val uriStr = uri.toString()
            val mimeType = when {
                uriStr.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                uriStr.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                uriStr.endsWith(".ass", ignoreCase = true) || uriStr.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                uriStr.endsWith(".ttml", ignoreCase = true) || uriStr.endsWith(".dfxp", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }

            val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(uri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setLabel(name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val existingSubtitles = currentMediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
            val newSubtitles = existingSubtitles + subtitleConfig

            val newMediaItem = currentMediaItem.buildUpon()
                .setSubtitleConfigurations(newSubtitles)
                .build()

            player.setMediaItem(newMediaItem, currentPos)
            player.prepare()
            if (isPlaying) player.play()

            val newTrackIndex = (getAvailableTextTracks().size - 1).coerceAtLeast(0)
            selectTextTrack(newTrackIndex)
            Logger.i("ExoPlayerManager", "Added external subtitle $name from $uri")
        } catch (e: Exception) {
            Logger.e("ExoPlayerManager", "Error adding external subtitle", e)
        }
    }
    
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
        val vFilterList = mutableListOf<String>()

        // 0. VIDEO TONEMAPPING (FFmpeg Fallback Only)
        if (state.isHdrContent && state.activeEngineName.contains("FFmpeg")) {
            // SDR Tonemapping for HDR content played via software
            vFilterList.add("tonemap=hable,format=yuv420p")
        }
        
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

        if (vFilterList.isNotEmpty()) {
            ffmpegEngine.setVideoFilters(vFilterList.joinToString(","))
        } else {
            ffmpegEngine.setVideoFilters("null") // No-op video filter
        }

        applyExoAudioEffects()
    }

    fun updateItemMetadata(updatedItem: MediaItem) {
        scope.launch(Dispatchers.Main) {
            val state = _playerState.value
            val isCurrent = state.currentItem?.uri?.toString() == updatedItem.uri.toString()
            val newCurrent = if (isCurrent) updatedItem else state.currentItem
            val newQueue = state.queue.map { if (it.uri.toString() == updatedItem.uri.toString()) updatedItem else it }
            
            _playerState.value = state.copy(
                currentItem = newCurrent,
                queue = newQueue
            )

            if (isCurrent) {
                FloatingPlayerService.startOrUpdateService(
                    context = context,
                    title = updatedItem.title,
                    artist = updatedItem.artist ?: updatedItem.album ?: "MediaNest",
                    isPlaying = state.isPlaying,
                    artworkUri = updatedItem.albumArtUri?.toString() ?: updatedItem.uri.toString(),
                    isVideo = updatedItem.type == com.medianest.data.db.MediaType.VIDEO
                )
            }
        }
    }

    fun setVideoBackgroundPlayEnabled(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isVideoBackgroundPlayEnabled = enabled)
        scope.launch(Dispatchers.IO) {
            settingsManager.setVideoBackgroundPlay(enabled)
        }
    }
    fun clearAllCache(onComplete: () -> Unit = {}) { onComplete() }
    fun addToQueue(items: List<MediaItem>) {}
    fun stopPlayback() { 
        stop()
        stopAllEnginesExcept(null)
        FloatingPlayerService.stopService(context)
        _playerState.value = PlayerState() 
    }

    private var currentVideoEffect: com.medianest.ui.components.media.MediaEffect = com.medianest.ui.components.media.MediaEffect.OFF

    fun setVideoEffect(effect: com.medianest.ui.components.media.MediaEffect) {
        currentVideoEffect = effect
        // Note: Picture mode color matrix filters are rendered via Compose graphicsLayer RenderEffect 
        // in VideoPlayerScreen.kt to keep ExoPlayer's surface pipeline 100% stable without TextureView surface teardowns.
    }
}
