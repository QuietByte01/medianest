package com.medianest.ui.components.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.BackdropGlassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reusable Slideshow Viewer component for Android featuring:
 * - Glitch-free slide transitions with HorizontalPager and clipped bounds
 * - Minimal clean layout without top header bar clutter
 * - Tap side areas (left 25% / right 25%) for instant prev/next slide navigation
 * - Tap center area (middle 50%) to show/hide minimal play/pause overlay and filmstrip
 * - Zoom & slide enabled when paused (auto-pauses on pinch/double-tap zoom)
 * - Sleek bottom filmstrip and blurred glass speed panel (1s, 2s, 3s, 5s, 10s), loop, and shuffle
 */
@Composable
fun <T> SlideshowViewer(
    items: List<T>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    getItemModel: (T) -> Any? = { item ->
        when (item) {
            is MediaItem -> item.uri
            else -> item
        }
    },
    getItemTitle: ((T) -> String)? = { item ->
        when (item) {
            is MediaItem -> item.title
            else -> ""
        }
    },
    backgroundColor: Color = Color.Black
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size }
    )
    val filmstripListState = rememberLazyListState()

    var isPlaying by remember { mutableStateOf(true) }
    var intervalSeconds by remember { mutableIntStateOf(3) }
    var isLooping by remember { mutableStateOf(true) }
    var isShuffle by remember { mutableStateOf(false) }
    var showOverlayControls by remember { mutableStateOf(false) }
    var showSpeedPanel by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = zoomScale > 1.05f

    // Intercept back press to exit slideshow
    BackHandler {
        onDismiss()
    }

    // Scroll filmstrip to center active thumbnail on page change
    LaunchedEffect(pagerState.currentPage) {
        if (items.isNotEmpty()) {
            filmstripListState.animateScrollToItem(pagerState.currentPage)
        }
    }

    // Automatically pause slideshow when zoomed in
    LaunchedEffect(isZoomed) {
        if (isZoomed && isPlaying) {
            isPlaying = false
        }
    }

    // Timer Progress Bar Animation
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(pagerState.currentPage, isPlaying, intervalSeconds, isZoomed) {
        if (isPlaying && !isZoomed && items.size > 1) {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = intervalSeconds * 1000, easing = LinearEasing)
            )
        } else {
            progressAnim.snapTo(0f)
        }
    }

    // Auto-advance Page Switcher (guaranteed clean animation completion)
    LaunchedEffect(pagerState.currentPage, isPlaying, intervalSeconds, isShuffle, isLooping, isZoomed) {
        if (isPlaying && !isZoomed && items.size > 1) {
            delay(intervalSeconds * 1000L)
            if (isPlaying && !isZoomed && !pagerState.isScrollInProgress) {
                val currentPage = pagerState.currentPage
                val nextPage = if (isShuffle) {
                    var rand = (0 until items.size).random()
                    while (rand == currentPage && items.size > 1) {
                        rand = (0 until items.size).random()
                    }
                    rand
                } else {
                    if (currentPage + 1 < items.size) {
                        currentPage + 1
                    } else if (isLooping) {
                        0
                    } else {
                        isPlaying = false
                        null
                    }
                }

                if (nextPage != null) {
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. Horizontal Image Pager Layer
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            pageSpacing = 0.dp,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageItem = items[pageIndex]
            val pageModel = getItemModel(pageItem)
            val pageTitle = getItemTitle?.invoke(pageItem) ?: ""

            var localScale by remember { mutableFloatStateOf(1.0f) }
            var localOffset by remember { mutableStateOf(Offset.Zero) }

            val isPageCurrent = pagerState.currentPage == pageIndex
            if (!isPageCurrent && localScale != 1.0f) {
                localScale = 1.0f
                localOffset = Offset.Zero
            }

            LaunchedEffect(localScale, localOffset, isPageCurrent) {
                if (isPageCurrent) {
                    zoomScale = localScale
                    zoomOffset = localOffset
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(pageIndex) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val containerWidth = size.width
                                val tapX = tapOffset.x

                                if (localScale > 1.05f) {
                                    // If zoomed in, tap resets zoom
                                    localScale = 1.0f
                                    localOffset = Offset.Zero
                                } else {
                                    when {
                                        tapX < containerWidth * 0.25f -> {
                                            // Tap on left 25%: Previous slide
                                            if (!pagerState.isScrollInProgress) {
                                                scope.launch {
                                                    val prevPage = if (pagerState.currentPage > 0) {
                                                        pagerState.currentPage - 1
                                                    } else if (isLooping) {
                                                        items.size - 1
                                                    } else null
                                                    if (prevPage != null) {
                                                        pagerState.animateScrollToPage(prevPage, animationSpec = tween(400, easing = FastOutSlowInEasing))
                                                    }
                                                }
                                            }
                                        }
                                        tapX > containerWidth * 0.75f -> {
                                            // Tap on right 25%: Next slide
                                            if (!pagerState.isScrollInProgress) {
                                                scope.launch {
                                                    val nextPage = if (pagerState.currentPage + 1 < items.size) {
                                                        pagerState.currentPage + 1
                                                    } else if (isLooping) {
                                                        0
                                                    } else null
                                                    if (nextPage != null) {
                                                        pagerState.animateScrollToPage(nextPage, animationSpec = tween(400, easing = FastOutSlowInEasing))
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            // Tap in middle 50%: Show/Hide minimal controls & filmstrip
                                            showOverlayControls = !showOverlayControls
                                        }
                                    }
                                }
                            },
                            onDoubleTap = { tapOffset ->
                                val containerWidth = size.width
                                val tapX = tapOffset.x

                                // Only allow double tap zoom in the center 50% region (ignore left/right 25%)
                                if (tapX >= containerWidth * 0.25f && tapX <= containerWidth * 0.75f) {
                                    if (localScale > 1.05f) {
                                        localScale = 1.0f
                                        localOffset = Offset.Zero
                                    } else {
                                        localScale = 2.5f
                                        localOffset = Offset.Zero
                                        isPlaying = false // Auto pause on zoom
                                        showOverlayControls = true
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(pageIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (localScale * zoom).coerceIn(1.0f, 5.0f)
                            localScale = newScale
                            if (newScale > 1.05f) {
                                localOffset += pan
                            } else {
                                localOffset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(pageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = pageTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = localScale,
                            scaleY = localScale,
                            translationX = localOffset.x,
                            translationY = localOffset.y
                        )
                )
            }
        }

        // 2. Top Controls Overlay: Progress Bar & Back Button
        AnimatedVisibility(
            visible = showOverlayControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isPlaying && !isZoomed) {
                    LinearProgressIndicator(
                        progress = { progressAnim.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter),
                        color = Color(0xFF10B981),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }

                // Floating Minimal Back Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(12.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Slideshow",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 4. Minimal Center Play/Pause Floating Button (Toggled on center tap)
        AnimatedVisibility(
            visible = showOverlayControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier
                    .size(56.dp)
                    .clickable { isPlaying = !isPlaying }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // 5. Bottom Controls Overlay: Filmstrip & Speed Control Pill
        AnimatedVisibility(
            visible = showOverlayControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Filmstrip Thumbnail Bar
                if (items.size > 1) {
                    BackdropGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x66000000),
                        borderColor = Color(0x38FFFFFF),
                        enableBlur = true,
                        blurRadius = 16.dp
                    ) {
                        LazyRow(
                            state = filmstripListState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(items) { index, item ->
                                val isSelected = index == pagerState.currentPage
                                val thumbModel = getItemModel(item)

                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 40.dp else 34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF10B981) else Color.White.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbModel)
                                            .size(120, 120)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Playback Speed Control Pill
                BackdropGlassSurface(
                    modifier = Modifier.clickable { showSpeedPanel = !showSpeedPanel },
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = Color(0x77000000),
                    borderColor = Color(0x38FFFFFF),
                    enableBlur = true,
                    blurRadius = 20.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Speed else Icons.Default.Pause,
                            contentDescription = "Playback Speed",
                            tint = if (isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = if (isZoomed) "Zoomed" else if (!isPlaying) "Paused • ${intervalSeconds}s" else "${intervalSeconds}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )

                        Text(
                            text = "${pagerState.currentPage + 1}/${items.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // 6. Blurred Glass Panel for Speed & Playback Settings
        if (showSpeedPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showSpeedPanel = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                BackdropGlassSurface(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth(0.92f)
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp)
                        .clickable(enabled = false) {}, // Intercept click inside panel
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = Color(0x66000000),
                    borderColor = Color(0x38FFFFFF),
                    enableBlur = true,
                    blurRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "SLIDESHOW SPEED",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }

                            IconButton(
                                onClick = { showSpeedPanel = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Speed Option Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(1, 2, 3, 5, 10).forEach { sec ->
                                val isSelected = intervalSeconds == sec
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFF10B981) else Color.White.copy(alpha = 0.1f),
                                    contentColor = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.clickable {
                                        intervalSeconds = sec
                                    }
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Play/Pause, Loop & Shuffle Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play/Pause Pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isPlaying) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, if (isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isPlaying = !isPlaying }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPlaying) "Pause" else "Play",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    )
                                }
                            }

                            // Loop Toggle Pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isLooping) Color(0xFF10B981).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isLooping) Color(0xFF10B981) else Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isLooping = !isLooping }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = if (isLooping) Color(0xFF10B981) else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Loop",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isLooping) Color(0xFF10B981) else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Shuffle Toggle Pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isShuffle) Color(0xFF10B981).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isShuffle) Color(0xFF10B981) else Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isShuffle = !isShuffle }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = if (isShuffle) Color(0xFF10B981) else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shuffle",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isShuffle) Color(0xFF10B981) else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
