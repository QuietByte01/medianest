package com.medianest.ui.audioplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.medianest.ui.components.AudioVisualizer
import com.medianest.ui.components.VisualizerStyle

@Composable
fun AudioReactiveVisualizerPattern(
    isPlaying: Boolean,
    audioSessionId: Int = 0,
    modifier: Modifier = Modifier,
    hue: Float? = null,
    style: VisualizerStyle = VisualizerStyle.GLOSSY_SPECTRUM_BARS,
    showControls: Boolean = false
) {
    // Generate colors based on hue if available, else use defaults
    val primaryColor = hue?.let { Color.hsv(it, 0.8f, 0.9f) } ?: Color(0xFF00E5FF)
    val secondaryColor = hue?.let { Color.hsv((it + 40f) % 360f, 0.7f, 0.8f) } ?: Color(0xFFD500F9)
    val accentColor = hue?.let { Color.hsv((it - 40f + 360f) % 360f, 0.9f, 1.0f) } ?: Color(0xFFFFD600)

    AudioVisualizer(
        isPlaying = isPlaying,
        audioSessionId = audioSessionId,
        style = style,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        accentColor = accentColor,
        showControls = showControls,
        modifier = modifier.fillMaxSize()
    )
}