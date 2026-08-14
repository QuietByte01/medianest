package com.medianest.ui.videoplayer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.medianest.ui.components.AppSlider
import com.medianest.ui.components.AppSliderStyle
import com.medianest.ui.components.AppSliderThickness
import com.medianest.ui.components.AppSliderHeadStyle
import com.medianest.ui.components.AppVerticalSlider
import com.medianest.ui.components.AppCriticalButton
import com.medianest.ui.components.AppActionButton
import com.medianest.ui.components.AppPillButton
import com.medianest.ui.components.AppSwitch
import com.medianest.ui.components.AppSwitchStyle
import com.medianest.ui.components.AppButtonStyle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.medianest.data.model.MediaItem
import com.medianest.data.model.SubtitleItem
import com.medianest.data.repository.NetworkRepository
import com.medianest.data.repository.SubtitleProvider
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.PictureMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarQueueDrawer(
    playerState: PlayerState,
    onVideoClick: (MediaItem) -> Unit,
    onClose: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0xF20E111A),
        borderColor = Color(0x33FFFFFF),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    val breadcrumbParts = remember(playerState.currentItem) {
                        getBreadcrumbParts(context, playerState.currentItem)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        breadcrumbParts.forEachIndexed { index, part ->
                            Text(
                                text = part,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (index < breadcrumbParts.lastIndex) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0x99FFFFFF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playerState.queue) { video ->
                    val isCurrent = video.uri == playerState.currentItem?.uri
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onVideoClick(video)
                            },
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = if (isCurrent) Color(0x33FFFFFF) else Color(0x1AFFFFFF),
                        borderColor = if (isCurrent) Color.White else Color(0x22FFFFFF)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFF581C87), Color(0xFF0F172A))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = video.albumArtUri ?: video.uri,
                                    contentDescription = video.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = safeFormatDuration(context, video),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = video.title,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleOptionsDialog(
    playerState: PlayerState,
    embeddedTracks: List<SubtitleItem>,
    selectedTrackIndex: Int,
    onTrackSelect: (Int) -> Unit,
    onCustomizeClick: () -> Unit,
    onSearchOnline: (SubtitleProvider) -> Unit,
    isSearching: Boolean,
    onlineSubtitles: List<SubtitleItem>,
    onOnlineSubClick: (SubtitleItem) -> Unit,
    statusMessage: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0xF20E111A),
        borderColor = Color(0x33FFFFFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            Text(
                text = "Embedded Subtitle Tracks:",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val isOffSelected = selectedTrackIndex == -1
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTrackSelect(-1)
                        },
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = if (isOffSelected) Color(0x33FFFFFF) else Color(0x1EFFFFFF),
                    borderColor = if (isOffSelected) Color.White else Color(0x1AFFFFFF)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subtitles Off",
                            fontSize = 14.sp,
                            fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White
                        )
                        if (isOffSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (embeddedTracks.isNotEmpty()) {
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
                } else {
                    Text(
                        text = "No embedded subtitles found in this media file.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCustomizeClick() },
                shape = RoundedCornerShape(12.dp),
                backgroundColor = Color(0x338B5CF6),
                borderColor = Color(0x448B5CF6)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                        Text("Customize Subtitle Style...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Text(text = "Search Online Subtitles:", fontSize = 13.sp, color = Color(0xFF94A3B8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // OpenSubtitles Button
                AppActionButton(
                    text = "OpenSubtitles",
                    icon = Icons.Default.CloudDownload,
                    onClick = { onSearchOnline(SubtitleProvider.OPEN_SUBTITLES) },
                    enabled = !isSearching,
                    style = AppButtonStyle.Glossy,
                    modifier = Modifier.weight(1f)
                )

                // Community (YTS) Button
                AppActionButton(
                    text = "Community",
                    icon = Icons.Default.People,
                    onClick = { onSearchOnline(SubtitleProvider.YTS) },
                    enabled = !isSearching,
                    style = AppButtonStyle.Glossy,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleCustomizationSheet(
    onDismiss: () -> Unit,
    activeSubtitleText: String?,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    textColor: Color,
    onTextColorChange: (Color) -> Unit,
    bgColor: Color,
    onBgColorChange: (Color) -> Unit,
    hasShadow: Boolean,
    onHasShadowChange: (Boolean) -> Unit
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xDC121522)
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

            // Preview Card
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

            // Size Slider
            Column {
                Text(text = "Font Size: ${fontSizeSp.toInt()} sp", fontSize = 13.sp, color = Color.White)
                AppSlider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..36f,
                    accentColor = Color(0xFFEEEEEE)
                )
            }

            // Text Color selector
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

            // Background Color selector
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

            // Text Shadow Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Text Drop Shadow", fontSize = 13.sp, color = Color.White)
                AppSwitch(
                    checked = hasShadow,
                    onCheckedChange = onHasShadowChange,
                    style = AppSwitchStyle.Glossy,
                    accentColor = Color(0xFF64748B)
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
fun AudioTrackSelectionSheet(
    onDismiss: () -> Unit,
    playerManager: ExoPlayerManager,
    audioSyncOffsetMs: Long,
    onAudioSyncOffsetChange: (Long) -> Unit,
    context: Context
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xDC121522)
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
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerManager.selectAudioTrack(track.index)
                                onDismiss()
                                android.widget.Toast.makeText(context, "Selected ${track.name}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = if (track.isSelected) Color(0x4DFFFFFF) else Color(0x1AFFFFFF),
                        borderColor = if (track.isSelected) Color.White else Color(0x33FFFFFF),
                        enableBlur = true,
                        blurRadius = 12.dp
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

            // Audio Sync Offset Control
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerSettingsDialog(
    onDismiss: () -> Unit,
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    pictureMode: String,
    onPictureModeChange: (String) -> Unit,
    audioSyncOffsetMs: Long,
    onAudioSyncOffsetChange: (Long) -> Unit,
    isFilmGrainEnabled: Boolean,
    onFilmGrainEnabledChange: (Boolean) -> Unit,
    filmGrainIntensity: Float,
    onFilmGrainIntensityChange: (Float) -> Unit,
    onShowDetails: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600 && configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    if (isCompact) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F111A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Custom Full Screen Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Text(
                                text = "Player Settings",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        SettingsContent(
                            playerState = playerState,
                            playerManager = playerManager,
                            pictureMode = pictureMode,
                            onPictureModeChange = onPictureModeChange,
                            audioSyncOffsetMs = audioSyncOffsetMs,
                            onAudioSyncOffsetChange = onAudioSyncOffsetChange,
                            isFilmGrainEnabled = isFilmGrainEnabled,
                            onFilmGrainEnabledChange = onFilmGrainEnabledChange,
                            filmGrainIntensity = filmGrainIntensity,
                            onFilmGrainIntensityChange = onFilmGrainIntensityChange,
                            onShowDetails = onShowDetails
                        )
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xF20E111A),
            shape = RoundedCornerShape(24.dp),
            title = { Text(text = "Video Player Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    SettingsContent(
                        playerState = playerState,
                        playerManager = playerManager,
                        pictureMode = pictureMode,
                        onPictureModeChange = onPictureModeChange,
                        audioSyncOffsetMs = audioSyncOffsetMs,
                        onAudioSyncOffsetChange = onAudioSyncOffsetChange,
                        isFilmGrainEnabled = isFilmGrainEnabled,
                        onFilmGrainEnabledChange = onFilmGrainEnabledChange,
                        filmGrainIntensity = filmGrainIntensity,
                        onFilmGrainIntensityChange = onFilmGrainIntensityChange,
                        onShowDetails = onShowDetails
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    pictureMode: String,
    onPictureModeChange: (String) -> Unit,
    audioSyncOffsetMs: Long,
    onAudioSyncOffsetChange: (Long) -> Unit,
    isFilmGrainEnabled: Boolean,
    onFilmGrainEnabledChange: (Boolean) -> Unit,
    filmGrainIntensity: Float,
    onFilmGrainIntensityChange: (Float) -> Unit,
    onShowDetails: () -> Unit
) {
    val glossyChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(0x1AFFFFFF),
        labelColor = Color(0xFF9EA3B0),
        selectedContainerColor = Color(0x4DFFFFFF),
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White
    )

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color(0x33FFFFFF)
    )

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowDetails() },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0x1AFFFFFF),
            borderColor = Color(0x33FFFFFF)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("View Detailed File Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Repeat mode
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
                            selectedBorderColor = Color.White,
                            borderWidth = 0.5.dp,
                            selectedBorderWidth = 1.0.dp
                        )
                    )
                }
            }
        }

        // Picture Mode
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Picture Mode: ${PictureMode.fromKey(pictureMode).displayName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PictureMode.entries) { mode ->
                    val isSelected = pictureMode.equals(mode.key, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPictureModeChange(mode.key) },
                        label = { Text(mode.displayName) },
                        colors = glossyChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0x33FFFFFF),
                            selectedBorderColor = Color.White,
                            borderWidth = 0.5.dp,
                            selectedBorderWidth = 1.0.dp
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // Native Audio DSP (FFmpeg + Oboe)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Native Audio DSP (Low Latency)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
            
            // Bass Boost
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

            // Equalizer (5-Band)
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
                        accentColor = Color(0xFF38BDF8)
                    )
                }

                if (playerState.isEqEnabled) {
                    // Presets
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

                    // Sliders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val labels = listOf("60", "230", "910", "3.6K", "14K")
                        playerState.eqBands.forEachIndexed { index, gain ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
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
                                    modifier = Modifier.height(100.dp).fillMaxWidth()
                                )
                                Text(text = "${if (gain > 0) "+" else ""}${gain.toInt()}dB", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                Text(labels[index], fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // Film Grain
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
                    style = AppSwitchStyle.Glossy,
                    accentColor = Color(0xFF38BDF8)
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
                        Text(text = "$percent%", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                    AppSlider(
                        value = filmGrainIntensity,
                        onValueChange = onFilmGrainIntensityChange,
                        valueRange = 0.05f..0.30f,
                        accentColor = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

@Composable
private fun DiagnosticStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = Color(0xFF94A3B8))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun VideoPlayerOverflowMenu(
    onDismiss: () -> Unit,
    onOpenWith: () -> Unit,
    onShowInFolder: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    isBackgroundPlayEnabled: Boolean,
    onToggleBackgroundPlay: (Boolean) -> Unit,
    isAutoRepeatEnabled: Boolean,
    onToggleAutoRepeat: (Boolean) -> Unit,
    onVideoFx: () -> Unit,
    onAudioTracks: () -> Unit,
    onCast: () -> Unit,
    onSettings: () -> Unit,
    onShowInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        GlassSurface(
            modifier = modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 56.dp, end = 16.dp)
                .width(220.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0xF20E111A),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DropdownMenuItem(
                    text = { Text("File Info", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onShowInfo(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Open with", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onOpenWith(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Show in folder", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onShowInFolder(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                    onClick = { onDelete(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Share", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onShare(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Edit Video", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onEdit(); onDismiss() }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                
                DropdownMenuItem(
                    text = { Text("Background Play: ${if (isBackgroundPlayEnabled) "On" else "Off"}", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onToggleBackgroundPlay(!isBackgroundPlayEnabled) }
                )
                DropdownMenuItem(
                    text = { Text("Auto Repeat: ${if (isAutoRepeatEnabled) "On" else "Off"}", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Repeat, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onToggleAutoRepeat(!isAutoRepeatEnabled) }
                )
                DropdownMenuItem(
                    text = { Text("Audio Tracks", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Audiotrack, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onAudioTracks(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Cast to TV", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Tv, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onCast(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Video FX & Filters", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Tune, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onVideoFx(); onDismiss() }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                
                DropdownMenuItem(
                    text = { Text("Settings", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = { onSettings(); onDismiss() }
                )
            }
        }
    }
}

@Composable
fun AspectRatioModal(
    currentMode: String,
    onModeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF2121522),
            borderColor = Color(0x33FFFFFF),
            enableBlur = true,
            blurRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Aspect Ratio", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                val aspectRatios = listOf(
                    "FIT" to "Fit", "CROP" to "Crop", "STRETCH" to "Stretch",
                    "ORIGINAL" to "Original", "16:9" to "16:9", "4:3" to "4:3", "FILL" to "Fill"
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(aspectRatios) { (code, label) ->
                        val isSelected = currentMode.equals(code, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clickable { onModeChange(code); onDismiss() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, color = if (isSelected) Color.White else Color(0x80FFFFFF), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackSpeedModal(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF2121522),
            borderColor = Color(0x33FFFFFF),
            enableBlur = true,
            blurRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Playback Speed", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(speeds) { speed ->
                        val isSelected = currentSpeed == speed
                        val rawLabel = if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "$speed"
                        val displayLabel = if (isSelected) "${rawLabel}x" else if (speed == 1.0f) "1.0x" else rawLabel
                        Box(
                            modifier = Modifier
                                .clickable { onSpeedChange(speed); onDismiss() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = displayLabel, color = if (isSelected) Color.White else Color(0x80FFFFFF), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPostProcessingPanel(
    currentEffect: String,
    onEffectChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(bottom = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF2121522),
            borderColor = Color(0x33FFFFFF),
            enableBlur = true,
            blurRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Video FX & Post-Processing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val effects = listOf(
                    "NORMAL" to "Normal",
                    "SHARPEN" to "Sharpen (Unsharp)",
                    "HIGH_CONTRAST" to "High Contrast",
                    "NIGHT_VISION" to "Night Vision",
                    "VINTAGE_CRT" to "Vintage CRT",
                    "SEPIA" to "Sepia Film"
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(effects) { (code, label) ->
                        val isSelected = currentEffect.equals(code, ignoreCase = true)
                        Surface(
                            onClick = { onEffectChange(code); onDismiss() },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0x22FFFFFF),
                            border = BorderStroke(1.dp, if (isSelected) Color.White else Color(0x1AFFFFFF))
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmstripTrack(
    thumbnails: List<Bitmap>,
    isExtracting: Boolean,
    videoDurationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val durationFloat = videoDurationMs.toFloat().coerceAtLeast(1000f)

    val startFrac = (startMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / durationFloat).coerceIn(0f, 1f)

    val handleWidthDp = 18.dp
    val handleWidthPx = with(density) { handleWidthDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
    ) {
        // 1. Filmstrip Frame Thumbnails (Extracted from real video)
        if (thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            // Skeleton Placeholder while loading or fallback
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 8) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(Color.White.copy(alpha = if (isExtracting) 0.08f + (i % 2) * 0.04f else 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Filmstrip Sprocket Perforations (Top & Bottom subtle dotted cinema look)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(16) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(16) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
            }
        }

        if (trackWidthPx > 0f) {
            val usableWidth = trackWidthPx - (handleWidthPx * 2)
            val startLeftPx = (startFrac * usableWidth).coerceAtLeast(0f)
            val endRightPx = (handleWidthPx + endFrac * usableWidth).coerceAtMost(trackWidthPx)
            val selectionWidthPx = (endRightPx - startLeftPx).coerceAtLeast(handleWidthPx * 2)

            // Dimmed Left Region
            if (startLeftPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { startLeftPx.toDp() })
                        .align(Alignment.CenterStart)
                        .background(Color.Black.copy(alpha = 0.68f))
                )
            }

            // Dimmed Right Region
            val rightDimWidthPx = (trackWidthPx - (startLeftPx + selectionWidthPx)).coerceAtLeast(0f)
            if (rightDimWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { rightDimWidthPx.toDp() })
                        .align(Alignment.CenterEnd)
                        .background(Color.Black.copy(alpha = 0.68f))
                )
            }

            // Active Highlight Selection Window (Draggable body to move entire selected interval)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = with(density) { startLeftPx.toDp() })
                    .width(with(density) { selectionWidthPx.toDp() })
                    .background(Color(0x2238BDF8))
                    .border(
                        border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .pointerInput(usableWidth, durationFloat, startMs, endMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (usableWidth > 0f) {
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val curDuration = endMs - startMs
                                val newStart = (startMs + deltaMs).coerceIn(0L, videoDurationMs - curDuration)
                                val newEnd = newStart + curDuration
                                onRangeChange(newStart, newEnd)
                            }
                        }
                    }
            )

            // Left Drag Handle (Start Time)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = with(density) { startLeftPx.toDp() })
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color(0xFF38BDF8))
                    .pointerInput(usableWidth, durationFloat, startMs, endMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (usableWidth > 0f) {
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val minGapMs = 500L
                                val newStart = (startMs + deltaMs).coerceIn(0L, endMs - minGapMs)
                                onRangeChange(newStart, endMs)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Dual vertical grip ridges
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                }
            }

            // Right Drag Handle (End Time)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = with(density) { (startLeftPx + selectionWidthPx - handleWidthPx).toDp() })
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFF38BDF8))
                    .pointerInput(usableWidth, durationFloat, startMs, endMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (usableWidth > 0f) {
                                val deltaMs = (dragAmount / usableWidth * durationFloat).toLong()
                                val minGapMs = 500L
                                val newEnd = (endMs + deltaMs).coerceIn(startMs + minGapMs, videoDurationMs)
                                onRangeChange(startMs, newEnd)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Dual vertical grip ridges
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(1.0.dp).height(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerBottomSheet(
    videoUri: Uri? = null,
    videoDurationMs: Long,
    currentPositionMs: Long,
    onTrim: (startMs: Long, endMs: Long, exportGif: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalDur = videoDurationMs.coerceAtLeast(1000L)
    var startMs by remember { mutableLongStateOf((currentPositionMs - 3000L).coerceAtLeast(0L)) }
    var endMs by remember { mutableLongStateOf((currentPositionMs + 5000L).coerceAtMost(totalDur)) }
    if (endMs <= startMs) {
        endMs = (startMs + 4000L).coerceAtMost(totalDur)
    }

    var exportFormat by remember { mutableStateOf("GIF") } // "VIDEO", "GIF", "WEBP"

    // Real video frame extraction via MediaMetadataRetriever
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }

    LaunchedEffect(videoUri, videoDurationMs) {
        if (videoUri == null || videoDurationMs <= 0L) return@LaunchedEffect
        isExtracting = true
        val frames = withContext(Dispatchers.IO) {
            val list = mutableListOf<Bitmap>()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val count = 9
                val stepUs = (videoDurationMs * 1000L) / count
                for (i in 0 until count) {
                    val timeUs = (i * stepUs).coerceAtLeast(0L)
                    val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, 140, 90, true)
                        list.add(scaled)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoTrimmer", "Thumbnail extraction notice: ${e.message}")
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            list
        }
        thumbnails = frames
        isExtracting = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF00A0F1D), // Compact sleek dark frosted background
        scrimColor = Color.Black.copy(alpha = 0.40f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Video Trimmer & GIF Creator",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Interactive Draggable Filmstrip Track with real frame images
            FilmstripTrack(
                thumbnails = thumbnails,
                isExtracting = isExtracting,
                videoDurationMs = totalDur,
                startMs = startMs,
                endMs = endMs,
                onRangeChange = { newStart, newEnd ->
                    startMs = newStart
                    endMs = newEnd
                }
            )

            // Duration & Timestamp Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start: ${com.medianest.ui.components.formatDuration(startMs)}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Clip: ${com.medianest.ui.components.formatDuration((endMs - startMs).coerceAtLeast(0L))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "End: ${com.medianest.ui.components.formatDuration(endMs)}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // Export Format Choice (Compact Text Chips)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formats = listOf(
                    "GIF" to "Animated GIF",
                    "VIDEO" to "Trim MP4 Video",
                    "WEBP" to "Animated WebP"
                )
                formats.forEach { (key, label) ->
                    val isSelected = exportFormat == key
                    Surface(
                        onClick = { exportFormat = key },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF38BDF8) else Color(0x1FFFFFFF),
                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0x22FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Action Buttons (Cancel Text Button + Main Primary Action Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.35f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        onTrim(startMs, endMs, exportFormat == "GIF" || exportFormat == "WEBP")
                        onDismiss()
                    },
                    modifier = Modifier.weight(0.65f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Icon(
                        imageVector = if (exportFormat == "VIDEO") Icons.Default.ContentCut else Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (exportFormat) {
                            "GIF" -> "Create GIF Clip"
                            "WEBP" -> "Create WebP Clip"
                            else -> "Save Trimmed Video"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

