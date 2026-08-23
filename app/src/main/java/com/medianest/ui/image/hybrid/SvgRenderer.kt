package com.medianest.ui.image.hybrid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Size
import kotlin.math.roundToInt

/**
 * Renderer for SVG/Vector images that maintains sharpness during zoom.
 */
class SvgRenderer(
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
        
        // Quantize scale and debounce updates during active gestures
        var debouncedScale by remember { mutableFloatStateOf(viewport.scale) }
        
        LaunchedEffect(viewport.scale) {
            // Short delay to avoid continuous Coil re-decodes during pinch gestures
            kotlinx.coroutines.delay(150)
            debouncedScale = viewport.scale
        }

        val quantizedScale = remember(debouncedScale) {
            ((debouncedScale * 2).roundToInt() / 2.0f).coerceAtLeast(1.0f)
        }

        val request = remember(source, quantizedScale, viewport.containerSize) {
            val baseWidth = viewport.containerSize.width.toInt()
            val baseHeight = viewport.containerSize.height.toInt()
            
            val modelData = when (source) {
                is ImageSource.FromUri -> source.uri
                is ImageSource.FromFile -> source.file
                is ImageSource.FromByteArray -> source.bytes
            }

            val builder = ImageRequest.Builder(context)
                .data(modelData)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(true)

            // Only override decode size if container size has been properly measured (> 10px)
            if (baseWidth > 10 && baseHeight > 10) {
                val targetSize = if (quantizedScale > 1.0f) {
                    Size((baseWidth * quantizedScale).toInt(), (baseHeight * quantizedScale).toInt())
                } else {
                    Size(baseWidth, baseHeight)
                }
                builder.size(targetSize)
            }

            builder.build()
        }

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
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.BrokenImage,
                        contentDescription = "Error loading SVG",
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                }
            },
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = viewport.scale / quantizedScale.coerceAtLeast(1.0f),
                    scaleY = viewport.scale / quantizedScale.coerceAtLeast(1.0f),
                    translationX = viewport.offsetX,
                    translationY = viewport.offsetY,
                    rotationZ = viewport.rotation
                )
        )
    }

    override fun release() {
    }
}
