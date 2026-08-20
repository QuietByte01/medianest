package com.medianest.ui.videoeditor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.medianest.ui.videoeditor.components.VideoTimeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    videoUri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: VideoEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(videoUri) {
        viewModel.initialize(videoUri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Video", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { /* TODO: Save */ }) {
                        Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Video Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                val player = viewModel.getPlayer()
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play/Pause Overlay
                IconButton(
                    onClick = { viewModel.togglePlayback() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Playback",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // 2. Timeline Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(vertical = 16.dp)
            ) {
                VideoTimeline(
                    state = state,
                    thumbnailRepository = viewModel.getThumbnailRepository(),
                    onPositionChanged = { viewModel.seekTo(it) },
                    onTrimChanged = { start, end -> 
                        if (start != state.trimStartMs) viewModel.setTrimStart(start)
                        if (end != state.trimEndMs) viewModel.setTrimEnd(end)
                    },
                    onZoomChanged = { viewModel.setZoom(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                
                // Duration Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(state.positionMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Trimmed: ${formatMs(state.trimmedDurationMs)}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatMs(state.durationMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 100
    return "%02d:%02d.%d".format(minutes, seconds, millis)
}
