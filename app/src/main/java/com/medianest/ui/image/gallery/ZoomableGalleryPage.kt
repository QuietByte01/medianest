package com.medianest.ui.image.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * High-performance zoomable image gallery page using Telephoto.
 * Automatically delegates to sub-sampled tile decoding for high-res images (up to 100MP+)
 * while seamlessly managing gesture delegation with HorizontalPager.
 */
@Composable
fun ZoomableGalleryPage(
    model: Any?,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (isZoomed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailModel: Any? = null,
    colorFilter: ColorFilter? = null,
    maxZoomFactor: Float = 5.0f
) {
    val context = LocalContext.current
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(
            maxZoomFactor = maxZoomFactor
        )
    )
    val zoomableImageState = rememberZoomableImageState(zoomableState = zoomableState)

    // Notify parent if page is zoomed in (used to coordinate pager scroll and drag-to-dismiss)
    val isZoomed = (zoomableState.zoomFraction ?: 0f) > 0.01f
    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    // Auto-reset zoom level when swiped away from this page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && isZoomed) {
            zoomableState.resetZoom(animationSpec = androidx.compose.animation.core.tween(300))
        }
    }

    val fullRequest = remember(model, thumbnailModel) {
        ImageRequest.Builder(context)
            .data(model)
            .decoderFactory(SvgDecoder.Factory())
            .decoderFactory(GifDecoder.Factory())
            .crossfade(true)
            .apply {
                if (thumbnailModel != null) {
                    placeholderMemoryCacheKey(thumbnailModel.toString())
                }
            }
            .build()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ZoomableAsyncImage(
            model = fullRequest,
            contentDescription = null,
            state = zoomableImageState,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onClick = { onTap() },
            modifier = Modifier.fillMaxSize()
        )
    }
}
