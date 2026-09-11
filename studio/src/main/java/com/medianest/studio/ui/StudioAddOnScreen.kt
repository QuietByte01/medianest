package com.medianest.studio.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.studio.util.StudioMediaResolver

@Composable
fun FullStudioAddOnScreen(
    action: String?,
    mediaUriString: String?,
    initialTabStr: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val parsedUri = remember(mediaUriString) {
        if (!mediaUriString.isNullOrBlank()) runCatching { Uri.parse(mediaUriString) }.getOrNull() else null
    }

    val mediaItem = remember(parsedUri) {
        parsedUri?.let { uri ->
            StudioMediaResolver.resolveMediaItem(context, uri)
        }
    }

    val isEditorMode = action == "com.medianest.studio.ACTION_EDIT_VIDEO"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        if (isEditorMode && mediaItem != null) {
            VideoEditorStudioSheet(
                mediaItem = mediaItem,
                onDismiss = onClose
            )
        } else {
            val studioTab = runCatching { StudioTab.valueOf(initialTabStr.uppercase()) }.getOrDefault(StudioTab.CONVERT)
            MediaConverterStudioDialog(
                initialTab = studioTab,
                imagesList = emptyList(),
                videosList = if (mediaItem != null) listOf(mediaItem) else emptyList(),
                audioList = emptyList(),
                onDismissRequest = onClose,
                onOpenVideoPlayer = { _, _, _ -> },
                onOpenAudioPlayer = {}
            )
        }
    }
}
