package com.medianest.ui.videoeditor.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoTimelineState(
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val pixelsPerSecond: Float = 100f,
    val isPlaying: Boolean = false,
    val isScrolling: Boolean = false
) {
    val trimmedDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
}
