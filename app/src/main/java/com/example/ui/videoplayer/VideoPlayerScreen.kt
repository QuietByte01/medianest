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
import com.example.MediaNestApp
import com.example.data.db.PlaybackState
import com.example.data.repository.NetworkRepository
import com.example.data.repository.SubtitleProvider
import com.example.player.ExoPlayerManager
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.dismissKeyboardOnOutsideTap
import com.example.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var cropMode by remember { mutableStateOf("FIT") } 
    val savedDecoderMode by settingsManager.decoderMode.collectAsState(initial = "HW+")
    var decoderMode by remember(savedDecoderMode) { mutableStateOf(savedDecoderMode) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }

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
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var activeSubtitleText by remember { mutableStateOf<String?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    var swipeEdgeState by remember { mutableStateOf(SwipeEdge.NONE) }
    var swipeProgressState by remember { mutableFloatStateOf(0f) }

    var subtitleList by remember { mutableStateOf<List<com.example.data.model.SubtitleItem>>(emptyList()) }
    var audioSyncOffsetMs by remember { mutableLongStateOf(0L) }
    var isSearchingSubtitles by remember { mutableStateOf(false) }
    var subtitleStatusMessage by remember { mutableStateOf<String?>(null) }

    var subtitleFontSizeSp by remember { mutableFloatStateOf(20f) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var subtitleBgColor by remember { mutableStateOf(Color(0x99000000)) }
    var subtitleHasShadow by remember { mutableStateOf(true) }
    var isFilmGrainEnabled by remember { mutableStateOf(false) }
    var filmGrainIntensity by remember { mutableFloatStateOf(0.10f) }

    val embeddedTracks = remember(playerState.currentItem) {
        val list = mutableListOf<com.example.data.model.SubtitleItem>()
        try {
            val tracks = playerManager.exoPlayer.currentTracks
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language ?: "und"
                        val label = format.label ?: "Track ${i + 1}"
                        list.add(com.example.data.model.SubtitleItem(id = "embedded_$i", name = label, language = lang, isLocal = true))
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

    DisposableEffect(playerManager.exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                if (selectedSubtitleTrackIndex != -1 && cueGroup.cues.isNotEmpty()) {
                    activeSubtitleText = cueGroup.cues.joinToString("\n") { it.text ?: "" }.trim().ifEmpty { null }
                } else {
                    activeSubtitleText = null
                }
            }
        }
        playerManager.exoPlayer.addListener(listener)
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
            delay(4000)
            showControls = false
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoPlayerGestures(
                    playerState = playerState,
                    isControlsLocked = isControlsLocked,
                    onToggleControls = { showControls = !showControls },
                    onSeekDelta = { delta ->
                        isHorizontalDragging = true
                        seekDeltaMs += delta
                        seekTargetPositionMs = (playerState.currentPositionMs + seekDeltaMs).coerceIn(0L, playerState.durationMs)
                    },
                    onSeekTo = { playerManager.seekTo(it) },
                    onVolumeChange = { delta ->
                        isDraggingVolume = true
                        currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (currentVolume * maxVol).toInt(), 0)
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
                    onScaleChange = { zoom -> scale = (scale * zoom).coerceIn(1f, 10f) },
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
                        if (edge == SwipeEdge.LEFT && progress >= 1.0f) {
                            onClose()
                        }
                    },
                    coroutineScope = scope
                )
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
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale)
            )

            ZoomPercentagePill(scale = scale, onReset = { scale = 1f }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 160.dp, end = 24.dp))
            SubtitleTextOverlay(text = activeSubtitleText, isVisible = showControls, fontSizeSp = subtitleFontSizeSp, textColor = subtitleTextColor, bgColor = subtitleBgColor, hasShadow = subtitleHasShadow, modifier = Modifier.align(Alignment.BottomCenter))
        }

        AnimatedVisibility(visible = showControls && !isControlsLocked && !isHorizontalDragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            CenterTransportControls(isPlaying = playerState.isPlaying, onPrevious = { playerManager.previous() }, onNext = { playerManager.next() }, onTogglePlayPause = { playerManager.togglePlayPause() })
        }

        AnimatedVisibility(visible = isDraggingBrightness, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp)) {
            VerticalGestureHUD(value = currentBrightness, icon = Icons.Default.WbSunny, color = Color(0xFFF59E0B))
        }

        AnimatedVisibility(visible = isDraggingVolume, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)) {
            VerticalGestureHUD(value = currentVolume, icon = Icons.Default.VolumeUp, color = Color.White)
        }

        AnimatedVisibility(visible = isHorizontalDragging, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
            SeekHUD(deltaMs = seekDeltaMs, targetPositionMs = seekTargetPositionMs, durationMs = playerState.durationMs)
        }

        AnimatedVisibility(visible = gestureFeedbackText != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
            gestureFeedbackText?.let { GestureFeedbackHUD(text = it) }
        }

        AnimatedVisibility(visible = (showControls || showOverflowMenu || showDetailsSheet || showSubtitleSheet) && !isHorizontalDragging && !showDrawer, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            VideoPlayerTopBar(
                playerState = playerState, onClose = onClose, decoderMode = decoderMode,
                onDecoderModeClick = {
                    decoderMode = when (decoderMode) { "HW+" -> "HW"; "HW" -> "SW"; else -> "HW+" }
                    scope.launch { settingsManager.setDecoderMode(decoderMode) }
                },
                onDrawerClick = { showDrawer = true }, onSubtitleClick = { showSubtitleSheet = true },
                onInfoClick = { showDetailsSheet = true }, onMenuClick = { showOverflowMenu = true },
                onCaptureClick = { captureVideoFrame(context, playerState.currentItem, playerState.currentPositionMs) },
                isControlsLocked = isControlsLocked
            )
        }

        AnimatedVisibility(visible = (showControls || isHorizontalDragging) && !showDrawer && !showSubtitleSheet && !showDetailsSheet && !showSettingsSheet && !showAudioTrackSheet, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
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
                    cropMode = when (cropMode) { "FIT" -> "CROP"; "CROP" -> "STRETCH"; "STRETCH" -> "ORIGINAL"; else -> "FIT" }
                    gestureFeedbackText = "Aspect Ratio: $cropMode"
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
                        // We reset the list when searching a specific provider to avoid confusion
                        subtitleList = emptyList() 
                        subtitleList = networkRepository.searchOnlineSubtitles(
                            playerState.currentItem?.title ?: "", 
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
                            // Select the newly added track (which will be the last one usually, or index 0 if it's the only one)
                            // For simplicity, we just trigger a UI refresh of cues
                            selectedSubtitleTrackIndex = 0 // The addExternalSubtitle logic in mgr selects it
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
        
        if (showAudioTrackSheet) AudioTrackSelectionSheet(onDismiss = { showAudioTrackSheet = false }, playerManager = playerManager, context = context)
        
        if (showSettingsSheet) VideoPlayerSettingsDialog(onDismiss = { showSettingsSheet = false }, playerState = playerState, playerManager = playerManager, cropMode = cropMode, onCropModeChange = { cropMode = it }, pictureMode = pictureMode, onPictureModeChange = { scope.launch { settingsManager.setPictureMode(it) } }, audioSyncOffsetMs = audioSyncOffsetMs, onAudioSyncOffsetChange = { audioSyncOffsetMs = it }, isFilmGrainEnabled = isFilmGrainEnabled, onFilmGrainEnabledChange = { isFilmGrainEnabled = it }, filmGrainIntensity = filmGrainIntensity, onFilmGrainIntensityChange = { filmGrainIntensity = it }, onShowDetails = { showSettingsSheet = false; showDetailsSheet = true })
        
        if (showAspectRatioMenu) AspectRatioModal(currentMode = cropMode, onModeChange = { 
            cropMode = it
            gestureFeedbackText = "Aspect Ratio: $cropMode"
        }, onDismiss = { showAspectRatioMenu = false })
        
        if (showSpeedMenu) PlaybackSpeedModal(currentSpeed = playerState.playbackSpeed, onSpeedChange = { playerManager.setPlaybackSpeed(it) }, onDismiss = { showSpeedMenu = false })
        
        if (showDetailsSheet && playerState.currentItem != null) MediaInfoBottomSheet(item = playerState.currentItem, onDismiss = { showDetailsSheet = false })
        
        if (showOverflowMenu) {
            VideoPlayerOverflowMenu(
                onDismiss = { showOverflowMenu = false },
                onOpenWith = {
                    playerState.currentItem?.let { item ->
                        val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
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
                    playerState.currentItem?.let { item ->
                        val folderKey = item.relativePath?.trim('/') ?: item.bucketName ?: "Videos"
                        val mainIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                            putExtra("open_screen", "VIDEOS_FOLDER")
                            putExtra("folder_name", folderKey)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(mainIntent)
                        onClose()
                    }
                },
                onDelete = { showDeleteDialog = true },
                onShare = {
                    playerState.currentItem?.let { item ->
                        val sharingUri = com.example.util.ContentUriUtils.getSharingUri(context, item.uri)
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = item.mimeType.ifEmpty { "video/*" }
                            putExtra(android.content.Intent.EXTRA_STREAM, sharingUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                    }
                },
                onEdit = {
                    playerState.currentItem?.let { item ->
                        val editIntent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                            setDataAndType(item.uri, item.mimeType.ifEmpty { "video/*" })
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(editIntent) }.onFailure {
                            android.widget.Toast.makeText(context, "No editor available", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                isBackgroundPlayEnabled = playerState.isBackgroundPlayEnabled,
                onToggleBackgroundPlay = { enabled ->
                    playerManager.setBackgroundPlayEnabled(enabled)
                    android.widget.Toast.makeText(context, "Background Play ${if (enabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                },
                isAutoRepeatEnabled = playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                onToggleAutoRepeat = { enabled ->
                    val nextMode = if (enabled) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                    playerManager.setRepeatMode(nextMode)
                    android.widget.Toast.makeText(context, "Auto Repeat ${if (enabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onAudioTracks = { showAudioTrackSheet = true },
                onCast = {
                    val castIntent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                    runCatching { context.startActivity(android.content.Intent.createChooser(castIntent, "Cast to TV")) }
                },
                onSettings = { showSettingsSheet = true }
            )
        }

        if (showDeleteDialog) {
            val item = playerState.currentItem
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Video") },
                text = { Text("Are you sure you want to delete '${item?.title}'? This will permanently remove the file from your device.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            scope.launch {
                                playerState.currentItem?.uri?.let { com.example.util.FolderHiddenUtils.deleteMediaUri(context, it) }
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
