package com.medianest.ui.components

import android.content.Context
import java.util.Locale
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.medianest.MediaNestApp
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.audioplayer.AudioPlayerActivity
import com.medianest.ui.theme.MediaNestTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OverlayMode {
    BAR,
    MINIMIZED,
    LARGE_ART
}

object OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}

object MiniPlayerOverlayManager {
    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var currentParams: WindowManager.LayoutParams? = null

    fun updateLayoutParams(
        context: Context,
        mode: OverlayMode,
        isLeftSide: Boolean,
        isTablet: Boolean
    ) {
        try {
            val params = currentParams ?: return
            val displayMetrics = context.resources.displayMetrics

            when (mode) {
                OverlayMode.BAR -> {
                    params.width = if (isTablet) (380 * displayMetrics.density).toInt() else WindowManager.LayoutParams.MATCH_PARENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.gravity = if (isTablet) {
                        Gravity.BOTTOM or (if (isLeftSide) Gravity.START else Gravity.END)
                    } else {
                        Gravity.BOTTOM
                    }
                }
                OverlayMode.MINIMIZED -> {
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.gravity = Gravity.BOTTOM or (if (isLeftSide) Gravity.START else Gravity.END)
                }
                OverlayMode.LARGE_ART -> {
                    params.width = if (isTablet) (340 * displayMetrics.density).toInt() else WindowManager.LayoutParams.MATCH_PARENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.gravity = if (isTablet) {
                        Gravity.BOTTOM or (if (isLeftSide) Gravity.START else Gravity.END)
                    } else {
                        Gravity.BOTTOM
                    }
                }
            }

            overlayView?.let { windowManager?.updateViewLayout(it, params) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun show(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission(context)
            return
        }
        if (overlayView != null) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = context.resources.displayMetrics
            val widthDp = displayMetrics.widthPixels / displayMetrics.density
            val isTablet = widthDp >= 600

            val overlayWidth = if (isTablet) (380 * displayMetrics.density).toInt() else WindowManager.LayoutParams.MATCH_PARENT
            val overlayGravity = if (isTablet) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM

            val params = WindowManager.LayoutParams(
                overlayWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = overlayGravity
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    blurBehindRadius = 80
                }
            }
            currentParams = params

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(OverlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(OverlayLifecycleOwner)
                setContent {
                    MediaNestTheme {
                        OverlayHost(
                            onExpandFull = {
                                hide()
                                val currentItem = ExoPlayerManager.getInstance(context).playerState.value.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.AUDIO }
                                if (currentItem == null) { hide(); return@OverlayHost }
                                val targetClass = AudioPlayerActivity::class.java
                                val intent = Intent(context, targetClass).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
            overlayView = composeView
            windowManager?.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            currentParams = null
        }
    }

    private fun requestOverlayPermission(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d", min, sec)
}

@Composable
fun OverlayHost(
    onExpandFull: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = (configuration.screenWidthDp >= 600)

    var mode by remember { mutableStateOf(OverlayMode.BAR) }
    var isLeftSide by remember { mutableStateOf(false) }

    val activeManager = remember { ExoPlayerManager.getInstance(context) }
    val playerState by activeManager.playerState.collectAsState()
    val currentItem = playerState.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.AUDIO }

    // If currentItem is null for more than 1500ms (to prevent race condition during initial loading or track change), hide overlay
    LaunchedEffect(currentItem) {
        if (currentItem == null) {
            kotlinx.coroutines.delay(1500)
            if (activeManager.playerState.value.currentItem == null) {
                MiniPlayerOverlayManager.hide()
            }
        }
    }

    if (currentItem == null) {
        return
    }

    // Sync window manager layout when mode or side changes
    LaunchedEffect(mode, isLeftSide, isTablet) {
        MiniPlayerOverlayManager.updateLayoutParams(context, mode, isLeftSide, isTablet)
    }

    when (mode) {
        OverlayMode.BAR -> {
            FloatingMiniPlayerBar(
                isLeftSide = isLeftSide,
                isTablet = isTablet,
                onMoveSide = { targetLeft ->
                    isLeftSide = targetLeft
                },
                onMinimize = {
                    mode = OverlayMode.MINIMIZED
                },
                onOpenLargeArt = {
                    mode = OverlayMode.LARGE_ART
                },
                onExpandFull = onExpandFull
            )
        }
        OverlayMode.MINIMIZED -> {
            MinimizedFloatingBubble(
                isLeftSide = isLeftSide,
                isTablet = isTablet,
                onRestore = {
                    mode = OverlayMode.BAR
                },
                onClose = {
                    MiniPlayerOverlayManager.hide()
                    activeManager.stopPlayback()
                }
            )
        }
        OverlayMode.LARGE_ART -> {
            LargeAlbumArtOverlay(
                isLeftSide = isLeftSide,
                isTablet = isTablet,
                onBackToMini = {
                    mode = OverlayMode.BAR
                },
                onClose = {
                    MiniPlayerOverlayManager.hide()
                    activeManager.stopPlayback()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingMiniPlayerBar(
    isLeftSide: Boolean,
    isTablet: Boolean,
    onMoveSide: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onOpenLargeArt: () -> Unit,
    onExpandFull: () -> Unit
) {
    val context = LocalContext.current
    val activeManager = remember { ExoPlayerManager.getInstance(context) }
    val playerState by activeManager.playerState.collectAsState()
    val currentItem = playerState.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.AUDIO } ?: return
    val isPlaying = playerState.isPlaying

    val titleText = remember(currentItem.title, currentItem.uri) {
        val raw = currentItem.title
        if (raw.isBlank() || raw.all { it.isDigit() }) {
            val lastSeg = currentItem.uri.lastPathSegment ?: ""
            if (lastSeg.isNotBlank() && !lastSeg.all { it.isDigit() }) {
                lastSeg.substringBeforeLast('.')
            } else {
                "Track ${currentItem.id}"
            }
        } else {
            raw
        }
    }

    val currentPosMs = playerState.currentPositionMs
    val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem.durationMs

    val density = context.resources.displayMetrics.density
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val swipeGestureModifier = Modifier.pointerInput(isLeftSide, isTablet) {
        detectDragGestures(
            onDragStart = {
                totalDragX = 0f
                totalDragY = 0f
            },
            onDragEnd = {
                val thresholdPx = 36f * density
                val absX = kotlin.math.abs(totalDragX)
                val absY = kotlin.math.abs(totalDragY)
                val isSwipeDown = totalDragY > thresholdPx && totalDragY > absX
                val isSwipeHorizontal = absX > thresholdPx && absX > absY

                if (isSwipeDown) {
                    MiniPlayerOverlayManager.hide()
                    activeManager.stopPlayback()
                } else if (isSwipeHorizontal) {
                    if (isLeftSide) {
                        // If on left side:
                        // Swipe Right -> move to right side
                        // Swipe Left -> minimize
                        if (totalDragX > thresholdPx) {
                            if (isTablet) onMoveSide(false) else onMinimize()
                        } else if (totalDragX < -thresholdPx) {
                            onMinimize()
                        }
                    } else {
                        // If on right side:
                        // Swipe Left -> move to left side
                        // Swipe Right -> minimize
                        if (totalDragX < -thresholdPx) {
                            if (isTablet) onMoveSide(true) else onMinimize()
                        } else if (totalDragX > thresholdPx) {
                            onMinimize()
                        }
                    }
                }
            },
            onDragCancel = {
                totalDragX = 0f
                totalDragY = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
            }
        )
    }

    AmbientGlassSurface(
        modifier = Modifier
            .then(if (isTablet) Modifier.width(380.dp) else Modifier.fillMaxWidth())
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .then(swipeGestureModifier)
            .clickable { onExpandFull() },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.Transparent,
        backgroundImage = currentItem.albumArtUri ?: currentItem.uri
    ) {
        var isDraggingSeek by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }

        val progress = if (isDraggingSeek) {
            dragProgress
        } else if (durationMs > 0L) {
            (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val artSize = 90.dp
        val paddingAround = 10.dp
        val maxHeight = artSize + (paddingAround * 2)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .padding(paddingAround),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art (Clicking opens Large Album Art Mode)
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (currentItem.albumArtUri != null) {
                            Modifier.background(Color.White.copy(alpha = 0.15f))
                        } else {
                            Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            Color.White.copy(alpha = 0.15f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenLargeArt
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (currentItem.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentItem.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = titleText,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Right Content Column (Track Info, Controls, Seekbar)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Title + Play/Pause Button (Custom Naked Icon)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee()
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { activeManager.togglePlayPause() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            RoundedPauseIcon(
                                modifier = Modifier.size(22.dp),
                                tint = Color.White
                            )
                        } else {
                            RoundedPlayIcon(
                                modifier = Modifier.size(22.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                Text(
                    text = currentItem.artist ?: currentItem.album ?: "MediaNest",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Seekbar with perfectly centered circular head
                AppSlider(
                    value = progress,
                    onValueChange = {
                        isDraggingSeek = true
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        isDraggingSeek = false
                        if (durationMs > 0) {
                            val seekPos = (dragProgress * durationMs).toLong()
                            activeManager.seekTo(seekPos)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                    style = AppSliderStyle.Solid,
                    thickness = AppSliderThickness.Thin,
                    accentColor = Color.White,
                    customThumbSize = DpSize(12.dp, 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeMs(currentPosMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTimeMs(durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Minimized compact floating bubble for quick playback access.
 */
@Composable
fun MinimizedFloatingBubble(
    isLeftSide: Boolean,
    isTablet: Boolean,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activeManager = remember { ExoPlayerManager.getInstance(context) }
    val playerState by activeManager.playerState.collectAsState()
    val currentItem = playerState.currentItem ?: return

    val density = context.resources.displayMetrics.density
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val swipeModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { totalDragY = 0f },
            onDragEnd = {
                val thresholdPx = 36f * density
                if (totalDragY > thresholdPx) {
                    onClose()
                }
            },
            onDragCancel = { totalDragY = 0f },
            onDrag = { change, dragAmount ->
                change.consume()
                totalDragY += dragAmount.y
            }
        )
    }

    Box(
        modifier = Modifier
            .padding(12.dp)
            .then(swipeModifier)
            .size(58.dp)
            .clip(CircleShape)
            .background(Color(0x80181A20))
            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onRestore),
        contentAlignment = Alignment.Center
    ) {
        if (currentItem.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentItem.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = currentItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Large Album Art Player Mode.
 * Everything is rendered directly ON the album art (no elements above or below).
 */
@Composable
fun LargeAlbumArtOverlay(
    isLeftSide: Boolean,
    isTablet: Boolean,
    onBackToMini: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activeManager = remember { ExoPlayerManager.getInstance(context) }
    val playerState by activeManager.playerState.collectAsState()
    val currentItem = playerState.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.AUDIO } ?: return
    val isPlaying = playerState.isPlaying

    val db = remember { MediaNestApp.instance.database }
    val audioPlaylists by db.categoryDao().getCategoriesByType("AUDIO").collectAsState(initial = emptyList())
    val isFavoriteFromDb by remember(currentItem, audioPlaylists) {
        db.categoryDao().getAllCrossRefs().map { refs ->
            val favoritesCat = audioPlaylists.find { it.name.equals("Favorites", ignoreCase = true) }
            favoritesCat != null && refs.any { it.categoryId == favoritesCat.id && it.mediaUri == currentItem.uri.toString() }
        }
    }.collectAsState(initial = false)

    var optimisticFavorite by remember(currentItem.uri) { mutableStateOf<Boolean?>(null) }
    val isFavorite = optimisticFavorite ?: isFavoriteFromDb

    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showHeartAnimation by remember { mutableStateOf(false) }

    val toggleFavorite: () -> Unit = {
        val targetState = !isFavorite
        optimisticFavorite = targetState
        showHeartAnimation = true
        scope.launch(Dispatchers.IO) {
            try {
                var favCat = db.categoryDao().getCategoryByNameAndType("Favorites", "AUDIO")
                if (favCat == null) {
                    val newId = db.categoryDao().insertCategory(
                        MediaCategory(name = "Favorites", type = "AUDIO")
                    )
                    favCat = MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                }
                if (!targetState) {
                    db.categoryDao().removeMediaFromCategory(favCat.id, currentItem.uri.toString())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    db.categoryDao().insertCategoryCrossRef(
                        CategoryMediaCrossRef(categoryId = favCat.id, mediaUri = currentItem.uri.toString())
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(showHeartAnimation) {
        if (showHeartAnimation) {
            kotlinx.coroutines.delay(800)
            showHeartAnimation = false
        }
    }

    val density = context.resources.displayMetrics.density
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val titleText = remember(currentItem.title, currentItem.uri) {
        val raw = currentItem.title
        if (raw.isBlank() || raw.all { it.isDigit() }) {
            val lastSeg = currentItem.uri.lastPathSegment ?: ""
            if (lastSeg.isNotBlank() && !lastSeg.all { it.isDigit() }) {
                lastSeg.substringBeforeLast('.')
            } else {
                "Track ${currentItem.id}"
            }
        } else {
            raw
        }
    }

    val currentPosMs = playerState.currentPositionMs
    val durationMs = if (playerState.durationMs > 0) playerState.durationMs else currentItem.durationMs

    var isDraggingSeek by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val progress = if (isDraggingSeek) {
        dragProgress
    } else if (durationMs > 0L) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    showControls = !showControls
                },
                onDoubleTap = {
                    toggleFavorite()
                }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    totalDragX = 0f
                    totalDragY = 0f
                },
                onDragEnd = {
                    val thresholdPx = 40f * density
                    val absX = kotlin.math.abs(totalDragX)
                    val absY = kotlin.math.abs(totalDragY)

                    // Swipe Down -> back to mini album art player
                    if (totalDragY > thresholdPx && totalDragY > absX) {
                        onBackToMini()
                    } else if (absX > thresholdPx && absX > absY) {
                        if (totalDragX < -thresholdPx) {
                            activeManager.next()
                        } else if (totalDragX > thresholdPx) {
                            activeManager.previous()
                        }
                    }
                },
                onDragCancel = {
                    totalDragX = 0f
                    totalDragY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    totalDragY += dragAmount.y
                }
            )
        }

    Box(
        modifier = Modifier
            .then(if (isTablet) Modifier.width(340.dp) else Modifier.fillMaxWidth())
            .padding(horizontal = if (isTablet) 12.dp else 20.dp, vertical = 12.dp),
        contentAlignment = if (isTablet) (if (isLeftSide) Alignment.BottomStart else Alignment.BottomEnd) else Alignment.BottomCenter
    ) {
        // Large Album Art Surface with AmbientGlassSurface background (matching mini player)
        AmbientGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .then(gestureModifier),
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.Transparent,
            backgroundImage = currentItem.albumArtUri ?: currentItem.uri
        ) {
            // Album Art Image with increased inner padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (currentItem.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentItem.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = titleText,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF2C3E50),
                                        Color(0xFF000000)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            // Top gradient overlay for controls visibility
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Bottom gradient overlay for title/artist/seekbar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            }

            // TOP RIGHT: Play/Pause icon & Close icon (Naked icons, no background)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play/Pause icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { activeManager.togglePlayPause() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            RoundedPauseIcon(
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        } else {
                            RoundedPlayIcon(
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }

                    // Close icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClose
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedCloseIcon(
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // CENTER: Animated Double Tap Heart Feedback
            if (showHeartAnimation) {
                val scaleAnim by animateFloatAsState(
                    targetValue = if (showHeartAnimation) 1.2f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "heartScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scaleAnim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            // BOTTOM CENTER: Title + Artist + Minimal Seekbar (No duration numbers)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 4.dp, top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Track Title
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Artist name
                    Text(
                        text = currentItem.artist ?: currentItem.album ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Wavy seekbar directly on album art (lowered at bottom edge with smaller thumb)
                    WavySeekBar(
                        value = if (durationMs > 0) (if (isDraggingSeek) (dragProgress * durationMs) else currentPosMs.toFloat()).coerceIn(0f, durationMs.toFloat()) else 0f,
                        onValueChange = {
                            isDraggingSeek = true
                            dragProgress = if (durationMs > 0) it / durationMs.toFloat() else 0f
                        },
                        onValueChangeFinished = {
                            isDraggingSeek = false
                            if (durationMs > 0) {
                                val seekPos = (dragProgress * durationMs).toLong()
                                activeManager.seekTo(seekPos)
                            }
                        },
                        valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                        isPlaying = isPlaying,
                        activeColor = Color.White,
                        inactiveColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                        activeTrackHeightDp = 4.dp,
                        inactiveTrackHeightDp = 3.dp,
                        heightDp = 28.dp,
                        customThumbRadiusDp = 5.dp,
                        showThumb = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                }
            }
        }
    }
}
