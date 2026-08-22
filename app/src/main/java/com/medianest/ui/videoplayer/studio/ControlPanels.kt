package com.medianest.ui.videoplayer.studio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.AppSlider
import com.medianest.ui.components.AppSliderStyle
import com.medianest.ui.components.IconActionChip
import com.medianest.ui.components.TypographyTagChip
import com.medianest.ui.components.media.*

@Composable
internal fun TrimControlPanel(
    startMs: Long,
    endMs: Long,
    onResetTrim: () -> Unit
) {
    val clipDurationMs = (endMs - startMs).coerceAtLeast(0L)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "TRIM TIMELINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD54F)
            )
            Text(
                text = "Start: ${formatTimeShort(startMs)}  •  Clip: ${formatTimeShort(clipDurationMs)}  •  End: ${formatTimeShort(endMs)}",
                fontSize = 12.sp,
                color = Color.White
            )
        }
        OutlinedButton(
            onClick = onResetTrim,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Reset Trim", fontSize = 11.sp)
        }
    }
}

@Composable
internal fun CropControlPanel(
    currentPreset: MediaAspectRatio,
    isCustomCrop: Boolean,
    onSelectPreset: (MediaAspectRatio) -> Unit,
    onEnableCustomCrop: () -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconActionChip(
            label = "Custom",
            icon = Icons.Default.Crop,
            isSelected = isCustomCrop,
            onClick = onEnableCustomCrop
        )

        MediaAspectRatioSelector(
            options = listOf(
                MediaAspectRatio.ORIGINAL, MediaAspectRatio.P_16_9, MediaAspectRatio.P_9_16,
                MediaAspectRatio.P_4_3, MediaAspectRatio.P_3_4, MediaAspectRatio.P_1_1,
                MediaAspectRatio.P_4_5, MediaAspectRatio.P_21_9
            ),
            selected = if (isCustomCrop) MediaAspectRatio.FIT else currentPreset, // Dummy selected if custom
            onSelect = { onSelectPreset(it) },
            style = AspectRatioSelectorStyle.STUDIO_CHIP,
            modifier = Modifier.weight(1f, fill = false)
        )

        IconButton(onClick = onRotate) {
            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White)
        }
        IconButton(onClick = onFlipH) {
            Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FiltersAndBlurControlPanel(
    activeFilter: MediaEffect,
    onSelectFilter: (MediaEffect) -> Unit,
    blurMode: StudioBlurMode,
    onSelectBlurMode: (StudioBlurMode) -> Unit,
    blurIntensity: Float,
    onBlurIntensityChange: (Float) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Filters, 1: Blur Effects

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Text(
                text = "Color Filters",
                fontSize = 11.sp,
                fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedSubTab == 0) Color(0xFFFFD54F) else Color.White.copy(0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { selectedSubTab = 0 }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Text(
                text = "Blur & Privacy",
                fontSize = 11.sp,
                fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedSubTab == 1) Color(0xFFFFD54F) else Color.White.copy(0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { selectedSubTab = 1 }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        if (selectedSubTab == 0) {
            MediaFilterCarousel(
                filters = listOf(
                    MediaEffect.ORIGINAL, MediaEffect.CINEMA, MediaEffect.VIVID,
                    MediaEffect.NOIR, MediaEffect.VINTAGE, MediaEffect.WARM,
                    MediaEffect.COOL, MediaEffect.CYBERPUNK, MediaEffect.DREAMY
                ),
                activeFilter = activeFilter,
                onFilterChange = onSelectFilter,
                style = FilterCarouselStyle.STUDIO_PREVIEW
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioBlurMode.entries.forEach { mode ->
                        val isSel = blurMode == mode
                        FilterChip(
                            selected = isSel,
                            onClick = { onSelectBlurMode(mode) },
                            label = { Text(mode.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                if (blurMode != StudioBlurMode.NONE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Intensity: ${(blurIntensity * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFFFD54F))
                        AppSlider(
                            value = blurIntensity,
                            onValueChange = onBlurIntensityChange,
                            valueRange = 0.05f..1.0f,
                            accentColor = Color(0xFFFFD54F),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdjustmentsControlPanel(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit
) {
    var selectedAdjustTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Brightness", "Contrast", "Saturation").forEachIndexed { idx, label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (selectedAdjustTab == idx) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedAdjustTab == idx) Color(0xFFFFD54F) else Color.White.copy(0.7f),
                    modifier = Modifier
                        .clickable { selectedAdjustTab = idx }
                        .padding(4.dp)
                )
            }
        }
        when (selectedAdjustTab) {
            0 -> {
                AppSlider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -0.4f..0.4f,
                    style = AppSliderStyle.Glossy,
                    accentColor = Color(0xFFFFD54F)
                )
            }
            1 -> {
                AppSlider(
                    value = contrast,
                    onValueChange = onContrastChange,
                    valueRange = 0.5f..1.8f,
                    accentColor = Color(0xFFFFD54F)
                )
            }
            2 -> {
                AppSlider(
                    value = saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0.0f..2.0f,
                    accentColor = Color(0xFFFFD54F)
                )
            }
        }
    }
}

@Composable
internal fun TextAndStickersControlPanel(
    onAddText: () -> Unit,
    onAddEmoji: () -> Unit,
    onOpenKeyboardStickers: () -> Unit,
    onPickFileGif: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypographyTagChip(
            label = "Add Text",
            icon = Icons.Default.TextFields,
            accentColor = Color(0xFF60A5FA),
            onClick = onAddText
        )

        TypographyTagChip(
            label = "Stickers",
            icon = Icons.Default.InsertEmoticon,
            accentColor = Color(0xFFF472B6),
            onClick = onAddEmoji
        )

        TypographyTagChip(
            label = "Keyboard GIF / Sticker",
            icon = Icons.Default.Keyboard,
            accentColor = Color(0xFFFFD54F),
            isSelected = true,
            onClick = onOpenKeyboardStickers
        )

        TypographyTagChip(
            label = "+ GIF / Image",
            icon = Icons.Default.Gif,
            accentColor = Color(0xFF38BDF8),
            onClick = onPickFileGif
        )
    }
}

@Composable
internal fun AudioControlPanel(
    videoVolume: Float,
    onVideoVolumeChange: (Float) -> Unit,
    bgmTitle: String?,
    bgmVolume: Float,
    onBgmVolumeChange: (Float) -> Unit,
    onPickMusic: () -> Unit,
    onRemoveBgm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Vol: ${(videoVolume * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (videoVolume > 0f) {
                    Text(
                        text = "Mute",
                        fontSize = 10.sp,
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.clickable { onVideoVolumeChange(0f) }
                    )
                } else {
                    Text(
                        text = "Unmute",
                        fontSize = 10.sp,
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.clickable { onVideoVolumeChange(1f) }
                    )
                }
            }
            AppSlider(
                value = videoVolume,
                onValueChange = onVideoVolumeChange,
                valueRange = 0f..2f,
                style = AppSliderStyle.Glossy,
                accentColor = Color(0xFFFFD54F)
            )
        }

        if (bgmTitle != null) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Music: ${(bgmVolume * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                    IconButton(onClick = onRemoveBgm, modifier = Modifier.size(16.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
                AppSlider(
                    value = bgmVolume,
                    onValueChange = onBgmVolumeChange,
                    valueRange = 0f..2f,
                    style = AppSliderStyle.Glossy,
                    accentColor = Color(0xFF64B5F6)
                )
            }
        } else {
            Button(
                onClick = onPickMusic,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FFFFFF)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Music", fontSize = 11.sp, color = Color(0xFF64B5F6))
            }
        }
    }
}
