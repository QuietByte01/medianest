package com.medianest.ui.components

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Reusable Backdrop Blur Engine for Jetpack Compose.
 *
 * Pipeline (unchanged conceptually from the original):
 * 1. Source content is recorded into a reusable hardware GraphicsLayer every draw pass.
 * 2. The receiver invalidates itself every frame the source draws (via a draw-phase
 *    "generation" counter — see note on FIX 1 below for why this is done carefully).
 * 3. Dual-stage downsample + GPU bilinear magnification.
 * 4. Hardware RenderEffect Gaussian blur (API 31+), diffuse multi-sample fallback below.
 * 5. Opaque base occlusion + blurred backdrop + translucent tint + optional top border.
 */

private const val TAG = "BackdropBlur"

@Stable
class BackdropBlurState {
    var sourceLayer: GraphicsLayer? = null
    var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
    var drawSignal by mutableStateOf(0)

    private val invalidators = mutableListOf<() -> Unit>()

    fun registerReceiverInvalidator(invalidate: () -> Unit): () -> Unit {
        invalidators.add(invalidate)
        return {
            invalidators.remove(invalidate)
        }
    }

    fun notifySourceDrawn() {
        for (i in 0 until invalidators.size) {
            invalidators.getOrNull(i)?.invoke()
        }
    }

    fun getRelativeOffset(receiverCoordinates: LayoutCoordinates?): Offset {
        val src = sourceCoordinates ?: return Offset.Zero
        val rec = receiverCoordinates ?: return Offset.Zero
        if (!src.isAttached || !rec.isAttached) return Offset.Zero
        return try {
            val srcPos = src.positionOnScreen()
            val recPos = rec.positionOnScreen()
            Offset(x = recPos.x - srcPos.x, y = recPos.y - srcPos.y)
        } catch (e: Throwable) {
            Log.w(TAG, "positionOnScreen failed, falling back to boundsInRoot", e)
            val srcBounds = src.boundsInRoot()
            val recBounds = rec.boundsInRoot()
            Offset(x = recBounds.left - srcBounds.left, y = recBounds.top - srcBounds.top)
        }
    }
}

@Composable
fun rememberBackdropBlurState(): BackdropBlurState {
    val state = remember { BackdropBlurState() }
    val sourceLayer = rememberGraphicsLayer()
    state.sourceLayer = sourceLayer
    return state
}

val LocalBackdropState = compositionLocalOf<BackdropBlurState?> { null }

val LocalIsSurfaceViewMode = compositionLocalOf { false }

/**
 * Marks the composable (e.g. main screen or video player) as the backdrop source to be
 * sampled and blurred.
 */
fun Modifier.backdropSource(
    state: BackdropBlurState,
    backgroundColor: Color = Color.Unspecified
): Modifier = this
    .onGloballyPositioned { coordinates ->
        state.sourceCoordinates = coordinates
    }
    .drawWithContent {
        @Suppress("UNUSED_EXPRESSION")
        state.drawSignal
        val layer = state.sourceLayer
        if (layer != null) {
            val srcSize = IntSize(
                size.width.toInt().coerceAtLeast(1),
                size.height.toInt().coerceAtLeast(1)
            )
            try {
                layer.record(size = srcSize) {
                    if (backgroundColor != Color.Unspecified) {
                        drawRect(color = backgroundColor)
                    }
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
                state.notifySourceDrawn()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to record source layer (size=$srcSize)", e)
                drawContent()
            }
        } else {
            drawContent()
        }
    }

/**
 * Applies real-time hardware backdrop blur to the background content behind this composable.
 * Uses Modifier.composed to ensure every receiver composable gets its own GraphicsLayers,
 * its own coordinates, and its own lifecycle-bound invalidation listener.
 */
fun Modifier.backdropReceiver(
    state: BackdropBlurState,
    blurRadius: Dp = 28.dp,
    tint: Color = Color(0x6608090E),
    baseColor: Color = Color.Transparent,
    showTopBorder: Boolean = false,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    downscaleFactor: Float = 3f
): Modifier = composed {
    val isSurfaceViewMode = LocalIsSurfaceViewMode.current
    var receiverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val downsampleLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()

    var invalidationTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(state) {
        val unregister = state.registerReceiverInvalidator {
            invalidationTick++
        }
        onDispose {
            unregister()
        }
    }

    this
        .onGloballyPositioned { coordinates ->
            receiverCoordinates = coordinates
        }
        .drawBehind {
            @Suppress("UNUSED_EXPRESSION")
            invalidationTick

            // 1. Opaque / acrylic base layer. If over a zero-copy SurfaceView, apply frosted black
            // acrylic tint (0xD908080C - 85%) so the panel has a rich frosted black acrylic backing.
            // When in TextureView or other screens, keep transparent so live video blur shines through!
            val effectiveBaseColor = if (isSurfaceViewMode && baseColor == Color.Transparent) {
                Color(0xD908080C)
            } else {
                baseColor
            }

            if (effectiveBaseColor != Color.Transparent) {
                drawRect(color = effectiveBaseColor, size = size)
            }

            val offset = state.getRelativeOffset(receiverCoordinates)
            val sourceLayer = state.sourceLayer

            if (sourceLayer != null && size.width > 0 && size.height > 0) {
                val factor = downscaleFactor.coerceAtLeast(1f)
                val radiusPx = blurRadius.toPx().coerceAtLeast(1f)
                val padPx = kotlin.math.ceil(radiusPx / factor).toInt()

                val receiverSize = IntSize(
                    size.width.toInt().coerceAtLeast(1),
                    size.height.toInt().coerceAtLeast(1)
                )
                val downsampledSize = IntSize(
                    (receiverSize.width / factor).toInt().coerceAtLeast(1) + 2 * padPx,
                    (receiverSize.height / factor).toInt().coerceAtLeast(1) + 2 * padPx
                )

                try {
                    // Stage 1: Downsample background slice with surrounding margin.
                    downsampleLayer.record(size = downsampledSize) {
                        translate(left = (-offset.x / factor) + padPx, top = (-offset.y / factor) + padPx) {
                            scale(scaleX = 1f / factor, scaleY = 1f / factor, pivot = Offset.Zero) {
                                drawLayer(sourceLayer)
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Stage 2a: GPU Gaussian blur, applied in upscaled (full-res) space
                        blurLayer.renderEffect = BlurEffect(
                            radiusX = radiusPx,
                            radiusY = radiusPx,
                            edgeTreatment = TileMode.Clamp
                        )
                        blurLayer.record(size = receiverSize) {
                            translate(left = -padPx * factor, top = -padPx * factor) {
                                scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                                    drawLayer(downsampleLayer)
                                }
                            }
                        }
                    } else {
                        // Stage 2b: Multi-pass diffuse sampling fallback for API < 31.
                        val spread = (radiusPx * 0.3f).coerceAtLeast(1f)
                        val sampleOffsets = listOf(
                            Offset(-spread, -spread), Offset(spread, -spread),
                            Offset(-spread, spread), Offset(spread, spread),
                            Offset(-spread / 2, 0f), Offset(spread / 2, 0f),
                            Offset(0f, -spread / 2), Offset(0f, spread / 2),
                            Offset(0f, 0f)
                        )
                        val upscaled = downsampleLayer
                        blurLayer.record(size = receiverSize) {
                            translate(left = -padPx * factor, top = -padPx * factor) {
                                for (sampleOffset in sampleOffsets) {
                                    translate(sampleOffset.x, sampleOffset.y) {
                                        scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                                            drawLayer(upscaled)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Stage 3: Render the blurred background slice.
                    clipRect(0f, 0f, size.width, size.height) {
                        drawLayer(blurLayer)
                    }
                } catch (e: Throwable) {
                    Log.e(
                        TAG,
                        "Backdrop blur draw failed (receiverSize=$receiverSize, " +
                                "downsampledSize=$downsampledSize, factor=$factor)",
                        e
                    )
                }
            }

            // Stage 4: Translucent theme backdrop gradient tint.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = (tint.alpha * 0.80f).coerceIn(0f, 1f)),
                        tint.copy(alpha = (tint.alpha * 0.98f).coerceIn(0f, 1f))
                    )
                ),
                size = size
            )

            // Stage 5: Optional frosted top border line.
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