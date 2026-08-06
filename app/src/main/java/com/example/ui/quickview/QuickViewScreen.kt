package com.example.ui.quickview

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.MediaInfoBottomSheet
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
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.formatDuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickViewScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    onClose: () -> Unit,
    onOpenFullPlayer: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mutableMediaList by remember(mediaList) { mutableStateOf(mediaList) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(true) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val settingsManager = com.example.MediaNestApp.instance.settingsManager
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
        if (mutableMediaList.isNotEmpty() && pagerState.currentPage in mutableMediaList.indices) {
            filmstripListState.animateScrollToItem(pagerState.currentPage)
        }
    }

    var imageHue by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(currentItem, mutableMediaList) {
        val targetItem = currentItem ?: mutableMediaList.firstOrNull()
        if (targetItem != null) {
            val uri = targetItem.albumArtUri ?: targetItem.uri
            imageHue = com.example.ui.components.extractBaseHueFromArt(context, uri)
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
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        if (mutableMediaList.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = mutableMediaList[page]
                when (item.type) {
                    MediaType.IMAGE -> ZoomableImageView(
                        item = item,
                        pictureModeEnabled = pictureModeEnabled,
                        pictureMode = pictureMode,
                        customSat = customSat,
                        customCon = customCon,
                        customWarmth = customWarmth,
                        onToggleControls = { showControls = !showControls },
                        onSwipeUpForInfo = { showInfoBottomSheet = true }
                    )
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
                .statusBarsPadding()
                .padding(16.dp)
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pictureModeEnabled) {
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { showPictureModeDialog = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Picture Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                val modeLabel = com.example.ui.components.PictureMode.fromKey(pictureMode).displayName
                                Text(
                                    text = modeLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
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

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = Color(0xDC141722),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open with", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showOverflowMenu = false
                                    currentItem?.let { item ->
                                        val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(sharingUri, item.mimeType.ifEmpty { "image/*" })
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(openIntent, "Open with"))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showOverflowMenu = false
                                    currentItem?.let { item ->
                                        val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                            setDataAndType(item.uri, item.mimeType.ifEmpty { "image/*" })
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(editIntent, "Edit image"))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showOverflowMenu = false
                                    currentItem?.let { item ->
                                        val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = item.mimeType.ifEmpty { "image/*" }
                                            putExtra(Intent.EXTRA_STREAM, sharingUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("File Info", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showOverflowMenu = false
                                    showInfoBottomSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEF4444)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
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
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
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
                                val db = com.example.MediaNestApp.instance.database
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
                            isFavorite = !isFavorite
                            val item = currentItem ?: return@IconButton
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val db = com.example.MediaNestApp.instance.database
                                val dao = db.categoryDao()
                                val categories = dao.getCategoriesByType("IMAGE").first()
                                var fav = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                val favId = if (fav != null) fav.id else {
                                    dao.insertCategory(com.example.data.db.MediaCategory(name = "Favorites", type = "IMAGE"))
                                }
                                if (isFavorite) {
                                    dao.insertCategoryCrossRef(com.example.data.db.CategoryMediaCrossRef(categoryId = favId, mediaUri = item.uri.toString()))
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
                        IconButton(onClick = { showInfoBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info Details",
                                tint = actionIconTint
                            )
                        }

                        // 4. Share
                        IconButton(onClick = {
                            currentItem?.let { item ->
                                val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
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
                        IconButton(onClick = { showDeleteDialog = true }) {
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
                containerColor = Color(0xDC141722),
                shape = RoundedCornerShape(24.dp),
                title = { Text("Delete File?") },
                text = { Text("Are you sure you want to delete '${currentItem.title}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        try {
                            com.example.util.FolderHiddenUtils.deleteMediaUri(context, currentItem.uri)
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
                containerColor = Color(0xDC141722),
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
                        com.example.ui.components.PictureMode.entries.forEach { mode ->
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
                onDismiss = { showInfoBottomSheet = false }
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
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
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
        containerColor = MaterialTheme.colorScheme.surface
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
fun ZoomableImageView(
    item: MediaItem,
    pictureModeEnabled: Boolean = true,
    pictureMode: String = "BALANCED",
    customSat: Float = 1.18f,
    customCon: Float = 1.06f,
    customWarmth: Float = 0.03f,
    onToggleControls: () -> Unit = {},
    onSwipeUpForInfo: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val animatedScale = remember { Animatable(1f) }
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }

    val colorFilter = remember(pictureModeEnabled, pictureMode, customSat, customCon, customWarmth) {
        com.example.ui.components.PictureModeUtils.getComposeColorFilter(
            modeKey = pictureMode,
            customSat = customSat,
            customCon = customCon,
            customWarmth = customWarmth,
            enabled = pictureModeEnabled
        )
    }

    LaunchedEffect(item.uri) {
        animatedScale.snapTo(1f)
        animatedOffsetX.snapTo(0f)
        animatedOffsetY.snapTo(0f)
    }

    val isPng = remember(item) {
        item.title.endsWith(".png", ignoreCase = true) || item.mimeType.lowercase().contains("png")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isPng) Color.White else Color.Black)
    ) {
        var imageIntrinsicSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    imageIntrinsicSize = state.painter?.intrinsicSize ?: androidx.compose.ui.geometry.Size.Zero
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animatedScale.value,
                    scaleY = animatedScale.value,
                    translationX = animatedOffsetX.value,
                    translationY = animatedOffsetY.value
                )
                .pointerInput(item.uri) {
                    var lastTapTime = 0L
                    val doubleTapTimeout = 300L

                    awaitEachGesture {
                        var isPinching = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            
                            if (changes.all { !it.pressed }) {
                                // Detect one-finger double tap on release
                                if (!isPinching && changes.size == 1) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastTapTime < doubleTapTimeout) {
                                        // Double Tap Triggered
                                        val centroid = changes[0].position
                                        scope.launch {
                                            if (animatedScale.value > 1.1f) {
                                                launch { animatedScale.animateTo(1f, tween(300)) }
                                                launch { animatedOffsetX.animateTo(0f, tween(300)) }
                                                launch { animatedOffsetY.animateTo(0f, tween(300)) }
                                            } else {
                                                val targetScale = 4f
                                                val centerX = size.width / 2f
                                                val centerY = size.height / 2f
                                                val targetX = (centerX - centroid.x) * (targetScale - 1f)
                                                val targetY = (centerY - centroid.y) * (targetScale - 1f)

                                                // Strict Bounds calculation
                                                val f = if (imageIntrinsicSize.width > 0 && imageIntrinsicSize.height > 0) {
                                                    kotlin.math.min(size.width / imageIntrinsicSize.width, size.height / imageIntrinsicSize.height)
                                                } else 1f
                                                val actualImgW = imageIntrinsicSize.width * f
                                                val actualImgH = imageIntrinsicSize.height * f
                                                
                                                val maxOffsetX = kotlin.math.max(0f, (actualImgW * targetScale - size.width) / 2f)
                                                val maxOffsetY = kotlin.math.max(0f, (actualImgH * targetScale - size.height) / 2f)

                                                launch { animatedScale.animateTo(targetScale, tween(350)) }
                                                launch { animatedOffsetX.animateTo(targetX.coerceIn(-maxOffsetX, maxOffsetX), tween(350)) }
                                                launch { animatedOffsetY.animateTo(targetY.coerceIn(-maxOffsetY, maxOffsetY), tween(350)) }
                                            }
                                        }
                                        lastTapTime = 0L
                                    } else {
                                        lastTapTime = currentTime
                                    }
                                }
                                break
                            }

                            if (changes.size > 1) {
                                // PINCH ZOOM
                                isPinching = true
                                val zoom = event.calculateZoom()
                                val oldScale = animatedScale.value
                                val newScale = (oldScale * zoom).coerceIn(1f, 10f)
                                
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                
                                val scaleFactor = newScale / oldScale
                                val newOffsetX = (animatedOffsetX.value + centroid.x - centerX) * scaleFactor - (centroid.x - centerX)
                                val newOffsetY = (animatedOffsetY.value + centroid.y - centerY) * scaleFactor - (centroid.y - centerY)

                                scope.launch {
                                    animatedScale.snapTo(newScale)
                                    
                                    val f = if (imageIntrinsicSize.width > 0 && imageIntrinsicSize.height > 0) {
                                        kotlin.math.min(size.width / imageIntrinsicSize.width, size.height / imageIntrinsicSize.height)
                                    } else 1f
                                    val actualImgW = imageIntrinsicSize.width * f
                                    val actualImgH = imageIntrinsicSize.height * f
                                    
                                    val maxOffsetX = kotlin.math.max(0f, (actualImgW * newScale - size.width) / 2f)
                                    val maxOffsetY = kotlin.math.max(0f, (actualImgH * newScale - size.height) / 2f)
                                    
                                    animatedOffsetX.snapTo(newOffsetX.coerceIn(-maxOffsetX, maxOffsetX))
                                    animatedOffsetY.snapTo(newOffsetY.coerceIn(-maxOffsetY, maxOffsetY))
                                }
                                changes.forEach { it.consume() }
                            } else if (changes.size == 1 && !isPinching) {
                                // PAN or SWIPE
                                val change = changes[0]
                                val dragAmount = change.position - change.previousPosition
                                
                                if (animatedScale.value > 1.01f) {
                                    val newX = animatedOffsetX.value + dragAmount.x
                                    val newY = animatedOffsetY.value + dragAmount.y
                                    
                                    val f = if (imageIntrinsicSize.width > 0 && imageIntrinsicSize.height > 0) {
                                        kotlin.math.min(size.width / imageIntrinsicSize.width, size.height / imageIntrinsicSize.height)
                                    } else 1f
                                    val actualImgW = imageIntrinsicSize.width * f
                                    val actualImgH = imageIntrinsicSize.height * f
                                    
                                    val maxOffsetX = kotlin.math.max(0f, (actualImgW * animatedScale.value - size.width) / 2f)
                                    val maxOffsetY = kotlin.math.max(0f, (actualImgH * animatedScale.value - size.height) / 2f)
                                    
                                    scope.launch {
                                        animatedOffsetX.snapTo(newX.coerceIn(-maxOffsetX, maxOffsetX))
                                        animatedOffsetY.snapTo(newY.coerceIn(-maxOffsetY, maxOffsetY))
                                    }
                                    
                                    // Lock to pan if not hitting edges horizontally
                                    if (newX.coerceIn(-maxOffsetX, maxOffsetX) == newX) {
                                        change.consume()
                                    }
                                } else {
                                    // Info swipe up
                                    if (dragAmount.y < -15f && kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x) * 2f) {
                                        onSwipeUpForInfo()
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onToggleControls() }
        )

        // Bottom-Right Glass Zoom Percentage Control Pill
        AnimatedVisibility(
            visible = animatedScale.value > 1.01f,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 16.dp)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x66000000),
                borderColor = Color(0x40FFFFFF)
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val newScale = (animatedScale.value - 1f).coerceAtLeast(1f)
                            animatedScale.animateTo(newScale)
                            if (newScale == 1f) {
                                animatedOffsetX.animateTo(0f)
                                animatedOffsetY.animateTo(0f)
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "${(animatedScale.value * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { 
                        scope.launch {
                            launch { animatedScale.animateTo(1f) }
                            launch { animatedOffsetX.animateTo(0f) }
                            launch { animatedOffsetY.animateTo(0f) }
                        }
                    }.padding(4.dp)
                )

                IconButton(
                    onClick = {
                        scope.launch {
                            animatedScale.animateTo((animatedScale.value + 1f).coerceAtMost(10f))
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
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
    val exoPlayerManager = remember { com.example.player.ExoPlayerManager.getInstance(context) }

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
        com.example.ui.components.MusicNotificationCard(
            item = item,
            exoPlayerManager = exoPlayerManager,
            onClose = onClose,
            onOpenFullPlayer = onOpenFullPlayer
        )
    }
}
