package com.medianest.player

import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

data class EngineDiagnosticState(
    val engineName: String = "Media3",
    val containerName: String = "Unknown",
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val decoderName: String = "Hardware Default",
    val isHardwareAccelerated: Boolean = true,
    val currentBitrate: Long = 0L,
    val droppedFrames: Int = 0,
    val audioDecodeErrors: Int = 0,
    val audioMissingFrames: Int = 0,
    val corruptedFrames: Int = 0,
    val bitrateHistory: List<Long> = emptyList(),
    val timestampRecoveryCount: Int = 0,
    val decoderRecoveryCount: Int = 0,
    val lastRecoveryReason: String? = null,
    val isHdr: Boolean = false,
    val hdrType: String = "SDR", // SDR, HDR10, HDR10+, Dolby Vision, HLG
    val colorSpace: String = "SDR",
    val audioSampleRate: Int = 0,
    val audioSampleFormat: String = "32-bit Float",
    val audioSharingMode: String = "AAudio Exclusive"
)

interface PlaybackEngine {
    val diagnosticState: StateFlow<EngineDiagnosticState>

    fun prepare(uri: Uri, playWhenReady: Boolean)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setRepeatMode(repeatMode: Int)
    fun setSurface(surface: Surface?)
    fun setVolume(volume: Float)
    fun stop()
    fun release()

    val currentPositionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
}
