package com.medianest.ui.image.hybrid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Standard renderer using Coil for normal-sized raster images.
 * Includes loading placeholders and error fallback.
 */
class StandardBitmapRenderer(
    private val onContentSizeChanged: (androidx.compose.ui.geometry.Size) -> Unit
) : ImageRenderer {

    @Composable
    override fun Render(
        source: ImageSource,
        viewport: ImageViewport,
        config: HybridImageViewerConfig,
        colorFilter: ColorFilter?,
        modifier: Modifier
    ) {
        val context = LocalContext.current
        val request = ImageRequest.Builder(context)
            .data(if (source is ImageSource.FromUri) source.uri else source.key)
            .crossfade(true)
            .build()

        SubcomposeAsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size != androidx.compose.ui.geometry.Size.Unspecified && size != androidx.compose.ui.geometry.Size.Zero) {
                    onContentSizeChanged(size)
                }
            },
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.medianest.ui.components.MediaLoadingAnimation(
                        mediaType = com.medianest.data.db.MediaType.IMAGE,
                        iconSize = 38.dp
                    )
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Error, contentDescription = "Error loading image", tint = Color.Red.copy(alpha = 0.7f))
                }
            },
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = viewport.scale,
                    scaleY = viewport.scale,
                    translationX = viewport.offsetX,
                    translationY = viewport.offsetY,
                    rotationZ = viewport.rotation
                )
        )
    }

    override fun release() {
        // Coil handles its own memory/cache
    }
}
