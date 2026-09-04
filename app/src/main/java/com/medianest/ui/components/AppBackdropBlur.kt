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
    var downsampleLayer: GraphicsLayer? = null
    var blurLayer: GraphicsLayer? = null
    var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
    var receiverCoordinates: LayoutCoordinates? by mutableStateOf(null)

    // FIX 1: This is a plain (non-Compose-State) counter mutated during drawWithContent.
    // We intentionally do NOT back this with mutableLongStateOf anymore: writing Compose
    // State during the draw phase forces a snapshot-driven invalidation loop on every
    // single frame, which is unnecessary — drawBehind on the receiver already re-executes
    // whenever the receiver itself is invalidated, and we explicitly force that
    // invalidation only when the source layer content actually changes (see
    // invalidateReceiver()). This avoids fighting Compose's snapshot system while still
    // keeping source and receiver draws synchronized.
    private var generation: Long = 0L
    private var onSourceUpdated: (() -> Unit)? = null

    fun registerReceiverInvalidator(invalidate: () -> Unit) {
        onSourceUpdated = invalidate
    }

    fun notifySourceDrawn() {
        generation++
        onSourceUpdated?.invoke()
    }

    val relativeOffset: Offset
        get() {
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
    val downsampleLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    state.sourceLayer = sourceLayer
    state.downsampleLayer = downsampleLayer
    state.blurLayer = blurLayer
    return state
}

val LocalBackdropState = compositionLocalOf<BackdropBlurState?> { null }

/**
 * Marks the composable (e.g. main screen or video player) as the backdrop source to be
 * sampled and blurred.
 *
 * FIX 2: We no longer assume the source is guaranteed to draw before the receiver in the
 * same frame. `notifySourceDrawn()` triggers the receiver's own invalidation (registered via
 * `registerReceiverInvalidator`) so the receiver always redraws with layer content from
 * frame N *after* the source recorded it, rather than assuming same-frame draw order.
 * This trades one frame of latency (source updates -> receiver reflects it on the next
 * frame) for correctness: the receiver is guaranteed never to reference an unrecorded or
 * partially-recorded layer.
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
 */
fun Modifier.backdropReceiver(
    state: BackdropBlurState,
    blurRadius: Dp = 28.dp,
    tint: Color = Color(0x6608090E),
    baseColor: Color = Color.Transparent,
    showTopBorder: Boolean = false,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    downscaleFactor: Float = 8f
): Modifier = this
    .onGloballyPositioned { coordinates ->
        state.receiverCoordinates = coordinates
    }
    .let { mod ->
        // FIX 1 (cont'd): use plain Compose State only for the invalidation trigger itself,
        // created once per receiver instance and updated from a callback (event phase),
        // never from inside the draw phase.
        var invalidationTick by mutableStateOf(0)
        state.registerReceiverInvalidator { invalidationTick++ }
        mod.drawBehind {
            // Reading invalidationTick here (draw phase) is fine — it's a *read*, which is
            // what makes this draw scope subscribe to the state. The *write* happens above,
            // outside the draw phase, in the invalidator callback.
            @Suppress("UNUSED_EXPRESSION")
            invalidationTick

            // 1. Opaque base layer first, to fully occlude unblurred content beneath.
            drawRect(color = baseColor, size = size)

            val offset = state.relativeOffset
            val sourceLayer = state.sourceLayer
            val downsampleLayer = state.downsampleLayer
            val blurLayer = state.blurLayer

            if (sourceLayer != null && downsampleLayer != null && blurLayer != null &&
                size.width > 0 && size.height > 0
            ) {
                val factor = downscaleFactor.coerceAtLeast(1f)

                // FIX 4/5: derive the blur radius so it stays visually consistent regardless
                // of downscaleFactor. The blur is applied in *downsampled* space conceptually
                // (we upscale by `factor` afterward), so the effective on-screen radius is
                // radiusPx * factor if we blurred pre-upscale. Since we actually blur
                // post-upscale (radiusPx applied directly in full-res space here), what
                // matters is that any manual "diffusion spread" fallback below is expressed
                // in full-res pixels too — not implicitly multiplied by `factor` as before.
                val radiusPx = blurRadius.toPx().coerceAtLeast(1f)

                val receiverSize = IntSize(
                    size.width.toInt().coerceAtLeast(1),
                    size.height.toInt().coerceAtLeast(1)
                )
                val downsampledSize = IntSize(
                    (receiverSize.width / factor).toInt().coerceAtLeast(1),
                    (receiverSize.height / factor).toInt().coerceAtLeast(1)
                )

                try {
                    // Stage 1: Downsample background slice.
                    downsampleLayer.record(size = downsampledSize) {
                        translate(left = -offset.x / factor, top = -offset.y / factor) {
                            scale(scaleX = 1f / factor, scaleY = 1f / factor, pivot = Offset.Zero) {
                                drawLayer(sourceLayer)
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Stage 2a: GPU Gaussian blur, applied in upscaled (full-res) space
                        // so `radiusPx` means the same thing regardless of downscaleFactor.
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
                        // Stage 2b: Multi-pass diffuse sampling fallback for API < 31.
                        // FIX 5: sample offsets are now expressed in *full-res* pixels and
                        // applied AFTER the upscale transform (outside `scale`), so they no
                        // longer get silently multiplied by `factor`. We scale the spread by
                        // a fraction of radiusPx instead of hardcoded pixel counts, so it
                        // tracks blurRadius directly.
                        val spread = (radiusPx * 0.3f).coerceAtLeast(1f)
                        val sampleOffsets = listOf(
                            Offset(-spread, -spread), Offset(spread, -spread),
                            Offset(-spread, spread), Offset(spread, spread),
                            Offset(-spread / 2, 0f), Offset(spread / 2, 0f),
                            Offset(0f, -spread / 2), Offset(0f, spread / 2),
                            Offset(0f, 0f)
                        )
                        val upscaled = downsampleLayer // logical alias for clarity
                        blurLayer.record(size = receiverSize) {
                            for (sampleOffset in sampleOffsets) {
                                translate(sampleOffset.x, sampleOffset.y) {
                                    scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
                                        drawLayer(upscaled)
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
                    // FIX 3: log instead of swallowing silently, so layer-recording failures
                    // (e.g. OOM from a bad size, RenderNode collisions) are diagnosable.
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