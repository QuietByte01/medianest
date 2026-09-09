@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.medianest.ui.videoplayer

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import com.medianest.util.Logger
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.medianest.player.fx.MediaFxPipeline
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.medianest.MediaNestApp
import com.medianest.data.db.PlaybackState
import com.medianest.data.model.SubtitleItem
import com.medianest.data.repository.NetworkRepository
import com.medianest.data.repository.SubtitleProvider
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.debug.PlayerDebugOverlay
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.HardwareAccelerationSetting
import com.medianest.ui.components.MediaInfoBottomSheet
import com.medianest.ui.videoplayer.studio.VideoEditorStudioSheet
import com.medianest.ui.videoplayer.panels.*
import com.medianest.util.BlurUtils.videoBlur
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.components.backdropSource
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.ui.components.SidebarQueueDrawer
import com.medianest.ui.components.media.*
import com.medianest.ui.components.dismissKeyboardOnOutsideTap
import com.medianest.ui.components.formatDuration
import com.medianest.ui.components.mediainfo.findLocalSubtitlesInDirectory
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

    val hdrPlaybackEnabledSetting by settingsManager.hdrPlaybackEnabled.collectAsState(initial = true)

    // HANDLE WINDOW HDR MODE
    LaunchedEffect(playerState.isHdrContent, hdrPlaybackEnabledSetting) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity?.window?.let { window ->
                if (playerState.isHdrContent && hdrPlaybackEnabledSetting) {
                    Logger.i("VideoPlayerScreen", "Enabling Window HDR Mode")
                    window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_HDR
                } else {
                    Logger.i("VideoPlayerScreen", "Restoring Window SDR Mode")
                    window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }
    }

    val showStatusBar by settingsManager.showStatusBarInPlayback.collectAsState(initial = false)
    val keepScreenOn by settingsManager.keepScreenOn.collectAsState(initial = true)
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val hiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())

    val pictureModeEnabled by settingsManager.pictureModeEnabled.collectAsState(initial = true)
    val pictureMode by settingsManager.pictureMode.collectAsState(initial = "BALANCED")
    val activeMediaEffect = remember(pictureModeEnabled, pictureMode, playerState.isHdrContent) {
        if (playerState.isHdrContent || !pictureModeEnabled || pictureMode == "DEVICE_DEFAULT" || pictureMode == "OFF") {
            com.medianest.ui.components.media.MediaEffect.OFF
        } else {
            try {
                com.medianest.ui.components.media.MediaEffect.fromString(pictureMode)
            } catch (_: Exception) {
                com.medianest.ui.components.media.MediaEffect.OFF
            }
        }
    }
    val androidFxColorFilter = remember(activeMediaEffect) {
        MediaFxPipeline.getAndroidColorFilter(activeMediaEffect)
    }
    LaunchedEffect(activeMediaEffect) {
        playerManager.setVideoEffect(activeMediaEffect)
    }
    val customSat by settingsManager.customSaturation.collectAsState(initial = 1.18f)
    val customCon by settingsManager.customContrast.collectAsState(initial = 1.06f)
    val customWarmth by settingsManager.customWarmth.collectAsState(initial = 0.03f)

    val isFilmGrainEnabled by settingsManager.filmGrainEnabled.collectAsState(initial = false)
    val filmGrainIntensity by settingsManager.filmGrainIntensity.collectAsState(initial = 0.15f)
    val showDebugOverlay by settingsManager.showPlayerDebugInfo.collectAsState(initial = false)

    var showControls by remember { mutableStateOf(true) }
    var cropMode by remember { mutableStateOf(MediaAspectRatio.FIT) }
    val savedDecoderMode by settingsManager.decoderMode.collectAsState(initial = "HW+")
    var decoderMode by remember(savedDecoderMode) { mutableStateOf(savedDecoderMode) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val rememberVideoPosition by settingsManager.rememberVideoPosition.collectAsState(initial = true)
    var resumePromptPositionMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentItem?.id, currentItem?.uri) {
        resumePromptPositionMs = null
        val item = currentItem ?: return@LaunchedEffect
        if (rememberVideoPosition) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = com.medianest.MediaNestApp.instance.database
                    val savedState = db.playbackStateDao().getPlaybackState(item.uri.toString())
                    if (savedState != null && savedState.positionMs > 5000L && savedState.durationMs > 10000L && savedState.positionMs < (savedState.durationMs - 5000L)) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            resumePromptPositionMs = savedState.positionMs
                        }
                        kotlinx.coroutines.delay(7000L)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            resumePromptPositionMs = null
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("VideoPlayerScreen", "Failed to query playback state", e)
                }
            }
        }
    }

    val playerBackdropState = rememberBackdropBlurState()

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
    var initialSeekPositionMs by remember { mutableLongStateOf(0L) }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }
    var seekTargetPositionMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var lastScrubSeekTime by remember { mutableLongStateOf(0L) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedSubtitleTrackIndex by remember { mutableIntStateOf(0) }

    var showDrawer by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleCustomizationSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showVideoFxSheet by remember { mutableStateOf(false) }
    var showVideoEditorSheet by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showAbRepeatBar by remember { mutableStateOf(false) }
    var activeSubtitleText by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var decoderDropdownExpanded by remember { mutableStateOf(false) }

    var swipeEdgeState by remember { mutableStateOf(SwipeEdge.NONE) }
    var swipeProgressState by remember { mutableFloatStateOf(0f) }

    var activeTextureViewRef by remember { mutableStateOf<android.view.TextureView?>(null) }

    var subtitleList by remember { mutableStateOf<List<com.medianest.data.model.SubtitleItem>>(emptyList()) }
    var audioSyncOffsetMs by remember { mutableLongStateOf(0L) }
    var isSearchingSubtitles by remember { mutableStateOf(false) }
    var subtitleStatusMessage by remember { mutableStateOf<String?>(null) }

    var subtitleFontSizeSp by remember { mutableFloatStateOf(20f) }
    var subtitleFontFamily by remember { mutableStateOf(SubtitleFontFamily.DEFAULT) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var subtitleBgColor by remember { mutableStateOf(Color(0x99000000)) }
    var subtitleHasShadow by remember { mutableStateOf(true) }

    var embeddedTracksState by remember(currentItem?.id, playerState.media3InstanceId) {
        mutableStateOf<List<SubtitleItem>>(emptyList())
    }

    val localDirSubtitles = remember(currentItem?.uri, currentItem?.id) {
        if (currentItem != null) {
            findLocalSubtitlesInDirectory(context, currentItem)
        } else emptyList()
    }

    LaunchedEffect(currentItem?.id, localDirSubtitles) {
        if (localDirSubtitles.isNotEmpty() && currentItem != null) {
            val bestMatch = localDirSubtitles.firstOrNull()
            if (bestMatch?.downloadUrl != null) {
                try {
                    val subUri = Uri.parse(bestMatch.downloadUrl)
                    playerManager.addExternalSubtitle(subUri, bestMatch.name)
                    val newTracks = playerManager.getAvailableTextTracks()
                    selectedSubtitleTrackIndex = (newTracks.size - 1).coerceAtLeast(0)
                    playerManager.selectTextTrack(selectedSubtitleTrackIndex)
                } catch (e: Exception) {
                    Logger.e("VideoPlayerScreen", "Error auto-applying local directory subtitle", e)
                }
            }
        }
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var fileName = "Local Subtitle"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}

            playerManager.addExternalSubtitle(uri, fileName)
            subtitleStatusMessage = "Applied local subtitle: $fileName"
            val newTracks = playerManager.getAvailableTextTracks()
            selectedSubtitleTrackIndex = (newTracks.size - 1).coerceAtLeast(0)
            playerManager.selectTextTrack(selectedSubtitleTrackIndex)
        }
    }

    var isControlsLocked by remember { mutableStateOf(false) }

    val anyOverlayOpen = showOverflowMenu || showDetailsSheet || showDrawer || showSubtitleSheet ||
            showSubtitleCustomizationSheet || showSettingsSheet || showAudioTrackSheet ||
            showAspectRatioMenu || showSpeedMenu || showAbRepeatBar || showEngineDialog ||
            showVideoFxSheet || showVideoEditorSheet || showDeleteDialog

    LaunchedEffect(anyOverlayOpen) {
        if (anyOverlayOpen) {
//            playerBackdropState.drawSignal++
        }
    }

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
                showAbRepeatBar -> showAbRepeatBar = false
                showEngineDialog -> showEngineDialog = false
                showVideoFxSheet -> showVideoFxSheet = false
                showVideoEditorSheet -> showVideoEditorSheet = false
                showDeleteDialog -> showDeleteDialog = false
            }
        } else if (isControlsLocked) {
            isControlsLocked = false
        } else {
            onClose()
        }
    }

    var containerSizePx by remember { mutableStateOf(IntSize.Zero) }

    var activeVideoSize by remember(currentItem?.id, playerState.activeEngineName, playerState.media3InstanceId) {
        mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN)
    }

    val isMedia3 = playerState.activeEngineName.contains("Media3")
    val rawEncodedWidth = if (isMedia3 && activeVideoSize.width > 0) activeVideoSize.width.toFloat() else 0f
    val rawEncodedHeight = if (isMedia3 && activeVideoSize.height > 0) activeVideoSize.height.toFloat() else 0f
    val rotation = if (isMedia3) activeVideoSize.unappliedRotationDegrees else 0
    val isRotated = rotation == 90 || rotation == 270
    val pixelRatio = if (isMedia3 && activeVideoSize.pixelWidthHeightRatio > 0f) activeVideoSize.pixelWidthHeightRatio else 1f

    val videoAspectRatio: Float = if (rawEncodedWidth > 0f && rawEncodedHeight > 0f) {
        val displayWidth = (if (isRotated) rawEncodedHeight else rawEncodedWidth) * pixelRatio
        val displayHeight = if (isRotated) rawEncodedWidth else rawEncodedHeight
        (displayWidth / displayHeight.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)
    } else {
        val itemW = (currentItem?.width?.takeIf { it > 0 } ?: 1920).toFloat()
        val itemH = (currentItem?.height?.takeIf { it > 0 } ?: 1080).toFloat()
        (itemW / itemH.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)
    }

    DisposableEffect(currentItem?.id, playerState.activeEngineName, playerState.media3InstanceId) {
        if (!playerState.activeEngineName.contains("Media3")) {
            return@DisposableEffect onDispose {}
        }
        val player = playerManager.exoPlayer
        if (player == null) return@DisposableEffect onDispose {}

        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    activeVideoSize = videoSize
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val currentM3 = playerManager.exoPlayer ?: return
                val vs = currentM3.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    activeVideoSize = vs
                }
                val textTracks = playerManager.getAvailableTextTracks()
                embeddedTracksState = textTracks.mapIndexed { idx, info ->
                    val langPart = if (info.name.contains("(")) info.name.substringAfter("(").substringBefore(")") else "und"
                    SubtitleItem(
                        id = "embedded_$idx",
                        name = info.name,
                        language = langPart,
                        isLocal = true
                    )
                }
                if (selectedSubtitleTrackIndex >= 0 && textTracks.isNotEmpty()) {
                    val targetIndex = selectedSubtitleTrackIndex.coerceIn(0, textTracks.size - 1)
                    if (selectedSubtitleTrackIndex != targetIndex) {
                        selectedSubtitleTrackIndex = targetIndex
                    }
                    playerManager.selectTextTrack(targetIndex)
                }
            }
            override fun onCues(cueGroup: CueGroup) {
                if (selectedSubtitleTrackIndex != -1 && cueGroup.cues.isNotEmpty()) {
                    val cueText = cueGroup.cues.mapNotNull { cue ->
                        cue.text?.toString()?.takeIf { str -> str.isNotBlank() }
                    }.joinToString("\n").trim()
                    activeSubtitleText = cueText.ifEmpty { null }
                } else {
                    activeSubtitleText = null
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(showStatusBar, keepScreenOn) {
        activity?.let { act ->
            if (keepScreenOn) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (!showStatusBar) {
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
                // Use DEFAULT behavior so edge swipes go to back navigation, not transient bar reveal
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {}
    }

    LaunchedEffect(showControls, anyOverlayOpen, isControlsLocked, decoderDropdownExpanded) {
        if (showControls && !isControlsLocked && !anyOverlayOpen && !decoderDropdownExpanded) {
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

    CompositionLocalProvider(com.medianest.ui.components.LocalBackdropState provides playerBackdropState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .dismissKeyboardOnOutsideTap()
        ) {
            // VIDEO SURFACE, GESTURES & ON-SCREEN CONTROLS LAYER
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(state = playerBackdropState, backgroundColor = Color.Black)
                    .videoPlayerGestures(
                    currentItemId = currentItem?.id,
                    isControlsLocked = isControlsLocked,
                    isZoomed = scale > 1.05f,
                    coroutineScope = scope,
                    onToggleControls = { showControls = !showControls },
                    onSeekStart = {
                        if (!isControlsLocked) {
                            isHorizontalDragging = true
                            initialSeekPositionMs = playerState.currentPositionMs
                            seekDeltaMs = 0L
                            seekTargetPositionMs = playerState.currentPositionMs
                            playerManager.scrubStart()
                            lastScrubSeekTime = System.currentTimeMillis()
                        }
                    },
                    onSeekDelta = { delta ->
                        if (!isControlsLocked) {
                            if (!isHorizontalDragging) {
                                isHorizontalDragging = true
                                initialSeekPositionMs = playerState.currentPositionMs
                                seekDeltaMs = 0L
                                seekTargetPositionMs = playerState.currentPositionMs
                                playerManager.scrubStart()
                                    lastScrubSeekTime = System.currentTimeMillis()
                                }
                                seekDeltaMs += delta
                                val newTarget = (initialSeekPositionMs + seekDeltaMs).coerceIn(0L, playerState.durationMs)
                                seekTargetPositionMs = newTarget
                                val now = System.currentTimeMillis()
                                if (now - lastScrubSeekTime >= 60L) {
                                    lastScrubSeekTime = now
                                    playerManager.scrubSeek(newTarget)
                                }
                            }
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
                                playerManager.scrubEnd(seekTargetPositionMs)
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
                    )
            ) {
                // Video Surface Container with Aspect Ratio Bounds
                val density = LocalDensity.current

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSizePx = it },
                    contentAlignment = Alignment.Center
                ) {
                    if (containerSizePx.width > 0 && containerSizePx.height > 0) {
                        val maxWidth = with(density) { containerSizePx.width.toDp() }
                        val maxHeight = with(density) { containerSizePx.height.toDp() }
                        val containerRatio = containerSizePx.width.toFloat() / containerSizePx.height.toFloat().coerceAtLeast(1f)

                        fun containBoxSize(targetRatio: Float): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
                            return if (targetRatio >= containerRatio) {
                                maxWidth to (maxWidth / targetRatio)
                            } else {
                                (maxHeight * targetRatio) to maxHeight
                            }
                        }

                        val (boxWidthDp, boxHeightDp) = when (cropMode) {
                            MediaAspectRatio.FIT -> containBoxSize(videoAspectRatio)
                            MediaAspectRatio.CROP, MediaAspectRatio.STRETCH -> maxWidth to maxHeight
                            MediaAspectRatio.ORIGINAL -> {
                                val w = if (rawEncodedWidth > 0f) {
                                    (if (isRotated) rawEncodedHeight else rawEncodedWidth) * pixelRatio
                                } else {
                                    (currentItem?.width?.takeIf { it > 0 } ?: 1920).toFloat()
                                }
                                val h = if (rawEncodedHeight > 0f) {
                                    if (isRotated) rawEncodedWidth else rawEncodedHeight
                                } else {
                                    (currentItem?.height?.takeIf { it > 0 } ?: 1080).toFloat()
                                }
                                with(density) { w.toDp() } to with(density) { h.toDp() }
                            }
                            else -> {
                                val targetRatio = cropMode.ratio ?: videoAspectRatio
                                containBoxSize(targetRatio)
                            }
                        }

                        val surfaceModifier = Modifier.requiredSize(boxWidthDp, boxHeightDp)

                        val (videoWidthDp, videoHeightDp) = when (cropMode) {
                            MediaAspectRatio.FIT, MediaAspectRatio.ORIGINAL -> boxWidthDp to boxHeightDp
                            MediaAspectRatio.STRETCH -> boxWidthDp to boxHeightDp
                            else -> {
                                val boxRatio = boxWidthDp.value / boxHeightDp.value.coerceAtLeast(0.001f)
                                if (videoAspectRatio >= boxRatio) {
                                    (boxHeightDp * videoAspectRatio) to boxHeightDp
                                } else {
                                    boxWidthDp to (boxWidthDp / videoAspectRatio.coerceAtLeast(0.001f))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = surfaceModifier.clipToBounds(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredSize(videoWidthDp, videoHeightDp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = panOffset.x
                                            translationY = panOffset.y
                                            // Do NOT apply sRGB 8-bit RenderEffect on HDR content (causes milky/grainy white overlay)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && androidFxColorFilter != null && !playerState.isHdrContent) {
                                                renderEffect = android.graphics.RenderEffect.createColorFilterEffect(androidFxColorFilter).asComposeRenderEffect()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Key ONLY on engine and instanceId — do NOT key on currentItem.id,
                                    // as changing items or background metadata updates would tear down
                                    // the SurfaceTexture and cause playback to freeze at 00:00.
                                    androidx.compose.runtime.key(playerState.activeEngineName, playerState.media3InstanceId) {
                                        if (playerState.activeEngineName.contains("Media3")) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    Logger.i("VideoPlayerScreen", "Creating NEW PlayerView for Media3")
                                                    val inflater = android.view.LayoutInflater.from(ctx)
                                                    (inflater.inflate(com.medianest.R.layout.player_view_texture, null) as androidx.media3.ui.PlayerView).apply {
                                                        useController = false
                                                        subtitleView?.visibility = View.GONE
                                                        setKeepContentOnPlayerReset(true)
                                                        try {
                                                            this.player = playerManager.exoPlayer
                                                        } catch (_: Exception) {}
                                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                                        activeTextureViewRef = videoSurfaceView as? android.view.TextureView
                                                    }
                                                },
                                                update = { view ->
                                                    try {
                                                        val currentExo = playerManager.exoPlayer
                                                        if (view.player != currentExo) {
                                                            Logger.i("VideoPlayerScreen", "Syncing PlayerView with new ExoPlayer instance")
                                                            view.player = currentExo
                                                        }
                                                        view.onResume()
                                                    } catch (e: Exception) {
                                                        Logger.e("VideoPlayerScreen", "Error syncing player", e)
                                                    }
                                                    view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                    activeTextureViewRef = view.videoSurfaceView as? android.view.TextureView
                                                },
                                                onRelease = { view ->
                                                    if (activeTextureViewRef == view.videoSurfaceView) {
                                                        activeTextureViewRef = null
                                                    }
                                                    try {
                                                        view.onPause()
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            AndroidView(
                                                factory = { ctx ->
                                                    Logger.i("VideoPlayerScreen", "Creating TextureView for FFmpeg")
                                                    android.view.TextureView(ctx).apply {
                                                        activeTextureViewRef = this
                                                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                                            private var activeSurface: android.view.Surface? = null
                                                            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                                                Logger.i("VideoPlayerScreen", "FFmpeg SurfaceTexture Available ($w x $h)")
                                                                activeSurface?.release()
                                                                val newSurface = android.view.Surface(st)
                                                                activeSurface = newSurface
                                                                playerManager.setVideoSurface(newSurface)
                                                            }
                                                            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                                                if (activeSurface == null || !activeSurface!!.isValid) {
                                                                    activeSurface?.release()
                                                                    activeSurface = android.view.Surface(st)
                                                                }
                                                                playerManager.setVideoSurface(activeSurface)
                                                            }
                                                            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                                                                Logger.i("VideoPlayerScreen", "FFmpeg SurfaceTexture Destroyed")
                                                                if (playerManager.lastSurface == activeSurface) {
                                                                    playerManager.setVideoSurface(null)
                                                                }
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
                                                    activeTextureViewRef = view
                                                    if (view.isAvailable && view.surfaceTexture != null) {
                                                        val st = view.surfaceTexture!!
                                                        if (playerManager.lastSurface == null || !playerManager.lastSurface!!.isValid) {
                                                            playerManager.setVideoSurface(android.view.Surface(st))
                                                        }
                                                    }
                                                },
                                                onRelease = { view ->
                                                    if (activeTextureViewRef == view) {
                                                        activeTextureViewRef = null
                                                    }
                                                    view.surfaceTextureListener = null
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                if (isFilmGrainEnabled && !playerState.isHdrContent) {
                                    FilmGrainOverlay(intensity = filmGrainIntensity, modifier = Modifier.matchParentSize())
                                }
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
                SubtitleTextOverlay(text = activeSubtitleText, isVisible = showControls, fontSizeSp = subtitleFontSizeSp, fontFamily = subtitleFontFamily.fontFamily, textColor = subtitleTextColor, bgColor = subtitleBgColor, hasShadow = subtitleHasShadow, modifier = Modifier.align(Alignment.BottomCenter))

            AnimatedVisibility(
                visible = showControls && !isControlsLocked && !isHorizontalDragging && !showAbRepeatBar,
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

            AnimatedVisibility(visible = isHorizontalDragging, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
                SeekHUD(
                    deltaMs = seekDeltaMs,
                    targetPositionMs = seekTargetPositionMs,
                    durationMs = playerState.durationMs
                )
            }

            AnimatedVisibility(
                visible = (showControls || showOverflowMenu || showDetailsSheet || showSubtitleSheet) && !isHorizontalDragging && !showDrawer && !showAbRepeatBar,
                enter = fadeIn(animationSpec = controlsFadeSpec),
                exit = fadeOut(animationSpec = controlsFadeSpec),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                VideoPlayerTopBar(
                    playerState = playerState, onClose = onClose, decoderMode = decoderMode,
                    onDecoderModeChange = { mode ->
                        decoderMode = mode
                        scope.launch { settingsManager.setDecoderMode(mode) }
                    },
                    decoderDropdownExpanded = decoderDropdownExpanded,
                    onDecoderDropdownExpandedChange = { decoderDropdownExpanded = it },
                    onDrawerClick = { showDrawer = true }, onSubtitleClick = { showSubtitleSheet = true },
                    onInfoClick = { showDetailsSheet = true }, onMenuClick = { showOverflowMenu = true },
                    onCaptureClick = { 
                        val liveBmp = try { activeTextureViewRef?.bitmap } catch (_: Exception) { null }
                        captureVideoFrame(context, currentItem, playerState.currentPositionMs, liveBmp)
                    },
                    isControlsLocked = isControlsLocked
                )
            }

            AnimatedVisibility(
                visible = showControls && !isHorizontalDragging && !showDrawer && !showSubtitleSheet && !showDetailsSheet && !showSettingsSheet && !showAudioTrackSheet && !showAbRepeatBar && !showVideoFxSheet && !showAspectRatioMenu && !showSpeedMenu,
                enter = fadeIn(animationSpec = controlsFadeSpec),
                exit = fadeOut(animationSpec = controlsFadeSpec),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                VideoPlayerBottomBar(
                    playerState = playerState, isControlsLocked = isControlsLocked, isHorizontalDragging = isHorizontalDragging,
                    seekTargetPositionMs = seekTargetPositionMs,
                    onSeekStart = { playerManager.scrubStart() },
                    onSeekProgress = { playerManager.scrubSeek(it) },
                    onSeekEnd = { playerManager.scrubEnd(it) },
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
                    onSpeedLongPress = {
                        showSpeedMenu = true
                        showAspectRatioMenu = false
                        showVideoFxSheet = false
                        showAbRepeatBar = false
                    }, onPipClick = onEnterPip,
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
                    onAspectRatioLongPress = {
                        showAspectRatioMenu = true
                        showSpeedMenu = false
                        showVideoFxSheet = false
                        showAbRepeatBar = false
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showAbRepeatBar && !isControlsLocked && !showDrawer && !showSubtitleSheet && !showDetailsSheet && !showSettingsSheet && !showAudioTrackSheet && !showVideoFxSheet && !showVideoEditorSheet && !showAspectRatioMenu && !showSpeedMenu,
            enter = fadeIn(animationSpec = controlsFadeSpec) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = controlsFadeSpec) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            AbRepeatControlBar(
                abRepeatState = playerState.abRepeatState,
                backdropState = playerBackdropState,
                onSetPointA = {
                    playerManager.setAbRepeatPointA()
                    val pos = playerManager.playerState.value.abRepeatA ?: 0L
                    gestureFeedbackText = "Point A: ${formatDuration(pos)}"
                },
                onSetPointB = {
                    playerManager.setAbRepeatPointB()
                    val curA = playerManager.playerState.value.abRepeatA ?: 0L
                    val curB = playerManager.playerState.value.abRepeatB ?: 0L
                    gestureFeedbackText = "Loop Active: ${formatDuration(curA)} - ${formatDuration(curB)}"
                },
                onAdjustPointA = { delta ->
                    playerManager.adjustAbRepeatPointA(delta)
                    gestureFeedbackText = "Point A: ${formatDuration(playerManager.playerState.value.abRepeatA ?: 0L)}"
                },
                onAdjustPointB = { delta ->
                    playerManager.adjustAbRepeatPointB(delta)
                    gestureFeedbackText = "Point B: ${formatDuration(playerManager.playerState.value.abRepeatB ?: 0L)}"
                },
                onSeekToA = {
                    playerState.abRepeatA?.let { playerManager.seekTo(it) }
                },
                onSeekToB = {
                    playerState.abRepeatB?.let { playerManager.seekTo(it) }
                },
                onToggleActive = {
                    playerManager.toggleAbRepeat()
                    gestureFeedbackText = if (playerState.isAbRepeatActive) "A-B Loop Active" else "A-B Loop Paused"
                },
                onClear = {
                    playerManager.clearAbRepeat()
                    gestureFeedbackText = "A-B Repeat Cleared"
                },
                onClose = {
                    showAbRepeatBar = false
                }
            )
        }

        // Floating Resume / Restart Playback prompt
        AnimatedVisibility(
            visible = resumePromptPositionMs != null && !isControlsLocked && !showDrawer,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = if (showControls) (if (showAbRepeatBar) 220.dp else 165.dp) else (if (showAbRepeatBar) 90.dp else 28.dp)
                )
        ) {
            resumePromptPositionMs?.let { resumePos ->
                val shape = RoundedCornerShape(16.dp)
                val cardBg = Color(0x6608090E)
                GlassSurface(
                    shape = shape,
                    backgroundColor = if (playerBackdropState != null) Color.Transparent else cardBg,
                    borderColor = Color(0x33FFFFFF),
                    borderWidth = 0.5.dp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(shape)
                        .then(
                            if (playerBackdropState != null) {
                                Modifier.backdropReceiver(
                                    state = playerBackdropState,
                                    blurRadius = 24.dp,
                                    tint = cardBg,
                                    baseColor = Color.Transparent,
                                    showTopBorder = false
                                )
                            } else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Resume from ${formatDuration(resumePos)}?",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = {
                                playerManager.seekTo(resumePos)
                                resumePromptPositionMs = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.70f),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(text = "Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                playerManager.seekTo(0L)
                                resumePromptPositionMs = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(text = "Restart", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { resumePromptPositionMs = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
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
                onVideoClick = { playerManager.playMediaList(playerState.queue, playerState.queue.indexOf(it), queueTitle = playerState.queueTitle) },
                onClose = { showDrawer = false },
                context = context,
                showHidden = showHiddenFiles,
                hiddenFolders = hiddenFolders,
                backdropState = playerBackdropState,
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
                embeddedTracks = embeddedTracksState,
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
                            val newTracks = playerManager.getAvailableTextTracks()
                            selectedSubtitleTrackIndex = (newTracks.size - 1).coerceAtLeast(0)
                            playerManager.selectTextTrack(selectedSubtitleTrackIndex)
                            delay(1200)
                            showSubtitleSheet = false
                        } else {
                            subtitleStatusMessage = "Failed to download subtitle file."
                        }
                    }
                },
                localDirSubtitles = localDirSubtitles,
                onLocalDirSubClick = { dirSub ->
                    if (dirSub.downloadUrl != null) {
                        try {
                            val subUri = Uri.parse(dirSub.downloadUrl)
                            playerManager.addExternalSubtitle(subUri, dirSub.name)
                            subtitleStatusMessage = "Applied local subtitle: ${dirSub.name}"
                            val newTracks = playerManager.getAvailableTextTracks()
                            selectedSubtitleTrackIndex = (newTracks.size - 1).coerceAtLeast(0)
                            playerManager.selectTextTrack(selectedSubtitleTrackIndex)
                            showSubtitleSheet = false
                        } catch (e: Exception) {
                            Logger.e("VideoPlayerScreen", "Error applying directory subtitle", e)
                        }
                    }
                },
                onPickLocalSubtitle = {
                    subtitlePickerLauncher.launch(arrayOf("*/*", "text/*", "application/x-subrip", "application/octet-stream"))
                },
                statusMessage = subtitleStatusMessage,
                onClose = { showSubtitleSheet = false },
                backdropState = playerBackdropState,
                modifier = Modifier
                    .widthIn(max = minOf(420.dp, (LocalConfiguration.current.screenWidthDp * 0.92f).dp))
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)
                    .padding(12.dp)
            )
        }

        if (showSubtitleCustomizationSheet) SubtitleCustomizationSheet(
            onDismiss = { showSubtitleCustomizationSheet = false },
            activeSubtitleText = activeSubtitleText,
            fontSizeSp = subtitleFontSizeSp,
            onFontSizeChange = { subtitleFontSizeSp = it },
            selectedFontFamily = subtitleFontFamily,
            onFontFamilyChange = { subtitleFontFamily = it },
            textColor = subtitleTextColor,
            onTextColorChange = { subtitleTextColor = it },
            bgColor = subtitleBgColor,
            onBgColorChange = { subtitleBgColor = it },
            hasShadow = subtitleHasShadow,
            onHasShadowChange = { subtitleHasShadow = it },
            backdropState = playerBackdropState
        )

        if (showAudioTrackSheet) AudioTrackSelectionSheet(
            onDismiss = { showAudioTrackSheet = false },
            playerManager = playerManager,
            audioSyncOffsetMs = audioSyncOffsetMs,
            onAudioSyncOffsetChange = { audioSyncOffsetMs = it },
            context = context,
            backdropState = playerBackdropState
        )

        if (showVideoFxSheet) {
            VideoPostProcessingPanel(
                currentEffect = if (!pictureModeEnabled || pictureMode == "DEVICE_DEFAULT") MediaEffect.OFF else MediaEffect.fromString(pictureMode),
                onEffectChange = { newMode: MediaEffect ->
                    scope.launch {
                        if (newMode == MediaEffect.OFF || newMode == MediaEffect.NORMAL) {
                            settingsManager.setPictureMode("DEVICE_DEFAULT")
                            settingsManager.setPictureModeEnabled(false)
                            gestureFeedbackText = "Video FX: Off"
                        } else {
                            settingsManager.setPictureMode(newMode.name)
                            settingsManager.setPictureModeEnabled(true)
                            gestureFeedbackText = "Video FX: ${newMode.label}"
                        }
                    }
                },
                onDismiss = { showVideoFxSheet = false },
                backdropState = playerBackdropState
            )
        }

        if (showVideoEditorSheet && currentItem != null) {
            VideoEditorStudioSheet(
                mediaItem = currentItem!!,
                onDismiss = { showVideoEditorSheet = false }
            )
        }

        if (showSettingsSheet) VideoPlayerSettingsOverlay(
            onDismiss = { showSettingsSheet = false },
            playerState = playerState,
            playerManager = playerManager,
            settingsManager = settingsManager,
            audioSyncOffsetMs = audioSyncOffsetMs,
            onAudioSyncOffsetChange = { audioSyncOffsetMs = it },
            isFilmGrainEnabled = isFilmGrainEnabled,
            onFilmGrainEnabledChange = { scope.launch { settingsManager.setFilmGrainEnabled(it) } },
            filmGrainIntensity = filmGrainIntensity,
            onFilmGrainIntensityChange = { scope.launch { settingsManager.setFilmGrainIntensity(it) } },
            backdropState = playerBackdropState
        )

        if (showAspectRatioMenu) AspectRatioModal(currentMode = cropMode, onModeChange = { mode: MediaAspectRatio ->
            cropMode = mode
            scale = 1f
            gestureFeedbackText = "Aspect Ratio: ${mode.label}"
        }, onDismiss = { showAspectRatioMenu = false }, backdropState = playerBackdropState)

        if (showSpeedMenu) PlaybackSpeedModal(currentSpeed = playerState.playbackSpeed, onSpeedChange = { playerManager.setPlaybackSpeed(it) }, onDismiss = { showSpeedMenu = false }, backdropState = playerBackdropState)

        if (showDetailsSheet && currentItem != null) MediaInfoBottomSheet(item = currentItem, onDismiss = { showDetailsSheet = false })

        if (showOverflowMenu) {
            VideoPlayerOverflowMenu(
                onDismiss = { showOverflowMenu = false },
                backdropState = playerBackdropState,
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
                isAutoRepeatEnabled = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE,
                onToggleAutoRepeat = { enabled ->
                    val nextMode = if (enabled) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                    playerManager.setRepeatMode(nextMode)
                    android.widget.Toast.makeText(context, "Auto Repeat ${if (enabled) "Enabled" else "Disabled"}", android.widget.Toast.LENGTH_SHORT).show()
                },
                isAbRepeatActive = playerState.isAbRepeatActive,
                onAbRepeat = {
                    showAbRepeatBar = true
                    showVideoFxSheet = false
                    showAspectRatioMenu = false
                    showSpeedMenu = false
                },
                onVideoFx = {
                    showVideoFxSheet = true
                    showAspectRatioMenu = false
                    showSpeedMenu = false
                    showAbRepeatBar = false
                    showControls = false
                },
                onAudioTracks = { showAudioTrackSheet = true },
                onSettings = { showSettingsSheet = true },
                onShowInfo = { showDetailsSheet = true }
            )
        }

        if (showDebugOverlay) {
            val currentPlayingAspectRatio = when (cropMode) {
                MediaAspectRatio.FIT, MediaAspectRatio.ORIGINAL -> videoAspectRatio
                MediaAspectRatio.CROP, MediaAspectRatio.STRETCH -> {
                    if (containerSizePx.width > 0 && containerSizePx.height > 0) {
                        containerSizePx.width.toFloat() / containerSizePx.height.toFloat().coerceAtLeast(1f)
                    } else {
                        videoAspectRatio
                    }
                }
                else -> cropMode.ratio ?: videoAspectRatio
            }

            PlayerDebugOverlay(
                playerState = playerState,
                videoAspectRatio = videoAspectRatio,
                playingAspectRatio = currentPlayingAspectRatio,
                modifier = Modifier
                    .padding(top = 64.dp, start = 16.dp)
            )
        }

        if (showEngineDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showEngineDialog = false },
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.BackdropGlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    blurRadius = 24.dp,
                    tint = Color(0x660A0C10),
                    baseColor = Color.Transparent,
                    borderColor = Color(0x38FFFFFF),
                    borderWidth = 1.dp,
                    backdropState = playerBackdropState,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .wrapContentHeight()
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Engine & Hardware",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HardwareAccelerationSetting(settingsManager = settingsManager, showDecoderStrategy = false)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            com.medianest.ui.components.HdrPlaybackSetting(settingsManager = settingsManager)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showEngineDialog = false }) {
                                Text("Done", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && currentItem != null) {
            val item = currentItem
            com.medianest.ui.components.DeleteConfirmationDialog(
                title = "Delete Video",
                itemTitle = item.title,
                onDismiss = { showDeleteDialog = false },
                backdropState = playerBackdropState,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, item.uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        withContext(Dispatchers.Main) {
                            onClose()
                        }
                    }
                }
            )
        }

        EdgeBackSwipeOverlay(
            activeEdge = swipeEdgeState,
            swipeProgress = swipeProgressState
        )
    }
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

            // Reduce opacity: 0.15x multiplier (was 0.40x) makes grain subtle cinematic effect
            // instead of grey haze. With intensity=0.15f (default), this yields ~6 alpha.
            alpha = (intensity * 255 * 0.15f).toInt().coerceIn(0, 255)
            isFilterBitmap = true
            isAntiAlias = false
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}