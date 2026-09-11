package com.medianest.ui.videoplayer.studio

import androidx.compose.runtime.Composable
import com.medianest.data.model.MediaItem
import com.medianest.plugin.StudioInstallDialog

@Composable
fun VideoEditorStudioSheet(
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    StudioInstallDialog(
        onDismiss = onDismiss,
        titleName = "Video Editor"
    )
}
