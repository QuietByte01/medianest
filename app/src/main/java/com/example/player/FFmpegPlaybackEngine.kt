package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FFmpegPlaybackEngine(private val context: Context) : PlaybackEngine {

    companion object {
        private const val TAG = "FFmpegPlaybackEngine"
        private var isLibLoaded = false
        init {
            try {
                System.loadLibrary("medianest_ffmpeg")
                isLibLoaded = true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load medianest_ffmpeg native library: ${e.message}")
            }
        }
    }

    private var nativeContextPtr: Long = 0L
    private var lastProbeResult: String? = null

    private external fun nativeInit(): Long
    private external fun nativeProbe(ptr: Long, fd: Int): String?
    private external fun nativePrepare(ptr: Long, fd: Int, surface: Surface?): Boolean
    private external fun nativePlay(ptr: Long)
    private external fun nativePause(ptr: Long)
    private external fun nativeSeek(ptr: Long, positionMs: Long)
    private external fun nativeUpdateSurface(ptr: Long, surface: Surface?)
    private external fun nativeRelease(ptr: Long)
    private external fun nativeGetPosition(ptr: Long): Long
    private external fun nativeGetDuration(ptr: Long): Long
    private external fun nativeIsPlaying(ptr: Long): Boolean

    private val _diagnosticState = MutableStateFlow(
        EngineDiagnosticState(
            engineName = "FFmpeg",
            containerName = "Unknown",
            videoCodec = "Unknown",
            audioCodec = "Unknown",
            decoderName = "FFmpeg Software Decoder",
            isHardwareAccelerated = false,
        )
    )
    override val diagnosticState: StateFlow<EngineDiagnosticState> = _diagnosticState.asStateFlow()

    private var currentSurface: Surface? = null

    init {
        if (isLibLoaded) {
            try {
                nativeContextPtr = nativeInit()
                if (nativeContextPtr == 0L) {
                    Log.e(TAG, "Failed to initialize native FFmpeg context")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error calling nativeInit: ${e.message}")
            }
        }
    }

    fun probe(uri: Uri): String? {
        if (nativeContextPtr == 0L) return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val res = nativeProbe(nativeContextPtr, pfd.fd)
                lastProbeResult = res
                res
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to probe URI: $uri", e)
            null
        }
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        if (nativeContextPtr == 0L) return
        
        val profile = MediaCapabilityInspector.inspect(uri, lastProbeResult)
        _diagnosticState.value = _diagnosticState.value.copy(
            containerName = profile.container,
            videoCodec = profile.videoCodec,
            audioCodec = profile.audioCodec,
            decoderName = "FFmpeg Software Decoder"
        )
        
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                nativePrepare(nativeContextPtr, pfd.fd, currentSurface)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open FD for prepare: $uri", e)
        }

        if (playWhenReady) {
            play()
        }
        Log.i(TAG, "Prepared FFmpeg engine for $uri with profile $profile")
    }

    override fun play() {
        if (nativeContextPtr != 0L) {
            try {
                nativePlay(nativeContextPtr)
            } catch (e: Throwable) {
                Log.e(TAG, "nativePlay failed", e)
            }
        }
    }

    override fun pause() {
        if (nativeContextPtr != 0L) {
            try {
                nativePause(nativeContextPtr)
            } catch (e: Throwable) {
                Log.e(TAG, "nativePause failed", e)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        if (nativeContextPtr != 0L) {
            try {
                nativeSeek(nativeContextPtr, positionMs)
            } catch (e: Throwable) {
                Log.e(TAG, "nativeSeek failed", e)
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {}
    override fun setRepeatMode(repeatMode: Int) {}

    override fun setSurface(surface: Surface?) {
        currentSurface = surface
        if (nativeContextPtr != 0L) {
            try {
                nativeUpdateSurface(nativeContextPtr, surface)
            } catch (e: Throwable) {
                Log.e(TAG, "nativeUpdateSurface failed", e)
            }
        }
    }

    override fun setVolume(volume: Float) {}

    override fun release() {
        if (nativeContextPtr != 0L) {
            try {
                nativeRelease(nativeContextPtr)
                nativeContextPtr = 0L
            } catch (e: Throwable) {
                Log.e(TAG, "nativeRelease failed", e)
            }
        }
        audioTrack?.release()
        audioTrack = null
    }

    override val currentPositionMs: Long
        get() = if (nativeContextPtr != 0L) { try { nativeGetPosition(nativeContextPtr) } catch(e: Throwable) { 0L } } else 0L

    override val durationMs: Long
        get() = if (nativeContextPtr != 0L) { try { nativeGetDuration(nativeContextPtr) } catch(e: Throwable) { 0L } } else 0L

    override val isPlaying: Boolean
        get() = if (nativeContextPtr != 0L) { try { nativeIsPlaying(nativeContextPtr) } catch(e: Throwable) { false } } else false

    fun onNativeStatsUpdate(dropped: Int, audioErrs: Int, tsRecov: Int) {
        _diagnosticState.value = _diagnosticState.value.copy(
            droppedFrames = dropped,
            audioDecodeErrors = audioErrs,
            timestampRecoveryCount = tsRecov
        )
    }

    private var audioTrack: android.media.AudioTrack? = null
    
    fun onAudioData(data: ByteArray, sampleRate: Int, channels: Int) {
        try {
            val track = audioTrack
            if (track == null || track.sampleRate != sampleRate) {
                Log.i(TAG, "Recreating AudioTrack: $sampleRate Hz, $channels ch")
                track?.release()
                val channelConfig = if (channels == 1) android.media.AudioFormat.CHANNEL_OUT_MONO else android.media.AudioFormat.CHANNEL_OUT_STEREO
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val format = android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
                
                val minBufSize = android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT)
                val newTrack = android.media.AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBufSize * 4)
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build()
                newTrack.play()
                audioTrack = newTrack
            }
            
            val result = audioTrack?.write(data, 0, data.size, android.media.AudioTrack.WRITE_NON_BLOCKING)
            if (result != null && result < 0) {
                Log.w(TAG, "AudioTrack write error: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write failed", e)
        }
    }
}
