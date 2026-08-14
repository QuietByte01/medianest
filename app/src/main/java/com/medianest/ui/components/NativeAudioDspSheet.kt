package com.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        // Centered Alert Dialog Box for Tablets
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = Modifier
                    .width(540.dp)
                    .padding(24.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xF20F172A),
                borderColor = Color(0x33FFFFFF),
                enableBlur = true,
                blurRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HeaderSection()
                    DspSheetContent(playerState = playerState, playerManager = playerManager)
                }
            }
        }
    } else {
        // Bottom Sheet for Phones
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color(0x990F172A),
            scrimColor = Color.Black.copy(alpha = 0.55f),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                backgroundColor = Color(0x550F172A),
                borderColor = Color(0x2EFFFFFF),
                enableBlur = true,
                blurRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HeaderSection()
                    DspSheetContent(playerState = playerState, playerManager = playerManager)
                }
            }
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
    // 1. Vocal Mute & Loudness Toggles (Cyan / Sky Blue theme, NO PURPLE)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vocal Mute Toggle
        Surface(
            onClick = { playerManager.setVocalMute(!playerState.isVocalMuteEnabled) },
            shape = RoundedCornerShape(16.dp),
            color = if (playerState.isVocalMuteEnabled) Color(0xFF0EA5E9) else Color(0x1F22D3EE),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (playerState.isVocalMuteEnabled) Color.White else Color(0xFFA5F3FC),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (playerState.isVocalMuteEnabled) "Vocal Mute ON" else "Vocal Mute",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Loudness Normalizer Toggle
        Surface(
            onClick = { playerManager.setLoudnessNormalizer(!playerState.isLoudnessNormalizerEnabled) },
            shape = RoundedCornerShape(16.dp),
            color = if (playerState.isLoudnessNormalizerEnabled) Color(0xFF06B6D4) else Color(0x1F22D3EE),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = if (playerState.isLoudnessNormalizerEnabled) Color.White else Color(0xFFA5F3FC),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (playerState.isLoudnessNormalizerEnabled) "Auto Level ON" else "Auto Level",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // 2. Pitch Shifter (-12 to +12 Semitones, Sky Blue accent)
    DspSliderItem(
        title = "Key / Pitch Shift",
        value = playerState.pitchSemitones.toFloat(),
        onValueChange = { playerManager.setPitchSemitones(it.toInt()) },
        valueRange = -12f..12f,
        icon = Icons.Default.Hearing,
        accentColor = Color(0xFF38BDF8),
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
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Text("5-Band Native Equalizer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(125.dp),
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppVerticalSlider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -10f..10f,
            style = AppSliderStyle.Glossy,
            thickness = AppSliderThickness.Thin,
            headStyle = AppSliderHeadStyle.Circular,
            accentColor = Color(0xFF38BDF8),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EngineStatusBadge(engine: String) {
    Surface(
        color = if (engine == "FFmpeg") Color(0x33F59E0B) else Color(0x1A10B981),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, if (engine == "FFmpeg") Color(0xFFF59E0B).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (engine == "FFmpeg") Color(0xFFF59E0B) else Color(0xFF10B981)))
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
