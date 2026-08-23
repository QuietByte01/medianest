@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.medianest.ui.videoplayer

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.medianest.MediaNestApp
import com.medianest.data.db.PlaybackState
import com.medianest.data.repository.NetworkRepository
import com.medianest.data.repository.SubtitleProvider
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.videoplayer.studio.VideoEditorStudioSheet
import com.medianest.ui.videoplayer.panels.*
import com.medianest.ui.components.SidebarQueueDrawer
import com.medianest.ui.components.media.*
import com.medianest.ui.components.dismissKeyboardOnOutsideTap
import com.medianest.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Video Playback Screen.
 * Logic for gestures, picture-in-picture, and UI states is handled here.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
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
    val currentItem = playerState.currentItem?.takeIf { it.type == com.medianest.data.db.MediaType.VIDEO }
    val settingsManager = MediaNestApp.instance.settingsManager

    val showStatusBar by settingsManager.showStatusBarInPlayback.collectAsState(initial = false)
    val keepScreenOn by settingsManager.keepScreenOn.collectAsState(initial = true)
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val hiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())

    val pictureModeEnabled by settingsManager.pictureModeEnabled.collectAsState(initial = true)
    val pictureMode by settingsManager.pictureMode.collectAsState(initial = "BALANCED")
    val customSat by settingsManager.customSaturation.collectAsState(initial = 1.18f)
    val customCon by settingsManager.customContrast.collectAsState(initial = 1.06f)
    val customWarmth by settingsManager.customWarmth.collectAsState(initial = 0.03f)

    val isFilmGrainEnabled by settingsManager.filmGrainEnabled.collectAsState(initial = false)
    val filmGrainIntensity by settingsManager.filmGrainIntensity.collectAsState(initial = 0.15f)

    var showControls by remember { mutableStateOf(true) }
    var cropMode by remember { mutableStateOf(MediaAspectRatio.FIT) } 
    val savedDecoderMode by settingsManager.decoderMode.collectAsState(initial = "HW+")
    var decoderMode by remember(savedDecoderMode) { mutableStateOf(savedDecoderMode) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            kotlinx.coroutines.delay(1000L) // Hide feedback after 1s
            gestureFeedbackText = null
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isHorizontalDragging by remember { mutableStateOf(false) }
    var seekTargetPositionMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedSubtitleTrackIndex by remember { mutableIntStateOf(0) }

    var showDrawer by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleCustomizationSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showVideoFxSheet by remember { mutableStateOf(false) }
    var showVideoEditorSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var activeSubtitleText by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var swipeEdgeState by remember { mutableStateOf(SwipeEdge.NONE) }
    var swipeProgressState by remember { mutableFloatStateOf(0f) }

    var subtitleList by remember { mutableStateOf<List<com.medianest.data.model.SubtitleItem>>(emptyList()) }
    var audioSyncOffsetMs by remember { mutableLongStateOf(0L) }
    var isSearchingSubtitles by remember { mutableStateOf(false) }
    var subtitleStatusMessage by remember { mutableStateOf<String?>(null) }

    var subtitleFontSizeSp by remember { mutableFloatStateOf(20f) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var subtitleBgColor by remember { mutableStateOf(Color(0x99000000)) }
    var subtitleHasShadow by remember { mutableStateOf(true) }

    val embeddedTracks = remember(currentItem) {
        val list = mutableListOf<com.medianest.data.model.SubtitleItem>()
        try {
            val tracks = playerManager.exoPlayer.currentTracks
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language ?: "und"
                        val label = format.label ?: "Track ${i + 1}"
                        list.add(com.medianest.data.model.SubtitleItem(id = "embedded_$i", name = label, language = lang, isLocal = true))
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    var isControlsLocked by remember { mutableStateOf(false) }

    val anyOverlayOpen = showOverflowMenu || showDetailsSheet || showDrawer || showSubtitleSheet ||
            showSubtitleCustomizationSheet || showSettingsSheet || showAudioTrackSheet ||
            showAspectRatioMenu || showSpeedMenu

    BackHandler(enabled = true) {
        if (anyOverlayOpen) {
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
            }
        } else if (isControlsLocked) {
            isControlsLocked = false
        } else {
            onClose()
        }
    }

    var activeVideoSize by remember(playerManager.exoPlayer) {
        mutableStateOf(playerManager.exoPlayer.videoSize)
    }

    DisposableEffect(playerManager.exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    activeVideoSize = videoSize
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val vs = playerManager.exoPlayer.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    activeVideoSize = vs
                }
            }
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                if (selectedSubtitleTrackIndex != -1 && cueGroup.cues.isNotEmpty()) {
                    activeSubtitleText = cueGroup.cues.joinToString("\n") { it.text ?: "" }.trim().ifEmpty { null }
                } else {
                    activeSubtitleText = null
                }
            }
        }
        playerManager.exoPlayer.addListener(listener)
        val initialVs = playerManager.exoPlayer.videoSize
        if (initialVs.width > 0 && initialVs.height > 0) {
            activeVideoSize = initialVs
        }
        onDispose { playerManager.exoPlayer.removeListener(listener) }
    }

    DisposableEffect(showStatusBar, keepScreenOn) {
        activity?.let { act ->
            if (keepScreenOn) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    LaunchedEffect(showControls, anyOverlayOpen, isControlsLocked) {
        if (showControls && !isControlsLocked && !anyOverlayOpen) {
            delay(3000) // Hide controls after 3s
            showControls = false
        }
    }

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    var currentBrightness by remember { mutableFloatStateOf(0.7f) }

    LaunchedEffect(isDraggingBrightness, currentBrightness) {
        if (isDraggingBrightness) {
            delay(1200L)
            isDraggingBrightness = false
        }
    }

    LaunchedEffect(isDraggingVolume, currentVolume) {
        if (isDraggingVolume) {
            delay(1200L)
            isDraggingVolume = false
        }
    }

    val controlsFadeSpec = tween<Float>(durationMillis = 500)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .dismissKeyboardOnOutsideTap()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoPlayerGestures(
                    playerState = playerState,
                    isControlsLocked = isControlsLocked,
                    isZoomed = scale > 1.05f,
                    onToggleControls = { showControls = !showControls },
                    onSeekDelta = { delta ->
                        isHorizontalDragging = true
                        seekDeltaMs += delta
                        seekTargetPositionMs = (playerState.currentPositionMs + seekDeltaMs).coerceIn(0L, playerState.durationMs)
                    },
                    onSeekTo = { playerManager.seekTo(it) },
                    onVolumeChange = { delta ->
                        isDraggingVolume = true
                        currentVolume = (currentVolume + delta).coerceIn(0f, 2.0f)
                        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        if (currentVolume <= 1.0f) {
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (currentVolume * maxVol).toInt(), 0)
                            playerManager.setVolumeBoost(0)
                        } else {
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol, 0)
                            val boostPercent = ((currentVolume - 1.0f) * 100f).toInt().coerceIn(0, 100)
                            playerManager.setVolumeBoost(boostPercent)
                        }
                    },
                    onBrightnessChange = { delta ->
                        isDraggingBrightness = true
                        currentBrightness = (currentBrightness + delta).coerceIn(0.05f, 1f)
                        activity?.let { act ->
                            val lp = act.window.attributes
                            lp.screenBrightness = currentBrightness
                            act.window.attributes = lp
                        }
                    },
                    onScaleChange = { zoom -> 
                        scale = (scale * zoom).coerceIn(1f, 10f)
                        if (scale <= 1.05f) {
                            scale = 1f
                            panOffset = Offset.Zero
                        }
                    },
                    onPanDelta = { delta ->
                        panOffset += delta
                    },
                    onSeekBackward = { playerManager.seekBackward(10000L); gestureFeedbackText = "-10s" },
                    onSeekForward = { playerManager.seekForward(10000L); gestureFeedbackText = "+10s" },
                    onTogglePlayPause = { playerManager.togglePlayPause() },
                    onFeedback = { gestureFeedbackText = it },
                    onDragStarted = { showControls = true },
                    onDragEnded = {
                        if (isHorizontalDragging) {
                            playerManager.seekTo(seekTargetPositionMs)
                            isHorizontalDragging = false
                            seekDeltaMs = 0L
                        }
                        isDraggingBrightness = false
                        isDraggingVolume = false
                    },
                    onEdgeSwipeProgress = { edge, progress ->
                        swipeEdgeState = edge
                        swipeProgressState = progress
                    },
                    coroutineScope = scope
                )
        ) {
            // Video Surface Container with Aspect Ratio Bounds
            val density = LocalDensity.current
            
            // ExoPlayer VideoSize reports the *encoded* width/height plus any unapplied rotation.
            // We need to compute the *display* aspect ratio (what the user actually sees).
            // pixelWidthHeightRatio compensates for non-square pixels (mostly = 1.0 for modern content).
            val rotation = activeVideoSize.unappliedRotationDegrees
            val isRotated = rotation == 90 || rotation == 270
            val pixelRatio = if (activeVideoSize.pixelWidthHeightRatio > 0f) activeVideoSize.pixelWidthHeightRatio else 1f

            val rawEncodedWidth = if (activeVideoSize.width > 0) activeVideoSize.width.toFloat() else 0f
            val rawEncodedHeight = if (activeVideoSize.height > 0) activeVideoSize.height.toFloat() else 0f

            val videoAspectRatio: Float
            if (rawEncodedWidth > 0f && rawEncodedHeight > 0f) {
                // Player told us the actual dimensions — swap if needed for rotated videos
                val displayWidth = (if (isRotated) rawEncodedHeight else rawEncodedWidth) * pixelRatio
                val displayHeight = if (isRotated) rawEncodedWidth else rawEncodedHeight
                videoAspectRatio = (displayWidth / displayHeight.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)
            } else {
                // Player hasn't reported size yet — use MediaStore dimensions directly.
                // Note: MediaStoreRepository already swapped width/height for rotated videos,
                // so currentItem.width/height represent the correct *display* dimensions.
                val itemW = (currentItem?.width?.takeIf { it > 0 } ?: 1920).toFloat()
                val itemH = (currentItem?.height?.takeIf { it > 0 } ?: 1080).toFloat()
                videoAspectRatio = (itemW / itemH.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val containerW = maxWidth.value
                val containerH = maxHeight.value.coerceAtLeast(0.01f)
                val containerRatio = containerW / containerH

                val surfaceModifier = when (cropMode) {
                    MediaAspectRatio.FIT -> {
                        Modifier.aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = videoAspectRatio <= containerRatio)
                    }
                    MediaAspectRatio.CROP, MediaAspectRatio.STRETCH -> {
                        Modifier.fillMaxSize()
                    }
                    MediaAspectRatio.ORIGINAL -> {
                        val w = if (rawEncodedWidth > 0f) rawEncodedWidth else (currentItem?.width?.takeIf { it > 0 } ?: 1920).toFloat()
                        val h = if (rawEncodedHeight > 0f) rawEncodedHeight else (currentItem?.height?.takeIf { it > 0 } ?: 1080).toFloat()
                        val widthDp = with(density) { w.toDp() }
                        val heightDp = with(density) { h.toDp() }
                        Modifier
                            .wrapContentSize(Alignment.Center, unbounded = true)
                            .requiredSize(widthDp, heightDp)
                    }
                    else -> {
                        // Fixed Aspect Ratio Presets (16:9, 16:10, 4:3, 1:1, 9:16, 4:5, 21:9):
                        // Fit the container window to the selected target aspect ratio within the screen,
                        // and RESIZE_MODE_ZOOM crops the video to completely fill this target ratio window.
                        val targetRatio = cropMode.ratio ?: videoAspectRatio
                        Modifier.aspectRatio(targetRatio, matchHeightConstraintsFirst = targetRatio <= containerRatio)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, 
                            scaleY = scale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = surfaceModifier.clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.runtime.key(playerState.activeEngineName) {
                            if (playerState.activeEngineName == "Media3") {
                                AndroidView(
                                    factory = { ctx ->
                                        Log.i("VideoPlayerScreen", "Creating NEW PlayerView for Media3")
                                        PlayerView(ctx).apply {
                                            useController = false
                                            try {
                                                this.player = playerManager.exoPlayer
                                            } catch (_: Exception) {}
                                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                        }
                                    },
                                    update = { view ->
                                        @androidx.annotation.OptIn(UnstableApi::class)
                                        fun applySettings() {
                                            try {
                                                val currentExo = playerManager.exoPlayer
                                                if (view.player != currentExo) {
                                                    Log.i("VideoPlayerScreen", "Syncing PlayerView with new ExoPlayer instance")
                                                    view.player = currentExo
                                                }
                                            } catch (e: Exception) {
                                                Log.e("VideoPlayerScreen", "Error syncing player", e)
                                            }
                                            
                                            // FIT & ORIGINAL: fit video into the frame.
                                            // STRETCH: fill frame without preserving aspect ratio.
                                            // CROP & all fixed presets (16:9, 16:10, 4:3, 1:1, 9:16, 4:5, 21:9):
                                            // RESIZE_MODE_ZOOM zooms the video so it completely fills the target frame and crops the excess!
                                            view.resizeMode = when (cropMode) {
                                                MediaAspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                MediaAspectRatio.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                MediaAspectRatio.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            }
                                        }
                                        applySettings()
                                        try {
                                            val androidFilter = com.medianest.ui.components.PictureModeUtils.getAndroidColorFilter(
                                                modeKey = pictureMode,
                                                customSat = customSat, customCon = customCon, customWarmth = customWarmth,
                                                enabled = pictureModeEnabled
                                            )
                                            if (androidFilter != null) {
                                                val paint = android.graphics.Paint().apply { colorFilter = androidFilter }
                                                view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                                            } else {
                                                view.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                            }
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // FFmpeg Rendering Path (TextureView)
                                AndroidView(
                                    factory = { ctx ->
                                        Log.i("VideoPlayerScreen", "Creating TextureView for FFmpeg")
                                        android.view.TextureView(ctx).apply {
                                            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                                private var activeSurface: android.view.Surface? = null
                                                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                                    Log.i("VideoPlayerScreen", "FFmpeg SurfaceTexture Available")
                                                    activeSurface?.release()
                                                    activeSurface = android.view.Surface(st)
                                                    playerManager.setVideoSurface(activeSurface)
                                                }
                                                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                                    if (activeSurface == null) {
                                                        activeSurface = android.view.Surface(st)
                                                    }
                                                    playerManager.setVideoSurface(activeSurface)
                                                }
                                                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                                                    Log.i("VideoPlayerScreen", "FFmpeg SurfaceTexture Destroyed")
                                                    playerManager.setVideoSurface(null)
                                                    activeSurface?.release()
                                                    activeSurface = null
                                                    return true
                                                }
                                                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                                            }
                                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                        }
                                    },
                                    update = { view ->
                                        val viewWidth = view.width.toFloat()
                                        val viewHeight = view.height.toFloat()
                                        val dispWidth = (if (isRotated) rawEncodedHeight else rawEncodedWidth)
                                            .let { if (it > 0f) it else (currentItem?.width?.takeIf { w -> w > 0 } ?: 1920).toFloat() }
                                        val dispHeight = (if (isRotated) rawEncodedWidth else rawEncodedHeight)
                                            .let { if (it > 0f) it else (currentItem?.height?.takeIf { h -> h > 0 } ?: 1080).toFloat() }
                                        if (viewWidth > 0 && viewHeight > 0 && dispWidth > 0 && dispHeight > 0) {
                                            val matrix = android.graphics.Matrix()
                                            val viewRatio = viewWidth / viewHeight
                                            val vidRatio = dispWidth / dispHeight
                                            
                                            val scaleX: Float
                                            val scaleY: Float
                                            
                                            when (cropMode) {
                                                MediaAspectRatio.STRETCH -> {
                                                    scaleX = 1f
                                                    scaleY = 1f
                                                }
                                                MediaAspectRatio.FIT, MediaAspectRatio.ORIGINAL -> {
                                                    if (vidRatio > viewRatio) {
                                                        scaleX = 1f
                                                        scaleY = viewRatio / vidRatio
                                                    } else {
                                                        scaleX = vidRatio / viewRatio
                                                        scaleY = 1f
                                                    }
                                                }
                                                else -> {
                                                    // Crop mode & all fixed ratios: zoom to cover entire view without stretching
                                                    if (vidRatio > viewRatio) {
                                                        scaleX = vidRatio / viewRatio
                                                        scaleY = 1f
                                                    } else {
                                                        scaleX = 1f
                                                        scaleY = viewRatio / vidRatio
                                                    }
                                                }
                                            }
                                            matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
                                            view.setTransform(matrix)
                                        }

                                        try {
                                            val androidFilter = com.medianest.ui.components.PictureModeUtils.getAndroidColorFilter(
                                                modeKey = pictureMode,
                                                customSat = customSat, customCon = customCon, customWarmth = customWarmth,
                                                enabled = pictureModeEnabled
                                            )
                                            if (androidFilter != null) {
                                                val paint = android.graphics.Paint().apply { colorFilter = androidFilter }
                                                view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                                            } else {
                                                view.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                            }
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (isFilmGrainEnabled) {
                            FilmGrainOverlay(intensity = filmGrainIntensity, modifier = Modifier.matchParentSize())
                        }
                    }
                }
            }

            ZoomPercentagePill(
                scale = scale, 
                onReset = { 
                    scale = 1f
                    panOffset = Offset.Zero
                }, 
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 160.dp, end = 24.dp)
            )
            SubtitleTextOverlay(text = activeSubtitleText, isVisible = showControls, fontSizeSp = subtitleFontSizeSp, textColor = subtitleTextColor, bgColor = subtitleBgColor, hasShadow = subtitleHasShadow, modifier = Modifier.align(Alignment.BottomCenter))
        }

        AnimatedVisibility(
            visible = showControls && !isControlsLocked && !isHorizontalDragging,
            enter = fadeIn(animationSpec = controlsFadeSpec),
            exit = fadeOut(animationSpec = controlsFadeSpec),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterTransportControls(isPlaying = playerState.isPlaying, onPrevious = { playerManager.previous() }, onNext = { playerManager.next() }, onTogglePlayPause = { playerManager.togglePlayPause() })
        }

        AnimatedVisibility(visible = isDraggingBrightness, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp)) {
            VerticalGestureHUD(value = currentBrightness, icon = Icons.Default.WbSunny, color = Color(0xFFF59E0B))
        }

        AnimatedVisibility(visible = isDraggingVolume, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)) {
            VerticalGestureHUD(value = currentVolume, icon = Icons.Default.VolumeUp, color = Color.White)
        }

        AnimatedVisibility(visible = gestureFeedbackText != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
            gestureFeedbackText?.let { GestureFeedbackHUD(text = it) }
        }

        AnimatedVisibility(
            visible = (showControls || showOverflowMenu || showDetailsSheet || showSubtitleSheet) && !isHorizontalDragging && !showDrawer,
            enter = fadeIn(animationSpec = controlsFadeSpec),
            exit = fadeOut(animationSpec = controlsFadeSpec),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            VideoPlayerTopBar(
                playerState = playerState, onClose = onClose, decoderMode = decoderMode,
                onDecoderModeClick = {
                    decoderMode = when (decoderMode) { "HW+" -> "HW"; "HW" -> "SW"; else -> "HW+" }
                    scope.launch { settingsManager.setDecoderMode(decoderMode) }
                },
                onDrawerClick = { showDrawer = true }, onSubtitleClick = { showSubtitleSheet = true },
                onInfoClick = { showDetailsSheet = true }, onMenuClick = { showOverflowMenu = true },
                onCaptureClick = { captureVideoFrame(context, currentItem, playerState.currentPositionMs) },
                isControlsLocked = isControlsLocked
            )
        }

        AnimatedVisibility(
            visible = (showControls || isHorizontalDragging) && !showDrawer && !showSubtitleSheet && !showDetailsSheet && !showSettingsSheet && !showAudioTrackSheet,
            enter = fadeIn(animationSpec = controlsFadeSpec),
            exit = fadeOut(animationSpec = controlsFadeSpec),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoPlayerBottomBar(
                playerState = playerState, isControlsLocked = isControlsLocked, isHorizontalDragging = isHorizontalDragging,
                seekTargetPositionMs = seekTargetPositionMs, onSeek = { playerManager.seekTo(it) },
                isControlsLockedState = isControlsLocked, onLockClick = { isControlsLocked = !isControlsLocked },
                onRotateClick = {
                    activity?.let { act ->
                        val currentOrientation = act.resources.configuration.orientation
                        act.requestedOrientation = if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                },
                onSpeedClick = {
                    val nextSpeed = when (playerState.playbackSpeed) { 0.5f -> 0.75f; 0.75f -> 1.0f; 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; 2.0f -> 3.0f; 3.0f -> 0.5f; else -> 1.0f }
                    playerManager.setPlaybackSpeed(nextSpeed)
                },
                onSpeedLongPress = { showSpeedMenu = true }, onPipClick = onEnterPip,
                onAspectRatioClick = {
                    cropMode = when (cropMode) {
                        MediaAspectRatio.FIT -> MediaAspectRatio.CROP
                        MediaAspectRatio.CROP -> MediaAspectRatio.P_16_9
                        MediaAspectRatio.P_16_9 -> MediaAspectRatio.P_16_10
                        MediaAspectRatio.P_16_10 -> MediaAspectRatio.P_4_3
                        MediaAspectRatio.P_4_3 -> MediaAspectRatio.P_1_1
                        MediaAspectRatio.P_1_1 -> MediaAspectRatio.P_9_16
                        MediaAspectRatio.P_9_16 -> MediaAspectRatio.P_4_5
                        MediaAspectRatio.P_4_5 -> MediaAspectRatio.P_21_9
                        MediaAspectRatio.P_21_9 -> MediaAspectRatio.ORIGINAL
                        MediaAspectRatio.ORIGINAL -> MediaAspectRatio.STRETCH
                        else -> MediaAspectRatio.FIT
                    }
                    scale = 1f
                    panOffset = Offset.Zero
                    gestureFeedbackText = "Aspect Ratio: ${cropMode.label}"
                },
                onAspectRatioLongPress = { showAspectRatioMenu = true }
            )
        }

        if (showDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showDrawer = false }
            )
            SidebarQueueDrawer(
                playerState = playerState,
                onVideoClick = { playerManager.playMediaList(playerState.queue, playerState.queue.indexOf(it)) },
                onClose = { showDrawer = false },
                context = context,
                showHidden = showHiddenFiles,
                hiddenFolders = hiddenFolders,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxHeight(0.92f)
                    .width(if (LocalConfiguration.current.screenWidthDp < 600) 260.dp else 320.dp)
            )
        }
        
        if (showSubtitleSheet) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showSubtitleSheet = false }, contentAlignment = Alignment.Center) {
            SubtitleOptionsDialog(
                playerState = playerState,
                embeddedTracks = embeddedTracks,
                selectedTrackIndex = selectedSubtitleTrackIndex,
                onTrackSelect = { selectedSubtitleTrackIndex = it; playerManager.selectTextTrack(it) },
                onCustomizeClick = { showSubtitleSheet = false; showSubtitleCustomizationSheet = true },
                onSearchOnline = { provider ->
                    scope.launch {
                        isSearchingSubtitles = true
                        subtitleStatusMessage = null
                        subtitleList = emptyList() 
                        subtitleList = networkRepository.searchOnlineSubtitles(
                            currentItem?.title ?: "", 
                            "en", 
                            offlineMode,
                            provider
                        )
                        isSearchingSubtitles = false
                        if (subtitleList.isEmpty()) {
                            subtitleStatusMessage = "No subtitles found from ${if (provider == SubtitleProvider.OPEN_SUBTITLES) "OpenSubtitles" else "Community sources"}."
                        }
                    }
                },
                isSearching = isSearchingSubtitles,
                onlineSubtitles = subtitleList,
                onOnlineSubClick = { subItem ->
                    scope.launch {
                        subtitleStatusMessage = "Downloading subtitle..."
                        val subUri = networkRepository.downloadSubtitleFile(context, subItem)
                        if (subUri != null) {
                            playerManager.addExternalSubtitle(subUri, subItem.name)
                            subtitleStatusMessage = "Subtitle applied successfully!"
                            selectedSubtitleTrackIndex = 0 
                            delay(1500)
                            showSubtitleSheet = false
                        } else {
                            subtitleStatusMessage = "Failed to download subtitle file."
                        }
                    }
                },
                statusMessage = subtitleStatusMessage,
                onClose = { showSubtitleSheet = false },
                modifier = Modifier.width(420.dp).padding(20.dp)
            )
        }

        if (showSubtitleCustomizationSheet) SubtitleCustomizationSheet(onDismiss = { showSubtitleCustomizationSheet = false }, activeSubtitleText = activeSubtitleText, fontSizeSp = subtitleFontSizeSp, onFontSizeChange = { subtitleFontSizeSp = it }, textColor = subtitleTextColor, onTextColorChange = { subtitleTextColor = it }, bgColor = subtitleBgColor, onBgColorChange = { subtitleBgColor = it }, hasShadow = subtitleHasShadow, onHasShadowChange = { subtitleHasShadow = it })
        
        if (showAudioTrackSheet) AudioTrackSelectionSheet(
            onDismiss = { showAudioTrackSheet = false },
            playerManager = playerManager,
            audioSyncOffsetMs = audioSyncOffsetMs,
            onAudioSyncOffsetChange = { audioSyncOffsetMs = it },
            context = context
        )
        
        if (showVideoFxSheet) {
            VideoPostProcessingPanel(
                currentEffect = MediaEffect.fromString(pictureMode),
                onEffectChange = { newMode: MediaEffect -> 
                    scope.launch {
                        settingsManager.setPictureMode(newMode.name)
                        settingsManager.setPictureModeEnabled(true)
                    }
                    gestureFeedbackText = "Video FX: ${newMode.label}"
                },
                onDismiss = { showVideoFxSheet = false }
            )
        }

        if (showVideoEditorSheet && currentItem != null) {
            VideoEditorStudioSheet(
                mediaItem = currentItem!!,
                onDismiss = { showVideoEditorSheet = false }
            )
        }
        
        if (showSettingsSheet) VideoPlayerSettingsDialog(
            onDismiss = { showSettingsSheet = false },
            playerState = playerState,
            playerManager = playerManager,
            pictureMode = pictureMode,
            onPictureModeChange = { scope.launch { settingsManager.setPictureMode(it) } },
            audioSyncOffsetMs = audioSyncOffsetMs,
            onAudioSyncOffsetChange = { audioSyncOffsetMs = it },
            isFilmGrainEnabled = isFilmGrainEnabled,
            onFilmGrainEnabledChange = { scope.launch { settingsManager.setFilmGrainEnabled(it) } },
            filmGrainIntensity = filmGrainIntensity,
            onFilmGrainIntensityChange = { scope.launch { settingsManager.setFilmGrainIntensity(it) } },
            onShowDetails = { showSettingsSheet = false; showDetailsSheet = true }
        )
        
        if (showAspectRatioMenu) AspectRatioModal(currentMode = cropMode, onModeChange = { mode: MediaAspectRatio -> 
            cropMode = mode
            scale = 1f
            gestureFeedbackText = "Aspect Ratio: ${mode.label}"
        }, onDismiss = { showAspectRatioMenu = false })
        
        if (showSpeedMenu) PlaybackSpeedModal(currentSpeed = playerState.playbackSpeed, onSpeedChange = { playerManager.setPlaybackSpeed(it) }, onDismiss = { showSpeedMenu = false })
        
        if (showDetailsSheet && currentItem != null) MediaInfoBottomSheet(item = currentItem, onDismiss = { showDetailsSheet = false })
        
        if (showOverflowMenu) {
            VideoPlayerOverflowMenu(
                onDismiss = { showOverflowMenu = false },
                backgroundImage = currentItem?.uri,
                onOpenWith = {
                    currentItem?.let { item ->
                        val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                        val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(sharingUri, item.mimeType.ifEmpty { "video/*" })
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(openIntent) }.onFailure {
                            android.widget.Toast.makeText(context, "No app available", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onShowInFolder = {
                    currentItem?.let { item ->
                        val folderKey = item.relativePath?.trim('/') ?: item.bucketName ?: "Videos"
                        val mainIntent = android.content.Intent(context, com.medianest.MainActivity::class.java).apply {
                            putExtra("open_screen", "VIDEOS_FOLDER")
                            putExtra("folder_name", folderKey)
                            putExtra("target_media_uri", item.uri.toString())
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(mainIntent)
                        onClose()
                    }
                },
                onDelete = { showDeleteDialog = true },
                onShare = {
                    currentItem?.let { item ->
                        val sharingUri = com.medianest.util.ContentUriUtils.getSharingUri(context, item.uri)
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = item.mimeType.ifEmpty { "video/*" }
                            putExtra(android.content.Intent.EXTRA_STREAM, sharingUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                    }
                },
                onEdit = {
                    playerManager.pause()
                    showVideoEditorSheet = true
                },
                isBackgroundPlayEnabled = playerState.isVideoBackgroundPlayEnabled,
                onToggleBackgroundPlay = { enabled ->
                    playerManager.setVideoBackgroundPlayEnabled(enabled)
                    android.widget.Toast.makeText(context, "Background Play ${if (enabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                },
                isAutoRepeatEnabled = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE,
                onToggleAutoRepeat = { enabled ->
                    val nextMode = if (enabled) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                    playerManager.setRepeatMode(nextMode)
                    android.widget.Toast.makeText(context, "Auto Repeat ${if (enabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onVideoFx = { showVideoFxSheet = true },
                onAudioTracks = { showAudioTrackSheet = true },
                onCast = {
                    val castIntent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                    runCatching { context.startActivity(android.content.Intent.createChooser(castIntent, "Cast to TV")) }
                },
                onSettings = { showSettingsSheet = true },
                onShowInfo = { showDetailsSheet = true }
            )
        }

        if (showDeleteDialog) {
            val item = currentItem
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Video") },
                text = { Text("Are you sure you want to delete '${item?.title}'? This will permanently remove the file from your device.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            scope.launch {
                                currentItem?.uri?.let { com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, it) }
                                onClose()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }

        EdgeBackSwipeOverlay(
            activeEdge = swipeEdgeState,
            swipeProgress = swipeProgressState
        )
    }
}

@Composable
fun FilmGrainOverlay(
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val noiseBitmap = remember {
        val size = 128
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = java.util.Random()
        for (i in pixels.indices) {
            val v1 = random.nextFloat()
            val v2 = random.nextFloat()
            val gaussian = (kotlin.math.sqrt(-2.0 * kotlin.math.ln(v1.toDouble())) * kotlin.math.cos(2.0 * Math.PI * v2.toDouble())).toFloat()
            val luminance = ((128 + gaussian * 38).toInt()).coerceIn(0, 255)
            pixels[i] = android.graphics.Color.argb(luminance, 255, 255, 255)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        bitmap
    }

    val infiniteTransition = rememberInfiniteTransition(label = "FilmGrainCinema")
    val frameStep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GrainFlicker"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.BitmapShader(
                noiseBitmap,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT
            )
            val matrix = android.graphics.Matrix()
            val step = frameStep.toInt()
            val randomAngle = (step * 90) % 360
            val randomOffsetX = (step * 37) % noiseBitmap.width
            val randomOffsetY = (step * 53) % noiseBitmap.height
            matrix.postRotate(randomAngle.toFloat(), noiseBitmap.width / 2f, noiseBitmap.height / 2f)
            matrix.postTranslate(randomOffsetX.toFloat(), randomOffsetY.toFloat())
            shader.setLocalMatrix(matrix)
            
            alpha = (intensity * 255 * 0.40f).toInt().coerceIn(0, 255)
            isFilterBitmap = true
            isAntiAlias = false
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}
