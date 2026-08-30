package com.medianest.ui.audioplayer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.medianest.data.model.MediaItem
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.StyleSelectorBar
import com.medianest.ui.components.VisualizerStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumSongsSheet(
    albumName: String,
    albumSongs: List<MediaItem>,
    playerState: PlayerState,
    playerManager: ExoPlayerManager,
    onDismiss: () -> Unit,
    currentItem: MediaItem? = null
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xEB101114),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Album: $albumName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(albumSongs) { idx, track ->
                    val isPlayingThis = currentItem != null && track.uri == currentItem.uri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPlayingThis) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isPlayingThis) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                val queueIndex = playerState.queue.indexOf(track)
                                if (queueIndex != -1) {
                                    playerManager.playMediaList(playerState.queue, queueIndex)
                                }
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            fontSize = 14.sp,
                            color = if (isPlayingThis) Color.White else Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist ?: "Unknown Artist",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isPlayingThis) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Playing",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            if (albumSongs.isEmpty()) {
                Text(
                    "No songs from this album found in your library.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun FullscreenVisualizerDialog(
    isPlaying: Boolean,
    audioSessionId: Int,
    albumArtHue: Float?,
    currentItem: MediaItem?,
    onDismiss: () -> Unit
) {
    var selectedVisualizerStyle by remember { mutableStateOf<VisualizerStyle>(VisualizerStyle.ENERGY_PARTICLES) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(Unit) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                w.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
                androidx.core.view.WindowInsetsControllerCompat(w, w.decorView).let { controller ->
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                }
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AudioReactiveVisualizerPattern(
                isPlaying = isPlaying,
                audioSessionId = audioSessionId,
                hue = albumArtHue,
                style = selectedVisualizerStyle,
                onStyleChange = { selectedVisualizerStyle = it },
                showControls = false,
                isFullscreen = true,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))))
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentItem?.title ?: "No Track",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentItem?.artist ?: "Unknown Artist",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(18.dp))
                StyleSelectorBar(
                    currentStyle = selectedVisualizerStyle,
                    onSelectStyle = { selectedVisualizerStyle = it },
                    isFullscreen = true,
                    hue = albumArtHue ?: 210f
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 24.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}
