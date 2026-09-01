package com.medianest.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal providing active backdrop blur state for surface overlays.
 */
val LocalBackdropBlurState = compositionLocalOf<BackdropBlurState?> { null }

/**
 * Reusable Backdrop Blur Engine for Jetpack Compose.
 *
 * Implements YouTube's native frosted glass backdrop blur pipeline:
 * 1. Source content writes its draw operations to a reusable hardware GraphicsLayer.
 * 2. Emits draw invalidation signals so receiver re-renders synchronously on every scroll/animation frame.
 * 3. Dual-Stage Optical Downsampling (default 8x) + GPU Bilinear Magnification:
 *    - Collapses high-frequency sharp text glyphs into smooth color halos.
 * 4. Hardware RenderEffect Blur:
 *    - Applies GPU Gaussian BlurEffect (API 31+) over downsampled buffers.
 * 5. Acrylic Frosted Finish:
 *    - Draws opaque base occlusion layer (to block unblurred background text/pixels),
 *      the downsampled & blurred backdrop layer, translucent theme tint, and optional frosted top border line.
 */
@Stable
class BackdropBlurState {
    var sourceLayer: GraphicsLayer? = null
    var downsampleLayer: GraphicsLayer? = null
    var blurLayer: GraphicsLayer? = null
    var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
    var receiverCoordinates: LayoutCoordinates? by mutableStateOf(null)
    var drawSignal by mutableLongStateOf(0L)

    val relativeOffset: Offset
        get() {
            val src = sourceCoordinates ?: return Offset.Zero
            val rec = receiverCoordinates ?: return Offset.Zero
            if (!src.isAttached || !rec.isAttached) return Offset.Zero
            val srcBounds = src.boundsInRoot()
            val recBounds = rec.boundsInRoot()
            return Offset(
                x = recBounds.left - srcBounds.left,
                y = recBounds.top - srcBounds.top
            )
        }
}

@Composable
fun rememberBackdropBlurState(): BackdropBlurState {
    val state = remember { BackdropBlurState() }
    val sourceLayer = rememberGraphicsLayer()
    val downsampleLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    state.sourceLayer = sourceLayer
    state.downsampleLayer = downsampleLayer
    state.blurLayer = blurLayer
    return state
}

/**
 * Marks the composable (e.g. main screen or video player) as the backdrop source to be sampled and blurred.
 */
fun Modifier.backdropSource(
    state: BackdropBlurState,
    backgroundColor: Color = Color.Unspecified
): Modifier = this
    .onGloballyPositioned { coordinates ->
        state.sourceCoordinates = coordinates
    }
    .drawWithContent {
        val layer = state.sourceLayer
        if (layer != null) {
            val srcSize = IntSize(
                size.width.toInt().coerceAtLeast(1),
                size.height.toInt().coerceAtLeast(1)
            )
            layer.record(size = srcSize) {
                if (backgroundColor != Color.Unspecified) {
                    drawRect(color = backgroundColor)
                }
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
            state.drawSignal++
        } else {
            drawContent()
        }
    }

/**
 * Applies real-time hardware backdrop blur to the background content behind this composable.
 */
fun Modifier.backdropReceiver(
    state: BackdropBlurState,
    blurRadius: Dp = 28.dp,
    tint: Color = Color(0xBF0F0F0F),
    baseColor: Color = Color(0xFF0F0F0F),
    blurAlpha: Float = 0.45f,
    showTopBorder: Boolean = false,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    downscaleFactor: Float = 8f
): Modifier = this
    .onGloballyPositioned { coordinates ->
        state.receiverCoordinates = coordinates
    }
    .drawBehind {
        // 1. Draw opaque base layer first to completely block any underlying sharp text/pixels
        drawRect(color = baseColor, size = size)

        // Read drawSignal to trigger re-draw whenever source content updates or scrolls
        @Suppress("UNUSED_VARIABLE")
        val signal = state.drawSignal
        val offset = state.relativeOffset
        val sourceLayer = state.sourceLayer
        val downsampleLayer = state.downsampleLayer
        val blurLayer = state.blurLayer

        if (sourceLayer != null && downsampleLayer != null && blurLayer != null && size.width > 0 && size.height > 0) {
            val radiusPx = (blurRadius.toPx() * 1.6f).coerceAtLeast(1f)
            val receiverSize = IntSize(
                size.width.toInt().coerceAtLeast(1),
                size.height.toInt().coerceAtLeast(1)
            )

            // Clamp offsets to sourceLayer bounds so sampled slice is always 100% full
            val srcSize = sourceLayer.size
            val maxX = (srcSize.width - receiverSize.width).coerceAtLeast(0).toFloat()
            val maxY = (srcSize.height - receiverSize.height).coerceAtLeast(0).toFloat()

            val clampedX = offset.x.coerceIn(0f, maxX)
            val clampedY = offset.y.coerceIn(0f, maxY)

            // Stage 1: Downsample background slice (8x factor)
            val factor = downscaleFactor.coerceAtLeast(1f)
            val downsampledSize = IntSize(
                (receiverSize.width / factor).toInt().coerceAtLeast(1),
                (receiverSize.height / factor).toInt().coerceAtLeast(1)
            )

            downsampleLayer.record(size = downsampledSize) {
                scale(scaleX = 1f / factor, scaleY = 1f / factor, pivot = Offset.Zero) {
                    translate(left = -clampedX, top = -clampedY) {
                        drawLayer(sourceLayer)
                    }
                }
            }

            // Stage 2: Upscale with Bilinear Interpolation + GPU Gaussian BlurEffect
            blurLayer.alpha = blurAlpha.coerceIn(0f, 1f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurLayer.renderEffect = BlurEffect(
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    edgeTreatment = TileMode.Clamp
                )
                blurLayer.record(size = receiverSize) {
                    scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                        drawLayer(downsampleLayer)
                    }
                }
            } else {
                // Multi-pass diffuse sampling fallback for API < 31
                blurLayer.record(size = receiverSize) {
                    val sampleOffsets = listOf(
                        Offset(-4f, -4f), Offset(4f, -4f), Offset(-4f, 4f), Offset(4f, 4f),
                        Offset(-2f, 0f), Offset(2f, 0f), Offset(0f, -2f), Offset(0f, 2f), Offset(0f, 0f)
                    )
                    scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                        for (sampleOffset in sampleOffsets) {
                            translate(sampleOffset.x, sampleOffset.y) {
                                drawLayer(downsampleLayer)
                            }
                        }
                    }
                }
            }

            // Stage 3: Render the diffused, blurred background slice
            clipRect(0f, 0f, size.width, size.height) {
                drawLayer(blurLayer)
            }
        }

        // Stage 4: Draw translucent theme backdrop gradient tint
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tint.copy(alpha = (tint.alpha * 0.80f).coerceIn(0f, 1f)),
                    tint.copy(alpha = (tint.alpha * 0.98f).coerceIn(0f, 1f))
                )
            ),
            size = size
        )

        // Stage 5: Optional frosted top border line
        if (showTopBorder) {
            val strokeWidthPx = 0.6.dp.toPx()
            drawLine(
                color = borderColor,
                start = Offset(0f, strokeWidthPx / 2),
                end = Offset(size.width, strokeWidthPx / 2),
                strokeWidth = strokeWidthPx
            )
        }
    }

/**
 * Reusable container Box that renders a frosted backdrop blur overlay over source content.
 */
@Composable
fun BackdropBlurBox(
    state: BackdropBlurState,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 28.dp,
    tint: Color = Color(0xBF0F0F0F),
    baseColor: Color = Color(0xFF0F0F0F),
    showTopBorder: Boolean = false,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.backdropReceiver(
            state = state,
            blurRadius = blurRadius,
            tint = tint,
            baseColor = baseColor,
            showTopBorder = showTopBorder,
            borderColor = borderColor
        ),
        contentAlignment = contentAlignment,
        content = content
    )
}
