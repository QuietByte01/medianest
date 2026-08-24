package com.medianest.ui.image.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive container that handles drag-down gestures to dismiss the image viewer.
 * Scales down content and animates background opacity smoothly with spring physics.
 */
@Composable
fun DragToDismissContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val dismissThreshold = 300f // pixels required to trigger dismiss

    val progress = (abs(offsetY.value) / 1000f).coerceIn(0f, 1f)
    val backgroundAlpha = (1f - progress * 1.2f).coerceIn(0f, 1f)
    val scale = (1f - progress * 0.3f).coerceIn(0.7f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor.copy(alpha = backgroundAlpha))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        // Only allow dragging downwards or returning from downward pull
                        if (offsetY.value >= 0 || dragAmount > 0) {
                            change.consume()
                            coroutineScope.launch {
                                offsetY.snapTo(offsetY.value + dragAmount)
                            }
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetY.value > dismissThreshold) {
                                offsetY.animateTo(
                                    targetValue = 2000f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                                onDismiss()
                            } else {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetY.animateTo(0f)
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            content = content
        )
    }
}
