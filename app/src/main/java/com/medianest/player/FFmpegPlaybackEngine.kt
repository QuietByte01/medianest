package com.medianest.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.medianest.util.Logger
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
                Logger.e(TAG, "Failed to load medianest_ffmpeg native library: ${e.message}")
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
    private external fun nativeSetRepeatMode(ptr: Long, repeatMode: Int)
    private external fun nativeSetAudioFilters(ptr: Long, filters: String)
    private external fun nativeSetVideoFilters(ptr: Long, filters: String)

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
    internal val _isSurfaceReady = MutableStateFlow(false)
    val isSurfaceReady: StateFlow<Boolean> = _isSurfaceReady.asStateFlow()

    init {
        if (isLibLoaded) {
            try {
                nativeContextPtr = nativeInit()
            } catch (e: Throwable) {
                Logger.e(TAG, "Error calling nativeInit: ${e.message}")
            }
        }
    }

    fun probe(uri: Uri): String? {
        if (nativeContextPtr == 0L) return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                // dup() the fd so the native layer has its own independent copy that survives PFD close
                val dupFd = android.os.ParcelFileDescriptor.dup(pfd.fileDescriptor)
                val res = try {
                    nativeProbe(nativeContextPtr, dupFd.fd)
                } finally {
                    dupFd.close()
                }
                lastProbeResult = res
                res
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to probe URI: $uri", e)
            null
        }
    }

    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        if (nativeContextPtr == 0L) {
            Logger.e(TAG, "prepare failed: nativeContextPtr is 0")
            return
        }
        
        val profile = MediaCapabilityInspector.inspect(uri, lastProbeResult)
        Logger.i(TAG, "Preparing FFmpeg for $uri. Profile: $profile")
        _diagnosticState.value = _diagnosticState.value.copy(
            containerName = profile.container,
            videoCodec = profile.videoCodec,
            audioCodec = profile.audioCodec,
            decoderName = "FFmpeg Native (AAudio 32-bit Float)",
            audioSampleFormat = "32-bit Float PCM",
            audioSharingMode = "AAudio Exclusive"
        )
        
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                // dup() the fd — native layer holds it open independently for seeks/async reads
                val dupFd = android.os.ParcelFileDescriptor.dup(pfd.fileDescriptor)
                Logger.d(TAG, "Opening FD: ${dupFd.fd} for nativePrepare")
                try {
                    nativePrepare(nativeContextPtr, dupFd.fd, currentSurface)
                } finally {
                    // CRITICAL: We must close the Kotlin-side ParcelFileDescriptor. 
                    // The native layer (JNI) performs its own dup() to take ownership of the underlying FD.
                    dupFd.close()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open FD for prepare: $uri", e)
        }

        if (playWhenReady) {
            Logger.d(TAG, "Calling nativePlay from prepare")
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

    override fun stop() {
        // NOTE: Do NOT seekTo(0L) here — it forces an expensive native FFmpeg demuxer
        // reset on every stop() call, causing AV1 lag. Position resets happen when
        // prepare() is called with a new URI.
        if (nativeContextPtr != 0L) try { nativePause(nativeContextPtr) } catch (e: Throwable) {}
    }

    override fun setPlaybackSpeed(speed: Float) {}
    override fun setRepeatMode(repeatMode: Int) {
        if (nativeContextPtr != 0L) try { nativeSetRepeatMode(nativeContextPtr, repeatMode) } catch (e: Throwable) {}
    }

    override fun setSurface(surface: Surface?) {
        Logger.i(TAG, "setSurface: $surface")
        currentSurface = surface
        _isSurfaceReady.value = surface != null
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

    fun setVideoFilters(filters: String) {
        if (nativeContextPtr != 0L) try { nativeSetVideoFilters(nativeContextPtr, filters) } catch (e: Throwable) {}
    }

    // Called from Native
    fun onNativePlaybackEnded() {
        ExoPlayerManager.getInstance(context).onEnginePlaybackEnded()
    }

    fun onNativeStatsUpdate(dropped: Int, audioErrs: Int, tsRecov: Int) {
        _diagnosticState.value = _diagnosticState.value.copy(
            droppedFrames = dropped,
            audioDecodeErrors = audioErrs,
            timestampRecoveryCount = tsRecov
        )
    }

    fun onNativeHdrUpdate(isHdr: Boolean, hdrType: String, colorSpace: String) {
        _diagnosticState.value = _diagnosticState.value.copy(
            isHdr = isHdr,
            hdrType = hdrType,
            colorSpace = colorSpace
        )
    }

    override val currentPositionMs: Long
        get() = if (nativeContextPtr != 0L) try { nativeGetPosition(nativeContextPtr) } catch(e: Throwable) { 0L } else 0L

    override val durationMs: Long
        get() = if (nativeContextPtr != 0L) try { nativeGetDuration(nativeContextPtr) } catch(e: Throwable) { 0L } else 0L

    override val isPlaying: Boolean
        get() = if (nativeContextPtr != 0L) try { nativeIsPlaying(nativeContextPtr) } catch(e: Throwable) { false } else false
}
