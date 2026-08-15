package com.medianest.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.audioplayer.AudioPlayerActivity
import com.medianest.ui.theme.MediaNestTheme

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

    fun show(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission(context)
            return
        }
        if (overlayView != null) return
        
        // Ensure there is something to play before showing overlay
        if (ExoPlayerManager.activeManager == null) return

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

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(OverlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(OverlayLifecycleOwner)
                setContent {
                    MediaNestTheme {
                        FloatingMiniPlayerBar(
                            onExpand = {
                                hide()
                                val mgr = ExoPlayerManager.activeManager ?: ExoPlayerManager.getInstance(context)
                                val currentItem = mgr.playerState.value.currentItem
                                val isVideo = currentItem?.mimeType?.startsWith("video") == true || currentItem?.type == com.medianest.data.db.MediaType.VIDEO
                                val targetClass = if (isVideo) com.medianest.ui.videoplayer.VideoPlayerActivity::class.java else AudioPlayerActivity::class.java
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
    return String.format("%d:%02d", min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingMiniPlayerBar(
    onExpand: () -> Unit
) {
    val activeManager = ExoPlayerManager.activeManager ?: return
    val playerState by activeManager.playerState.collectAsState()
    val currentItem = playerState.currentItem ?: return
    val context = LocalContext.current
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

    val swipeGestureModifier = Modifier.pointerInput(Unit) {
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
                val isSwipeUp = totalDragY < -thresholdPx && absY > absX
                val isSwipeHorizontal = absX > thresholdPx && absX > absY

                if (isSwipeDown) {
                    MiniPlayerOverlayManager.hide()
                } else if (isSwipeUp || isSwipeHorizontal) {
                    MiniPlayerOverlayManager.hide()
                    activeManager.stopPlayback()
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var albumArtHue by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(currentItem.albumArtUri, currentItem.uri) {
        val artUri = currentItem.albumArtUri ?: currentItem.uri
        albumArtHue = extractBaseHueFromArt(context, artUri)
    }

    val ambientHueBrush = remember(albumArtHue) {
        val hue = albumArtHue
        if (hue != null) {
            val topColor = Color.hsv(hue, 0.65f, 0.35f).copy(alpha = 0.82f)
            val midColor = Color.hsv((hue + 15f) % 360f, 0.48f, 0.22f).copy(alpha = 0.85f)
            val bottomColor = Color(0xEB111418)
            Brush.verticalGradient(listOf(topColor, midColor, bottomColor))
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xCC28303C),
                    Color(0xEB111418)
                )
            )
        }
    }

    GlassSurface(
        modifier = Modifier
            .then(if (isTablet) Modifier.width(380.dp) else Modifier.fillMaxWidth())
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(swipeGestureModifier)
            .clickable { onExpand() },
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x66181C20),
        borderColor = Color.White.copy(alpha = 0.35f),
        backgroundImage = currentItem.albumArtUri ?: currentItem.uri,
        blurRadius = 24.dp,
        backgroundBrush = ambientHueBrush
    ) {
        var isDraggingSeek by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }

        val progress = if (isDraggingSeek) {
            dragProgress
        } else if (durationMs > 0) {
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
            // Album Art
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
                // Top Row: Title + Play/Pause Button
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

                    IconButton(
                        onClick = { activeManager.togglePlayPause() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isPlaying) {
                            RoundedPauseIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        } else {
                            RoundedPlayIcon(
                                modifier = Modifier.size(20.dp),
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
                Slider(
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
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier.size(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    },
                    track = { sliderState ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(3.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }
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
