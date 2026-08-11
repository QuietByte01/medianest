package com.example.ui.videoplayer

import android.app.Activity
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerState
import com.example.ui.components.ThinSeekBar
import com.example.ui.components.formatDuration

@Composable
fun VideoPlayerTopBar(
    playerState: PlayerState,
    onClose: () -> Unit,
    decoderMode: String,
    onDecoderModeClick: () -> Unit,
    onDrawerClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onInfoClick: () -> Unit,
    onMenuClick: () -> Unit,
    onCaptureClick: () -> Unit,
    isControlsLocked: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Transparent)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playerState.currentItem?.title ?: "Video Player",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )

                val config = LocalConfiguration.current
                if (!(config.screenWidthDp < 600 && config.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Transparent)
                            .border(0.5.dp, Color(0xB3FFFFFF), RoundedCornerShape(4.dp))
                            .clickable { onDecoderModeClick() }
                            .padding(horizontal = 5.dp)
                    ) {
                        Text(text = decoderMode, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, contentColor = Color.White) {
                Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDrawerClick) { Icon(Icons.Default.FormatListBulleted, contentDescription = "Sidebar", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onSubtitleClick) { Icon(Icons.Default.ClosedCaption, contentDescription = "Subtitles", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onInfoClick) { Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!isControlsLocked) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .clickable { onCaptureClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun VideoPlayerBottomBar(
    playerState: PlayerState,
    isControlsLocked: Boolean,
    isHorizontalDragging: Boolean,
    seekTargetPositionMs: Long,
    onSeek: (Long) -> Unit,
    isControlsLockedState: Boolean,
    onLockClick: () -> Unit,
    onRotateClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSpeedLongPress: () -> Unit,
    onPipClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onAspectRatioLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                bottom = if (isLandscape) 8.dp else 12.dp,
                start = if (isLandscape) 24.dp else 16.dp,
                end = if (isLandscape) 24.dp else 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isControlsLocked) {
            val effectiveSeekPos = if (isHorizontalDragging) seekTargetPositionMs else playerState.currentPositionMs
            ThinSeekBar(
                value = if (playerState.durationMs > 0) effectiveSeekPos.toFloat() else 0f,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(playerState.durationMs.toFloat().coerceAtLeast(1f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                thumbColor = Color.White
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatDuration(effectiveSeekPos), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(text = formatDuration(playerState.durationMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
        }

        if (!isHorizontalDragging) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, contentColor = Color.White) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onLockClick) {
                        Icon(
                            imageVector = if (isControlsLockedState) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = if (isControlsLockedState) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onRotateClick) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onSpeedClick() },
                                    onLongPress = { onSpeedLongPress() }
                                )
                            }
                            .padding(6.dp)
                    ) {
                        Text(text = "${playerState.playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(onClick = onPipClick) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onAspectRatioClick() },
                                    onLongPress = { onAspectRatioLongPress() }
                                )
                            }
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
