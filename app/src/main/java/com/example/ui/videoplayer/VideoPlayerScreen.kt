@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.videoplayer

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.MediaNestApp
import com.example.data.db.PlaybackState
import com.example.data.model.MediaItem
import com.example.data.model.SubtitleItem
import com.example.data.repository.NetworkRepository
import com.example.player.ExoPlayerManager
import com.example.ui.components.GlassSurface
import com.example.ui.components.AdaptiveBottomSheet
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.ThinSeekBar
import com.example.ui.components.dismissKeyboardOnOutsideTap
import com.example.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    playerManager: ExoPlayerManager,
    networkRepository: NetworkRepository,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenAppSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val playerState by playerManager.playerState.collectAsState()
    val settingsManager = MediaNestApp.instance.settingsManager

    val showStatusBar by settingsManager.showStatusBarInPlayback.collectAsState(initial = false)
    val keepScreenOn by settingsManager.keepScreenOn.collectAsState(initial = true)
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)

    val pictureModeEnabled by settingsManager.pictureModeEnabled.collectAsState(initial = true)
    val pictureMode by settingsManager.pictureMode.collectAsState(initial = "BALANCED")
    val customSat by settingsManager.customSaturation.collectAsState(initial = 1.18f)
    val customCon by settingsManager.customContrast.collectAsState(initial = 1.06f)
    val customWarmth by settingsManager.customWarmth.collectAsState(initial = 0.03f)

    var showControls by remember { mutableStateOf(true) }
    var cropMode by remember { mutableStateOf("FIT") } // FIT, CROP, STRETCH, ORIGINAL
    val savedDecoderMode by settingsManager.decoderMode.collectAsState(initial = "HW+")
    var decoderMode by remember(savedDecoderMode) { mutableStateOf(savedDecoderMode) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var filmGrainIntensity by remember { mutableFloatStateOf(0.10f) }
    var isFilmGrainEnabled by remember { mutableStateOf(false) }

    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }

    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isHorizontalDragging by remember { mutableStateOf(false) }
    var seekTargetPositionMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isBackgroundPlayEnabled by remember { mutableStateOf(false) }
    var selectedSubtitleTrackIndex by remember { mutableIntStateOf(0) }

    var showDrawer by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleCustomizationSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var selectedAudioTrackIndex by remember { mutableIntStateOf(0) }
    var isAutoRepeatEnabled by remember { mutableStateOf(false) }

    var subtitleFontSizeSp by remember { mutableFloatStateOf(20f) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var subtitleBgColor by remember { mutableStateOf(Color(0x99000000)) }
    var subtitleHasShadow by remember { mutableStateOf(true) }
    var activeSubtitleText by remember { mutableStateOf<String?>(null) }

    var subtitleList by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var audioSyncOffsetMs by remember { mutableLongStateOf(0L) }
    var isSearchingSubtitles by remember { mutableStateOf(false) }
    var subtitleSearchQuery by remember(playerState.currentItem) { mutableStateOf(playerState.currentItem?.title ?: "") }
    var subtitleStatusMessage by remember { mutableStateOf<String?>(null) }

    val embeddedTracks = remember(playerState.currentItem) {
        val list = mutableListOf<SubtitleItem>()
        try {
            val tracks = playerManager.exoPlayer.currentTracks
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language ?: "und"
                        val label = format.label ?: "Track ${i + 1}"
                        list.add(
                            SubtitleItem(
                                id = "embedded_$i",
                                name = label,
                                language = lang,
                                isLocal = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    var isControlsLocked by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            showOverflowMenu -> showOverflowMenu = false
            showDetailsSheet -> showDetailsSheet = false
            showDrawer -> showDrawer = false
            showSubtitleSheet -> showSubtitleSheet = false
            showSubtitleCustomizationSheet -> showSubtitleCustomizationSheet = false
            showSettingsSheet -> showSettingsSheet = false
            showAudioTrackSheet -> showAudioTrackSheet = false
            showAspectRatioMenu -> showAspectRatioMenu = false
            showSpeedMenu -> showSpeedMenu = false
            isControlsLocked -> isControlsLocked = false
            else -> onClose()
        }
    }

    // Real Subtitle Cue Listener
    DisposableEffect(playerManager.exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                if (selectedSubtitleTrackIndex != -1 && cueGroup.cues.isNotEmpty()) {
                    val text = cueGroup.cues.joinToString("\n") { it.text ?: "" }.trim()
                    activeSubtitleText = text.ifEmpty { null }
                } else {
                    activeSubtitleText = null
                }
            }
        }
        playerManager.exoPlayer.addListener(listener)
        onDispose {
            playerManager.exoPlayer.removeListener(listener)
        }
    }

    // Status bar & keep screen on handling
    DisposableEffect(showStatusBar, keepScreenOn) {
        activity?.let { act ->
            if (keepScreenOn) {
                act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (!showStatusBar) {
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {}
    }

    // Auto-hide controls timer
    val anyModalOpen = showOverflowMenu || showDetailsSheet || showDrawer || showSubtitleSheet ||
                       showSubtitleCustomizationSheet || showSettingsSheet || showAudioTrackSheet ||
                       showAspectRatioMenu || showSpeedMenu
    LaunchedEffect(showControls, anyModalOpen, isControlsLocked) {
        if (showControls && !isControlsLocked && !anyModalOpen) {
            delay(4000)
            showControls = false
        }
    }

    // Auto-dismiss gesture feedback HUD after 1200ms
    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            delay(1200)
            gestureFeedbackText = null
        }
    }

    // Dynamic Video Background Gradient
    var videoHue by remember { mutableStateOf<Float?>(null) }
    val currentVideoItem = playerState.currentItem

    LaunchedEffect(currentVideoItem, playerState.queue) {
        val targetItem = currentVideoItem ?: playerState.queue.firstOrNull()
        if (targetItem != null) {
            val uri = targetItem.albumArtUri ?: targetItem.uri
            videoHue = com.example.ui.components.extractBaseHueFromArt(context, uri)
        } else {
            videoHue = null
        }
    }

    val videoBgBrush = remember(videoHue) {
        val hue = videoHue
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

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    var currentBrightness by remember { mutableFloatStateOf(0.7f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .dismissKeyboardOnOutsideTap()
    ) {
        // ExoPlayer AndroidView surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var isZooming = false
                        var startVolume = currentVolume
                        var startBrightness = currentBrightness
                        
                        var dragType = 0 // 0: None, 1: Vertical, 2: Horizontal
                        var totalDragX = 0f
                        var totalDragY = 0f
                        val slop = 15f // dp slop approximately

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            
                            if (changes.all { !it.pressed }) break

                            if (changes.size > 1) {
                                // Multi-finger gesture -> Zoom
                                isZooming = true
                                isDraggingVolume = false
                                isDraggingBrightness = false
                                isHorizontalDragging = false
                                dragType = 0
                                
                                val zoom = event.calculateZoom()
                                if (!isControlsLocked) {
                                    scale = (scale * zoom).coerceIn(1f, 10f)
                                }
                            } else if (!isZooming && changes.size == 1) {
                                // Single-finger gesture
                                val change = changes[0]
                                val dragAmount = change.position - change.previousPosition
                                val screenWidth = size.width
                                val screenHeight = size.height
                                
                                if (dragType == 0) {
                                    totalDragX += kotlin.math.abs(dragAmount.x)
                                    totalDragY += kotlin.math.abs(dragAmount.y)
                                    
                                    if (totalDragY > slop && totalDragY > totalDragX) {
                                        dragType = 1 // Vertical Lock
                                    } else if (totalDragX > slop && totalDragX > totalDragY) {
                                        dragType = 2 // Horizontal Lock
                                    }
                                }

                                if (dragType == 1) {
                                    // Vertical drag -> Vol/Brightness
                                    if (!isControlsLocked) {
                                        val delta = -dragAmount.y / screenHeight.toFloat()
                                        if (change.position.x < screenWidth / 2f) {
                                            isDraggingBrightness = true
                                            isDraggingVolume = false
                                            currentBrightness = (currentBrightness + delta).coerceIn(0.05f, 1f)
                                            activity?.let { act ->
                                                val lp = act.window.attributes
                                                lp.screenBrightness = currentBrightness
                                                act.window.attributes = lp
                                            }
                                        } else {
                                            isDraggingVolume = true
                                            isDraggingBrightness = false
                                            currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                                            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                            audioManager.setStreamVolume(
                                                android.media.AudioManager.STREAM_MUSIC,
                                                (currentVolume * maxVol).toInt(),
                                                0
                                            )
                                        }
                                    }
                                } else if (dragType == 2) {
                                    // Horizontal drag -> Seek
                                    if (!isControlsLocked && playerState.durationMs > 0) {
                                        isHorizontalDragging = true
                                        showControls = true
                                        val deltaRatio = dragAmount.x / screenWidth.toFloat()
                                        val addedMs = (deltaRatio * 120_000).toLong()
                                        seekDeltaMs += addedMs
                                        seekTargetPositionMs = (playerState.currentPositionMs + seekDeltaMs).coerceIn(0L, playerState.durationMs)
                                    }
                                }
                            }
                            changes.forEach { it.consume() }
                        }
                        
                        // Drag end
                        if (isHorizontalDragging) {
                            playerManager.seekTo(seekTargetPositionMs)
                        }
                        isDraggingBrightness = false
                        isDraggingVolume = false
                        isHorizontalDragging = false
                        isZooming = false
                        seekDeltaMs = 0L
                        dragType = 0
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            if (!isControlsLocked) {
                                val screenWidth = size.width
                                when {
                                    offset.x < screenWidth / 3f -> {
                                        playerManager.seekBackward(10000L)
                                        gestureFeedbackText = "-10s"
                                    }
                                    offset.x > (screenWidth * 2f / 3f) -> {
                                        playerManager.seekForward(10000L)
                                        gestureFeedbackText = "+10s"
                                    }
                                    else -> {
                                        playerManager.togglePlayPause()
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playerManager.exoPlayer
                        useController = false
                        resizeMode = when (cropMode) {
                            "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            "ORIGINAL" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = when (cropMode) {
                        "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "ORIGINAL" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    try {
                        val androidFilter = com.example.ui.components.PictureModeUtils.getAndroidColorFilter(
                            modeKey = pictureMode,
                            customSat = customSat,
                            customCon = customCon,
                            customWarmth = customWarmth,
                            enabled = pictureModeEnabled
                        )
                        if (androidFilter != null) {
                            val paint = android.graphics.Paint().apply {
                                colorFilter = androidFilter
                            }
                            view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                        } else {
                            view.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("VideoPlayerScreen", "Failed to apply hardware layer color filter, falling back to default", e)
                        try {
                            view.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                        } catch (_: Throwable) {}
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )

            // Video Zoom Percentage Pill - Relocated to Bottom-Right
            if (scale > 1.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 120.dp, end = 24.dp)
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { scale = 1f },
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0x33000000),
                        borderColor = Color(0x1AFFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Zoom: ${(scale * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(modifier = Modifier.size(1.dp, 10.dp).background(Color.White.copy(alpha = 0.2f)))
                            Text(
                                text = "Reset",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Active Subtitle Text Overlay
            if (!activeSubtitleText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (showControls && !showDrawer) 120.dp else 45.dp, start = 24.dp, end = 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(subtitleBgColor)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = activeSubtitleText!!,
                        color = subtitleTextColor,
                        fontSize = subtitleFontSizeSp.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = if (subtitleHasShadow) {
                            androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        } else androidx.compose.ui.text.TextStyle.Default
                    )
                }
            }
        }

        // Center Overlay Floating Controls (Previous, Play/Pause, Next) matching user reference image
        AnimatedVisibility(
            visible = showControls && !isControlsLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerManager.previous() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .clickable { playerManager.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = { playerManager.next() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Left Vertical Bar for Brightness Gesture
        AnimatedVisibility(
            visible = isDraggingBrightness,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color.Transparent,
                borderColor = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .height(200.dp)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.WbSunny, contentDescription = "Brightness", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))

                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(currentBrightness.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFF59E0B))
                        )
                    }

                    Text(
                        text = "${(currentBrightness * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Right Vertical Bar for Volume Gesture
        AnimatedVisibility(
            visible = isDraggingVolume,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color.Transparent,
                borderColor = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .height(200.dp)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color(0xFFEEEEEE), modifier = Modifier.size(20.dp))

                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(currentVolume.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFEEEEEE))
                        )
                    }

                    Text(
                        text = "${(currentVolume * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Horizontal Swipe Seek HUD
        AnimatedVisibility(
            visible = isHorizontalDragging,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color.Transparent,
                borderColor = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (seekDeltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (seekDeltaMs >= 0) "+${formatDuration(seekDeltaMs)}" else "-${formatDuration(-seekDeltaMs)}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatDuration(seekTargetPositionMs)} / ${formatDuration(playerState.durationMs)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Gesture Feedback HUD Pill
        AnimatedVisibility(
            visible = gestureFeedbackText != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0x33000000),
                borderColor = Color(0x33FFFFFF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val text = gestureFeedbackText ?: ""
                    val icon = when {
                        text.contains("Volume") -> Icons.Default.VolumeUp
                        text.contains("Brightness") -> Icons.Default.Brightness6
                        text.contains("-") || text.contains("<<") -> Icons.Default.FastRewind
                        else -> Icons.Default.FastForward
                    }
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top Glass / Pill Controls
        AnimatedVisibility(
            visible = (showControls || showOverflowMenu) && !showDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val displayTitle = playerState.currentItem?.title ?: "Video Player"
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val config = androidx.compose.ui.platform.LocalConfiguration.current
                        val isPortrait = config.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                        val isPhone = config.screenWidthDp < 600

                        if (!(isPhone && isPortrait)) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Transparent)
                                    .border(0.5.dp, Color(0xB3FFFFFF), RoundedCornerShape(4.dp))
                                    .clickable {
                                        val nextMode = when (decoderMode) {
                                            "HW+" -> "HW"
                                            "HW" -> "SW"
                                            else -> "HW+"
                                        }
                                        decoderMode = nextMode
                                        scope.launch {
                                            settingsManager.setDecoderMode(nextMode)
                                        }
                                        android.widget.Toast.makeText(context, "Decoder: $nextMode", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 5.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = decoderMode,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Top Right Pill Container (Sidebar, CC, Info, Overflow Menu)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        contentColor = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showDrawer = !showDrawer }) {
                                Icon(
                                    imageVector = Icons.Default.FormatListBulleted,
                                    contentDescription = "Sidebar Queue",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = {
                                showSubtitleSheet = true
                                scope.launch {
                                    val title = playerState.currentItem?.title ?: ""
                                    subtitleList = networkRepository.searchOnlineSubtitles(title, "en", offlineMode)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = "CC Subtitles",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = { showDetailsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "File Info",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sub-top Left Circular Capture Button
                if (!isControlsLocked) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable {
                                captureVideoFrame(context, playerState.currentItem, playerState.currentPositionMs)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Video Frame",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom Glass / Pill Transport Bar matching reference image 2
        AnimatedVisibility(
            visible = (showControls || isHorizontalDragging) && !showDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (isLandscape) 20.dp else 12.dp,
                        start = if (isLandscape) 24.dp else 16.dp,
                        end = if (isLandscape) 24.dp else 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isControlsLocked) {
                    val effectiveSeekPos = if (isHorizontalDragging) seekTargetPositionMs else playerState.currentPositionMs

                    // Seekbar with solid white track and white thumb
                    ThinSeekBar(
                        value = if (playerState.durationMs > 0) effectiveSeekPos.toFloat() else 0f,
                        onValueChange = { playerManager.seekTo(it.toLong()) },
                        valueRange = 0f..(playerState.durationMs.toFloat().coerceAtLeast(1f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        thumbColor = Color.White
                    )

                    // Time Labels directly below seekbar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(effectiveSeekPos),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatDuration(playerState.durationMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Bottom Floating Pill Bar with 5 icons (Lock, Rotate, Speed, PiP, Aspect Ratio)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Lock Button (Left-most)
                        IconButton(onClick = {
                            isControlsLocked = !isControlsLocked
                        }) {
                            Icon(
                                imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock",
                                tint = if (isControlsLocked) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Rotate Button
                        IconButton(onClick = {
                            val activity = context as? android.app.Activity
                            activity?.let { act ->
                                val currentOrientation = act.resources.configuration.orientation
                                act.requestedOrientation = if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        }) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 3. Playback Speed
                        Box(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            val nextSpeed = when (playerState.playbackSpeed) {
                                                0.5f -> 0.75f
                                                0.75f -> 1.0f
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 2.0f
                                                2.0f -> 3.0f
                                                3.0f -> 0.5f
                                                else -> 1.0f
                                            }
                                            playerManager.setPlaybackSpeed(nextSpeed)
                                        },
                                        onLongPress = { showSpeedMenu = true }
                                    )
                                }
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "${playerState.playbackSpeed}x",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // 4. Pop-up / PiP Button (Before Aspect Ratio)
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 5. Aspect Ratio Button (Right-most)
                        Box(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            cropMode = when (cropMode) {
                                                "FIT" -> "CROP"
                                                "CROP" -> "STRETCH"
                                                "STRETCH" -> "ORIGINAL"
                                                else -> "FIT"
                                            }
                                            android.widget.Toast.makeText(context, "Aspect Ratio: $cropMode", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onLongPress = { showAspectRatioMenu = true }
                                    )
                                }
                                .padding(6.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Overflow Menu Popup
        if (showOverflowMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showOverflowMenu = false }
            ) {
                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 56.dp, end = 16.dp)
                        .width(220.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0xF20E111A),
                    borderColor = Color(0x33FFFFFF)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DropdownMenuItem(
                            text = { Text("Info", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                showDetailsSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open with", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                val item = playerState.currentItem
                                if (item?.uri != null) {
                                    val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
                                    val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(sharingUri, item.mimeType.ifEmpty { "video/*" })
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(android.content.Intent.createChooser(openIntent, "Open video with"))
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "No app available to open video", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Show in folder", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                val item = playerState.currentItem
                                if (item != null) {
                                    val folderKey = item.relativePath?.trim('/') ?: item.bucketName ?: "Videos"
                                    val mainIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                                        putExtra("open_screen", "VIDEOS_FOLDER")
                                        putExtra("folder_name", folderKey)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    }
                                    context.startActivity(mainIntent)
                                    onClose()
                                } else {
                                    showDrawer = true
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete / Move to Trash", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                val item = playerState.currentItem
                                if (item?.uri != null) {
                                    try {
                                        com.example.util.FolderHiddenUtils.deleteMediaUri(context, item.uri)
                                        android.widget.Toast.makeText(context, "Deleted video", android.widget.Toast.LENGTH_SHORT).show()
                                        onClose()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Could not delete video", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                val item = playerState.currentItem
                                if (item?.uri != null) {
                                    val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = item.mimeType.ifEmpty { "video/*" }
                                        putExtra(android.content.Intent.EXTRA_STREAM, sharingUri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, item.title)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Video", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                val item = playerState.currentItem
                                if (item?.uri != null) {
                                    val editIntent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                                        setDataAndType(item.uri, item.mimeType.ifEmpty { "video/*" })
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(android.content.Intent.createChooser(editIntent, "Edit Video"))
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "No video editor found on device", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Background Play: ${if (isBackgroundPlayEnabled) "On" else "Off"}", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                isBackgroundPlayEnabled = !isBackgroundPlayEnabled
                                showOverflowMenu = false
                                android.widget.Toast.makeText(context, "Background Play ${if (isBackgroundPlayEnabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Auto Repeat: ${if (isAutoRepeatEnabled) "On" else "Off"}", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                isAutoRepeatEnabled = !isAutoRepeatEnabled
                                playerManager.setRepeatMode(
                                    if (isAutoRepeatEnabled) androidx.media3.common.Player.REPEAT_MODE_ALL else androidx.media3.common.Player.REPEAT_MODE_OFF
                                )
                                showOverflowMenu = false
                                android.widget.Toast.makeText(context, "Auto Repeat ${if (isAutoRepeatEnabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio Tracks", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                showAudioTrackSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Samsung Smart View", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                android.widget.Toast.makeText(context, "Opening Samsung Smart View...", android.widget.Toast.LENGTH_SHORT).show()
                                val castIntent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                                runCatching {
                                    context.startActivity(castIntent)
                                }.onFailure {
                                    val wirelessIntent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                    runCatching { context.startActivity(wirelessIntent) }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Subtitle Customization", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                showSubtitleCustomizationSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings", color = Color.White, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOverflowMenu = false
                                if (onOpenAppSettings != null) {
                                    onOpenAppSettings()
                                } else {
                                    val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                                        putExtra("open_screen", "SETTINGS")
                                        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Sidebar Panel Backdrop (Click outside to close)
        if (showDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showDrawer = false }
            )
        }

        // Sidebar Panel matching sidebar.video.tab.png
        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxHeight(0.92f)
                .width(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 260.dp else 320.dp)
        ) {
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0xF20E111A),
                borderColor = Color(0x33FFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            val breadcrumbParts = remember(playerState.currentItem) {
                                val item = playerState.currentItem
                                val realPath = item?.uri?.let { com.example.ui.components.getFilePathFromUri(context, it) } ?: ""
                                if (realPath.isNotBlank()) {
                                    val storagePrefix = "/storage/emulated/0/"
                                    val cleanPath = if (realPath.startsWith(storagePrefix)) {
                                        realPath.removePrefix(storagePrefix)
                                    } else {
                                        realPath.trim('/')
                                    }
                                    val folderPath = if (cleanPath.contains('/')) cleanPath.substringBeforeLast('/') else cleanPath
                                    val segments = folderPath.split('/').filter { it.isNotBlank() }
                                    if (segments.isNotEmpty()) {
                                        listOf("Internal Storage") + segments
                                    } else {
                                        listOf("Internal Storage", "Download")
                                    }
                                } else {
                                    val rel = item?.relativePath?.trim('/')
                                    if (!rel.isNullOrBlank()) {
                                        listOf("Internal Storage") + rel.split('/').filter { it.isNotBlank() }
                                    } else if (!item?.bucketName.isNullOrBlank()) {
                                        listOf("Internal Storage", item!!.bucketName!!)
                                    } else {
                                        listOf("Internal Storage", "Download", "Movies")
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                breadcrumbParts.forEachIndexed { index, part ->
                                    Text(
                                        text = part,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCBD5E1),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (index < breadcrumbParts.lastIndex) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = Color(0x99FFFFFF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { showDrawer = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playerState.queue) { video ->
                            val isCurrent = video.uri == playerState.currentItem?.uri
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val idx = playerState.queue.indexOf(video)
                                        if (idx != -1) playerManager.playMediaList(playerState.queue, idx)
                                        showDrawer = false
                                    },
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = if (isCurrent) Color(0x33FFFFFF) else Color(0x1AFFFFFF),
                                borderColor = if (isCurrent) Color.White else Color(0x22FFFFFF)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(Color(0xFF581C87), Color(0xFF0F172A))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = video.albumArtUri ?: video.uri,
                                            contentDescription = video.title,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0x99000000))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = safeFormatDuration(context, video),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = video.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitles Options Dialog matching subtile.video.tab.png
        if (showSubtitleSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showSubtitleSheet = false },
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    modifier = Modifier
                        .clickable(enabled = false) {}
                        .width(420.dp)
                        .padding(20.dp),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0xF20E111A),
                    borderColor = Color(0x33FFFFFF)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "CC",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Subtitle Options",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { showSubtitleSheet = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = "Embedded Subtitle Tracks:",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Subtitles Off option
                            val isOffSelected = selectedSubtitleTrackIndex == -1
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSubtitleTrackIndex = -1
                                        playerManager.selectTextTrack(-1)
                                        activeSubtitleText = null
                                    },
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = if (isOffSelected) Color(0x33FFFFFF) else Color(0x1EFFFFFF),
                                borderColor = if (isOffSelected) Color.White else Color(0x1AFFFFFF)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Subtitles Off",
                                        fontSize = 14.sp,
                                        fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White
                                    )
                                    if (isOffSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (embeddedTracks.isNotEmpty()) {
                                embeddedTracks.forEachIndexed { idx, track ->
                                    val isSelected = selectedSubtitleTrackIndex == idx
                                    GlassSurface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedSubtitleTrackIndex = idx
                                                playerManager.selectTextTrack(idx)
                                                activeSubtitleText = null
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = if (isSelected) Color(0x33FFFFFF) else Color(0x1EFFFFFF),
                                        borderColor = if (isSelected) Color.White else Color(0x1AFFFFFF)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${track.name} (${track.language.uppercase()})",
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = Color.White
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFFA78BFA),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "No embedded subtitles found in this media file.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        // Customize Subtitle Style button
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSubtitleSheet = false
                                    showSubtitleCustomizationSheet = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = Color(0x338B5CF6),
                            borderColor = Color(0x448B5CF6)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Style, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                                    Text("Customize Subtitle Style...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Search online subtitles button
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        isSearchingSubtitles = true
                                        val title = playerState.currentItem?.title ?: ""
                                        subtitleList = networkRepository.searchOnlineSubtitles(title, "en", offlineMode)
                                        isSearchingSubtitles = false
                                        if (subtitleList.isEmpty()) {
                                            subtitleStatusMessage = "No online subtitles found for '$title'"
                                        } else {
                                            subtitleStatusMessage = "Found ${subtitleList.size} online subtitles"
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = Color(0x26FFFFFF),
                            borderColor = Color(0x33FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSearchingSubtitles) {
                                    CircularProgressIndicator(color = Color(0xFFA78BFA), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Searching Online...", fontSize = 13.sp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Search & Fetch Subtitles Online",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        subtitleStatusMessage?.let { msg ->
                            Text(text = msg, fontSize = 12.sp, color = Color(0xFFA78BFA))
                        }

                        if (subtitleList.isNotEmpty()) {
                            Text(text = "Online Search Results:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                subtitleList.forEach { onlineSub ->
                                    GlassSurface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeSubtitleText = "${onlineSub.name} (${onlineSub.language.uppercase()})"
                                                subtitleStatusMessage = "Applied: ${onlineSub.name}"
                                                android.widget.Toast.makeText(context, "Applied subtitle: ${onlineSub.name}", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        backgroundColor = Color(0x268B5CF6),
                                        borderColor = Color(0x338B5CF6)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = onlineSub.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(text = "Lang: ${onlineSub.language.uppercase()}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            }
                                            Icon(Icons.Default.CloudDownload, contentDescription = "Select", tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Video Player Settings Dialog
        if (showSettingsSheet) {
            AlertDialog(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color(0xDC121522),
                shape = RoundedCornerShape(20.dp),
                title = { Text(text = "Video Player Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedButton(
                            onClick = {
                                showSettingsSheet = false
                                showDetailsSheet = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View File Info")
                        }

                        // Repeat mode
                        Text(text = "Repeat Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF,
                                onClick = { playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF) },
                                label = { Text("Off") }
                            )
                            FilterChip(
                                selected = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE,
                                onClick = { playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE) },
                                label = { Text("Repeat One") }
                            )
                            FilterChip(
                                selected = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL,
                                onClick = { playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL) },
                                label = { Text("Repeat All") }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Playback Speed
                        Text(text = "Playback Speed: ${playerState.playbackSpeed}x", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val speedList = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
                            items(speedList) { spd ->
                                FilterChip(
                                    selected = playerState.playbackSpeed == spd,
                                    onClick = { playerManager.setPlaybackSpeed(spd) },
                                    label = { Text("${spd}x") }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Aspect Ratio Scale Mode
                        Text(text = "Aspect Ratio / Scale Mode: $cropMode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = cropMode == "FIT",
                                onClick = { cropMode = "FIT" },
                                label = { Text("Fit") }
                            )
                            FilterChip(
                                selected = cropMode == "CROP",
                                onClick = { cropMode = "CROP" },
                                label = { Text("Fill / Crop") }
                            )
                            FilterChip(
                                selected = cropMode == "STRETCH",
                                onClick = { cropMode = "STRETCH" },
                                label = { Text("Stretch") }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(text = "Picture Mode: ${com.example.ui.components.PictureMode.fromKey(pictureMode).displayName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(com.example.ui.components.PictureMode.entries) { mode ->
                                FilterChip(
                                    selected = pictureMode.equals(mode.key, ignoreCase = true),
                                    onClick = {
                                        scope.launch {
                                            settingsManager.setPictureMode(mode.key)
                                        }
                                    },
                                    label = { Text(mode.displayName) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Audio Sync Delay Adjustment
                        Text(text = "Audio Sync Delay: ${audioSyncOffsetMs}ms", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = { audioSyncOffsetMs -= 100 }) { Text("-100ms") }
                            OutlinedButton(onClick = { audioSyncOffsetMs += 100 }) { Text("+100ms") }
                            OutlinedButton(onClick = { audioSyncOffsetMs = 0 }) { Text("Reset") }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(text = "Audio Boost (Loudness Enhancer): ${playerState.audioBoostPercent}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Slider(
                            value = playerState.audioBoostPercent.toFloat(),
                            onValueChange = { playerManager.setAudioBoost(it.toInt()) },
                            valueRange = 0f..100f
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Film Grain Overlay", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Switch(
                                checked = isFilmGrainEnabled,
                                onCheckedChange = { isFilmGrainEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF94A3B8),
                                    checkedBorderColor = Color(0xFFCBD5E1),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = Color(0x3364748B),
                                    uncheckedBorderColor = Color(0x44CBD5E1)
                                )
                            )
                        }

                        if (isFilmGrainEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Grain Intensity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = filmGrainIntensity,
                                onValueChange = { filmGrainIntensity = it },
                                valueRange = 0.05f..0.30f
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsSheet = false }) {
                        Text("Close", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        // Aspect Ratio Modal (Bottom Center, 70% width, tap outside to close)
        if (showAspectRatioMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { showAspectRatioMenu = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .padding(bottom = 24.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0xF2121522),
                    borderColor = Color(0x33FFFFFF),
                    enableBlur = true,
                    blurRadius = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aspect Ratio",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val aspectRatios = listOf(
                            "FIT" to "Fit",
                            "CROP" to "Crop",
                            "STRETCH" to "Stretch",
                            "ORIGINAL" to "Original",
                            "16:9" to "16:9",
                            "4:3" to "4:3",
                            "FILL" to "Fill"
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(aspectRatios) { (code, label) ->
                                val isSelected = cropMode.equals(code, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            cropMode = code
                                            showAspectRatioMenu = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0x80FFFFFF),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playback Speed Modal (Bottom Center, 70% width, tap outside to close)
        if (showSpeedMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { showSpeedMenu = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .padding(bottom = 24.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0xF2121522),
                    borderColor = Color(0x33FFFFFF),
                    enableBlur = true,
                    blurRadius = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Playback Speed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(speeds) { speed ->
                                val isSelected = playerState.playbackSpeed == speed
                                val rawLabel = if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "$speed"
                                val displayLabel = if (isSelected) "${rawLabel}x" else if (speed == 1.0f) "1.0x" else rawLabel

                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            playerManager.setPlaybackSpeed(speed)
                                            showSpeedMenu = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayLabel,
                                        color = if (isSelected) Color.White else Color(0x80FFFFFF),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAudioTrackSheet) {
            AdaptiveBottomSheet(
                onDismissRequest = { showAudioTrackSheet = false },
                containerColor = Color(0xDC121522)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Select Audio Track",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    val availableAudioTracks = remember(showAudioTrackSheet, playerState.currentItem) {
                        playerManager.getAvailableAudioTracks()
                    }

                    if (availableAudioTracks.isNotEmpty()) {
                        availableAudioTracks.forEach { track ->
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerManager.selectAudioTrack(track.index)
                                        showAudioTrackSheet = false
                                        android.widget.Toast.makeText(context, "Selected ${track.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = if (track.isSelected) Color(0x44FFFFFF) else Color(0x1EFFFFFF),
                                borderColor = if (track.isSelected) Color.White else Color(0x1AFFFFFF)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = track.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                    if (track.isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = Color(0x33FFFFFF),
                            borderColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Default Audio Stream",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showDetailsSheet && playerState.currentItem != null) {
            MediaInfoBottomSheet(
                item = playerState.currentItem,
                onDismiss = { showDetailsSheet = false }
            )
        }

        if (showSubtitleCustomizationSheet) {
            AdaptiveBottomSheet(
                onDismissRequest = { showSubtitleCustomizationSheet = false },
                containerColor = Color(0xDC121522)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Subtitle Styling & Customization",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(subtitleBgColor)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = activeSubtitleText ?: "Sample Subtitle Text 123",
                                color = subtitleTextColor,
                                fontSize = subtitleFontSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                style = if (subtitleHasShadow) {
                                    androidx.compose.ui.text.TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black,
                                            offset = Offset(2f, 2f),
                                            blurRadius = 4f
                                        )
                                    )
                                } else androidx.compose.ui.text.TextStyle.Default
                            )
                        }
                    }

                    // Size Slider
                    Column {
                        Text(text = "Font Size: ${subtitleFontSizeSp.toInt()} sp", fontSize = 13.sp, color = Color.White)
                        Slider(
                            value = subtitleFontSizeSp,
                            onValueChange = { subtitleFontSizeSp = it },
                            valueRange = 12f..36f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFFEEEEEE),
                                inactiveTrackColor = Color(0x40FFFFFF)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        )
                    }

                    // Text Color selector (Scrollable chips)
                    Column {
                        Text(text = "Text Color", fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colors = listOf(
                                "White" to Color.White,
                                "Dark Grey" to Color(0xFF333333),
                                "Silver" to Color(0xFFC0C0C0),
                                "Yellow" to Color.Yellow,
                                "Cyan" to Color.Cyan,
                                "Green" to Color.Green,
                                "Pink" to Color(0xFFFF4081)
                            )
                            colors.forEach { (label, c) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (subtitleTextColor == c) Color(0xFFEEEEEE) else Color(0x22FFFFFF))
                                        .clickable { subtitleTextColor = c }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(c).border(0.5.dp, Color.White, CircleShape))
                                        Text(text = label, fontSize = 11.sp, color = if (subtitleTextColor == c) Color(0xFF0F172A) else Color.White, fontWeight = if (subtitleTextColor == c) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    // Background Color selector (Scrollable chips)
                    Column {
                        Text(text = "Background & Opacity", fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bgColors = listOf(
                                "10% Opacity" to Color(0x1A000000),
                                "Dark Grey" to Color(0xCC222222),
                                "Silver" to Color(0xCCC0C0C0),
                                "Dark (50%)" to Color(0x80000000),
                                "Dark (80%)" to Color(0xCC000000),
                                "Solid Black" to Color(0xFF000000),
                                "Transparent" to Color.Transparent
                            )
                            bgColors.forEach { (label, bgC) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (subtitleBgColor == bgC) Color(0xFFEEEEEE) else Color(0x22FFFFFF))
                                        .clickable { subtitleBgColor = bgC }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = if (subtitleBgColor == bgC) Color(0xFF0F172A) else Color.White,
                                        fontWeight = if (subtitleBgColor == bgC) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Text Shadow Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Text Drop Shadow", fontSize = 13.sp, color = Color.White)
                        Switch(
                            checked = subtitleHasShadow,
                            onCheckedChange = { subtitleHasShadow = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF64748B)
                            )
                        )
                    }

                    Button(
                        onClick = { showSubtitleCustomizationSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Text("Apply Style", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun safeFormatDuration(context: android.content.Context, item: MediaItem): String {
    if (item.durationMs > 0) return formatDuration(item.durationMs)
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, item.uri)
        val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        val duration = timeStr?.toLongOrNull() ?: 0L
        if (duration > 0) formatDuration(duration) else "00:00"
    } catch (e: Exception) {
        "00:00"
    }
}

private fun captureVideoFrame(context: android.content.Context, item: MediaItem?, currentPositionMs: Long) {
    if (item?.uri == null) return
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, item.uri)
            val positionUs = currentPositionMs * 1000L
            val bitmap = retriever.getFrameAtTime(positionUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            if (bitmap != null) {
                saveScreenshot(context, bitmap)
            } else {
                (context as? android.app.Activity)?.runOnUiThread {
                    android.widget.Toast.makeText(context, "Frame extraction failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            (context as? android.app.Activity)?.runOnUiThread {
                android.widget.Toast.makeText(context, "Unable to extract frame", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun saveScreenshot(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val filename = "Video_Screenshot_${System.currentTimeMillis()}.png"
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Screenshots")
        }
    }
    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
    (context as? android.app.Activity)?.runOnUiThread {
        android.widget.Toast.makeText(context, "Screenshot captured & saved!", android.widget.Toast.LENGTH_SHORT).show()
    }
}
