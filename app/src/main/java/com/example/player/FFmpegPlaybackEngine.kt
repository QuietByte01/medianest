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
    private external fun nativeSetAudioFilters(ptr: Long, filters: String)

    private val _diagnosticState = MutableStateFlow(
        EngineDiagnosticState(
            engineName = "FFmpeg Native (Oboe)",
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
            decoderName = "FFmpeg Native (Oboe Low Latency)"
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
    }

    override fun play() {
        if (nativeContextPtr != 0L) try { nativePlay(nativeContextPtr) } catch (e: Throwable) {}
    }

    override fun pause() {
        if (nativeContextPtr != 0L) try { nativePause(nativeContextPtr) } catch (e: Throwable) {}
    }

    override fun seekTo(positionMs: Long) {
        if (nativeContextPtr != 0L) try { nativeSeek(nativeContextPtr, positionMs) } catch (e: Throwable) {}
    }

    override fun setPlaybackSpeed(speed: Float) {}
    override fun setRepeatMode(repeatMode: Int) {}

    override fun setSurface(surface: Surface?) {
        currentSurface = surface
        if (nativeContextPtr != 0L) try { nativeUpdateSurface(nativeContextPtr, surface) } catch (e: Throwable) {}
    }

    override fun setVolume(volume: Float) {}

    override fun release() {
        if (nativeContextPtr != 0L) {
            try {
                nativeRelease(nativeContextPtr)
                nativeContextPtr = 0L
            } catch (e: Throwable) {}
        }
    }

    fun setAudioFilters(filters: String) {
        if (nativeContextPtr != 0L) try { nativeSetAudioFilters(nativeContextPtr, filters) } catch (e: Throwable) {}
    }

    // Called from Native
    fun onNativeStatsUpdate(dropped: Int, audioErrs: Int, tsRecov: Int) {
        _diagnosticState.value = _diagnosticState.value.copy(
            droppedFrames = dropped,
            audioDecodeErrors = audioErrs,
            timestampRecoveryCount = tsRecov
        )
    }

    override val currentPositionMs: Long
        get() = if (nativeContextPtr != 0L) try { nativeGetPosition(nativeContextPtr) } catch(e: Throwable) { 0L } else 0L

    override val durationMs: Long
        get() = if (nativeContextPtr != 0L) try { nativeGetDuration(nativeContextPtr) } catch(e: Throwable) { 0L } else 0L

    override val isPlaying: Boolean
        get() = if (nativeContextPtr != 0L) try { nativeIsPlaying(nativeContextPtr) } catch(e: Throwable) { false } else false
}
