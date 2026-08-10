package com.example.ui.audioplayer

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.components.AudioVisualizer
import com.example.ui.components.VisualizerStyle

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
    var styleIndex by remember { mutableIntStateOf(0) }
    val styles = VisualizerStyle.entries.toTypedArray()
    val currentStyle = styles[styleIndex.coerceIn(0, styles.size - 1)]

    AudioVisualizer(
        isPlaying = isPlaying,
        audioSessionId = audioSessionId,
        currentPosMs = currentPosMs,
        trackSeed = trackSeed,
        albumArtUri = albumArtUri,
        numBands = barCount,
        style = currentStyle,
        primaryColor = Color.White,
        secondaryColor = Color(0xFFD0D0D0),
        accentColor = Color(0xFFE8E8E8),
        modifier = modifier.clickable {
            styleIndex = (styleIndex + 1) % styles.size
        }
    )
}