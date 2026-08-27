package com.medianest.ui.image.hybrid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.medianest.ui.components.GlassSurface

/**
 * Modern High-Performance Image Viewer with custom physics and AGSL integration coming.
 */
@Composable
fun HybridImageViewer(
    source: ImageSource,
    modifier: Modifier = Modifier,
    config: HybridImageViewerConfig = HybridImageViewerConfig(),
    colorFilter: ColorFilter? = null,
    backgroundColor: Color = Color.Black,
    zoomControlsBottomPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onInteraction: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
    onToggleControls: () -> Unit = {},
    onSwipeUpForInfo: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewportState = rememberViewportState()

    val modelData = remember(source) {
        when (source) {
            is ImageSource.FromUri -> source.uri
            is ImageSource.FromFile -> source.file
            is ImageSource.FromByteArray -> source.bytes
        }
    }

    val isZoomed = viewportState.scale > 1.01f

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    val request = remember(modelData) {
        ImageRequest.Builder(context)
            .data(modelData)
            .decoderFactory(SvgDecoder.Factory())
            .decoderFactory(GifDecoder.Factory())
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onSizeChanged { viewportState.viewportSize = it.toSize() }
            .pointerInput(Unit) {
                detectInertialTransformGestures(
                    canConsumePan = { viewportState.scale > 1.0f },
                    onGestureStart = { onInteraction() },
                    onGesture = { centroid, pan, zoom ->
                        viewportState.onGesture(centroid, pan, zoom)
                    },
                    onGestureEnd = { velocity ->
                        viewportState.onGestureEnd(velocity)
                    },
                    onSwipeUp = {
                        onInteraction()
                        onSwipeUpForInfo()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggleControls()
                    },
                    onDoubleTap = { centroid ->
                        onInteraction()
                        viewportState.toggleZoom(centroid)
                    },
                    onLongPress = {
                        onInteraction()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val tileManager = remember { TileManager(androidx.compose.ui.geometry.Size(2000f, 2000f)) } // Placeholder size until decoder fetches it
        val scope = rememberCoroutineScope()
        val decoderEngine = remember { RegionDecoderEngine(context, scope) }
        val tileCache = remember { TileCache(context) }
        
        LaunchedEffect(source) {
            if (source is com.medianest.ui.image.hybrid.ImageSource.FromUri) {
                decoderEngine.initialize(source.uri)
            }
        }
        
        // Use our GL Surface instead of standard AsyncImage
        HybridGLSurface(
            viewportState = viewportState,
            tileManager = tileManager,
            decoderEngine = decoderEngine,
            modifier = Modifier.fillMaxSize()
        )
        
        var intrinsicImageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

        LaunchedEffect(intrinsicImageSize, viewportState.viewportSize) {
            if (intrinsicImageSize.width > 0 && intrinsicImageSize.height > 0 && viewportState.viewportSize.width > 0) {
                val scaleX = viewportState.viewportSize.width / intrinsicImageSize.width
                val scaleY = viewportState.viewportSize.height / intrinsicImageSize.height
                val fitScale = minOf(scaleX, scaleY)
                viewportState.contentSize = androidx.compose.ui.geometry.Size(
                    intrinsicImageSize.width * fitScale,
                    intrinsicImageSize.height * fitScale
                )
                tileManager.imageSize = intrinsicImageSize
            }
        }

        // Base low-res preview (fallback if GL is not yet ready, or for transitions)
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                intrinsicImageSize = androidx.compose.ui.geometry.Size(size.width, size.height)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Fix black screen by ensuring scale is never 0 or NaN
                    scaleX = if (viewportState.scale.isNaN() || viewportState.scale <= 0f) 1f else viewportState.scale
                    scaleY = if (viewportState.scale.isNaN() || viewportState.scale <= 0f) 1f else viewportState.scale
                    translationX = if (viewportState.offset.x.isNaN()) 0f else viewportState.offset.x
                    translationY = if (viewportState.offset.y.isNaN()) 0f else viewportState.offset.y
                    
                    // We apply transparency when the GL surface takes over, but for now we leave it visible.
                    // alpha = if (isGLReady) 0f else 1f
                }
        )

        // Sleek Zoom Percentage Floating Pill Overlay
        val animatedBottomPadding by animateDpAsState(
            targetValue = zoomControlsBottomPadding,
            animationSpec = tween(300),
            label = "ZoomPillPadding"
        )

        AnimatedVisibility(
            visible = isZoomed,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = animatedBottomPadding)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x66000000),
                borderColor = Color(0x40FFFFFF),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consumes click to block background toggle */ }
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${(viewportState.scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
