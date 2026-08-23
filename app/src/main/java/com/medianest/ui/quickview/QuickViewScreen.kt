package com.medianest.ui.quickview

import android.content.Intent
import java.util.Locale
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import coil.compose.AsyncImagePainter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.MediaInfoBottomSheet
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.medianest.ui.image.hybrid.HybridImageViewer
import com.medianest.ui.image.hybrid.ImageSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickViewScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    isLoading: Boolean = false,
    onClose: () -> Unit,
    onOpenFullPlayer: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mutableMediaList by remember(mediaList) { mutableStateOf(mediaList) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var viewerBgColor by remember { mutableStateOf<Color?>(null) }

    var showControls by remember { mutableStateOf(true) }
    var controlsTimerKey by remember { mutableIntStateOf(0) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    // Auto-hide controls timer
    LaunchedEffect(showControls, controlsTimerKey) {
        if (showControls) {
            delay(3500)
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

    val settingsManager = com.medianest.MediaNestApp.instance.settingsManager
    val pictureModeEnabled by settingsManager.pictureModeEnabled.collectAsState(initial = true)
    val pictureMode by settingsManager.pictureMode.collectAsState(initial = "BALANCED")
    val customSat by settingsManager.customSaturation.collectAsState(initial = 1.18f)
    val customCon by settingsManager.customContrast.collectAsState(initial = 1.06f)
    val customWarmth by settingsManager.customWarmth.collectAsState(initial = 0.03f)
    var showPictureModeDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mutableMediaList.size - 1).coerceAtLeast(0))
    ) {
        mutableMediaList.size
    }
    val filmstripListState = rememberLazyListState()

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showPictureModeDialog -> showPictureModeDialog = false
            showDeleteDialog -> showDeleteDialog = false
            showInfoBottomSheet -> showInfoBottomSheet = false
            else -> onClose()
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(imageBgBrush) // Dynamic ambient gradient
            .blur(if (viewerBgColor == Color.Transparent) 40.dp else 0.dp) // Frosted glass effect when transparent
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { 
                // Only toggle if no items are shown or we're in video/audio
                // Image viewer has its own cleaner toggle logic now.
                if (mutableMediaList.isEmpty() || (currentItem != null && currentItem.type != MediaType.IMAGE)) {
                    toggleControls()
                }
            }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.MediaLoadingAnimation(
                    mediaType = currentItem?.type ?: MediaType.IMAGE,
                    iconSize = 52.dp
                )
            }
        } else if (mutableMediaList.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isCurrentPageZoomed
            ) { page ->
                val item = mutableMediaList[page]
                when (item.type) {
                    MediaType.IMAGE -> {
                        val colorFilter = remember(pictureModeEnabled, pictureMode, customSat, customCon, customWarmth) {
                            com.medianest.ui.components.PictureModeUtils.getComposeColorFilter(
                                modeKey = pictureMode,
                                customSat = customSat,
                                customCon = customCon,
                                customWarmth = customWarmth,
                                enabled = pictureModeEnabled
                            )
                        }
                        val zoomPadding = if (showControls) 150.dp else 24.dp
                        
                        HybridImageViewer(
                            source = ImageSource.from(item.uri),
                            colorFilter = colorFilter,
                            backgroundColor = viewerBgColor ?: Color.Black, // Consistent black default
                            zoomControlsBottomPadding = zoomPadding,
                            onInteraction = { resetControlsTimer() },
                            onZoomChanged = { zoomed ->
                                if (pagerState.currentPage == page) {
                                    isCurrentPageZoomed = zoomed
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
                        onClose = onClose,
                        onOpenFullPlayer = { onOpenFullPlayer(item) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No Media Found", color = Color.White)
            }
        }

        // Top Floating Action Buttons (Back on TopStart, Picture Mode on TopEnd)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
                    var showBgColorPicker by remember { mutableStateOf(false) }
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
                        Color(0xFF2C3E50), // Midnight Blue / Charcoal
                        Color(0xFF34495E), // Wet Asphalt
                        Color(0xFF7F8C8D), // Asbestos Gray
                        Color(0xFFE67E22), // Pumpkin
                        Color(0xFF27AE60)  // Emerald
                    )

                    // Inline Expanding Background Color Bar (No Circular Border, Text Label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { showBgColorPicker = !showBgColorPicker }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = "Background", tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Background", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(visible = showBgColorPicker) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .widthIn(max = 280.dp) // Bound the width for scrolling
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                colorOptions.forEach { color ->
                                    val isTransparent = color == Color.Transparent
                                    
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
                                                    ).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                                } else {
                                                    Modifier.background(color)
                                                }
                                            )
                                            .clickable {
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

                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { showOverflowMenu = true },
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
                                if (pictureModeEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("Picture Mode", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showPictureModeDialog = true
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
                                            runCatching {
                                                context.startActivity(openIntent)
                                            }.onFailure {
                                                android.widget.Toast.makeText(context, "No app available to open image", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar with Filmstrip Carousel
        AnimatedVisibility(
            visible = showControls && currentItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Horizontal Filmstrip / Thumbnail Carousel (Centered, transparent background)
                if (mutableMediaList.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LazyRow(
                            state = filmstripListState,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            itemsIndexed(mutableMediaList) { index, item ->
                                val isSelected = index == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 50.dp else 40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            resetControlsTimer()
                                            scope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(item.uri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating Pill Container Bar (Glass translucent capsule bar)
                GlassSurface(
                    shape = CircleShape,
                    backgroundColor = Color.Black.copy(alpha = 0.50f),
                    borderColor = Color.White.copy(alpha = 0.3f),
                    enableBlur = true,
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isFavorite by remember(currentItem?.uri) { mutableStateOf(false) }

                        LaunchedEffect(currentItem?.uri) {
                            val uri = currentItem?.uri?.toString() ?: return@LaunchedEffect
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val db = com.medianest.MediaNestApp.instance.database
                                val dao = db.categoryDao()
                                val categories = dao.getCategoriesByType("IMAGE").first()
                                val fav = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                if (fav != null) {
                                    val uris = dao.getMediaUrisForCategory(fav.id).first()
                                    isFavorite = uris.contains(uri)
                                }
                            }
                        }

                        val actionIconTint = Color.White

                        // 1. Favorite / Heart
                        IconButton(onClick = {
                            resetControlsTimer()
                            isFavorite = !isFavorite
                            val item = currentItem ?: return@IconButton
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val db = com.medianest.MediaNestApp.instance.database
                                val dao = db.categoryDao()
                                val categories = dao.getCategoriesByType("IMAGE").first()
                                var fav = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                val favId = if (fav != null) fav.id else {
                                    dao.insertCategory(com.medianest.data.db.MediaCategory(name = "Favorites", type = "IMAGE"))
                                }
                                if (isFavorite) {
                                    dao.insertCategoryCrossRef(com.medianest.data.db.CategoryMediaCrossRef(categoryId = favId, mediaUri = item.uri.toString()))
                                } else {
                                    dao.removeMediaFromCategory(favId, item.uri.toString())
                                }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isFavorite) "Added to Favorites" else "Removed from Favorites",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFE53935) else actionIconTint
                            )
                        }

                        // 2. Edit / Pencil
                        var showEditDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = {
                            resetControlsTimer()
                            currentItem?.let { item ->
                                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                    setDataAndType(item.uri, item.mimeType.ifEmpty { "image/*" })
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(editIntent, "Edit Image"))
                                }.onFailure {
                                    showEditDialog = true
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Image",
                                tint = actionIconTint
                            )
                        }

                        // 3. Info Details Icon
                        IconButton(onClick = { 
                            resetControlsTimer()
                            showInfoBottomSheet = true 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info Details",
                                tint = actionIconTint
                            )
                        }

                        // 4. Share
                        IconButton(onClick = {
                            resetControlsTimer()
                            currentItem?.let { item ->
                                val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = item.mimeType
                                    putExtra(Intent.EXTRA_STREAM, sharingUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = actionIconTint
                            )
                        }

                        // 5. Delete / Trash
                        IconButton(onClick = { 
                            resetControlsTimer()
                            showDeleteDialog = true 
                        }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = actionIconTint
                            )
                        }

                        if (showEditDialog && currentItem != null) {
                            ImageEditModal(
                                currentItem = currentItem,
                                onDismiss = { showEditDialog = false }
                            )
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog && currentItem != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Delete File?") },
                text = { Text("Are you sure you want to delete '${currentItem.title}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        try {
                            com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, currentItem.uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        val newList = mutableMediaList.filter { it.uri != currentItem.uri }
                        if (newList.isEmpty()) {
                            onClose()
                        } else {
                            mutableMediaList = newList
                        }
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Picture Mode Quick Chooser Dialog
        if (showPictureModeDialog) {
            AlertDialog(
                onDismissRequest = { showPictureModeDialog = false },
                containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Select Picture Mode")
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.medianest.ui.components.PictureMode.entries.forEach { mode ->
                            val isSelected = pictureMode.equals(mode.key, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        scope.launch {
                                            settingsManager.setPictureMode(mode.key)
                                        }
                                        showPictureModeDialog = false
                                    },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = null,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            scope.launch {
                                                settingsManager.setPictureMode(mode.key)
                                            }
                                            showPictureModeDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPictureModeDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Info Bottom Sheet
        if (showInfoBottomSheet && currentItem != null) {
            MediaInfoBottomSheet(
                item = currentItem,
                onDismiss = { showInfoBottomSheet = false },
                onShowFileLocation = { item ->
                    showInfoBottomSheet = false
                    val folderKey = item.relativePath?.trim('/') ?: item.bucketName ?: "Pictures"
                    val targetScreen = when (item.type) {
                        MediaType.AUDIO -> "AUDIO_FOLDER"
                        MediaType.VIDEO -> "VIDEOS_FOLDER"
                        MediaType.IMAGE -> "IMAGES_FOLDER"
                    }
                    val mainIntent = android.content.Intent(context, com.medianest.MainActivity::class.java).apply {
                        putExtra("open_screen", targetScreen)
                        putExtra("folder_name", folderKey)
                        putExtra("target_media_uri", item.uri.toString())
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(mainIntent)
                    onClose()
                }
            )
        }
    }
}

fun getFilePathFromUri(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "file") return uri.path ?: uri.toString()
    var path: String? = null
    try {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                if (index != -1) {
                    path = cursor.getString(index)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return path ?: uri.path ?: uri.toString()
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.getDefault(), "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditModal(
    currentItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var isFlippedHorizontally by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Normal") }

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        backgroundImage = currentItem.uri
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Edit Image", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Live Preview with rotation/flip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = currentItem.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            rotationZ = rotationDegrees,
                            scaleX = if (isFlippedHorizontally) -1f else 1f
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Editing Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { rotationDegrees = (rotationDegrees - 90f) % 360f }) {
                    Text("Rotate -90°")
                }
                OutlinedButton(onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }) {
                    Text("Rotate +90°")
                }
                OutlinedButton(onClick = { isFlippedHorizontally = !isFlippedHorizontally }) {
                    Text("Flip")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = selectedFilter == "Normal",
                    onClick = { selectedFilter = "Normal" },
                    label = { Text("Normal") }
                )
                FilterChip(
                    selected = selectedFilter == "Grayscale",
                    onClick = { selectedFilter = "Grayscale" },
                    label = { Text("B&W") }
                )
                FilterChip(
                    selected = selectedFilter == "Sepia",
                    onClick = { selectedFilter = "Sepia" },
                    label = { Text("Sepia") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    onDismiss()
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(currentItem.uri)
                            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            if (originalBitmap != null) {
                                val matrix = android.graphics.Matrix()
                                if (rotationDegrees != 0f) matrix.postRotate(rotationDegrees)
                                if (isFlippedHorizontally) matrix.postScale(-1f, 1f)

                                val transformedBitmap = android.graphics.Bitmap.createBitmap(
                                    originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                                )

                                val filename = "Edited_${System.currentTimeMillis()}.jpg"
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Edited")
                                    }
                                }
                                val newUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                if (newUri != null) {
                                    context.contentResolver.openOutputStream(newUri)?.use { out ->
                                        transformedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Edited copy saved to gallery!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Copy")
            }
        }
    }
}

private fun gcd(a: Int, b: Int): Int {
    return if (b == 0) a else gcd(b, a % b)
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isMonospaceOrPath: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        SelectionContainer {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = if (isMonospaceOrPath) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                softWrap = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
fun QuickVideoPreview(item: MediaItem, onOpenFullPlayer: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onOpenFullPlayer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Video",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAudioPreview(
    item: MediaItem,
    mediaList: List<MediaItem> = listOf(item),
    currentIndex: Int = 0,
    onClose: () -> Unit,
    onOpenFullPlayer: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val exoPlayerManager = remember { com.medianest.player.ExoPlayerManager.getInstance(context) }

    LaunchedEffect(item.uri) {
        exoPlayerManager.playMediaList(mediaList, currentIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayerManager.exoPlayer.pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        com.medianest.ui.components.MusicNotificationCard(
            item = item,
            exoPlayerManager = exoPlayerManager,
            onClose = onClose,
            onOpenFullPlayer = onOpenFullPlayer
        )
    }
}
