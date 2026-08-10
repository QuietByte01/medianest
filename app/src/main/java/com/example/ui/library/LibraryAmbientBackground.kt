package com.example.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.MediaType
import com.example.data.model.MediaItem

@Composable
fun LibraryAmbientBackground(
    isDashboardTab: Boolean,
    isImagesTab: Boolean,
    isVideosTab: Boolean,
    isAudioTab: Boolean,
    currentPlayingTrack: MediaItem?,
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>
) {
    val context = LocalContext.current
    var activeHue by remember { mutableStateOf<Float?>(null) }

    val activeMediaTarget = remember(isDashboardTab, isImagesTab, isVideosTab, isAudioTab, currentPlayingTrack, imagesList, videosList, audioList) {
        when {
            isDashboardTab -> null
            isImagesTab -> imagesList.firstOrNull()
            isVideosTab -> currentPlayingTrack?.takeIf { it.type == MediaType.VIDEO } ?: videosList.firstOrNull()
            isAudioTab -> currentPlayingTrack ?: audioList.firstOrNull()
            else -> currentPlayingTrack ?: audioList.firstOrNull()
        }
    }

    LaunchedEffect(activeMediaTarget?.id, activeMediaTarget?.uri) {
        if (activeMediaTarget != null) {
            val uri = activeMediaTarget.albumArtUri ?: activeMediaTarget.uri
            activeHue = com.example.ui.components.extractBaseHueFromArt(context, uri)
        } else {
            activeHue = null
        }
    }

    val ambientTopColor = remember(activeHue, isDashboardTab) {
        if (isDashboardTab) Color(0xFF1E222A)
        else if (activeHue != null) Color.hsv(activeHue!!, 0.65f, 0.40f, 0.35f)
        else Color(0x354A3B2C)
    }

    val ambientBottomColor = remember(activeHue, isDashboardTab) {
        if (isDashboardTab) Color(0xFF121419)
        else if (activeHue != null) Color.hsv((activeHue!! + 25f) % 360f, 0.55f, 0.28f, 0.30f)
        else Color(0x301E2838)
    }

    val ambientImageUri = remember(isDashboardTab, activeMediaTarget) {
        if (isDashboardTab) null
        else activeMediaTarget?.albumArtUri ?: activeMediaTarget?.uri
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
    ) {
        // Dynamic Ambient Album Art Layer
        if (ambientImageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ambientImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .graphicsLayer { alpha = 0.35f }
            )
        }

        // Ambient Background Radial Glows (Top-Left & Bottom-Right)
        Box(
            modifier = Modifier
                .size(450.dp)
                .offset(x = (-120).dp, y = (-100).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientTopColor,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(480.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 120.dp, y = 120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientBottomColor,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}
