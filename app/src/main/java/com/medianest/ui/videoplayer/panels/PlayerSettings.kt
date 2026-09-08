package com.medianest.ui.videoplayer.panels

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.*

@Composable
internal fun VideoPlayerSettingsOverlay(
    onDismiss: () -> Unit,
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    settingsManager: com.medianest.data.settings.SettingsManager,
    audioSyncOffsetMs: Long,
    onAudioSyncOffsetChange: (Long) -> Unit,
    isFilmGrainEnabled: Boolean,
    onFilmGrainEnabledChange: (Boolean) -> Unit,
    filmGrainIntensity: Float,
    onFilmGrainIntensityChange: (Float) -> Unit,
    onShowDetails: (() -> Unit)? = null,
    backdropState: BackdropBlurState? = null
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        BackdropGlassSurface(
            modifier = Modifier
                .fillMaxWidth(if (isTablet) 0.96f else 0.94f)
                .fillMaxHeight(if (isLandscape) 0.92f else 0.86f)
                .padding(if (isTablet) 20.dp else 10.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            blurRadius = 28.dp,
            tint = Color(0x660A0C10),
            baseColor = Color.Transparent,
            borderColor = Color(0x38FFFFFF),
            borderWidth = 0.5.dp,
            backdropState = backdropState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isTablet) 28.dp else 18.dp, vertical = if (isTablet) 22.dp else 16.dp)
            ) {
                // Header with Icon, Title and Top-Right Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Player Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Close Icon Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Settings Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsContent(
                        playerState = playerState,
                        playerManager = playerManager,
                        settingsManager = settingsManager,
                        audioSyncOffsetMs = audioSyncOffsetMs,
                        onAudioSyncOffsetChange = onAudioSyncOffsetChange,
                        isFilmGrainEnabled = isFilmGrainEnabled,
                        onFilmGrainEnabledChange = onFilmGrainEnabledChange,
                        filmGrainIntensity = filmGrainIntensity,
                        onFilmGrainIntensityChange = onFilmGrainIntensityChange
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    settingsManager: com.medianest.data.settings.SettingsManager,
    audioSyncOffsetMs: Long,
    onAudioSyncOffsetChange: (Long) -> Unit,
    isFilmGrainEnabled: Boolean,
    onFilmGrainEnabledChange: (Boolean) -> Unit,
    filmGrainIntensity: Float,
    onFilmGrainIntensityChange: (Float) -> Unit
) {
    val glossyChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(0x1AFFFFFF),
        labelColor = Color(0xFF9EA3B0),
        selectedContainerColor = Color(0x4DFFFFFF),
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White
    )

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // Hardware & Performance Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HardwareAccelerationSetting(settingsManager = settingsManager, showDecoderStrategy = false)
            
            Spacer(modifier = Modifier.height(4.dp))
            
            com.medianest.ui.components.HdrPlaybackSetting(settingsManager = settingsManager)

            if (playerState.isHdrContent) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = Color(0x1AFFD700),
                    borderColor = Color(0x33FFD700)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Active ${playerState.hdrType} Pipeline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            Text(text = "Color Space: ${playerState.colorSpace} • 10-bit HDR Rendering", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        val rememberPosition by settingsManager.rememberVideoPosition.collectAsState(initial = true)
        val videoBackgroundPlay by settingsManager.videoBackgroundPlay.collectAsState(initial = false)
        val settingsScope = rememberCoroutineScope()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = "Remember Playback Position", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "Prompt to resume videos where you left off", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
            }
            AppSwitch(
                checked = rememberPosition,
                onCheckedChange = { checked ->
                    settingsScope.launch { settingsManager.setRememberVideoPosition(checked) }
                },
                style = AppSwitchStyle.Glossy,
                accentColor = Color(0xFF38BDF8)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = "Video Background Playback", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "Continue playing video audio when app is minimized", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
            }
            AppSwitch(
                checked = videoBackgroundPlay,
                onCheckedChange = { checked ->
                    settingsScope.launch {
                        settingsManager.setVideoBackgroundPlay(checked)
                        playerManager.setVideoBackgroundPlayEnabled(checked)
                    }
                },
                style = AppSwitchStyle.Glossy,
                accentColor = Color(0xFF38BDF8)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Repeat Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Player.REPEAT_MODE_OFF to "Off",
                    Player.REPEAT_MODE_ONE to "Single",
                    Player.REPEAT_MODE_ALL to "All"
                ).forEach { (mode, label) ->
                    val isSelected = playerState.repeatMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { playerManager.setRepeatMode(mode) },
                        label = { Text(label) },
                        colors = glossyChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0x33FFFFFF),
                            selectedBorderColor = Color.White.copy(alpha = 0.70f),
                            borderWidth = 0.5.dp,
                            selectedBorderWidth = 1.0.dp
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Native Audio DSP (Low Latency)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "High Bass Boost", fontSize = 12.sp, color = Color.White)
                    Text(text = "${playerState.bassBoostPercent}%", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
                AppSlider(
                    value = playerState.bassBoostPercent.toFloat(),
                    onValueChange = { playerManager.setBassBoost(it.toInt()) },
                    valueRange = 0f..100f,
                    accentColor = Color(0xFF10B981)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "5-Band Native Equalizer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    AppSwitch(
                        checked = playerState.isEqEnabled,
                        onCheckedChange = { playerManager.setEqEnabled(it) },
                        style = AppSwitchStyle.Glossy,
                        accentColor = Color(0xFF10B981)
                    )
                }

                if (playerState.isEqEnabled) {
                    val presets = listOf(
                        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
                        "Acoustic" to listOf(3f, 1f, 2f, 3f, 2f),
                        "Bass Boost" to listOf(6f, 4f, 0f, 0f, 0f),
                        "Classical" to listOf(3f, 2f, 0f, 2f, 3f),
                        "Electronic" to listOf(4f, -1f, 1f, 3f, 4f),
                        "Hip-Hop" to listOf(5f, 3f, 0f, 1f, 3f),
                        "Pop" to listOf(-1f, 2f, 4f, 3f, 1f),
                        "Rock" to listOf(4f, 2f, -1f, 3f, 4f),
                        "Vocal" to listOf(-2f, 1f, 5f, 4f, 0f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = playerState.eqBands == preset.second
                            AppPillButton(
                                text = preset.first,
                                isSelected = isSelected,
                                onClick = { playerManager.setEqBands(preset.second) },
                                accentColor = Color(0xFF38BDF8)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                        playerState.eqBands.forEachIndexed { index, gain ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "${if (gain > 0) "+" else ""}${gain.toInt()}dB", fontSize = 10.sp, color = if (gain != 0f) Color(0xFF38BDF8) else Color(0xFF64748B), fontWeight = if (gain != 0f) FontWeight.Bold else FontWeight.Normal)
                                AppVerticalSlider(
                                    value = gain,
                                    onValueChange = { newGain ->
                                        val newBands = playerState.eqBands.toMutableList()
                                        newBands[index] = newGain
                                        playerManager.setEqBands(newBands)
                                    },
                                    valueRange = -10f..10f,
                                    style = AppSliderStyle.Glossy,
                                    thickness = AppSliderThickness.Thin,
                                    headStyle = AppSliderHeadStyle.Circular,
                                    accentColor = Color(0xFF38BDF8),
                                    modifier = Modifier.height(145.dp).fillMaxWidth()
                                )
                                Text(labels[index], fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Film Grain Overlay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                AppSwitch(
                    checked = isFilmGrainEnabled,
                    onCheckedChange = onFilmGrainEnabledChange,
                    style = AppSwitchStyle.Glass
                )
            }

            if (isFilmGrainEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Grain Intensity", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        val percent = ((filmGrainIntensity - 0.05f) / (0.30f - 0.05f) * 100).toInt().coerceIn(0, 100)
                        Text(text = "$percent%", fontSize = 12.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold)
                    }
                    AppSlider(
                        value = filmGrainIntensity,
                        onValueChange = onFilmGrainIntensityChange,
                        valueRange = 0.05f..0.30f,
                        style = AppSliderStyle.Glossy,
                        accentColor = Color(0xFFE2E8F0),
                        activeTrackColor = Color(0xFFE2E8F0),
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                        thumbColor = Color.White
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

@Composable
internal fun DiagnosticStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = Color(0xFF94A3B8))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
