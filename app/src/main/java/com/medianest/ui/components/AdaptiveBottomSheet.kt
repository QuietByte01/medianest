package com.medianest.ui.components

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Adaptive bottom sheet component that renders as a translucent ambient glass bottom sheet on phones
 * and as a floating ambient glass dialog on tablets.
 *
 * Supports dynamic ambient background system (64dp blur thumbnail layer, extracted hue radial glows,
 * dark obsidian gradient overlay) and edge-to-edge transparent system navigation bar handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor: Color = Color.Unspecified,   // Unspecified → auto dark/light
    contentColor: Color = Color.White,
    backgroundImage: Any? = null,
    backgroundImageAlpha: Float = 1f,
    enableBlur: Boolean = true,
    hue: Float? = null,
    isSolidGlossy: Boolean = false,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Obsidian for dark, frosted white for light
    val resolvedColor = when {
        containerColor != Color.Unspecified -> containerColor
        isDark -> Color(0xCC08090E)
        else   -> Color(0xBFFFFFFF)
    }

    if (isTablet) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val dialogView = LocalView.current
            DisposableEffect(dialogView, isDark) {
                val window = (dialogView.parent as? DialogWindowProvider)?.window
                    ?: (dialogView.context as? Activity)?.window
                window?.let { w ->
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    w.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        w.isNavigationBarContrastEnforced = false
                    }
                    WindowInsetsControllerCompat(w, w.decorView).let { controller ->
                        controller.isAppearanceLightNavigationBars = !isDark
                    }
                }
                onDispose {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSolidGlossy) {
                    SolidGlossySurface(
                        modifier = modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = false
                            ) {}
                            .fillMaxWidth(0.70f),
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = if (containerColor != Color.Unspecified) containerColor else Color.Unspecified,
                        borderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f),
                        borderWidth = 1.dp,
                        drawBottomBorder = false,
                        showTopSheen = false
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            content()
                        }
                    }
                } else if (backgroundImage != null) {
                    SheetAmbientSurface(
                        modifier = modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = false
                            ) {}
                            .fillMaxWidth(0.70f),
                        shape = RoundedCornerShape(24.dp),
                        isDark = isDark,
                        backgroundImage = backgroundImage,
                        hue = hue,
                        containerColor = containerColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            content()
                        }
                    }
                } else {
                    GlassSurface(
                        modifier = modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = false
                            ) {}
                            .fillMaxWidth(0.70f),
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = resolvedColor,
                        borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
                        backgroundImage = backgroundImage,
                        backgroundImageAlpha = backgroundImageAlpha,
                        enableBlur = enableBlur
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            val sheetView = LocalView.current
            DisposableEffect(sheetView, isDark) {
                val window = (sheetView.parent as? DialogWindowProvider)?.window
                    ?: (sheetView.context as? Activity)?.window
                window?.let { w ->
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    w.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        w.isNavigationBarContrastEnforced = false
                    }
                    WindowInsetsControllerCompat(w, w.decorView).let { controller ->
                        controller.isAppearanceLightNavigationBars = !isDark
                    }
                }
                onDispose {}
            }

            if (isSolidGlossy) {
                SolidGlossySurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    backgroundColor = if (containerColor != Color.Unspecified) containerColor else Color.Unspecified,
                    borderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f),
                    borderWidth = 1.dp,
                    drawBottomBorder = false,
                    showTopSheen = false
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        if (dragHandle != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                dragHandle()
                            }
                        }
                        content()
                    }
                }
            } else if (backgroundImage != null) {
                SheetAmbientSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    isDark = isDark,
                    backgroundImage = backgroundImage,
                    hue = hue,
                    containerColor = containerColor
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        if (dragHandle != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                dragHandle()
                            }
                        }
                        content()
                    }
                }
            } else {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    backgroundColor = resolvedColor,
                    backgroundImage = backgroundImage,
                    backgroundImageAlpha = backgroundImageAlpha,
                    enableBlur = enableBlur
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        if (dragHandle != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                dragHandle()
                            }
                        }
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Rich ambient background container for bottom sheets matching the Library ambient background system.
 * Features a 64dp blurred thumbnail layer with darker tint (alpha = 0.22f) so bright thumbnails never overpower the UI,
 * dynamic hue-based top-left and bottom-right radial glow orbs, and a dark obsidian glass overlay.
 */
@Composable
private fun SheetAmbientSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    isDark: Boolean,
    backgroundImage: Any?,
    hue: Float?,
    containerColor: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var activeHue by remember { mutableStateOf(hue) }

    LaunchedEffect(backgroundImage, hue) {
        if (hue != null) {
            activeHue = hue
        } else if (backgroundImage is Uri) {
            activeHue = extractBaseHueFromArt(context, backgroundImage)
        } else {
            activeHue = null
        }
    }

    val ambientTopColor = remember(activeHue, isDark) {
        if (activeHue != null) Color.hsv(activeHue!!, 0.65f, 0.40f, 0.35f)
        else if (isDark) Color(0x354A3B2C)
        else Color(0x35E2E8F0)
    }

    val ambientBottomColor = remember(activeHue, isDark) {
        if (activeHue != null) Color.hsv((activeHue!! + 25f) % 360f, 0.55f, 0.28f, 0.30f)
        else if (isDark) Color(0x301E2838)
        else Color(0x30CBD5E1)
    }

    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .background(if (isDark) Color(0xFF0C0E14) else Color(0xFFF8FAFC))
    ) {
        // 1. Dynamic Blurred Background Thumbnail/Art (matching LibraryAmbientBackground)
        if (backgroundImage != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backgroundImage)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(64.dp)
                    .graphicsLayer { alpha = 0.32f }
            )
        }

        // 2. Ambient Radial Glow Orbs (Top-Left & Bottom-Right)
        Box(
            modifier = Modifier
                .size(450.dp)
                .offset(x = (-120).dp, y = (-100).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientTopColor,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(480.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 120.dp, y = 120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientBottomColor,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 3. Subtle glass sheen (ensures glowing ambient colors remain vibrant like the library)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0x18FFFFFF), Color(0x35000000))
                        } else {
                            listOf(Color(0x30FFFFFF), Color(0x10000000))
                        }
                    ),
                    shape = shape
                )
        )

        content()
    }
}
