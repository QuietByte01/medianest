package com.medianest.ui.videoplayer.panels

import android.content.Context
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.model.SubtitleItem
import com.medianest.data.repository.SubtitleProvider
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.*

@Composable
internal fun SubtitleOptionsDialog(
    playerState: PlayerState,
    embeddedTracks: List<SubtitleItem>,
    selectedTrackIndex: Int,
    onTrackSelect: (Int) -> Unit,
    onCustomizeClick: () -> Unit,
    onSearchOnline: (SubtitleProvider) -> Unit,
    isSearching: Boolean,
    onlineSubtitles: List<SubtitleItem>,
    onOnlineSubClick: (SubtitleItem) -> Unit,
    onPickLocalSubtitle: () -> Unit = {},
    statusMessage: String?,
    onClose: () -> Unit,
    backdropState: BackdropBlurState? = null,
    modifier: Modifier = Modifier
) {
    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
    val shape = RoundedCornerShape(20.dp)

    BackdropGlassSurface(
        modifier = modifier.clickable(enabled = false) {},
        shape = shape,
        blurRadius = 24.dp,
        tint = Color(0x770A0C10),
        baseColor = Color.Transparent,
        borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x28000000),
        borderWidth = 0.5.dp,
        backdropState = backdropState
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CC",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Subtitle Options",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            val isSubtitlesOn = selectedTrackIndex >= 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppActionButton(
                    text = if (isSubtitlesOn) "Subtitles ON" else "Subtitles OFF",
                    icon = if (isSubtitlesOn) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                    onClick = {
                        if (isSubtitlesOn) {
                            onTrackSelect(-1)
                        } else {
                            onTrackSelect(0)
                        }
                    },
                    style = AppButtonStyle.Glossy,
                    accentColor = if (isSubtitlesOn) Color(0xFF10B981) else Color(0xFFE2E8F0),
                    modifier = Modifier.weight(1f)
                )

                AppActionButton(
                    text = "Customize",
                    icon = Icons.Default.Style,
                    onClick = onCustomizeClick,
                    style = AppButtonStyle.Glossy,
                    accentColor = Color(0xFFC4B5FD),
                    modifier = Modifier.weight(1f)
                )
            }

            AppActionButton(
                text = "Import Local Subtitle File (.srt, .vtt...)",
                icon = Icons.Default.FolderOpen,
                onClick = onPickLocalSubtitle,
                style = AppButtonStyle.Glossy,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.fillMaxWidth()
            )

            if (embeddedTracks.isNotEmpty()) {
                Text(
                    text = "Embedded Subtitle Tracks:",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    embeddedTracks.forEachIndexed { idx, track ->
                        val isSelected = selectedTrackIndex == idx
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTrackSelect(idx)
                                },
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = if (isSelected) Color(0x33FFFFFF) else Color(0x1EFFFFFF),
                            borderColor = if (isSelected) Color.White else Color(0x1AFFFFFF)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${track.name} (${track.language.uppercase()})",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(text = "Search Online Subtitles:", fontSize = 13.sp, color = Color(0xFF94A3B8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppActionButton(
                    text = "OpenSubtitles",
                    icon = Icons.Default.CloudDownload,
                    onClick = { onSearchOnline(SubtitleProvider.OPEN_SUBTITLES) },
                    enabled = !isSearching,
                    style = AppButtonStyle.Glossy,
                    accentColor = Color(0xFFE2E8F0),
                    modifier = Modifier.weight(1f)
                )

                AppActionButton(
                    text = "Community",
                    icon = Icons.Default.People,
                    onClick = { onSearchOnline(SubtitleProvider.YTS) },
                    enabled = !isSearching,
                    style = AppButtonStyle.Glossy,
                    accentColor = Color(0xFFE2E8F0),
                    modifier = Modifier.weight(1f)
                )
            }

            if (isSearching) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Color(0xFFA78BFA), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching Subtitles...", fontSize = 13.sp, color = Color.White)
                }
            }

            statusMessage?.let { msg ->
                Text(text = msg, fontSize = 12.sp, color = Color(0xFFA78BFA))
            }

            if (onlineSubtitles.isNotEmpty()) {
                Text(text = "Online Search Results:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    onlineSubtitles.forEach { onlineSub ->
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOnlineSubClick(onlineSub) },
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = Color(0x268B5CF6),
                            borderColor = Color(0x338B5CF6)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = onlineSub.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = "Lang: ${onlineSub.language.uppercase()}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                                Icon(Icons.Default.CloudDownload, contentDescription = "Select", tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class SubtitleFontFamily(val displayName: String, val fontFamily: FontFamily) {
    DEFAULT("System Default", FontFamily.Default),
    SANS_SERIF("Sans-Serif", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONOSPACE("Monospace", FontFamily.Monospace),
    CURSIVE("Cursive", FontFamily.Cursive),
    CONDENSED("Condensed", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
    MEDIUM("Medium", FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))),
    LIGHT("Light", FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))),
    BLACK("Black", FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL))),
    SERIF_MONO("Serif Mono", FontFamily(Typeface.create("serif-monospace", Typeface.NORMAL)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleCustomizationSheet(
    onDismiss: () -> Unit,
    activeSubtitleText: String?,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    selectedFontFamily: SubtitleFontFamily = SubtitleFontFamily.DEFAULT,
    onFontFamilyChange: (SubtitleFontFamily) -> Unit = {},
    textColor: Color,
    onTextColorChange: (Color) -> Unit,
    bgColor: Color,
    onBgColorChange: (Color) -> Unit,
    hasShadow: Boolean,
    onHasShadowChange: (Boolean) -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        backdropState = backdropState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Subtitle Styling & Customization",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = activeSubtitleText ?: "Sample Subtitle Text 123",
                        color = textColor,
                        fontSize = fontSizeSp.sp,
                        fontFamily = selectedFontFamily.fontFamily,
                        fontWeight = FontWeight.Bold,
                        style = if (hasShadow) {
                            androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        } else androidx.compose.ui.text.TextStyle.Default
                    )
                }
            }

            Column {
                Text(text = "System Font Family", fontSize = 13.sp, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubtitleFontFamily.entries.forEach { option ->
                        val isSelected = selectedFontFamily == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFEEEEEE) else Color(0x22FFFFFF))
                                .clickable { onFontFamilyChange(option) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = option.displayName,
                                fontSize = 12.sp,
                                fontFamily = option.fontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF0F172A) else Color.White
                            )
                        }
                    }
                }
            }

            Column {
                Text(text = "Font Size: ${fontSizeSp.toInt()} sp", fontSize = 13.sp, color = Color.White)
                AppSlider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..36f,
                    accentColor = Color(0xFFEEEEEE)
                )
            }

            Column {
                Text(text = "Text Color", fontSize = 13.sp, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = listOf(
                        "White" to Color.White,
                        "Dark Grey" to Color(0xFF333333),
                        "Silver" to Color(0xFFC0C0C0),
                        "Yellow" to Color.Yellow,
                        "Cyan" to Color.Cyan,
                        "Green" to Color.Green,
                        "Pink" to Color(0xFFFF4081)
                    )
                    colors.forEach { (label, c) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (textColor == c) Color(0xFFEEEEEE) else Color(0x22FFFFFF))
                                .clickable { onTextColorChange(c) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(c).border(0.5.dp, Color.White, CircleShape))
                                Text(text = label, fontSize = 11.sp, color = if (textColor == c) Color(0xFF0F172A) else Color.White, fontWeight = if (textColor == c) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Column {
                Text(text = "Background & Opacity", fontSize = 13.sp, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bgColors = listOf(
                        "10% Opacity" to Color(0x1A000000),
                        "Dark Grey" to Color(0xCC222222),
                        "Silver" to Color(0xCCC0C0C0),
                        "Dark (50%)" to Color(0x80000000),
                        "Dark (80%)" to Color(0xCC000000),
                        "Solid Black" to Color(0xFF000000),
                        "Transparent" to Color.Transparent
                    )
                    bgColors.forEach { (label, bgC) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (bgColor == bgC) Color(0xFFEEEEEE) else Color(0x22FFFFFF))
                                .clickable { onBgColorChange(bgC) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (bgColor == bgC) Color(0xFF0F172A) else Color.White,
                                fontWeight = if (bgColor == bgC) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Text Drop Shadow", fontSize = 13.sp, color = Color.White)
                AppSwitch(
                    checked = hasShadow,
                    onCheckedChange = onHasShadowChange,
                    style = AppSwitchStyle.Glass
                )
            }

            AppActionButton(
                text = "Apply Style",
                onClick = { onDismiss() },
                style = AppButtonStyle.Solid,
                accentColor = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioTrackSelectionSheet(
    onDismiss: () -> Unit,
    playerManager: ExoPlayerManager,
    audioSyncOffsetMs: Long,
    onAudioSyncOffsetChange: (Long) -> Unit,
    context: Context,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        backdropState = backdropState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Select Audio Track",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            val availableAudioTracks = remember {
                playerManager.getAvailableAudioTracks()
            }

            if (availableAudioTracks.isNotEmpty()) {
                availableAudioTracks.forEach { track ->
                    BackdropGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerManager.selectAudioTrack(track.index)
                                onDismiss()
                                Toast.makeText(context, "Selected ${track.name}", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        tint = if (track.isSelected) Color(0x660284C7) else Color(0x330A0C10),
                        baseColor = Color.Transparent,
                        borderColor = if (track.isSelected) Color(0xFF38BDF8) else Color(0x33FFFFFF),
                        backdropState = backdropState
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (track.isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = track.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                            if (track.isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Text(text = "Audio Sync Delay", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        text = "${if (audioSyncOffsetMs > 0) "+" else ""}${audioSyncOffsetMs} ms",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onAudioSyncOffsetChange(audioSyncOffsetMs - 50L) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x22FFFFFF),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("-50ms", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AppCriticalButton(
                        text = "Reset (0)",
                        onClick = { onAudioSyncOffsetChange(0L) },
                        modifier = Modifier.weight(1f),
                        accentColor = Color(0xFFEF4444)
                    )

                    Surface(
                        onClick = { onAudioSyncOffsetChange(audioSyncOffsetMs + 50L) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x22FFFFFF),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("+50ms", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
