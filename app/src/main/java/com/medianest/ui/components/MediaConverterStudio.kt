package com.medianest.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Transform
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.medianest.data.model.MediaItem
import com.medianest.plugin.StudioInstallDialog

enum class StudioTab(val title: String, val icon: ImageVector) {
    CONVERT("Convert & Remux", Icons.Default.Transform),
    COMPRESS("Smart Compress", Icons.Default.Download),
    COLORIZE("GL Colorizer", Icons.Default.Palette),
    CROP("Crop & Reframe", Icons.Default.Crop),
    EXTRACT("Extract Streams", Icons.Default.Build),
    REPAIR("Bitstream Repair", Icons.Default.Build)
}

@Composable
fun MediaConverterStudioDialog(
    initialTab: StudioTab = StudioTab.CONVERT,
    imagesList: List<MediaItem> = emptyList(),
    videosList: List<MediaItem> = emptyList(),
    audioList: List<MediaItem> = emptyList(),
    onDismissRequest: () -> Unit = {},
    onOpenVideoPlayer: (MediaItem) -> Unit = {},
    onOpenAudioPlayer: (MediaItem) -> Unit = {}
) {
    StudioInstallDialog(
        onDismiss = onDismissRequest,
        titleName = "Media Studio"
    )
}
