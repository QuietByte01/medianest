package com.example.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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
    val isBackgroundPlayEnabled: Boolean = false,
    val activeDecoderName: String = "Hardware Default",
    val isHardwareAccelerated: Boolean = true,
    val droppedFrames: Int = 0,
    val decoderFallbackReason: String? = null,
    val audioSessionId: Int = 0
)

@OptIn(UnstableApi::class)
class ExoPlayerManager private constructor(private val context: Context) {

    companion object {
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
            combine(
                com.example.MediaNestApp.instance.settingsManager.showPlaybackNotification,
                com.example.MediaNestApp.instance.settingsManager.showVideoNotification,
                _playerState
            ) { audioNotif, videoNotif, state ->
                Triple(audioNotif, videoNotif, state)
            }.collectLatest { (audioNotif, videoNotif, state) ->
                val currentItem = state.currentItem
                if (currentItem != null && state.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED) {
                    val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.example.data.db.MediaType.VIDEO
                    val shouldShow = if (isVideo) videoNotif else audioNotif
                    if (shouldShow) {
                        FloatingPlayerService.startOrUpdateService(
                            context = context,
                            title = currentItem.title,
                            artist = currentItem.artist ?: currentItem.album ?: currentItem.bucketName ?: "MediaNest",
                            isPlaying = state.isPlaying,
                            artworkUri = currentItem.albumArtUri?.toString() ?: currentItem.uri.toString()
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
                true
            )
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
                startPositionTracker()
                attachAudioEffect()
            } else {
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
            } else if (playbackState == Player.STATE_ENDED) {
                val currentRepeatMode = exoPlayer.repeatMode
                if (currentRepeatMode == Player.REPEAT_MODE_ONE || currentRepeatMode == Player.REPEAT_MODE_ALL) {
                    exoPlayer.seekTo(0L)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
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
                recordPlay(currentItem.uri.toString())
            }
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

        try {
            exoPlayer.stop()
            exoPlayer.setMediaItems(media3Items, targetIndex, startPosMs)
            exoPlayer.prepare()
            exoPlayer.play()

            _playerState.value = _playerState.value.copy(
                queue = items,
                queueIndex = targetIndex,
                currentItem = currentTarget
            )
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Failed to prepare playback", e)
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
            val optUri = ContentUriUtils.resolveOptimizedUri(context, uri)
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
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
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
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        } else {
            // End of queue. If repeat ALL is not on, we loop manually to start
            if (exoPlayer.repeatMode == Player.REPEAT_MODE_OFF) {
                exoPlayer.seekToDefaultPosition(0)
            }
        }
    }

    fun previous() {
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

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun setRepeatMode(repeatMode: Int) {
        exoPlayer.repeatMode = repeatMode
        _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isBackgroundPlayEnabled = enabled)
        if (enabled) {
            // Keep audio focus and allow playback to continue when app goes to background.
            // ExoPlayer already handles AudioAttributes with handleAudioBecomingNoisy=true by default.
            // Ensure the foreground notification service is started so the OS does not kill playback.
            val currentItem = _playerState.value.currentItem
            if (currentItem != null) {
                FloatingPlayerService.startOrUpdateService(
                    context = context,
                    title = currentItem.title,
                    artist = currentItem.artist ?: currentItem.album ?: currentItem.bucketName ?: "MediaNest",
                    isPlaying = _playerState.value.isPlaying,
                    artworkUri = currentItem.albumArtUri?.toString() ?: currentItem.uri.toString()
                )
            }
        }
        // When disabled, the notification service lifecycle is managed by the existing
        // combine collector (lines 97-123) which stops the service when !isPlaying.
    }

    fun setShuffleMode(shuffleMode: Boolean) {
        exoPlayer.shuffleModeEnabled = shuffleMode
        _playerState.value = _playerState.value.copy(isShuffle = shuffleMode)
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
                val gainMb = (clamped * 20).toInt()
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
                                androidx.media3.common.TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
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
