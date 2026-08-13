package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ExoPlayerManager
import com.example.player.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeAudioDspSheet(
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HeaderSection()

            // 1. High Bass Boost
            DspSliderItem(
                title = "High Bass Boost",
                value = playerState.bassBoostPercent.toFloat(),
                onValueChange = { playerManager.setBassBoost(it.toInt()) },
                valueRange = 0f..100f,
                icon = Icons.Default.MusicNote,
                accentColor = Color(0xFF10B981),
                unit = "%"
            )

            // 2. 5-Band Equalizer
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Text("5-Band Native Equalizer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
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

            // 3. Super Volume Boost
            DspSliderItem(
                title = "Super Volume Boost",
                value = playerState.volumeBoostPercent.toFloat(),
                onValueChange = { playerManager.setVolumeBoost(it.toInt()) },
                valueRange = 0f..100f,
                icon = Icons.Default.VolumeUp,
                accentColor = Color(0xFFF59E0B),
                unit = "%"
            )
            
            // 4. Engine Status
            EngineStatusBadge(playerState.activeEngineName)
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
private fun DspSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    accentColor: Color,
    unit: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("${value.toInt()}$unit", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f).width(20.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = gain,
                onValueChange = onGainChange,
                valueRange = -10f..10f,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = -90f
                    }
                    .width(100.dp), // Height becomes width after rotation
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF38BDF8),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
        Text(label, fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EngineStatusBadge(engine: String) {
    Surface(
        color = if (engine == "FFmpeg") Color(0xFF1E293B) else Color(0x1A10B981),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (engine == "FFmpeg") Color(0xFF334155) else Color(0xFF10B981).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (engine == "FFmpeg") Color(0xFFF59E0B) else Color(0xFF10B981)))
            Text(
                text = "Active Engine: $engine",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (engine == "Media3") {
                Text(
                    "(Standard Mode)",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            }
        }
    }
}
