package com.example.ui.audioplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.components.AudioVisualizer

@Composable
fun AudioReactiveVisualizerPattern(
    isPlaying: Boolean,
    currentPosMs: Long,
    trackSeed: Long = 0L,
    albumArtUri: android.net.Uri? = null,
    audioSessionId: Int = 0,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    barColor: Color = Color.White.copy(alpha = 0.90f)
) {
    AudioVisualizer(
        isPlaying = isPlaying,
        audioSessionId = audioSessionId,
        currentPosMs = currentPosMs,
        trackSeed = trackSeed,
        albumArtUri = albumArtUri,
        numBands = barCount,
        primaryColor = Color.White,
        secondaryColor = Color(0xFFD0D0D0),
        accentColor = Color(0xFFE8E8E8),
        modifier = modifier.fillMaxSize()
    )
}