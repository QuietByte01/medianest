package com.medianest.ui.quickview

import android.app.WallpaperManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.medianest.data.model.MediaItem
import com.medianest.data.db.MediaType
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.image.hybrid.HybridImageViewer
import com.medianest.ui.image.hybrid.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickViewScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int = 0,
    isLoading: Boolean = false,
    onClose: () -> Unit,
    onOpenFullPlayer: (MediaItem) -> Unit = {},
    onDelete: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mutableMediaList by remember(mediaList) { mutableStateOf(mediaList) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var viewerBgColor by remember { mutableStateOf<Color?>(null) }

    var showControls by remember { mutableStateOf(true) }
    var controlsTimerKey by remember { mutableIntStateOf(0) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    var showPictureModeDialog by remember { mutableStateOf(false) }

    // Auto-hide controls timer
    LaunchedEffect(showControls, controlsTimerKey, showInfoBottomSheet, showOverflowMenu, showBgColorPicker, showPictureModeDialog, showDeleteDialog, showWallpaperDialog) {
        if (showControls && !showInfoBottomSheet && !showOverflowMenu && !showBgColorPicker && !showPictureModeDialog && !showDeleteDialog && !showWallpaperDialog) {
            delay(5000)
            showControls = false
        }
    }

    fun resetControlsTimer() {
        showControls = true
        controlsTimerKey++
    }

    fun toggleControls() {
        if (showControls) {
            showControls = false
        } else {
            resetControlsTimer()
        }
    }

    var pictureMode by remember { mutableStateOf("OFF") }
    var pictureModeEnabled by remember { mutableStateOf(false) }

    val app = com.medianest.MediaNestApp.instance
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            app.settingsManager.pictureMode.collect { mode ->
                pictureMode = mode
                pictureModeEnabled = mode != "OFF"
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mutableMediaList.size - 1).coerceAtLeast(0)),
        pageCount = { mutableMediaList.size }
    )
    val filmstripListState = rememberLazyListState()

    var isInitialScrollDone by remember { mutableStateOf(false) }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    // Scroll pager & filmstrip to initial index on initial load
    LaunchedEffect(mutableMediaList, initialIndex) {
        if (mutableMediaList.isNotEmpty() && !isInitialScrollDone) {
            val targetPage = initialIndex.coerceIn(0, mutableMediaList.size - 1)
            pagerState.scrollToPage(targetPage)
            filmstripListState.scrollToItem(targetPage)
            isInitialScrollDone = true
        }
    }

    val currentItem = if (mutableMediaList.isNotEmpty() && pagerState.currentPage in mutableMediaList.indices) {
        mutableMediaList[pagerState.currentPage]
    } else null

    // Scroll filmstrip when page changes
    LaunchedEffect(pagerState.currentPage) {
        isCurrentPageZoomed = false
        if (mutableMediaList.isNotEmpty() && pagerState.currentPage in mutableMediaList.indices) {
            filmstripListState.animateScrollToItem(pagerState.currentPage)
        }
    }

    var imageHue by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(currentItem, mutableMediaList) {
        val targetItem = currentItem ?: mutableMediaList.firstOrNull()
        if (targetItem != null) {
            val uri = targetItem.albumArtUri ?: targetItem.uri
            imageHue = com.medianest.ui.components.extractBaseHueFromArt(context, uri)
        } else {
            imageHue = null
        }
    }

    val imageBgBrush = remember(imageHue) {
        val hue = imageHue
        if (hue != null) {
            val topColor = Color.hsv(hue, 0.55f, 0.22f)
            val midColor = Color.hsv((hue + 15f) % 360f, 0.42f, 0.14f)
            val bottomColor = Color(0xFF0D0F12)
            Brush.verticalGradient(colors = listOf(topColor, midColor, bottomColor))
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF222834),
                    Color(0xFF141720),
                    Color(0xFF0C0E12)
                )
            )
        }
    }

    var currentDismissProgress by remember { mutableFloatStateOf(0f) }
    val effectiveBgAlpha = (1f - currentDismissProgress).coerceIn(0f, 1f)

    val effectiveBgColor = viewerBgColor ?: Color.Black

    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (viewerBgColor == Color.Transparent) {
                    imageBgBrush
                } else {
                    androidx.compose.ui.graphics.SolidColor(effectiveBgColor.copy(alpha = effectiveBgAlpha))
                }
            )
    ) {
        if (mutableMediaList.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isCurrentPageZoomed
            ) { page ->
                val item = mutableMediaList[page]
                when (item.type) {
                    MediaType.IMAGE -> {
                        val colorFilter = remember(pictureModeEnabled, pictureMode) {
                            if (pictureModeEnabled) {
                                val effect = try { com.medianest.ui.components.media.MediaEffect.fromString(pictureMode) } catch(e:Exception) { com.medianest.ui.components.media.MediaEffect.OFF }
                                com.medianest.player.fx.MediaFxPipeline.getComposeColorFilter(effect)
                            } else null
                        }
                        val zoomPadding = if (showControls) 150.dp else 24.dp
                        
                        HybridImageViewer(
                            source = ImageSource.from(item.uri),
                            colorFilter = colorFilter,
                            backgroundColor = Color.Transparent,
                            zoomControlsBottomPadding = zoomPadding,
                            onDismiss = { onClose() },
                            onInteraction = { resetControlsTimer() },
                            onZoomChanged = { zoomed ->
                                if (pagerState.currentPage == page) {
                                    isCurrentPageZoomed = zoomed
                                    if (zoomed) {
                                        showControls = false
                                    }
                                }
                            },
                            onDismissProgress = { progress ->
                                if (pagerState.currentPage == page) {
                                    currentDismissProgress = progress
                                }
                            },
                            onToggleControls = { toggleControls() },
                            onSwipeUpForInfo = { showInfoBottomSheet = true }
                        )
                    }

                    MediaType.VIDEO -> QuickVideoPreview(item = item, onOpenFullPlayer = { onOpenFullPlayer(item) })
                    MediaType.AUDIO -> QuickAudioPreview(
                        item = item,
                        mediaList = mutableMediaList,
                        currentIndex = page,
                        onIndexChange = { newIdx ->
                            scope.launch { pagerState.animateScrollToPage(newIdx) }
                        },
                        onOpenFullPlayer = { onOpenFullPlayer(item) }
                    )
                }
            }
        }

        // Overlay Controls (Top App Bar & Bottom Bar)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colorOptions = listOf(
                            Color.Transparent, // Dynamic Frosted
                            Color.Black,
                            Color.White,
                            Color(0xFF1A1C1E), // Dark Gray
                            Color(0xFF2D2D2D), // Medium Gray
                            Color(0xFFE0E0E0), // Light Gray
                            Color(0xFFFDF6E3), // Cream
                            Color(0xFF0D1117), // Deep Navy
                            Color(0xFF1E1E1E), // Slate
                            Color(0xFF2C3E50), // Midnight Blue
                            Color(0xFF34495E), // Wet Asphalt
                            Color(0xFF7F8C8D), // Asbestos Gray
                            Color(0xFFE67E22), // Pumpkin
                            Color(0xFF27AE60)  // Emerald
                        )

                        // Color Indicator & Selection Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Circular preview indicator of current background color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { 
                                        resetControlsTimer()
                                        showBgColorPicker = !showBgColorPicker 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val currentSelectedColor = viewerBgColor ?: Color.Black
                                val isDynamic = viewerBgColor == Color.Transparent
                                
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (isDynamic) {
                                                Modifier.background(
                                                    Brush.sweepGradient(
                                                        colors = listOf(
                                                            Color.Cyan,
                                                            Color.Magenta,
                                                            Color.Yellow,
                                                            Color.Cyan
                                                        )
                                                    )
                                                ).border(1.dp, Color.White, CircleShape)
                                            } else {
                                                Modifier.background(currentSelectedColor).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                            }
                                        )
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(visible = showBgColorPicker) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .widthIn(max = 240.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    colorOptions.forEach { color ->
                                        val isTransparent = color == Color.Transparent
                                        val isSelected = viewerBgColor == color || (viewerBgColor == null && color == Color.Black)
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    if (isTransparent) {
                                                        Modifier.background(
                                                            Brush.sweepGradient(
                                                                colors = listOf(
                                                                    Color.Cyan.copy(alpha = 0.6f),
                                                                    Color.Magenta.copy(alpha = 0.6f),
                                                                    Color.Yellow.copy(alpha = 0.6f),
                                                                    Color.Cyan.copy(alpha = 0.6f)
                                                                )
                                                            )
                                                        ).border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
                                                    } else {
                                                        Modifier.background(color).border(if (isSelected) 2.dp else 0.dp, Color.White, CircleShape)
                                                    }
                                                )
                                                .clickable {
                                                    resetControlsTimer()
                                                    viewerBgColor = color
                                                    showBgColorPicker = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isTransparent) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Overflow Options Menu
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { 
                                        resetControlsTimer()
                                        showOverflowMenu = true 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color.White
                                )
                            }

                            GlassDropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                backgroundImage = currentItem?.albumArtUri ?: currentItem?.uri,
                                hue = imageHue
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    if (currentItem?.type == MediaType.IMAGE) {
                                        DropdownMenuItem(
                                            text = { Text("Picture Mode (${pictureMode})", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showPictureModeDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Set as wallpaper", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showWallpaperDialog = true
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Open with", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showOverflowMenu = false
                                            currentItem?.let { item ->
                                                val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(sharingUri, item.mimeType.ifEmpty { "image/*" })
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(openIntent, "Open with"))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Set as...", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showOverflowMenu = false
                                            currentItem?.let { item ->
                                                val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                                                val setAsIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                                    setDataAndType(sharingUri, item.mimeType.ifEmpty { "image/*" })
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(setAsIntent, "Set as"))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showOverflowMenu = false
                                            currentItem?.let { item ->
                                                val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    putExtra(Intent.EXTRA_STREAM, sharingUri)
                                                    type = item.mimeType.ifEmpty { "image/*" }
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Filmstrip & Info Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    // Action Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            resetControlsTimer()
                            showInfoBottomSheet = true
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }

                        IconButton(onClick = {
                            resetControlsTimer()
                            currentItem?.let { item ->
                                val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, sharingUri)
                                    type = item.mimeType.ifEmpty { "image/*" }
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }

                        IconButton(onClick = {
                            resetControlsTimer()
                            showDeleteDialog = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }

                    // Filmstrip thumbnail scroll
                    if (mutableMediaList.size > 1) {
                        LazyRow(
                            state = filmstripListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            itemsIndexed(mutableMediaList) { index, item ->
                                val isSelected = index == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            resetControlsTimer()
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                ) {
                                    AsyncImage(
                                        model = item.albumArtUri ?: item.uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
    if (showWallpaperDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("Set as wallpaper", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose where to set this image:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    fun applyWallpaper(flag: Int, label: String) {
                        scope.launch {
                            try {
                                val wallpaperManager = WallpaperManager.getInstance(context)
                                val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, currentItem.uri)
                                withContext(Dispatchers.IO) {
                                    val bitmap = try {
                                        android.graphics.BitmapFactory.decodeStream(
                                            context.contentResolver.openInputStream(sharingUri)
                                                ?: context.contentResolver.openInputStream(currentItem.uri)
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }

                                    if (bitmap != null) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                            wallpaperManager.setBitmap(bitmap, null, true, flag)
                                        } else {
                                            wallpaperManager.setBitmap(bitmap)
                                        }
                                    } else {
                                        val stream = context.contentResolver.openInputStream(sharingUri)
                                            ?: context.contentResolver.openInputStream(currentItem.uri)
                                        stream?.use { inputStream ->
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                                wallpaperManager.setStream(inputStream, null, true, flag)
                                            } else {
                                                wallpaperManager.setStream(inputStream)
                                            }
                                        }
                                    }
                                }
                                Toast.makeText(context, "$label updated successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Failed to set wallpaper: ${e.localizedMessage ?: "Access denied"}", Toast.LENGTH_LONG).show()
                            }
                            showWallpaperDialog = false
                        }
                    }

                    ListItem(
                        headlineContent = { Text("Home Screen", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Home, contentDescription = null, tint = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val flag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    WallpaperManager.FLAG_SYSTEM
                                } else 0
                                applyWallpaper(flag, "Home Screen wallpaper")
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Lock Screen", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val flag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    WallpaperManager.FLAG_LOCK
                                } else 0
                                applyWallpaper(flag, "Lock Screen wallpaper")
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Both (Home & Lock Screen)", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val flag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                } else 0
                                applyWallpaper(flag, "Wallpaper")
                            }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (showInfoBottomSheet && currentItem != null) {
        MediaInfoBottomSheet(
            item = currentItem,
            onDismiss = { showInfoBottomSheet = false }
        )
    }

    if (showPictureModeDialog) {
        val modes = listOf("OFF", "VIBRANT", "NATURAL", "AMOLED", "CINEMATIC", "WARM", "COOL")
        AlertDialog(
            onDismissRequest = { showPictureModeDialog = false },
            title = { Text("Picture Mode", color = Color.White) },
            text = {
                Column {
                    modes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pictureMode = mode
                                    pictureModeEnabled = mode != "OFF"
                                    coroutineScope.launch {
                                        app.settingsManager.setPictureMode(mode)
                                    }
                                    showPictureModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pictureMode == mode,
                                onClick = {
                                    pictureMode = mode
                                    pictureModeEnabled = mode != "OFF"
                                    coroutineScope.launch {
                                        app.settingsManager.setPictureMode(mode)
                                    }
                                    showPictureModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPictureModeDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (showDeleteDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Media", color = Color.White) },
            text = { Text("Are you sure you want to delete this media item?", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = currentItem
                        onDelete(toDelete)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    }
}
@Composable
fun QuickVideoPreview(item: MediaItem, onOpenFullPlayer: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onOpenFullPlayer),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun QuickAudioPreview(
    item: MediaItem,
    mediaList: List<MediaItem>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onOpenFullPlayer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (item.albumArtUri != null) {
                    AsyncImage(
                        model = item.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = item.artist ?: "Unknown Artist",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
