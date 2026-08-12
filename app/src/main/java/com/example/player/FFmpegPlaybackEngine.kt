package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FFmpegPlaybackEngine(context: Context) : PlaybackEngine {

    companion object {
        private const val TAG = "FFmpegPlaybackEngine"
        init {
            try {
                System.loadLibrary("medianest_ffmpeg")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load medianest_ffmpeg native library", e)
            }
        }
    }

    private var nativeContextPtr: Long = 0L

    private external fun nativeInit(): Long
    private external fun nativePrepare(ptr: Long, uri: String, surface: Surface?): Boolean
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
        try {
            nativeContextPtr = nativeInit()
            if (nativeContextPtr == 0L) {
                Log.e(TAG, "Failed to initialize native FFmpeg context")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing native FFmpeg", e)
        }
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        if (nativeContextPtr == 0L) return
        
        val profile = MediaCapabilityInspector.inspect(uri)
        _diagnosticState.value = _diagnosticState.value.copy(
            containerName = profile.container,
            videoCodec = profile.videoCodec,
            audioCodec = profile.audioCodec,
            decoderName = "FFmpeg Software Decoder"
        )
        
        nativePrepare(nativeContextPtr, uri.toString(), currentSurface)
        if (playWhenReady) {
            nativePlay(nativeContextPtr)
        }
        Log.i(TAG, "Prepared FFmpeg engine for $uri with profile $profile")
    }

    override fun play() {
        if (nativeContextPtr != 0L) nativePlay(nativeContextPtr)
    }

    override fun pause() {
        if (nativeContextPtr != 0L) nativePause(nativeContextPtr)
    }

    override fun seekTo(positionMs: Long) {
        if (nativeContextPtr != 0L) nativeSeek(nativeContextPtr, positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        // FFmpeg speed adjustment
    }

    override fun setRepeatMode(repeatMode: Int) {
        // Repeat mode
    }

    override fun setSurface(surface: Surface?) {
        currentSurface = surface
        if (nativeContextPtr != 0L) {
            nativeUpdateSurface(nativeContextPtr, surface)
        }
    }

    override fun setVolume(volume: Float) {
        // Volume adjustment
    }

    override fun release() {
        if (nativeContextPtr != 0L) {
            try {
                nativeRelease(nativeContextPtr)
                nativeContextPtr = 0L
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing native FFmpeg", e)
            }
        }
    }

    override val currentPositionMs: Long
        get() = if (nativeContextPtr != 0L) nativeGetPosition(nativeContextPtr) else 0L

    override val durationMs: Long
        get() = if (nativeContextPtr != 0L) nativeGetDuration(nativeContextPtr) else 0L

    override val isPlaying: Boolean
        get() = if (nativeContextPtr != 0L) nativeIsPlaying(nativeContextPtr) else false
}
