package com.medianest.ui.image.hybrid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.medianest.ui.components.GlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Robust Hybrid Image Viewer that switches between standard, tiled, and SVG renderers.
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
    val metadata by produceState<ImageMetadata?>(initialValue = null, source) {
        value = withContext(Dispatchers.IO) {
            ImageMetadata.extract(context, source, config)
        }
    }
    
    val zoomController = rememberZoomController(config)
    val scope = rememberCoroutineScope()

    LaunchedEffect(metadata) {
        metadata?.let {
            zoomController.updateContentSize(Size(it.width.toFloat(), it.height.toFloat()))
            zoomController.setInitialGiantState(it.isGiantImage)
        }
    }

    // Notify parent about zoom state for disabling pager
    LaunchedEffect(zoomController.viewport.scale) {
        onZoomChanged(zoomController.viewport.scale > 1.01f)
        if (zoomController.viewport.scale > 1.01f) {
            onInteraction()
        }
    }

    // Determine which renderer implementation to use
    val mainRenderer = remember(metadata) {
        val meta = metadata
        when {
            meta == null -> StandardBitmapRenderer { zoomController.updateContentSize(it) } // Fallback to standard while loading
            meta.isSvg -> SvgRenderer { zoomController.updateContentSize(it) }
            else -> StandardBitmapRenderer { zoomController.updateContentSize(it) }
        }
    }

    // Tiled renderer is only instantiated if needed (not a giant image by default or zoom handoff reached)
    val tiledRenderer = remember(source) { TiledImageRenderer() }

    DisposableEffect(source) {
        onDispose {
            mainRenderer.release()
            tiledRenderer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onSizeChanged { zoomController.updateContainerSize(it.toSize()) }
    ) {
        // 1. Image Renderers (Bottom Layer)
        mainRenderer.Render(
            source = source,
            viewport = zoomController.viewport,
            config = config,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize()
        )

        if (zoomController.useTiledRenderer && metadata?.isSvg == false) {
            val showTiled = remember { mutableStateOf(false) }
            AnimatedVisibility(
                visible = showTiled.value,
                enter = fadeIn(tween(config.handoffCrossfadeDurationMs.toInt())),
                exit = fadeOut(tween(config.handoffCrossfadeDurationMs.toInt()))
            ) {
                tiledRenderer.Render(
                    source = source,
                    viewport = zoomController.viewport,
                    config = config,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LaunchedEffect(zoomController.useTiledRenderer) {
                if (zoomController.useTiledRenderer) {
                    delay(150)
                    showTiled.value = true
                } else {
                    showTiled.value = false
                }
            }
        }

        // 2. Gesture Overlay (Middle Layer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(source) {
                    awaitPointerEventScope {
                        var isPinching = false
                        var swipedUp = false
                        var dragStarted = false
                        var totalDragOffset = Offset.Zero
                        val panSlop = 10f

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            
                            if (event.type == PointerEventType.Release) {
                                if (changes.all { !it.pressed }) {
                                    isPinching = false
                                    swipedUp = false
                                    dragStarted = false
                                    totalDragOffset = Offset.Zero
                                }
                            }

                            if (event.type == PointerEventType.Move) {
                                if (changes.size > 1) {
                                    isPinching = true
                                    dragStarted = true
                                    val zoom = event.calculateZoom()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    scope.launch { zoomController.handleZoom(zoom, centroid) }
                                    changes.forEach { it.consume() }
                                    onInteraction()
                                } else if (changes.size == 1 && !isPinching && !swipedUp) {
                                    val change = changes[0]
                                    val dragAmount = change.position - change.previousPosition
                                    totalDragOffset += dragAmount
                                    
                                    if (zoomController.viewport.scale > 1.01f) {
                                        if (!dragStarted && totalDragOffset.getDistance() > panSlop) {
                                            dragStarted = true
                                        }
                                        if (dragStarted) {
                                            scope.launch { zoomController.handlePan(dragAmount) }
                                            change.consume()
                                            onInteraction()
                                        }
                                    } else {
                                        if (totalDragOffset.y < -15f && kotlin.math.abs(totalDragOffset.y) > kotlin.math.abs(totalDragOffset.x) * 2f) {
                                            onSwipeUpForInfo()
                                            swipedUp = true
                                            dragStarted = true
                                            change.consume()
                                        }
                                        if (swipedUp) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                            
                            if (event.type == PointerEventType.Press) {
                                if (changes.size == 1) {
                                    dragStarted = false
                                    totalDragOffset = Offset.Zero
                                }
                            }
                        }
                    }
                }
                .pointerInput(source) {
                    detectTapGestures(
                        onDoubleTap = { centroid -> 
                            onInteraction()
                            scope.launch { zoomController.handleDoubleTap(centroid) } 
                        },
                        onTap = { onToggleControls() }
                    )
                }
        )

        // 3. Zoom Controls (Top Layer)
        val animatedBottomPadding by androidx.compose.animation.core.animateDpAsState(
            targetValue = zoomControlsBottomPadding,
            animationSpec = tween(300),
            label = "ZoomPillPadding"
        )

        AnimatedVisibility(
            visible = zoomController.viewport.scale > 1.01f,
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
                    IconButton(
                        onClick = {
                            scope.launch {
                                zoomController.setScale(zoomController.viewport.scale - 1f)
                                onInteraction()
                            }
                        },
                        modifier = Modifier.size(24.dp) // Shrink hit area
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove, 
                            contentDescription = "Zoom Out", 
                            tint = Color.White, 
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "${(zoomController.viewport.scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch {
                                    zoomController.setScale(1f)
                                    onInteraction()
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp) // Shrink padding
                    )

                    IconButton(
                        onClick = {
                            scope.launch {
                                zoomController.setScale(zoomController.viewport.scale + 1f)
                                onInteraction()
                            }
                        },
                        modifier = Modifier.size(24.dp) // Shrink hit area
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add, 
                            contentDescription = "Zoom In", 
                            tint = Color.White, 
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
