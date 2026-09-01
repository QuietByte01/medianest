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
    val backdropState = LocalBackdropState.current

    // Translucent obsidian for dark, translucent frosted white for light
    val resolvedColor = when {
        containerColor != Color.Unspecified -> containerColor
        isDark -> Color(0x6608090E)
        else   -> Color(0x80FFFFFF)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        w.setBackgroundBlurRadius(80)
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
                            .fillMaxWidth(0.74f),
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
                            .fillMaxWidth(0.74f),
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
                    val tabletMod = modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = false
                        ) {}
                        .fillMaxWidth(0.74f)
                        .clip(RoundedCornerShape(24.dp))
                        .then(
                            if (backdropState != null) {
                                Modifier.backdropReceiver(
                                    state = backdropState,
                                    blurRadius = 32.dp,
                                    tint = resolvedColor,
                                    baseColor = Color.Transparent,
                                    showTopBorder = false
                                )
                            } else Modifier
                        )

                    GlassSurface(
                        modifier = tabletMod,
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = if (backdropState != null) Color.Transparent else resolvedColor,
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
            scrimColor = Color.Black.copy(alpha = 0.35f),
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        w.setBackgroundBlurRadius(80)
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
                val phoneMod = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .then(
                        if (backdropState != null) {
                            Modifier.backdropReceiver(
                                state = backdropState,
                                blurRadius = 32.dp,
                                tint = resolvedColor,
                                baseColor = Color.Transparent,
                                showTopBorder = false
                            )
                        } else Modifier
                    )

                GlassSurface(
                    modifier = phoneMod,
                    shape = shape,
                    backgroundColor = if (backdropState != null) Color.Transparent else resolvedColor,
                    borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
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
 * Delegated to the centralized [AmbientGlassSurface] component.
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
    AmbientGlassSurface(
        modifier = modifier,
        shape = shape,
        backgroundImage = backgroundImage,
        hue = hue,
        borderWidth = 0.5.dp,
        containerColor = containerColor,
        content = content
    )
}
