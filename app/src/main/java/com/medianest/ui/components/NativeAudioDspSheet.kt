package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeAudioDspSheet(
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    onDismiss: () -> Unit,
    backgroundImage: Any? = null
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        backgroundImage = backgroundImage,
        isSolidGlossy = true,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HeaderSection()
            DspSheetContent(playerState = playerState, playerManager = playerManager)
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Audio DSP Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Powered by FFmpeg & Oboe Native Engine", color = Color(0xFF94A3B8), fontSize = 12.sp)
    }
}

@Composable
private fun DspSheetContent(
    playerState: PlayerState,
    playerManager: ExoPlayerManager
) {
    // 1. Vocal Mute & Loudness Toggles (Glossy Pill Buttons)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppPillButton(
            text = if (playerState.isVocalMuteEnabled) "Vocal Mute ON" else "Vocal Mute",
            icon = Icons.Default.MusicNote,
            isSelected = playerState.isVocalMuteEnabled,
            onClick = { playerManager.setVocalMute(!playerState.isVocalMuteEnabled) },
            accentColor = Color(0xFF0EA5E9),
            modifier = Modifier.weight(1f)
        )

        AppPillButton(
            text = if (playerState.isLoudnessNormalizerEnabled) "Auto Level ON" else "Auto Level",
            icon = Icons.Default.Hearing,
            isSelected = playerState.isLoudnessNormalizerEnabled,
            onClick = { playerManager.setLoudnessNormalizer(!playerState.isLoudnessNormalizerEnabled) },
            accentColor = Color(0xFF06B6D4),
            modifier = Modifier.weight(1f)
        )
    }

    // 2. Pitch Shifter (-12 to +12 Semitones, Sky Blue accent)
    DspSliderItem(
        title = "Key / Pitch Shift",
        value = playerState.pitchSemitones.toFloat(),
        onValueChange = { playerManager.setPitchSemitones(it.toInt()) },
        valueRange = -12f..12f,
        icon = Icons.Default.Hearing,
        accentColor = Color(0xFFF1F5F9),
        unit = " semitones"
    )

    // 3. High Bass Boost
    DspSliderItem(
        title = "High Bass Boost",
        value = playerState.bassBoostPercent.toFloat(),
        onValueChange = { playerManager.setBassBoost(it.toInt()) },
        valueRange = 0f..100f,
        icon = Icons.Default.MusicNote,
        accentColor = Color(0xFF10B981),
        unit = "%"
    )

    // 4. 5-Band Equalizer in Glossy Glass Surface
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x1F1E293B),
        borderColor = Color(0x33FFFFFF)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFF1F5F9), modifier = Modifier.size(18.dp))
                Text("5-Band Native Equalizer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(165.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                playerState.eqBands.forEachIndexed { index, gain ->
                    EqBandSlider(
                        gain = gain,
                        label = labels[index],
                        onGainChange = { newGain ->
                            val newBands = playerState.eqBands.toMutableList()
                            newBands[index] = newGain
                            playerManager.setEqBands(newBands)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // 5. Super Volume Boost
    DspSliderItem(
        title = "Super Volume Boost",
        value = playerState.volumeBoostPercent.toFloat(),
        onValueChange = { playerManager.setVolumeBoost(it.toInt()) },
        valueRange = 0f..100f,
        icon = Icons.Default.VolumeUp,
        accentColor = Color(0xFFF59E0B),
        unit = "%"
    )

    // 6. Engine Status Chip at the bottom (clearly visible)
    EngineStatusBadge(playerState.activeEngineName)
}

@Composable
private fun DspSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    accentColor: Color,
    unit: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            val displayVal = if (unit == " semitones" && value.toInt() > 0) "+${value.toInt()}" else "${value.toInt()}"
            Text("$displayVal$unit", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            style = AppSliderStyle.Glossy,
            customTrackHeight = 8.dp,
            accentColor = accentColor
        )
    }
}

@Composable
private fun EqBandSlider(
    gain: Float,
    label: String,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${if (gain > 0) "+" else ""}${gain.toInt()}dB",
            fontSize = 10.sp,
            color = if (gain != 0f) Color(0xFFF1F5F9) else Color(0xFF64748B),
            fontWeight = if (gain != 0f) FontWeight.Bold else FontWeight.Normal
        )
        AppVerticalSlider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -10f..10f,
            style = AppSliderStyle.Glossy,
            headStyle = AppSliderHeadStyle.Circular,
            accentColor = Color(0xFFF1F5F9),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Text(label, fontSize = 9.5.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EngineStatusBadge(engine: String) {
    val isFFmpeg = engine == "FFmpeg"
    val accentColor = if (isFFmpeg) Color(0xFFF59E0B) else Color(0xFF10B981)
    
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.25f),
            accentColor.copy(alpha = 0.10f)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgBrush)
            .border(0.5.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Active Processing Engine: $engine",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
