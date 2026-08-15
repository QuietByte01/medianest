package com.medianest.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Adaptive bottom sheet component that renders as a translucent glass bottom sheet on phones
 * and as a floating glass dialog on tablets.
 *
 * Dark mode  → Obsidian tint (0xCC08090E) + frosted blur
 * Light mode → Frosted white glass (0xBFFFFFFF)
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
                        borderWidth = 1.dp
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
                        backgroundImage = backgroundImage
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
            content = {
                if (isSolidGlossy) {
                    SolidGlossySurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        backgroundColor = if (containerColor != Color.Unspecified) containerColor else Color.Unspecified,
                        borderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f),
                        borderWidth = 1.dp
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
                        backgroundImage = backgroundImage
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
        )
    }
}
