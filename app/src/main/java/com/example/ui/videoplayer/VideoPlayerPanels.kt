package com.example.ui.videoplayer

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.SubtitleItem
import com.example.data.repository.NetworkRepository
import com.example.player.ExoPlayerManager
import com.example.player.PlayerState
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.GlassSurface
import com.example.ui.components.PictureMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    onSearchOnline: (NetworkRepository.SubtitleProvider) -> Unit,
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                            modifier = Modifier.padding(horizontal = 4.dp)
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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Online Subtitle Sources:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceButton(
                            label = "OpenSubs",
                            icon = Icons.Default.Cloud,
                            isLoading = isSearching,
                            onClick = { onSearchOnline(NetworkRepository.SubtitleProvider.OPEN_SUBTITLES) },
                            modifier = Modifier.weight(1f)
                        )
                        SourceButton(
                            label = "Community",
                            icon = Icons.Default.Public,
                            isLoading = isSearching,
                            onClick = { onSearchOnline(NetworkRepository.SubtitleProvider.YTS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Text(text = msg, fontSize = 12.sp, color = Color(0xFFA78BFA), modifier = Modifier.padding(horizontal = 4.dp))
                }

                if (onlineSubtitles.isNotEmpty()) {
                    Text(text = "Online Search Results:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
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
}

@Composable
private fun SourceButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.clickable(enabled = !isLoading) { onClick() },
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0x26FFFFFF),
        borderColor = Color(0x33FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFA78BFA), modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
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
                Slider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..36f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFEEEEEE),
                        inactiveTrackColor = Color(0x40FFFFFF)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
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
                Switch(
                    checked = hasShadow,
                    onCheckedChange = onHasShadowChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF64748B)
                    )
                )
            }

            Button(
                onClick = { onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Text("Apply Style", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSelectionSheet(
    onDismiss: () -> Unit,
    playerManager: ExoPlayerManager,
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
            } else {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = Color(0x33FFFFFF),
                    borderColor = Color.White,
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Default Audio Stream",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
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
    cropMode: String,
    onCropModeChange: (String) -> Unit,
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
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        SettingsContent(
                            playerState = playerState,
                            playerManager = playerManager,
                            cropMode = cropMode,
                            onCropModeChange = onCropModeChange,
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
            containerColor = Color(0xDC121522),
            shape = RoundedCornerShape(20.dp),
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
                        cropMode = cropMode,
                        onCropModeChange = onCropModeChange,
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
                    Text("Close", color = Color(0xFF94A3B8))
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
    cropMode: String,
    onCropModeChange: (String) -> Unit,
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = onShowDetails,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("View File Info")
        }

        // Repeat mode
        Text(text = "Repeat Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF,
                onClick = { playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF) },
                label = { Text("Off") }
            )
            FilterChip(
                selected = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE,
                onClick = { playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE) },
                label = { Text("Repeat One") }
            )
            FilterChip(
                selected = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL,
                onClick = { playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL) },
                label = { Text("Repeat All") }
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Playback Speed
        Text(text = "Playback Speed: ${playerState.playbackSpeed}x", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val speedList = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
            items(speedList) { spd ->
                FilterChip(
                    selected = playerState.playbackSpeed == spd,
                    onClick = { playerManager.setPlaybackSpeed(spd) },
                    label = { Text("${spd}x") }
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Aspect Ratio Scale Mode
        Text(text = "Aspect Ratio / Scale Mode: $cropMode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = cropMode == "FIT", onClick = { onCropModeChange("FIT") }, label = { Text("Fit") })
            FilterChip(selected = cropMode == "CROP", onClick = { onCropModeChange("CROP") }, label = { Text("Fill / Crop") })
            FilterChip(selected = cropMode == "STRETCH", onClick = { onCropModeChange("STRETCH") }, label = { Text("Stretch") })
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Text(text = "Picture Mode: ${PictureMode.fromKey(pictureMode).displayName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PictureMode.entries) { mode ->
                FilterChip(
                    selected = pictureMode.equals(mode.key, ignoreCase = true),
                    onClick = { onPictureModeChange(mode.key) },
                    label = { Text(mode.displayName) }
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Audio Sync Delay
        Text(text = "Audio Sync Delay: ${audioSyncOffsetMs}ms", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onAudioSyncOffsetChange(audioSyncOffsetMs - 100) }, modifier = Modifier.weight(1f)) { Text("-100ms") }
            OutlinedButton(onClick = { onAudioSyncOffsetChange(audioSyncOffsetMs + 100) }, modifier = Modifier.weight(1f)) { Text("+100ms") }
            OutlinedButton(onClick = { onAudioSyncOffsetChange(0) }, modifier = Modifier.weight(1f)) { Text("Reset") }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Text(text = "Audio Boost: ${playerState.audioBoostPercent}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        Slider(
            value = playerState.audioBoostPercent.toFloat(),
            onValueChange = { playerManager.setAudioBoost(it.toInt()) },
            valueRange = 0f..100f
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Film Grain Overlay", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Switch(
                checked = isFilmGrainEnabled,
                onCheckedChange = onFilmGrainEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
            )
        }

        if (isFilmGrainEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Grain Intensity", fontSize = 12.sp, color = Color(0xFF94A3B8))
            Slider(
                value = filmGrainIntensity,
                onValueChange = onFilmGrainIntensityChange,
                valueRange = 0.05f..0.30f
            )
        }
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
    onAudioTracks: () -> Unit,
    onCast: () -> Unit,
    onSettings: () -> Unit,
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
