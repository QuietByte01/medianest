package com.medianest.ui.image.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.debug.ImageDebugOverlay

/**
 * Modern, high-performance image gallery viewer featuring:
 * - Telephoto sub-sampled zoomable images (crystal clear up to 100MP+)
 * - Jetpack Compose HorizontalPager for smooth multi-page navigation
 * - Smart gesture delegation (panning within zoomed viewport & swipe across pages at edges)
 * - Interactive Drag-to-Dismiss with smooth alpha & scale transitions
 * - Immersive mode toggle and sleek header overlay
 */
@Composable
fun <T> GalleryViewerScreen(
    items: List<T>,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    colorFilter: ColorFilter? = null,
    getItemModel: (T) -> Any? = { it },
    getThumbnailModel: ((T) -> Any?)? = null,
    topBarContent: (@Composable (currentPage: Int, totalCount: Int) -> Unit)? = null
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, items.size - 1),
        pageCount = { items.size }
    )

    val showDebugOverlay by com.medianest.MediaNestApp.instance.settingsManager.showImageDebugInfo.collectAsState(initial = false)
    var isImmersive by remember { mutableStateOf(false) }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    DragToDismissContainer(
        onDismiss = onDismiss,
        backgroundColor = backgroundColor,
        enabled = !isCurrentPageZoomed, // Only allow drag-to-dismiss when unzoomed
        modifier = modifier
    ) {
        // 1. Horizontal Image Pager Layer
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1, // Preloads adjacent pages for lag-free swiping
            pageSpacing = 16.dp,
            userScrollEnabled = !isCurrentPageZoomed,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val item = items[pageIndex]
            val model = getItemModel(item)
            val source = when (model) {
                is android.net.Uri -> com.medianest.ui.image.hybrid.ImageSource.FromUri(model)
                is java.io.File -> com.medianest.ui.image.hybrid.ImageSource.FromFile(model)
                is ByteArray -> com.medianest.ui.image.hybrid.ImageSource.FromByteArray(model)
                is String -> com.medianest.ui.image.hybrid.ImageSource.FromUri(android.net.Uri.parse(model))
                else -> null
            }
            
            if (source != null) {
                com.medianest.ui.image.hybrid.HybridImageViewer(
                    source = source,
                    colorFilter = colorFilter,
                    backgroundColor = Color.Transparent,
                    onToggleControls = { isImmersive = !isImmersive },
                    onZoomChanged = { zoomed ->
                        if (pagerState.currentPage == pageIndex) {
                            isCurrentPageZoomed = zoomed
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Unsupported format", color = Color.White)
                }
            }
        }

        // 2. Top App Bar & Page Indicator Overlay
        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            if (topBarContent != null) {
                topBarContent(pagerState.currentPage, items.size)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        // Sleek Page Pill: "3 / 24"
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${items.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // Balance spacing
                        Box(modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }

        // IMAGE DEBUG OVERLAY
        if (showDebugOverlay) {
            ImageDebugOverlay(
                item = items.getOrNull(pagerState.currentPage) as? com.medianest.data.model.MediaItem,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .padding(bottom = 60.dp)
            )
        }
    }
}
