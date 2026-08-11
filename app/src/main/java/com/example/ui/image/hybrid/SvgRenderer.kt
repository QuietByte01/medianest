package com.example.ui.image.hybrid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
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
        
        // Quantize scale to steps of 0.5 to avoid excessive re-decoding while zooming
        // but still providing enough resolution for sharpness.
        val quantizedScale = remember(viewport.scale) {
            (viewport.scale * 2).roundToInt() / 2.0f
        }

        val request = remember(source, quantizedScale, viewport.containerSize) {
            val baseWidth = viewport.containerSize.width.toInt().coerceAtLeast(1)
            val baseHeight = viewport.containerSize.height.toInt().coerceAtLeast(1)
            
            // Request a size that accounts for the current zoom level
            val targetSize = if (quantizedScale > 1.0f) {
                Size((baseWidth * quantizedScale).toInt(), (baseHeight * quantizedScale).toInt())
            } else {
                Size(baseWidth, baseHeight)
            }

            ImageRequest.Builder(context)
                .data(if (source is ImageSource.FromUri) source.uri else source.key)
                .decoderFactory(SvgDecoder.Factory())
                .size(targetSize)
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onState = { state ->
                state.painter?.intrinsicSize?.let {
                    if (it != androidx.compose.ui.geometry.Size.Unspecified && it != androidx.compose.ui.geometry.Size.Zero) {
                        onContentSizeChanged(it)
                    }
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
