package com.medianest.ui.videoplayer.studio

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import com.medianest.util.setDataSourceSafe
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.media.*
import com.medianest.util.MediaProcessorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorStudioSheet(
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val projectClips = remember {
        mutableStateListOf(
            StudioClipItem(
                id = UUID.randomUUID().toString(),
                uri = mediaItem.uri,
                title = mediaItem.title.ifBlank { "Original Video" },
                isVideo = true,
                durationMs = if (mediaItem.durationMs > 0) mediaItem.durationMs else 5000L
            )
        )
    }
    var activeClipIndex by remember { mutableIntStateOf(0) }
    val currentActiveClip = projectClips.getOrNull(activeClipIndex) ?: projectClips.first()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var videoDurationMs by remember { mutableLongStateOf(currentActiveClip.durationMs.coerceAtLeast(1000L)) }

    var activeTool by remember { mutableStateOf(StudioTool.TRIM) }

    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(videoDurationMs) }

    var videoSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    var cropPreset by remember { mutableStateOf(MediaAspectRatio.ORIGINAL) }
    var isCustomCrop by remember { mutableStateOf(false) }
    var cropNormX by remember { mutableFloatStateOf(0f) }
    var cropNormY by remember { mutableFloatStateOf(0f) }
    var cropNormW by remember { mutableFloatStateOf(1f) }
    var cropNormH by remember { mutableFloatStateOf(1f) }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    var activeFilter by remember { mutableStateOf(MediaEffect.ORIGINAL) }
    var blurMode by remember { mutableStateOf(StudioBlurMode.NONE) }
    var blurIntensity by remember { mutableFloatStateOf(0f) }

    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    val textOverlays = remember { mutableStateListOf<StudioTextOverlay>() }
    val emojiStickers = remember { mutableStateListOf<StudioEmojiSticker>() }
    val imageStickers = remember { mutableStateListOf<StudioImageSticker>() }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showKeyboardStickerDialog by remember { mutableStateOf(false) }

    var videoVolume by remember { mutableFloatStateOf(1.0f) }
    var bgmUri by remember { mutableStateOf<Uri?>(null) }
    var bgmTitle by remember { mutableStateOf<String?>(null) }
    var bgmVolume by remember { mutableFloatStateOf(0.7f) }

    val thumbnails = remember { mutableStateListOf<Bitmap>() }
    var isExtractingFrames by remember { mutableStateOf(true) }

    var showExportSheet by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val processingState by MediaProcessorEngine.processingState.collectAsState()

    var gifDirection by remember { mutableStateOf(MediaProcessorEngine.GifDirection.FORWARD) }
    var gifQualityPreset by remember { mutableStateOf("MAX_CLARITY") }

    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }

    fun applyCropPreset(preset: MediaAspectRatio) {
        cropPreset = preset
        isCustomCrop = false
        val vW = videoWidth.toFloat().coerceAtLeast(1f)
        val vH = videoHeight.toFloat().coerceAtLeast(1f)
        val vRatio = vW / vH

        when (preset) {
            MediaAspectRatio.ORIGINAL -> {
                cropNormX = 0f; cropNormY = 0f; cropNormW = 1f; cropNormH = 1f
            }
            MediaAspectRatio.P_1_1 -> {
                if (vRatio >= 1.0f) {
                    val wNorm = (1.0f / vRatio).coerceIn(0.1f, 1f)
                    cropNormX = (1f - wNorm) / 2f
                    cropNormY = 0f
                    cropNormW = wNorm
                    cropNormH = 1f
                } else {
                    val hNorm = vRatio.coerceIn(0.1f, 1f)
                    cropNormX = 0f
                    cropNormY = (1f - hNorm) / 2f
                    cropNormW = 1f
                    cropNormH = hNorm
                }
            }
            MediaAspectRatio.P_16_9 -> {
                val targetRatio = 16f / 9f
                if (vRatio >= targetRatio) {
                    val wNorm = (targetRatio / vRatio).coerceIn(0.1f, 1f)
                    cropNormX = (1f - wNorm) / 2f; cropNormY = 0f; cropNormW = wNorm; cropNormH = 1f
                } else {
                    val hNorm = (vRatio / targetRatio).coerceIn(0.1f, 1f)
                    cropNormX = 0f; cropNormY = (1f - hNorm) / 2f; cropNormW = 1f; cropNormH = hNorm
                }
            }
            MediaAspectRatio.P_9_16 -> {
                val targetRatio = 9f / 16f
                if (vRatio >= targetRatio) {
                    val wNorm = (targetRatio / vRatio).coerceIn(0.1f, 1f)
                    cropNormX = (1f - wNorm) / 2f; cropNormY = 0f; cropNormW = wNorm; cropNormH = 1f
                } else {
                    val hNorm = (vRatio / targetRatio).coerceIn(0.1f, 1f)
                    cropNormX = 0f; cropNormY = (1f - hNorm) / 2f; cropNormW = 1f; cropNormH = hNorm
                }
            }
            MediaAspectRatio.P_4_3 -> {
                val targetRatio = 4f / 3f
                if (vRatio >= targetRatio) {
                    val wNorm = (targetRatio / vRatio).coerceIn(0.1f, 1f)
                    cropNormX = (1f - wNorm) / 2f; cropNormY = 0f; cropNormW = wNorm; cropNormH = 1f
                } else {
                    val hNorm = (vRatio / targetRatio).coerceIn(0.1f, 1f)
                    cropNormX = 0f; cropNormY = (1f - hNorm) / 2f; cropNormW = 1f; cropNormH = hNorm
                }
            }
            else -> {
                cropNormX = 0f; cropNormY = 0f; cropNormW = 1f; cropNormH = 1f
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    if (cropPreset != MediaAspectRatio.ORIGINAL && !isCustomCrop) {
                        applyCropPreset(cropPreset)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(activeTool) {
        if (activeTool == StudioTool.GIF_MAKER) {
            if (trimEndMs - trimStartMs > 60_000L) {
                trimEndMs = (trimStartMs + 60_000L).coerceAtMost(videoDurationMs)
            }
        }
    }

    val addClipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val isVid = context.contentResolver.getType(uri)?.startsWith("video") == true || uri.toString().contains("video")
                projectClips.add(
                    StudioClipItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        title = uri.lastPathSegment ?: "Added Clip",
                        isVideo = isVid,
                        durationMs = 5000L
                    )
                )
            }
            Toast.makeText(context, "Added ${uris.size} clip(s) to timeline", Toast.LENGTH_SHORT).show()
        }
    }

    val gifPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageStickers.add(
                StudioImageSticker(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    isGif = uri.toString().lowercase().endsWith(".gif")
                )
            )
            Toast.makeText(context, "Sticker/GIF placed on video", Toast.LENGTH_SHORT).show()
        }
    }

    val bgmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            bgmUri = uri
            bgmTitle = uri.lastPathSegment ?: "Audio Track"
            Toast.makeText(context, "Music track attached: $bgmTitle", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(currentActiveClip.uri) {
        val exoItem = ExoMediaItem.fromUri(currentActiveClip.uri)
        exoPlayer.setMediaItem(exoItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(exoPlayer, trimStartMs, trimEndMs, currentActiveClip) {
        while (true) {
            val dur = exoPlayer.duration.coerceAtLeast(1000L)
            if (dur > 1000L && videoDurationMs != dur) {
                videoDurationMs = dur
                if (trimEndMs > dur || trimEndMs <= trimStartMs) {
                    trimEndMs = dur
                }
            }
            val pos = exoPlayer.currentPosition
            currentPositionMs = pos
            isPlaying = exoPlayer.isPlaying

            if (trimEndMs > trimStartMs && (pos >= trimEndMs || pos < trimStartMs)) {
                exoPlayer.seekTo(trimStartMs)
            }
            delay(16)
        }
    }

    LaunchedEffect(videoSpeed) {
        exoPlayer.setPlaybackSpeed(videoSpeed)
    }

    LaunchedEffect(videoVolume) {
        exoPlayer.volume = videoVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(currentActiveClip.uri) {
        isExtractingFrames = true
        thumbnails.clear()
        thumbnails.addAll(withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val extractedList = mutableListOf<Bitmap>()
            try {
                retriever.setDataSourceSafe(context, currentActiveClip.uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationUs = (durationStr?.toLongOrNull() ?: 5000L) * 1000L
                val frameCount = 10

                for (i in 0 until frameCount) {
                    val timeUs = (durationUs / (frameCount + 1)) * (i + 1)
                    val frame = retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        140,
                        90
                    )
                    if (frame != null) extractedList.add(frame)
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                retriever.release()
            }
            extractedList
        })
        if (thumbnails.isNotEmpty()) {
            currentActiveClip.thumbnail = thumbnails.first()
        }
        isExtractingFrames = false
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            MediaProcessorEngine.resetState()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                StudioTopBar(
                    clipCount = projectClips.size,
                    activeClipIndex = activeClipIndex,
                    onClose = onDismiss,
                    onReset = {
                        trimStartMs = 0L
                        trimEndMs = videoDurationMs
                        videoSpeed = 1.0f
                        cropPreset = MediaAspectRatio.ORIGINAL
                        isCustomCrop = false
                        cropNormX = 0f
                        cropNormY = 0f
                        cropNormW = 1f
                        cropNormH = 1f
                        rotationDegrees = 0
                        flipHorizontal = false
                        flipVertical = false
                        activeFilter = MediaEffect.ORIGINAL
                        blurMode = StudioBlurMode.NONE
                        blurIntensity = 0f
                        brightness = 0f
                        contrast = 1f
                        saturation = 1f
                        videoVolume = 1.0f
                        bgmUri = null
                        bgmTitle = null
                        textOverlays.clear()
                        emojiStickers.clear()
                        imageStickers.clear()
                        Toast.makeText(context, "All studio settings reset", Toast.LENGTH_SHORT).show()
                    },
                    onExport = { showExportSheet = true }
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF070709))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val vAspect = if (videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else 16f / 9f
                    val availW = maxWidth.value.coerceAtLeast(1f)
                    val availH = maxHeight.value.coerceAtLeast(1f)
                    val cAspect = availW / availH

                    val (renderWidthDp, renderHeightDp) = if (vAspect > cAspect) {
                        Pair(availW.dp, (availW / vAspect).dp)
                    } else {
                        Pair((availH * vAspect).dp, availH.dp)
                    }

                    val blurMod = if (blurIntensity > 0.01f && blurMode == StudioBlurMode.GAUSSIAN) {
                        Modifier.blur((blurIntensity * 18).dp)
                    } else Modifier

                    Box(
                        modifier = Modifier
                            .size(renderWidthDp, renderHeightDp)
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer {
                                rotationZ = rotationDegrees.toFloat()
                                scaleX = if (flipHorizontal) -1f else 1f
                                scaleY = if (flipVertical) -1f else 1f
                            }
                            .then(blurMod),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    keepScreenOn = true
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val overlayColor = when (activeFilter) {
                                MediaEffect.CINEMA -> Color(0x28005577)
                                MediaEffect.WARM -> Color(0x22FFA500)
                                MediaEffect.COOL -> Color(0x2200BFFF)
                                MediaEffect.CYBERPUNK -> Color(0x28FF007F)
                                MediaEffect.VINTAGE -> Color(0x28C4A482)
                                MediaEffect.DREAMY -> Color(0x20FFFFFF)
                                else -> Color.Transparent
                            }
                            if (overlayColor != Color.Transparent) {
                                drawRect(color = overlayColor, blendMode = BlendMode.Overlay)
                            }

                            if (blurIntensity > 0.01f && blurMode == StudioBlurMode.RADIAL_FOCUS) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = blurIntensity * 0.7f)),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.minDimension * 0.45f
                                    )
                                )
                            }
                        }

                        if (activeTool == StudioTool.CROP || (activeTool == StudioTool.GIF_MAKER && (cropPreset != MediaAspectRatio.ORIGINAL || isCustomCrop))) {
                            CropViewfinderView(
                                isCustomCrop = isCustomCrop,
                                cropNormX = cropNormX,
                                cropNormY = cropNormY,
                                cropNormW = cropNormW,
                                cropNormH = cropNormH,
                                onCropChange = { nx, ny, nw, nh ->
                                    isCustomCrop = true
                                    cropNormX = nx
                                    cropNormY = ny
                                    cropNormW = nw
                                    cropNormH = nh
                                }
                            )
                        }

                        for (item in textOverlays) {
                            key(item.id) {
                                DraggableTextOverlayView(item = item, onDelete = { textOverlays.remove(item) })
                            }
                        }

                        for (item in emojiStickers) {
                            key(item.id) {
                                DraggableEmojiStickerView(item = item, onDelete = { emojiStickers.remove(item) })
                            }
                        }

                        for (item in imageStickers) {
                            key(item.id) {
                                DraggableImageStickerView(item = item, onDelete = { imageStickers.remove(item) })
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassSurface(
                            shape = CircleShape,
                            backgroundColor = Color(0x992B271F),
                            borderColor = Color(0x33FFFFFF),
                            modifier = Modifier.clickable {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${formatTimeShort(currentPositionMs)} / ${formatTimeShort(videoDurationMs)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        GlassSurface(
                            shape = CircleShape,
                            backgroundColor = Color(0x992B271F),
                            borderColor = Color(0x33FFFFFF),
                            modifier = Modifier.clickable { showSpeedDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (videoSpeed != 1.0f) Color(0xFFFFD54F) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090C))
                        .padding(vertical = 6.dp)
                ) {
                    if (projectClips.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(projectClips) { index, clip ->
                                val isSelected = index == activeClipIndex
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF1E222D))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { activeClipIndex = index }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (index > 0) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Move Left",
                                            tint = Color.White.copy(0.7f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    val temp = projectClips[index]
                                                    projectClips[index] = projectClips[index - 1]
                                                    projectClips[index - 1] = temp
                                                    activeClipIndex = index - 1
                                                }
                                        )
                                    }

                                    Text(
                                        text = "${index + 1}. ${clip.title.take(10)}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White
                                    )

                                    if (index < projectClips.lastIndex) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Move Right",
                                            tint = Color.White.copy(0.7f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    val temp = projectClips[index]
                                                    projectClips[index] = projectClips[index + 1]
                                                    projectClips[index + 1] = temp
                                                    activeClipIndex = index + 1
                                                }
                                        )
                                    }

                                    if (index > 0) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove Clip",
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    projectClips.removeAt(index)
                                                    activeClipIndex = (activeClipIndex - 1).coerceAtLeast(0)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .clickable { addClipLauncher.launch("*/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Clip or Photo",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        MediaTrimTimeline(
                            thumbnails = thumbnails,
                            isExtracting = isExtractingFrames,
                            videoDurationMs = videoDurationMs,
                            currentPositionMs = currentPositionMs,
                            startMs = trimStartMs,
                            endMs = trimEndMs,
                            maxDurationMs = if (activeTool == StudioTool.GIF_MAKER) 60_000L else null,
                            onRangeChange = { s, e ->
                                trimStartMs = s
                                trimEndMs = e
                                exoPlayer.seekTo(s)
                            },
                            onScrubPosition = { scrubMs ->
                                currentPositionMs = scrubMs
                                exoPlayer.seekTo(scrubMs)
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = if (activeTool == StudioTool.GIF_MAKER) Color(0xFFFFD54F) else Color.White,
                            handleWidthDp = 24,
                            showPlayhead = true
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .background(Color(0xFF0F0F14))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (activeTool) {
                        StudioTool.TRIM -> {
                            TrimControlPanel(
                                startMs = trimStartMs,
                                endMs = trimEndMs,
                                onResetTrim = {
                                    trimStartMs = 0L
                                    trimEndMs = videoDurationMs
                                }
                            )
                        }
                        StudioTool.CROP -> {
                            CropControlPanel(
                                currentPreset = cropPreset,
                                isCustomCrop = isCustomCrop,
                                onSelectPreset = { preset -> applyCropPreset(preset) },
                                onEnableCustomCrop = {
                                    isCustomCrop = true
                                    Toast.makeText(context, "Drag corners/edges on the video canvas to crop", Toast.LENGTH_SHORT).show()
                                },
                                onRotate = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                onFlipH = { flipHorizontal = !flipHorizontal },
                                onFlipV = { flipVertical = !flipVertical }
                            )
                        }
                        StudioTool.FILTERS -> {
                            FiltersAndBlurControlPanel(
                                activeFilter = activeFilter,
                                onSelectFilter = { activeFilter = it },
                                blurMode = blurMode,
                                onSelectBlurMode = { blurMode = it },
                                blurIntensity = blurIntensity,
                                onBlurIntensityChange = { blurIntensity = it }
                            )
                        }
                        StudioTool.ADJUST -> {
                            AdjustmentsControlPanel(
                                brightness = brightness,
                                onBrightnessChange = { brightness = it },
                                contrast = contrast,
                                onContrastChange = { contrast = it },
                                saturation = saturation,
                                onSaturationChange = { saturation = it }
                            )
                        }
                        StudioTool.TEXT -> {
                            TextAndStickersControlPanel(
                                onAddText = { showAddTextDialog = true },
                                onAddEmoji = { showEmojiPicker = true },
                                onOpenKeyboardStickers = { showKeyboardStickerDialog = true },
                                onPickFileGif = { gifPickerLauncher.launch("image/*") }
                            )
                        }
                        StudioTool.AUDIO -> {
                            AudioControlPanel(
                                videoVolume = videoVolume,
                                onVideoVolumeChange = { videoVolume = it },
                                bgmTitle = bgmTitle,
                                bgmVolume = bgmVolume,
                                onBgmVolumeChange = { bgmVolume = it },
                                onPickMusic = { bgmLauncher.launch("audio/*") },
                                onRemoveBgm = { bgmUri = null; bgmTitle = null }
                            )
                        }
                        StudioTool.GIF_MAKER -> {
                            val durationMs = (trimEndMs - trimStartMs).coerceAtLeast(500L)
                            GifMakerControlPanel(
                                durationMs = durationMs,
                                direction = gifDirection,
                                onDirectionChange = { gifDirection = it },
                                qualityPreset = gifQualityPreset,
                                onQualityPresetChange = { gifQualityPreset = it },
                                cropPreset = cropPreset,
                                onCropPresetChange = { preset -> applyCropPreset(preset) },
                                onCreateGif = {
                                    isExporting = true
                                    scope.launch {
                                        MediaProcessorEngine.createStudioGif(
                                            context = context,
                                            inputPath = currentActiveClip.uri.toString(),
                                            inputUri = currentActiveClip.uri,
                                            startMs = trimStartMs,
                                            endMs = trimEndMs,
                                            direction = gifDirection,
                                            cropPreset = cropPreset.name,
                                            cropNormX = cropNormX,
                                            cropNormY = cropNormY,
                                            cropNormW = cropNormW,
                                            cropNormH = cropNormH,
                                            isCustomCrop = isCustomCrop,
                                            videoSpeed = videoSpeed,
                                            rotationDegrees = rotationDegrees,
                                            flipH = flipHorizontal,
                                            flipV = flipVertical,
                                            filterEffect = activeFilter.name,
                                            brightness = brightness,
                                            contrast = contrast,
                                            saturation = saturation,
                                            targetQualityPreset = gifQualityPreset
                                        )
                                        isExporting = false
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioToolItem(
                        icon = Icons.Default.ContentCut,
                        label = "Trim",
                        isSelected = activeTool == StudioTool.TRIM,
                        onClick = { activeTool = StudioTool.TRIM }
                    )
                    StudioToolItem(
                        icon = Icons.Default.Crop,
                        label = "Crop",
                        isSelected = activeTool == StudioTool.CROP,
                        onClick = { activeTool = StudioTool.CROP }
                    )
                    StudioToolItem(
                        icon = Icons.Default.Animation,
                        label = "GIF Maker",
                        isSelected = activeTool == StudioTool.GIF_MAKER,
                        onClick = { activeTool = StudioTool.GIF_MAKER }
                    )
                    StudioToolItem(
                        icon = Icons.Default.ColorLens,
                        label = "Filters & Blur",
                        isSelected = activeTool == StudioTool.FILTERS,
                        onClick = { activeTool = StudioTool.FILTERS }
                    )
                    StudioToolItem(
                        icon = Icons.Default.WbSunny,
                        label = "Adjust",
                        isSelected = activeTool == StudioTool.ADJUST,
                        onClick = { activeTool = StudioTool.ADJUST }
                    )
                    StudioToolItem(
                        icon = Icons.Default.InsertEmoticon,
                        label = "Text/Sticker",
                        isSelected = activeTool == StudioTool.TEXT,
                        onClick = { activeTool = StudioTool.TEXT }
                    )
                    StudioToolItem(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "Audio",
                        isSelected = activeTool == StudioTool.AUDIO,
                        onClick = { activeTool = StudioTool.AUDIO }
                    )
                }
            }
        }
    }

    if (showSpeedDialog) {
        SpeedSelectionDialog(
            currentSpeed = videoSpeed,
            onSpeedSelect = {
                videoSpeed = it
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showAddTextDialog) {
        AddRichTextDialog(
            onAdd = { text, fontStyle, color, bgStyle, fontSize ->
                textOverlays.add(
                    StudioTextOverlay(
                        text = text,
                        fontFamilyStyle = fontStyle,
                        color = color,
                        backgroundStyle = bgStyle,
                        fontSizeSp = fontSize
                    )
                )
                showAddTextDialog = false
            },
            onDismiss = { showAddTextDialog = false }
        )
    }

    if (showEmojiPicker) {
        CategoryStickerPickerDialog(
            onSelectSticker = { sticker ->
                emojiStickers.add(StudioEmojiSticker(emoji = sticker))
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }

    if (showKeyboardStickerDialog) {
        KeyboardStickerReceiverDialog(
            onReceiveContent = { uri, isGif ->
                imageStickers.add(
                    StudioImageSticker(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        isGif = isGif
                    )
                )
                showKeyboardStickerDialog = false
                Toast.makeText(context, "Sticker / GIF added to canvas!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showKeyboardStickerDialog = false }
        )
    }

    if (showExportSheet) {
        ExportStudioDialog(
            clipDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(500L),
            onExport = { format, gifFps, gifWidth ->
                showExportSheet = false
                isExporting = true
                scope.launch {
                    val success = MediaProcessorEngine.exportStudioVideo(
                        context = context,
                        inputPath = currentActiveClip.uri.toString(),
                        inputUri = currentActiveClip.uri,
                        startMs = trimStartMs,
                        endMs = trimEndMs,
                        videoSpeed = videoSpeed,
                        cropPreset = cropPreset.name,
                        cropNormX = cropNormX,
                        cropNormY = cropNormY,
                        cropNormW = cropNormW,
                        cropNormH = cropNormH,
                        isCustomCrop = isCustomCrop,
                        rotationDegrees = rotationDegrees,
                        flipH = flipHorizontal,
                        flipV = flipVertical,
                        filterEffect = activeFilter.name,
                        blurMode = blurMode.name,
                        blurIntensity = blurIntensity,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        videoVolume = videoVolume,
                        bgmUri = bgmUri,
                        bgmVolume = bgmVolume,
                        exportFormat = format,
                        gifFps = gifFps,
                        gifWidth = gifWidth
                    )
                    isExporting = false
                    if (success) {
                        Toast.makeText(context, "Export saved to 'MediaNest Studio' folder!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Export encountered an error.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showExportSheet = false }
        )
    }

    if (isExporting || processingState is MediaProcessorEngine.ProcessingState.Processing) {
        val currentProgress = (processingState as? MediaProcessorEngine.ProcessingState.Processing)?.progressPercent ?: 0
        val currentStatus = (processingState as? MediaProcessorEngine.ProcessingState.Processing)?.statusText ?: "Rendering Video in MediaNest Studio..."

        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = Color(0xF0111625),
                    borderColor = Color(0x33FFFFFF),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { currentProgress / 100f },
                                modifier = Modifier.size(72.dp),
                                color = Color(0xFFFFD54F),
                                trackColor = Color.White.copy(0.12f),
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = "$currentProgress%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = currentStatus,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        OutlinedButton(
                            onClick = {
                                MediaProcessorEngine.cancelCurrent()
                                isExporting = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                            border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel Export", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (processingState is MediaProcessorEngine.ProcessingState.Completed) {
        val completed = processingState as MediaProcessorEngine.ProcessingState.Completed
        Dialog(
            onDismissRequest = { MediaProcessorEngine.resetState() }
        ) {
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xF0111625),
                borderColor = Color(0x33FFFFFF),
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = if (completed.outputFile.extension.equals("gif", ignoreCase = true)) "GIF Created Successfully!" else "Video Exported Successfully!",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "File: ${completed.outputFile.name}",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Size: ${String.format(Locale.US, "%.1f MB", completed.newSizeBytes / (1024f * 1024f))}",
                                fontSize = 12.sp,
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Saved in: Movies / MediaNest Studio",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            MediaProcessorEngine.resetState()
                            Toast.makeText(context, "Saved to MediaNest Studio", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }

    if (processingState is MediaProcessorEngine.ProcessingState.Failed) {
        val failed = processingState as MediaProcessorEngine.ProcessingState.Failed
        Dialog(
            onDismissRequest = { MediaProcessorEngine.resetState() }
        ) {
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xF0111625),
                borderColor = Color(0x33EF4444),
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "Creation Failed",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = failed.errorMessage,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = { MediaProcessorEngine.resetState() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
