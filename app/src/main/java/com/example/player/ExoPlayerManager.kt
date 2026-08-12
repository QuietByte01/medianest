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
    val decoderFallbackReason: String? = null,
    val audioSessionId: Int = 0,
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
                // Update player attributes if playing? 
                // For simplicity, we'll re-apply focus strategy on next play
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

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.audioRepeatMode.collectLatest { mode ->
                _playerState.value = _playerState.value.copy(audioRepeatMode = mode)
                applySettingsForCurrentType()
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.videoRepeatMode.collectLatest { mode ->
                _playerState.value = _playerState.value.copy(videoRepeatMode = mode)
                applySettingsForCurrentType()
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.audioShuffleMode.collectLatest { enabled ->
                _playerState.value = _playerState.value.copy(isAudioShuffleEnabled = enabled)
                applySettingsForCurrentType()
            }
        }

        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.videoShuffleMode.collectLatest { enabled ->
                _playerState.value = _playerState.value.copy(isVideoShuffleEnabled = enabled)
                applySettingsForCurrentType()
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
                // Keep service running if we have a track, even if paused, to prevent process death.
                // Only stop if the queue is empty or playback explicitly ended.
                if (currentItem != null && (exoPlayer.playbackState != Player.STATE_ENDED || state.isPlaying)) {
                    val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.example.data.db.MediaType.VIDEO
                    
                    // Check background play permission for the specific type
                    val bgPlayEnabled = if (isVideo) state.isVideoBackgroundPlayEnabled else state.isAudioBackgroundPlayEnabled
                    
                    // Service shutdown policy:
                    // 1. Never stop if currently playing.
                    // 2. Only stop if (paused AND bg play is off) AND we are NOT in a transition.
                    // We detect "transition" by checking if playbackState is IDLE but queue is NOT empty.
                    val isTransitioning = exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0
                    
                    if (!bgPlayEnabled && !state.isPlaying && !isTransitioning) {
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
    }

    private val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val sorted = if (!isHwAccelEnabled || currentDecoderPreference == "SOFTWARE") {
            decoders.sortedWith(compareByDescending { it.softwareOnly || !it.hardwareAccelerated })
        } else if (currentDecoderPreference == "HARDWARE") {
            decoders.sortedWith(compareByDescending { it.hardwareAccelerated })
        } else { // "AUTO" -> Hardware -> Vendor -> Software fallback strategy
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

            if (!hasPermission) {
                Log.w("ExoPlayerManager", "READ_PHONE_STATE permission not granted. Telephony listener skipped.")
                return
            }

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
        } catch (e: SecurityException) {
            Log.e("ExoPlayerManager", "SecurityException setting up telephony listener", e)
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Error setting up telephony listener", e)
        }
    }

    private fun handleCallState(state: Int) {
        val wasCallActive = isCallActive
        isCallActive = state != TelephonyManager.CALL_STATE_IDLE
        
        if (isCallActive && !wasCallActive) {
            if (exoPlayer.isPlaying) {
                isPausedByCall = true
                exoPlayer.pause()
            }
        } else if (!isCallActive && wasCallActive) {
            if (isPausedByCall) {
                isPausedByCall = false
                exoPlayer.play()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) {
                if (isAutoResumeOnBluetooth && !exoPlayer.isPlaying && _playerState.value.currentItem != null) {
                    exoPlayer.play()
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
                    exoPlayer.play()
                    playOnFocusGain = false
                }
                exoPlayer.volume = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                playOnFocusGain = false
                exoPlayer.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // If uninterrupted mode is ON, we ONLY pause if it's a call
                // But wait, the system might have already paused us if it's a call.
                // Actually, if we manage focus ourselves, we decide.
                if (isUninterruptedMode && !isCallActive) {
                    // Ignore transient loss (notifications)
                } else {
                    playOnFocusGain = exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isUninterruptedMode) {
                    // Ignore ducking
                    exoPlayer.volume = 1.0f
                } else {
                    // Standard ducking: lower volume to 20%
                    exoPlayer.volume = 0.2f
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
                .setWillPauseWhenDucked(false) // We handle ducking manually in listener
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

    val exoPlayer: ExoPlayer by lazy {
        // 1. Configure Extractors for maximum format compatibility
        //    AVI has no native Media3 extractor — falls through to platform MediaCodec.
        //    FLV, MKV, MP4 are handled natively. Flags below maximise seek accuracy & resilience.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)           // Allow CBR seeking even without seek table
            .setAdtsExtractorFlags(
                androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
            .setAmrExtractorFlags(
                androidx.media3.extractor.amr.AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
            .setMp3ExtractorFlags(
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING
            )
            .setMatroskaExtractorFlags(0) // Use defaults — FLAG_EMIT_CUES_AS_METADATA not in Media3 1.5.1
            .setMp4ExtractorFlags(
                androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS
            )

        // 2. Configure MediaSourceFactory with optimized Extractors
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)

        // 3. Tune LoadControl for smooth high-bitrate AVI / FLV / MKV playback.
        //    AVI (via platform MediaCodec) benefits from a larger pre-roll buffer.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */                    2_000,   // 2 s  — enough for legacy AVI codec init
                /* maxBufferMs = */                   30_000,   // 30 s — big enough for high-bitrate AVI
                /* bufferForPlaybackMs = */              250,   // Start playing after just 250 ms
                /* bufferForPlaybackAfterRebufferMs = */ 1_500  // 1.5 s re-buffer threshold
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .build()

        // 4. Custom Renderer Factory with MediaCodecSelector & Decoder Fallback
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setMediaCodecSelector(customMediaCodecSelector)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false // Disable automatic focus handling, we do it manually now
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
            }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            Log.d("MediaNestDecoder", "Video Decoder Initialized: $decoderName (${initializationDurationMs}ms)")
            val lowerName = decoderName.lowercase()
            val isHw = !lowerName.contains("omx.google") &&
                       !lowerName.contains("c2.android") &&
                       !lowerName.contains("sw") &&
                       !lowerName.contains("software")
            _playerState.value = _playerState.value.copy(
                activeDecoderName = decoderName,
                isHardwareAccelerated = isHw,
                decoderFallbackReason = if (!isHw) "Fallback to Software Decoder ($decoderName)" else null
            )
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            Log.d("MediaNestDecoder", "Audio Decoder Initialized: $decoderName")
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            Log.w("MediaNestDecoder", "Dropped $droppedFrames video frames over ${elapsedMs}ms")
            _playerState.value = _playerState.value.copy(
                droppedFrames = _playerState.value.droppedFrames + droppedFrames
            )
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: androidx.media3.common.PlaybackException
        ) {
            Log.e("MediaNestDecoder", "Playback error encountered: ${error.message}", error)
            _playerState.value = _playerState.value.copy(
                decoderFallbackReason = "Playback Error: ${error.message}"
            )
        }
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var positionUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                requestAudioFocus()
                startPositionTracker()
                attachAudioEffect()
            } else {
                if (!isPausedByCall && !playOnFocusGain) {
                    abandonAudioFocus()
                }
                stopPositionTracker()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _playerState.value = _playerState.value.copy(isPlaying = playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _playerState.value = _playerState.value.copy(
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    audioSessionId = exoPlayer.audioSessionId
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: Media3Item?, reason: Int) {
            val curIndex = exoPlayer.currentMediaItemIndex
            val queue = _playerState.value.queue
            if (curIndex in queue.indices) {
                val currentItem = queue[curIndex]
                _playerState.value = _playerState.value.copy(
                    currentItem = currentItem,
                    queueIndex = curIndex,
                    audioSessionId = exoPlayer.audioSessionId
                )
                applySettingsForCurrentType()
                recordPlay(currentItem.uri.toString())
            }
        }
    }

    private fun applySettingsForCurrentType() {
        val currentItem = _playerState.value.currentItem ?: return
        val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.example.data.db.MediaType.VIDEO
        
        val targetRepeatMode = if (isVideo) _playerState.value.videoRepeatMode else _playerState.value.audioRepeatMode
        val targetShuffleEnabled = if (isVideo) _playerState.value.isVideoShuffleEnabled else _playerState.value.isAudioShuffleEnabled
        
        if (exoPlayer.repeatMode != targetRepeatMode) {
            exoPlayer.repeatMode = targetRepeatMode
            _playerState.value = _playerState.value.copy(repeatMode = targetRepeatMode)
        }
        if (exoPlayer.shuffleModeEnabled != targetShuffleEnabled) {
            exoPlayer.shuffleModeEnabled = targetShuffleEnabled
            _playerState.value = _playerState.value.copy(isShuffle = targetShuffleEnabled)
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

    fun playMediaList(items: List<MediaItem>, startIndex: Int = 0, startPosMs: Long = 0L) {
        if (items.isEmpty()) return

        val targetIndex = startIndex.coerceIn(0, items.size - 1)
        val currentTarget = items[targetIndex]

        val media3Items = items.map { item ->
            Media3Item.Builder()
                .setUri(item.uri)
                .setMimeType(item.mimeType)
                .build()
        }

        scope.launch {
            withContext(Dispatchers.Main) {
                try {
                    // Update state FIRST so service observers see the change
                    _playerState.value = _playerState.value.copy(
                        queue = items,
                        queueIndex = targetIndex,
                        currentItem = currentTarget
                    )

                    // exoPlayer.stop() removed to prevent service death on item change. 
                    // setMediaItems already resets the player state for the new items.
                    exoPlayer.setMediaItems(media3Items, targetIndex, startPosMs)
                    exoPlayer.prepare()
                    if (requestAudioFocus()) {
                        exoPlayer.play()
                    }
                } catch (e: Exception) {
                    Log.e("ExoPlayerManager", "Failed to prepare playback", e)
                }
            }
        }

        recordPlay(currentTarget.uri.toString())
    }

    fun addToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val optimizedItems = items.map { item ->
                val optUri = ContentUriUtils.resolveOptimizedUri(context, item.uri)
                if (optUri != item.uri) item.copy(uri = optUri) else item
            }

            val media3Items = optimizedItems.map { item ->
                Media3Item.Builder()
                    .setUri(item.uri)
                    .setMimeType(item.mimeType)
                    .build()
            }

            withContext(Dispatchers.Main) {
                exoPlayer.addMediaItems(media3Items)
                val updatedQueue = _playerState.value.queue + optimizedItems
                _playerState.value = _playerState.value.copy(queue = updatedQueue)
            }
        }
    }

    fun playSingleUri(uri: Uri, title: String, mimeType: String, startPosMs: Long = 0L) {
        scope.launch(Dispatchers.IO) {
            // Avoid aggressive resolution to file:// for external intents as it might lose permission.
            // Only resolve if it's already an internal URI.
            val isExternalIntentUri = uri.scheme == "content" && !uri.toString().contains(context.packageName)
            val optUri = if (isExternalIntentUri) uri else ContentUriUtils.resolveOptimizedUri(context, uri)
            
            val singleItem = if (mimeType.startsWith("audio")) {
                com.example.util.AudioMetadataUtils.extractMetadata(context, optUri, rawTitleHint = title, mimeTypeHint = mimeType)
            } else {
                MediaItem(
                    id = System.currentTimeMillis(),
                    uri = optUri,
                    title = title,
                    mimeType = mimeType,
                    type = if (mimeType.startsWith("video")) com.example.data.db.MediaType.VIDEO else com.example.data.db.MediaType.IMAGE
                )
            }
            playMediaList(listOf(singleItem), 0, startPosMs)
        }
    }

    fun togglePlayPause() {
        scope.launch {
            withContext(Dispatchers.Main) {
                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0)
                    if (requestAudioFocus()) {
                        exoPlayer.play()
                    }
                } else if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    if (requestAudioFocus()) {
                        exoPlayer.play()
                    }
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            withContext(Dispatchers.Main) {
                exoPlayer.seekTo(positionMs)
                _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
            }
        }
    }

    fun seekForward(ms: Long = 10000L) {
        val target = (exoPlayer.currentPosition + ms).coerceAtMost(exoPlayer.duration)
        seekTo(target)
    }

    fun seekBackward(ms: Long = 10000L) {
        val target = (exoPlayer.currentPosition - ms).coerceAtLeast(0L)
        seekTo(target)
    }

    fun next() {
        scope.launch {
            withContext(Dispatchers.Main) {
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                } else {
                    // End of queue. If repeat ALL is not on, we loop manually to start
                    if (exoPlayer.repeatMode == Player.REPEAT_MODE_OFF) {
                        exoPlayer.seekToDefaultPosition(0)
                    }
                }
            }
        }
    }

    fun previous() {
        scope.launch {
            withContext(Dispatchers.Main) {
                if (exoPlayer.currentPosition > 3000L) {
                    seekTo(0L)
                } else if (exoPlayer.hasPreviousMediaItem()) {
                    exoPlayer.seekToPreviousMediaItem()
                } else {
                    // Loop to end manually if at start
                    val queueSize = exoPlayer.mediaItemCount
                    if (queueSize > 0) {
                        exoPlayer.seekToDefaultPosition(queueSize - 1)
                    }
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun setRepeatMode(repeatMode: Int) {
        scope.launch {
            withContext(Dispatchers.Main) {
                val currentItem = _playerState.value.currentItem
                val isVideo = currentItem?.mimeType?.startsWith("video") == true || currentItem?.type == com.example.data.db.MediaType.VIDEO
                
                if (isVideo) {
                    com.example.MediaNestApp.instance.settingsManager.setVideoRepeatMode(repeatMode)
                } else {
                    com.example.MediaNestApp.instance.settingsManager.setAudioRepeatMode(repeatMode)
                }
                
                exoPlayer.repeatMode = repeatMode
                _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
            }
        }
    }

    fun setVideoBackgroundPlayEnabled(enabled: Boolean) {
        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.setVideoBackgroundPlay(enabled)
        }
    }

    fun setAudioBackgroundPlayEnabled(enabled: Boolean) {
        scope.launch {
            com.example.MediaNestApp.instance.settingsManager.setAudioBackgroundPlay(enabled)
        }
    }

    fun setShuffleMode(shuffleMode: Boolean) {
        scope.launch {
            withContext(Dispatchers.Main) {
                val currentItem = _playerState.value.currentItem
                val isVideo = currentItem?.mimeType?.startsWith("video") == true || currentItem?.type == com.example.data.db.MediaType.VIDEO
                
                if (isVideo) {
                    com.example.MediaNestApp.instance.settingsManager.setVideoShuffleMode(shuffleMode)
                } else {
                    com.example.MediaNestApp.instance.settingsManager.setAudioShuffleMode(shuffleMode)
                }
                
                exoPlayer.shuffleModeEnabled = shuffleMode
                _playerState.value = _playerState.value.copy(isShuffle = shuffleMode)
            }
        }
    }

    fun setAudioBoost(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _playerState.value = _playerState.value.copy(audioBoostPercent = clamped)

        try {
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                }
                // Gain in mB (millibels): 100% -> 2000mB (+20dB)
                val gainMb = clamped * 20
                loudnessEnhancer?.setTargetGain(gainMb)
                loudnessEnhancer?.enabled = clamped > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class AudioTrackInfo(
        val index: Int,
        val name: String,
        val language: String,
        val mimeType: String,
        val isSelected: Boolean
    )

    fun getAvailableAudioTracks(): List<AudioTrackInfo> {
        val tracks = mutableListOf<AudioTrackInfo>()
        try {
            val currentTracks = exoPlayer.currentTracks
            var trackCount = 0
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val lang = format.language?.uppercase() ?: ""
                        val channelCount = format.channelCount
                        val channelsStr = if (channelCount == 6) "5.1 Surround" else if (channelCount > 0) "${channelCount} Ch" else ""
                        val mime = format.sampleMimeType?.replace("audio/", "")?.uppercase() ?: ""
                        val trackName = buildString {
                            append("Audio Track ${trackCount + 1}")
                            if (lang.isNotBlank()) append(" ($lang)")
                            val details = listOf(mime, channelsStr).filter { it.isNotBlank() }.joinToString(" / ")
                            if (details.isNotBlank()) {
                                append(" • ")
                                append(details)
                            }
                        }
                        val isSelected = trackGroup.isTrackSelected(i)
                        tracks.add(AudioTrackInfo(trackCount, trackName, lang, mime, isSelected))
                        trackCount++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    data class SubtitleTrackInfo(
        val index: Int,
        val name: String,
        val language: String,
        val isSelected: Boolean
    )

    fun getAvailableTextTracks(): List<SubtitleTrackInfo> {
        val tracks = mutableListOf<SubtitleTrackInfo>()
        try {
            val currentTracks = exoPlayer.currentTracks
            var textCount = 0
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val lang = format.language?.uppercase() ?: ""
                        val label = format.label ?: ""
                        val trackName = buildString {
                            append("Subtitle ${textCount + 1}")
                            if (label.isNotBlank()) append(" ($label)")
                            else if (lang.isNotBlank()) append(" ($lang)")
                        }
                        val isSelected = trackGroup.isTrackSelected(i)
                        tracks.add(SubtitleTrackInfo(textCount, trackName, lang, isSelected))
                        textCount++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    fun selectTextTrack(trackIndex: Int) {
        try {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            if (trackIndex < 0) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                val currentTracks = exoPlayer.currentTracks
                var textCount = 0
                for (trackGroup in currentTracks.groups) {
                    if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until trackGroup.length) {
                            if (textCount == trackIndex) {
                                builder.setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                                )
                                exoPlayer.trackSelectionParameters = builder.build()
                                return
                            }
                            textCount++
                        }
                    }
                }
            }
            exoPlayer.trackSelectionParameters = builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectAudioTrack(trackIndex: Int) {
        try {
            val currentTracks = exoPlayer.currentTracks
            var trackCount = 0
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until trackGroup.length) {
                        if (trackCount == trackIndex) {
                            builder.setOverrideForType(
                                TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                            )
                            exoPlayer.trackSelectionParameters = builder.build()
                            return
                        }
                        trackCount++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addExternalSubtitle(uri: Uri, name: String = "External Subtitle") {
        try {
            val currentMedia3Item = exoPlayer.currentMediaItem ?: return
            val currentPosition = exoPlayer.currentPosition
            val isPlaying = exoPlayer.isPlaying

            val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(uri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP) // Most common for .srt
                .setLanguage("en")
                .setLabel(name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val newItem = currentMedia3Item.buildUpon()
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()

            exoPlayer.setMediaItem(newItem, false) // false = don't reset position (but we'll seek anyway)
            exoPlayer.prepare()
            exoPlayer.seekTo(currentPosition)
            if (isPlaying) exoPlayer.play()
            
            // Wait for tracks to be updated, then select the new subtitle
            scope.launch {
                delay(500)
                val tracks = exoPlayer.currentTracks
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                            .build()
                        exoPlayer.trackSelectionParameters = params
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Error adding external subtitle", e)
        }
    }

    private fun attachAudioEffect() {
        setAudioBoost(_playerState.value.audioBoostPercent)
    }

    private fun startPositionTracker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
                delay(200)
            }
        }
    }

    private fun stopPositionTracker() {
        positionUpdateJob?.cancel()
    }

    fun stopPlayback() {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _playerState.value = PlayerState()
            FloatingPlayerService.stopService(context)
            if (activeManager == this) {
                activeManager = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Clears thumbnails, image caches (Coil), and internal app caches.
     */
    fun clearAllCache(onComplete: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Clear Coil Cache
                val imageLoader = coil.ImageLoader(context)
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()

                // 2. Clear Internal Cache Directory
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()

                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        try {
            if (activeManager == this) {
                activeManager = null
                FloatingPlayerService.stopService(context)
            }
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
