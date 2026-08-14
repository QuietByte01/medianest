package com.medianest.ui.videoplayer

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import com.medianest.ui.components.AppSlider
import com.medianest.ui.components.AppSliderStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.util.MediaProcessorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-End Mobile Video Editor Studio:
 * 1. Immediate video pause when entering editor.
 * 2. Solid, smooth timeline handles with draggable trim heads & playhead needle.
 * 3. Interactive Crop Viewfinder with Custom Drag Box (Corners/Edges + Rule of Thirds) & Presets.
 * 4. Rich Text Overlays with Multiple Font Families, Background Badges, Sizes & Colors.
 * 5. Sticker & Keyboard GIF Engine (Gboard Stickers/GIFs, Bitmoji, File GIFs, Animated overlays).
 * 6. Precision Blur Options: Gaussian Blur, Tilt-Shift Radial Focus, Pixelate / Mosaic, Motion Blur.
 * 7. Dual Audio Mixer: Simultaneous Original Video Volume (0-200%) + Background Music (0-200%).
 * 8. Perfectly Centered Export Progress Dialog.
 * 9. Export folder named "MediaNest Studio".
 * 10. Multi-clip Timeline: Added videos/images shown side-by-side with drag re-ordering (before/after).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorStudioSheet(
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ----------------------------------------------------
    // Multi-Clip Timeline Project
    // ----------------------------------------------------
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

    // ----------------------------------------------------
    // ExoPlayer for In-Studio Live Preview
    // ----------------------------------------------------
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var videoDurationMs by remember { mutableLongStateOf(currentActiveClip.durationMs.coerceAtLeast(1000L)) }

    // ----------------------------------------------------
    // Editing Tools State
    // ----------------------------------------------------
    var activeTool by remember { mutableStateOf(StudioTool.TRIM) }

    // 1. Trim Range
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(videoDurationMs) }

    // 2. Playback Speed
    var videoSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // 3. Crop & Transform
    var cropPreset by remember { mutableStateOf(CropPreset.ORIGINAL) }
    var isCustomCrop by remember { mutableStateOf(false) }
    var cropNormX by remember { mutableFloatStateOf(0f) }
    var cropNormY by remember { mutableFloatStateOf(0f) }
    var cropNormW by remember { mutableFloatStateOf(1f) }
    var cropNormH by remember { mutableFloatStateOf(1f) }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    // 4. Filters & Blur
    var activeFilter by remember { mutableStateOf(VideoStudioFilter.ORIGINAL) }
    var blurMode by remember { mutableStateOf(StudioBlurMode.NONE) }
    var blurIntensity by remember { mutableFloatStateOf(0f) } // 0.0 to 1.0

    // 5. Adjustments
    var brightness by remember { mutableFloatStateOf(0f) } // -0.4 to 0.4
    var contrast by remember { mutableFloatStateOf(1f) }   // 0.5 to 1.8
    var saturation by remember { mutableFloatStateOf(1f) } // 0.0 to 2.0

    // 6. Text & Stickers (Text, Emojis, Keyboard Stickers & GIFs)
    val textOverlays = remember { mutableStateListOf<StudioTextOverlay>() }
    val emojiStickers = remember { mutableStateListOf<StudioEmojiSticker>() }
    val imageStickers = remember { mutableStateListOf<StudioImageSticker>() }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showKeyboardStickerDialog by remember { mutableStateOf(false) }

    // 7. Dual Audio Mixer (Original Video Vol + Additional Music Vol)
    var videoVolume by remember { mutableFloatStateOf(1.0f) }
    var bgmUri by remember { mutableStateOf<Uri?>(null) }
    var bgmTitle by remember { mutableStateOf<String?>(null) }
    var bgmVolume by remember { mutableFloatStateOf(0.7f) }

    // 8. Thumbnails Extraction for Active Clip
    val thumbnails = remember { mutableStateListOf<Bitmap>() }
    var isExtractingFrames by remember { mutableStateOf(true) }

    // 9. Master Exporter State
    var showExportSheet by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val processingState by MediaProcessorEngine.processingState.collectAsState()

    // Launcher to add new video clips or photos to timeline
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

    // Launcher for file GIF / sticker images
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

    // Launcher for background music track
    val bgmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            bgmUri = uri
            bgmTitle = uri.lastPathSegment ?: "Audio Track"
            Toast.makeText(context, "Music track attached: $bgmTitle", Toast.LENGTH_SHORT).show()
        }
    }

    // Switch ExoPlayer source when active clip changes
    LaunchedEffect(currentActiveClip.uri) {
        val exoItem = ExoMediaItem.fromUri(currentActiveClip.uri)
        exoPlayer.setMediaItem(exoItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Playback loop & trim boundary clamping
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

            // Clamp playback within trim selection
            if (trimEndMs > trimStartMs && (pos >= trimEndMs || pos < trimStartMs)) {
                exoPlayer.seekTo(trimStartMs)
            }
            delay(50)
        }
    }

    // Apply speed
    LaunchedEffect(videoSpeed) {
        exoPlayer.setPlaybackSpeed(videoSpeed)
    }

    // Apply video volume
    LaunchedEffect(videoVolume) {
        exoPlayer.volume = videoVolume.coerceIn(0f, 1f)
    }

    // Extract real video frames for current active clip
    LaunchedEffect(currentActiveClip.uri) {
        isExtractingFrames = true
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, currentActiveClip.uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationUs = (durationStr?.toLongOrNull() ?: 5000L) * 1000L
                val frameCount = 10
                val extractedList = mutableListOf<Bitmap>()

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

                withContext(Dispatchers.Main) {
                    thumbnails.clear()
                    thumbnails.addAll(extractedList)
                    if (extractedList.isNotEmpty()) {
                        currentActiveClip.thumbnail = extractedList.first()
                    }
                    isExtractingFrames = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isExtractingFrames = false }
            } finally {
                retriever.release()
            }
        }
    }

    // Cleanup when closing
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            MediaProcessorEngine.resetState()
        }
    }

    // Fullscreen Studio Dialog (Protected against accidental dismiss)
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
                // ====================================================
                // 1. TOP BAR: Close, Clip counter, Reset, Export
                // ====================================================
                StudioTopBar(
                    clipCount = projectClips.size,
                    activeClipIndex = activeClipIndex,
                    onClose = onDismiss,
                    onReset = {
                        trimStartMs = 0L
                        trimEndMs = videoDurationMs
                        videoSpeed = 1.0f
                        cropPreset = CropPreset.ORIGINAL
                        isCustomCrop = false
                        cropNormX = 0f
                        cropNormY = 0f
                        cropNormW = 1f
                        cropNormH = 1f
                        rotationDegrees = 0
                        flipHorizontal = false
                        flipVertical = false
                        activeFilter = VideoStudioFilter.ORIGINAL
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

                // ====================================================
                // 2. VIDEO PREVIEW CANVAS + INTERACTIVE CROP & BLUR
                // ====================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF070709)),
                    contentAlignment = Alignment.Center
                ) {
                    var containerWidthPx by remember { mutableFloatStateOf(1f) }
                    var containerHeightPx by remember { mutableFloatStateOf(1f) }

                    // Video Preview Viewport Box
                    val blurMod = if (blurIntensity > 0.01f && blurMode == StudioBlurMode.GAUSSIAN) {
                        Modifier.blur((blurIntensity * 18).dp)
                    } else Modifier

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .onSizeChanged {
                                containerWidthPx = it.width.toFloat().coerceAtLeast(1f)
                                containerHeightPx = it.height.toFloat().coerceAtLeast(1f)
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer {
                                rotationZ = rotationDegrees.toFloat()
                                scaleX = if (flipHorizontal) -1f else 1f
                                scaleY = if (flipVertical) -1f else 1f
                            }
                            .then(blurMod),
                        contentAlignment = Alignment.Center
                    ) {
                        // ExoPlayer Video View
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    keepScreenOn = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Visual Filter & Blur Canvas Overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val overlayColor = when (activeFilter) {
                                VideoStudioFilter.CINEMA -> Color(0x28005577)
                                VideoStudioFilter.WARM -> Color(0x22FFA500)
                                VideoStudioFilter.COOL -> Color(0x2200BFFF)
                                VideoStudioFilter.CYBERPUNK -> Color(0x28FF007F)
                                VideoStudioFilter.VINTAGE -> Color(0x28C4A482)
                                VideoStudioFilter.DREAMY -> Color(0x20FFFFFF)
                                else -> Color.Transparent
                            }
                            if (overlayColor != Color.Transparent) {
                                drawRect(color = overlayColor, blendMode = BlendMode.Overlay)
                            }

                            // Tilt-Shift / Radial Focus Blur Overlay
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

                        // Interactive Viewfinder & Crop Mask (Visible when CROP is active)
                        if (activeTool == StudioTool.CROP) {
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

                        // Draggable Text Overlays
                        for (item in textOverlays) {
                            key(item.id) {
                                DraggableTextOverlayView(item = item, onDelete = { textOverlays.remove(item) })
                            }
                        }

                        // Draggable Emoji Stickers
                        for (item in emojiStickers) {
                            key(item.id) {
                                DraggableEmojiStickerView(item = item, onDelete = { emojiStickers.remove(item) })
                            }
                        }

                        // Draggable Keyboard / GIF Image Stickers
                        for (item in imageStickers) {
                            key(item.id) {
                                DraggableImageStickerView(item = item, onDelete = { imageStickers.remove(item) })
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // FLOATING PLAYBACK CAPSULE (▶ 0:04 / 0:11) + (⏲) SPEED
                    // ----------------------------------------------------
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

                        // Speedometer Button (⏲)
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

                // ====================================================
                // 3. MULTI-CLIP TIMELINE TRACK ROW
                // Left: White rounded square [ + ] button
                // Right: Interactive Filmstrip Track with Trim Heads & Clips
                // ====================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090C))
                        .padding(vertical = 6.dp)
                ) {
                    // Multi-Clip Carousel / Segment Bar
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
                                    // Move Left button (if not first)
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

                                    // Move Right button (if not last)
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

                                    // Delete clip button
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

                    // Main Timeline Filmstrip Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // White Rounded Square [ + ] Button
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

                        // Robust Draggable Filmstrip Track with moving Trim heads
                        StudioFilmstripTrack(
                            thumbnails = thumbnails,
                            isExtracting = isExtractingFrames,
                            videoDurationMs = videoDurationMs,
                            currentPositionMs = currentPositionMs,
                            startMs = trimStartMs,
                            endMs = trimEndMs,
                            onRangeChange = { s, e ->
                                trimStartMs = s
                                trimEndMs = e
                                exoPlayer.seekTo(s)
                            },
                            onScrubPosition = { scrubMs ->
                                currentPositionMs = scrubMs
                                exoPlayer.seekTo(scrubMs)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ====================================================
                // 4. CONTEXTUAL TOOL CONTROLS PANEL
                // ====================================================
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
                                onSelectPreset = { preset ->
                                    cropPreset = preset
                                    isCustomCrop = false
                                    when (preset) {
                                        CropPreset.ORIGINAL -> { cropNormX = 0f; cropNormY = 0f; cropNormW = 1f; cropNormH = 1f }
                                        CropPreset.P_1_1 -> { cropNormX = 0.15f; cropNormY = 0f; cropNormW = 0.7f; cropNormH = 1f }
                                        CropPreset.P_9_16 -> { cropNormX = 0.22f; cropNormY = 0f; cropNormW = 0.56f; cropNormH = 1f }
                                        CropPreset.P_16_9 -> { cropNormX = 0f; cropNormY = 0.15f; cropNormW = 1f; cropNormH = 0.7f }
                                        CropPreset.P_4_3 -> { cropNormX = 0.1f; cropNormY = 0f; cropNormW = 0.8f; cropNormH = 1f }
                                        CropPreset.P_4_5 -> { cropNormX = 0.15f; cropNormY = 0f; cropNormW = 0.7f; cropNormH = 0.875f }
                                        CropPreset.P_21_9 -> { cropNormX = 0f; cropNormY = 0.25f; cropNormW = 1f; cropNormH = 0.5f }
                                    }
                                },
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
                    }
                }

                // ====================================================
                // 5. BOTTOM STUDIO TOOLBAR (6 Tools matching design)
                // ====================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
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

    // ----------------------------------------------------
    // Speed Selection Modal Dialog
    // ----------------------------------------------------
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

    // ----------------------------------------------------
    // Rich Text Overlay Customizer Dialog
    // ----------------------------------------------------
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

    // ----------------------------------------------------
    // Category Sticker & Emoji Picker Dialog
    // ----------------------------------------------------
    if (showEmojiPicker) {
        CategoryStickerPickerDialog(
            onSelectSticker = { sticker ->
                emojiStickers.add(StudioEmojiSticker(emoji = sticker))
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }

    // ----------------------------------------------------
    // Keyboard Sticker & GIF Receiver Dialog
    // ----------------------------------------------------
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

    // ----------------------------------------------------
    // Master Export Dialog
    // ----------------------------------------------------
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

    // ----------------------------------------------------
    // Live Export Progress Dialog (Centered Layout)
    // ----------------------------------------------------
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
}

// ====================================================
// TOP BAR COMPOSABLE
// ====================================================
@Composable
private fun StudioTopBar(
    clipCount: Int,
    activeClipIndex: Int,
    onClose: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Video Editor",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (clipCount > 1) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clip ${activeClipIndex + 1}/$clipCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White.copy(0.8f))
            }

            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Export", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ====================================================
// INTERACTIVE CROP VIEWFINDER WITH CORNERS & EDGES
// ====================================================
@Composable
private fun CropViewfinderView(
    isCustomCrop: Boolean,
    cropNormX: Float,
    cropNormY: Float,
    cropNormW: Float,
    cropNormH: Float,
    onCropChange: (nx: Float, ny: Float, nw: Float, nh: Float) -> Unit
) {
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }

    val currentCropX by rememberUpdatedState(cropNormX)
    val currentCropY by rememberUpdatedState(cropNormY)
    val currentCropW by rememberUpdatedState(cropNormW)
    val currentCropH by rememberUpdatedState(cropNormH)
    val currentOnCropChange by rememberUpdatedState(onCropChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewWidth = it.width.toFloat().coerceAtLeast(1f)
                viewHeight = it.height.toFloat().coerceAtLeast(1f)
            }
    ) {
        val leftPx = cropNormX * viewWidth
        val topPx = cropNormY * viewHeight
        val widthPx = (cropNormW * viewWidth).coerceAtLeast(60f)
        val heightPx = (cropNormH * viewHeight).coerceAtLeast(60f)
        val rightPx = leftPx + widthPx
        val bottomPx = topPx + heightPx

        // 1. Shaded Scrim for cropped-out areas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maskColor = Color(0xA6000000)
            // Top Mask
            drawRect(color = maskColor, topLeft = Offset.Zero, size = Size(size.width, topPx))
            // Bottom Mask
            drawRect(color = maskColor, topLeft = Offset(0f, bottomPx), size = Size(size.width, size.height - bottomPx))
            // Left Mask
            drawRect(color = maskColor, topLeft = Offset(0f, topPx), size = Size(leftPx, heightPx))
            // Right Mask
            drawRect(color = maskColor, topLeft = Offset(rightPx, topPx), size = Size(size.width - rightPx, heightPx))

            // Active Crop Rect Border (Glowing Yellow)
            drawRect(
                color = Color(0xFFFFD54F),
                topLeft = Offset(leftPx, topPx),
                size = Size(widthPx, heightPx),
                style = Stroke(width = 2.dp.toPx())
            )

            // Rule of Thirds Grid Lines
            val thirdW = widthPx / 3f
            val thirdH = heightPx / 3f
            drawLine(Color.White.copy(0.4f), Offset(leftPx + thirdW, topPx), Offset(leftPx + thirdW, bottomPx), 1.dp.toPx())
            drawLine(Color.White.copy(0.4f), Offset(leftPx + thirdW * 2, topPx), Offset(leftPx + thirdW * 2, bottomPx), 1.dp.toPx())
            drawLine(Color.White.copy(0.4f), Offset(leftPx, topPx + thirdH), Offset(rightPx, topPx + thirdH), 1.dp.toPx())
            drawLine(Color.White.copy(0.4f), Offset(leftPx, topPx + thirdH * 2), Offset(rightPx, topPx + thirdH * 2), 1.dp.toPx())
        }

        // 2. Drag Handles for 4 Corners & Entire Crop Window
        val density = LocalDensity.current

        // Whole Box Drag (Move Crop Window Smoothly)
        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val vw = viewWidth
                        val vh = viewHeight
                        if (vw > 0f && vh > 0f) {
                            val newX = (currentCropX + dragAmount.x / vw).coerceIn(0f, 1f - currentCropW)
                            val newY = (currentCropY + dragAmount.y / vh).coerceIn(0f, 1f - currentCropH)
                            currentOnCropChange(newX, newY, currentCropW, currentCropH)
                        }
                    }
                }
        )

        // Top-Left Corner Handle
        CornerHandle(
            offsetX = leftPx,
            offsetY = topPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curR = currentCropX + currentCropW
                    val curB = currentCropY + currentCropH
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newL = (currentCropX + dx / vw).coerceIn(0f, curR - minSizeX)
                    val newT = (currentCropY + dy / vh).coerceIn(0f, curB - minSizeY)
                    currentOnCropChange(newL, newT, curR - newL, curB - newT)
                }
            }
        )

        // Top-Right Corner Handle
        CornerHandle(
            offsetX = rightPx,
            offsetY = topPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curL = currentCropX
                    val curB = currentCropY + currentCropH
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newR = (curL + currentCropW + dx / vw).coerceIn(curL + minSizeX, 1f)
                    val newT = (currentCropY + dy / vh).coerceIn(0f, curB - minSizeY)
                    currentOnCropChange(curL, newT, newR - curL, curB - newT)
                }
            }
        )

        // Bottom-Left Corner Handle
        CornerHandle(
            offsetX = leftPx,
            offsetY = bottomPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curR = currentCropX + currentCropW
                    val curT = currentCropY
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newL = (currentCropX + dx / vw).coerceIn(0f, curR - minSizeX)
                    val newB = (curT + currentCropH + dy / vh).coerceIn(curT + minSizeY, 1f)
                    currentOnCropChange(newL, curT, curR - newL, newB - curT)
                }
            }
        )

        // Bottom-Right Corner Handle
        CornerHandle(
            offsetX = rightPx,
            offsetY = bottomPx,
            onDrag = { dx, dy ->
                val vw = viewWidth
                val vh = viewHeight
                if (vw > 0f && vh > 0f) {
                    val curL = currentCropX
                    val curT = currentCropY
                    val minSizeX = 60f / vw
                    val minSizeY = 60f / vh
                    val newR = (curL + currentCropW + dx / vw).coerceIn(curL + minSizeX, 1f)
                    val newB = (curT + currentCropH + dy / vh).coerceIn(curT + minSizeY, 1f)
                    currentOnCropChange(curL, curT, newR - curL, newB - curT)
                }
            }
        )
    }
}

@Composable
private fun CornerHandle(
    offsetX: Float,
    offsetY: Float,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val handleTouchSizeDp = 44.dp // Large touch target for easy finger grabbing
    val density = LocalDensity.current
    val halfTouchPx = with(density) { (handleTouchSizeDp / 2).toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset((offsetX - halfTouchPx).roundToInt(), (offsetY - halfTouchPx).roundToInt()) }
            .size(handleTouchSizeDp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Visual indicator circle
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD54F))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

// ====================================================
// STUDIO FILMSTRIP TRACK WITH ROBUST MOVING HANDLES
// ====================================================
@Composable
private fun StudioFilmstripTrack(
    thumbnails: List<Bitmap>,
    isExtracting: Boolean,
    videoDurationMs: Long,
    currentPositionMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    onScrubPosition: (scrubMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val durationFloat = videoDurationMs.toFloat().coerceAtLeast(1000f)

    val currentStartMs by rememberUpdatedState(startMs)
    val currentEndMs by rememberUpdatedState(endMs)
    val currentPosition by rememberUpdatedState(currentPositionMs)
    val currentVideoDurationMs by rememberUpdatedState(videoDurationMs)
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentOnScrubPosition by rememberUpdatedState(onScrubPosition)

    val startFrac = (startMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / durationFloat).coerceIn(0f, 1f)
    val currentFrac = (currentPositionMs.toFloat() / durationFloat).coerceIn(0f, 1f)

    val handleWidthDp = 24.dp
    val handleWidthPx = with(density) { handleWidthDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E222D))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val tw = trackWidthPx
                    if (tw > 0f) {
                        val frac = (offset.x / tw).coerceIn(0f, 1f)
                        val targetMs = (frac * currentVideoDurationMs.toFloat()).toLong()
                        currentOnScrubPosition(targetMs)
                    }
                }
            }
    ) {
        // 1. Video Frame Thumbnails
        if (thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(8) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(Color.White.copy(alpha = if (isExtracting) 0.08f else 0.04f))
                    )
                }
            }
        }

        // 2. Interactive Selection Box & Draggable Heads
        if (trackWidthPx > 0f) {
            val usableWidth = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
            val startLeftPx = (startFrac * usableWidth).coerceAtLeast(0f)
            val endRightPx = (handleWidthPx * 2 + endFrac * usableWidth).coerceAtMost(trackWidthPx)
            val selectionWidthPx = (endRightPx - startLeftPx).coerceAtLeast(handleWidthPx * 2)

            // Dimmed Left Scrim
            if (startLeftPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { startLeftPx.toDp() })
                        .background(Color.Black.copy(alpha = 0.70f))
                )
            }

            // Dimmed Right Scrim
            if (endRightPx < trackWidthPx) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset(endRightPx.roundToInt(), 0) }
                        .width(with(density) { (trackWidthPx - endRightPx).toDp() })
                        .background(Color.Black.copy(alpha = 0.70f))
                )
            }

            // Central Selection Border Window (Draggable to shift window smoothly)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(startLeftPx.roundToInt(), 0) }
                    .width(with(density) { selectionWidthPx.toDp() })
                    .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        var accumMs = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumMs = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                                val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                                accumMs += (dragAmount / uw) * dur
                                val deltaMs = accumMs.toLong()
                                if (deltaMs != 0L) {
                                    accumMs -= deltaMs
                                    val clipLen = currentEndMs - currentStartMs
                                    val newStart = (currentStartMs + deltaMs).coerceIn(0L, currentVideoDurationMs - clipLen)
                                    val newEnd = newStart + clipLen
                                    currentOnRangeChange(newStart, newEnd)
                                }
                            }
                        )
                    }
            )

            // Left White Handle with Grip Notch (|) - Touch target expanded
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(startLeftPx.roundToInt(), 0) }
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        var accumMs = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumMs = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                                val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                                accumMs += (dragAmount / uw) * dur
                                val deltaMs = accumMs.toLong()
                                if (deltaMs != 0L) {
                                    accumMs -= deltaMs
                                    val newStart = (currentStartMs + deltaMs).coerceIn(0L, currentEndMs - 500L)
                                    currentOnRangeChange(newStart, currentEndMs)
                                    currentOnScrubPosition(newStart)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(20.dp)
                        .background(Color(0xFF1E222D), RoundedCornerShape(1.dp))
                )
            }

            // Right White Handle with Grip Notch (|) - Touch target expanded
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset((endRightPx - handleWidthPx).roundToInt(), 0) }
                    .width(handleWidthDp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        var accumMs = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumMs = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                                val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                                accumMs += (dragAmount / uw) * dur
                                val deltaMs = accumMs.toLong()
                                if (deltaMs != 0L) {
                                    accumMs -= deltaMs
                                    val newEnd = (currentEndMs + deltaMs).coerceIn(currentStartMs + 500L, currentVideoDurationMs)
                                    currentOnRangeChange(currentStartMs, newEnd)
                                    currentOnScrubPosition(newEnd)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(20.dp)
                        .background(Color(0xFF1E222D), RoundedCornerShape(1.dp))
                )
            }

            // 3. Draggable Yellow/White Playhead Needle (|) in Center of Track
            val playheadX = (startLeftPx + handleWidthPx + (currentFrac * (selectionWidthPx - handleWidthPx * 2))).coerceIn(startLeftPx, endRightPx)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(playheadX.roundToInt() - with(density) { 10.dp.toPx() }.roundToInt(), 0) }
                    .width(20.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val dur = currentVideoDurationMs.toFloat().coerceAtLeast(1000f)
                            val uw = (trackWidthPx - handleWidthPx * 2).coerceAtLeast(10f)
                            val deltaMs = (dragAmount / uw * dur).toLong()
                            val newPos = (currentPosition + deltaMs).coerceIn(currentStartMs, currentEndMs)
                            currentOnScrubPosition(newPos)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(Color(0xFFFFD54F), RoundedCornerShape(1.5.dp))
                )
            }
        }
    }
}

// ====================================================
// BOTTOM STUDIO TOOL BAR ITEM
// ====================================================
@Composable
private fun StudioToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = Color(0xFFFFD54F)
    val inactiveColor = Color.White.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) activeColor else inactiveColor
        )
    }
}

// ====================================================
// TOOL CONTROL PANELS
// ====================================================
@Composable
private fun TrimControlPanel(
    startMs: Long,
    endMs: Long,
    onResetTrim: () -> Unit
) {
    val clipDurationMs = (endMs - startMs).coerceAtLeast(0L)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "TRIM TIMELINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD54F)
            )
            Text(
                text = "Start: ${formatTimeShort(startMs)}  •  Clip: ${formatTimeShort(clipDurationMs)}  •  End: ${formatTimeShort(endMs)}",
                fontSize = 12.sp,
                color = Color.White
            )
        }
        OutlinedButton(
            onClick = onResetTrim,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Reset Trim", fontSize = 11.sp)
        }
    }
}

@Composable
private fun CropControlPanel(
    currentPreset: CropPreset,
    isCustomCrop: Boolean,
    onSelectPreset: (CropPreset) -> Unit,
    onEnableCustomCrop: () -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = isCustomCrop,
            onClick = onEnableCustomCrop,
            label = { Text("Custom", fontSize = 11.sp, fontWeight = if (isCustomCrop) FontWeight.Bold else FontWeight.Normal) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFFFD54F),
                selectedLabelColor = Color.Black,
                containerColor = Color(0x22FFFFFF),
                labelColor = Color.White
            )
        )

        CropPreset.values().forEach { preset ->
            val isSel = currentPreset == preset && !isCustomCrop
            FilterChip(
                selected = isSel,
                onClick = { onSelectPreset(preset) },
                label = { Text(preset.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFD54F),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0x22FFFFFF),
                    labelColor = Color.White
                )
            )
        }

        IconButton(onClick = onRotate) {
            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White)
        }
        IconButton(onClick = onFlipH) {
            Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = Color.White)
        }
    }
}

@Composable
private fun FiltersAndBlurControlPanel(
    activeFilter: VideoStudioFilter,
    onSelectFilter: (VideoStudioFilter) -> Unit,
    blurMode: StudioBlurMode,
    onSelectBlurMode: (StudioBlurMode) -> Unit,
    blurIntensity: Float,
    onBlurIntensityChange: (Float) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Filters, 1: Blur Effects

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Text(
                text = "Color Filters",
                fontSize = 11.sp,
                fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedSubTab == 0) Color(0xFFFFD54F) else Color.White.copy(0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { selectedSubTab = 0 }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Text(
                text = "Blur & Privacy",
                fontSize = 11.sp,
                fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedSubTab == 1) Color(0xFFFFD54F) else Color.White.copy(0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { selectedSubTab = 1 }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        if (selectedSubTab == 0) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(VideoStudioFilter.values()) { filter ->
                    val isSel = activeFilter == filter
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectFilter(filter) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(filter.previewColor)
                                .border(
                                    width = if (isSel) 1.0.dp else 0.dp,
                                    color = if (isSel) Color(0xFFFFD54F) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = filter.label,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFFFFD54F) else Color.White
                        )
                    }
                }
            }
        } else {
            // Blur Mode Selector & Intensity Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioBlurMode.values().forEach { mode ->
                        val isSel = blurMode == mode
                        FilterChip(
                            selected = isSel,
                            onClick = { onSelectBlurMode(mode) },
                            label = { Text(mode.label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                if (blurMode != StudioBlurMode.NONE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Intensity: ${(blurIntensity * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFFFD54F))
                        AppSlider(
                            value = blurIntensity,
                            onValueChange = onBlurIntensityChange,
                            valueRange = 0.05f..1.0f,
                            accentColor = Color(0xFFFFD54F),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentsControlPanel(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit
) {
    var selectedAdjustTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Brightness", "Contrast", "Saturation").forEachIndexed { idx, label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (selectedAdjustTab == idx) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedAdjustTab == idx) Color(0xFFFFD54F) else Color.White.copy(0.7f),
                    modifier = Modifier
                        .clickable { selectedAdjustTab = idx }
                        .padding(4.dp)
                )
            }
        }
        when (selectedAdjustTab) {
            0 -> {
                AppSlider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -0.4f..0.4f,
                    style = AppSliderStyle.Glossy,
                    accentColor = Color(0xFFFFD54F)
                )
            }
            1 -> {
                AppSlider(
                    value = contrast,
                    onValueChange = onContrastChange,
                    valueRange = 0.5f..1.8f,
                    accentColor = Color(0xFFFFD54F)
                )
            }
            2 -> {
                AppSlider(
                    value = saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0.0f..2.0f,
                    accentColor = Color(0xFFFFD54F)
                )
            }
        }
    }
}

@Composable
private fun TextAndStickersControlPanel(
    onAddText: () -> Unit,
    onAddEmoji: () -> Unit,
    onOpenKeyboardStickers: () -> Unit,
    onPickFileGif: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onAddText,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.TextFields, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Text", fontSize = 11.sp, color = Color.White)
        }

        Button(
            onClick = onAddEmoji,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.InsertEmoticon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Stickers", fontSize = 11.sp, color = Color.White)
        }

        // Keyboard Stickers / GIFs Button
        Button(
            onClick = onOpenKeyboardStickers,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFD54F)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Keyboard, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Keyboard GIF / Sticker", fontSize = 11.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
        }

        // Pick GIF from Storage
        Button(
            onClick = onPickFileGif,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3364B5F6)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Gif, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("+ File GIF", fontSize = 11.sp, color = Color(0xFF64B5F6))
        }
    }
}

// ====================================================
// DUAL AUDIO MIXER CONTROL PANEL (Video Vol + Music Vol)
// ====================================================
@Composable
private fun AudioControlPanel(
    videoVolume: Float,
    onVideoVolumeChange: (Float) -> Unit,
    bgmTitle: String?,
    bgmVolume: Float,
    onBgmVolumeChange: (Float) -> Unit,
    onPickMusic: () -> Unit,
    onRemoveBgm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Original Video Audio Volume Slider
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Vol: ${(videoVolume * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (videoVolume > 0f) {
                    Text(
                        text = "Mute",
                        fontSize = 10.sp,
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.clickable { onVideoVolumeChange(0f) }
                    )
                } else {
                    Text(
                        text = "Unmute",
                        fontSize = 10.sp,
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.clickable { onVideoVolumeChange(1f) }
                    )
                }
            }
            AppSlider(
                value = videoVolume,
                onValueChange = onVideoVolumeChange,
                valueRange = 0f..2f,
                style = AppSliderStyle.Glossy,
                accentColor = Color(0xFFFFD54F)
            )
        }

        // 2. Added Background Music Volume Slider
        if (bgmTitle != null) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Music: ${(bgmVolume * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                    IconButton(onClick = onRemoveBgm, modifier = Modifier.size(16.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
                AppSlider(
                    value = bgmVolume,
                    onValueChange = onBgmVolumeChange,
                    valueRange = 0f..2f,
                    style = AppSliderStyle.Glossy,
                    accentColor = Color(0xFF64B5F6)
                )
            }
        } else {
            Button(
                onClick = onPickMusic,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x3364B5F6)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Music", fontSize = 11.sp, color = Color(0xFF64B5F6))
            }
        }
    }
}

// ====================================================
// DRAGGABLE OVERLAYS (TEXT, STICKERS & GIFS)
// ====================================================
@Composable
private fun DraggableTextOverlayView(
    item: StudioTextOverlay,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }

    val bgModifier = when (item.backgroundStyle) {
        TextBackgroundStyle.SOLID_BLACK -> Modifier.background(Color.Black.copy(0.8f), RoundedCornerShape(8.dp))
        TextBackgroundStyle.TRANSLUCENT -> Modifier.background(Color.Black.copy(0.45f), RoundedCornerShape(8.dp))
        TextBackgroundStyle.NEON_PILL -> Modifier.background(Color(0xFF6366F1).copy(0.85f), CircleShape)
        TextBackgroundStyle.NONE -> Modifier
    }

    val fontFam = when (item.fontFamilyStyle) {
        StudioFontFamily.SERIF -> FontFamily.Serif
        StudioFontFamily.MONOSPACE -> FontFamily.Monospace
        StudioFontFamily.CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    item.offsetX = offsetX
                    item.offsetY = offsetY
                }
            }
            .then(bgModifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = item.text,
            fontSize = item.fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFam,
            color = item.color
        )
    }
}

@Composable
private fun DraggableEmojiStickerView(
    item: StudioEmojiSticker,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    item.offsetX = offsetX
                    item.offsetY = offsetY
                }
            }
    ) {
        Text(text = item.emoji, fontSize = 38.sp)
    }
}

@Composable
private fun DraggableImageStickerView(
    item: StudioImageSticker,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }

    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    item.offsetX = offsetX
                    item.offsetY = offsetY
                }
            }
            .size(item.widthDp.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Sticker",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )

        // Delete badge on sticker
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.7f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

// ====================================================
// KEYBOARD STICKER & GIF RECEIVER DIALOG
// ====================================================
@Composable
private fun KeyboardStickerReceiverDialog(
    onReceiveContent: (uri: Uri, isGif: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Insert from Keyboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Open your keyboard (Gboard/SwiftKey) and tap any GIF or Sticker icon below to insert it directly onto the video.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(0.75f)
                )

                // Android EditText equipped with OnReceiveContentListener for Keyboard Rich Content
                AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            hint = "Tap here & pick GIF/Sticker from keyboard"
                            setHintTextColor(android.graphics.Color.GRAY)
                            setTextColor(android.graphics.Color.WHITE)
                            setBackgroundColor(android.graphics.Color.parseColor("#22FFFFFF"))
                            setPadding(32, 28, 32, 28)

                            // Accept image/gif mime types from keyboard
                            val mimeTypes = arrayOf("image/gif", "image/png", "image/webp", "image/jpeg", "image/*")
                            ViewCompat.setOnReceiveContentListener(
                                this,
                                mimeTypes,
                                object : OnReceiveContentListener {
                                    override fun onReceiveContent(view: android.view.View, payload: ContentInfoCompat): ContentInfoCompat? {
                                        val clipData = payload.clip
                                        for (i in 0 until clipData.itemCount) {
                                            val uri = clipData.getItemAt(i).uri
                                            if (uri != null) {
                                                val isGif = ctx.contentResolver.getType(uri)?.contains("gif") == true || uri.toString().lowercase().contains("gif")
                                                onReceiveContent(uri, isGif)
                                                return null
                                            }
                                        }
                                        return payload
                                    }
                                }
                            )
                            requestFocus()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// ====================================================
// MODAL DIALOGS (SPEED, RICH TEXT, STICKERS, EXPORT)
// ====================================================
@Composable
private fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(18.dp),
            backgroundColor = Color(0xF0141824),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Video Playback Speed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speeds) { sp ->
                        val isSel = kotlin.math.abs(currentSpeed - sp) < 0.05f
                        FilterChip(
                            selected = isSel,
                            onClick = { onSpeedSelect(sp) },
                            label = { Text("${sp}x", fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRichTextDialog(
    onAdd: (text: String, fontStyle: StudioFontFamily, color: Color, bgStyle: TextBackgroundStyle, fontSize: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(StudioFontFamily.SANS_SERIF) }
    var selectedColor by remember { mutableStateOf(Color.White) }
    var selectedBgStyle by remember { mutableStateOf(TextBackgroundStyle.TRANSLUCENT) }
    var fontSize by remember { mutableFloatStateOf(20f) }

    val colors = listOf(Color.White, Color.Yellow, Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFF69F0AE), Color(0xFFFF9100), Color(0xFFE040FB))

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Add Text & Font Styles", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type your caption...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Font Family Chips
                Text("Font Family", fontSize = 12.sp, color = Color(0xFFFFD54F))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioFontFamily.values().forEach { f ->
                        FilterChip(
                            selected = selectedFont == f,
                            onClick = { selectedFont = f },
                            label = { Text(f.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                // Font Colors
                Text("Font Color", fontSize = 12.sp, color = Color(0xFFFFD54F))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (selectedColor == c) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { selectedColor = c }
                        )
                    }
                }

                // Background Style
                Text("Background Badge", fontSize = 12.sp, color = Color(0xFFFFD54F))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextBackgroundStyle.values().forEach { bg ->
                        FilterChip(
                            selected = selectedBgStyle == bg,
                            onClick = { selectedBgStyle = bg },
                            label = { Text(bg.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                // Size Slider
                Text("Font Size: ${fontSize.toInt()}sp", fontSize = 12.sp, color = Color(0xFFFFD54F))
                AppSlider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 14f..42f,
                    accentColor = Color(0xFFFFD54F)
                )

                Button(
                    onClick = { if (text.isNotBlank()) onAdd(text, selectedFont, selectedColor, selectedBgStyle, fontSize) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Text to Video", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CategoryStickerPickerDialog(
    onSelectSticker: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("Emojis", "Badges & Tags", "Arrows & Shapes")

    val emojiList = listOf("🔥", "❤️", "✨", "😂", "🎬", "🍿", "🚀", "⚡", "💯", "🕶️", "👏", "🎉", "😍", "🌟", "👑", "🥳", "💡", "💎", "🏆", "💥")
    val badgeList = listOf("🔴 REC", "PRO", "4K", "HD", "LIVE", "SALE", "BEST", "NEW", "VLOG", "TOP 10", "MEME", "VIP", "HOT 🔥")
    val shapeList = listOf("➔", "➜", "⬆", "⬇", "⚡", "✦", "★", "💖", "💬", "💥", "🎯", "🚀", "💯", "👑")

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Pick Stickers & Badges", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

                // Category Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, cat ->
                        FilterChip(
                            selected = selectedCategory == index,
                            onClick = { selectedCategory = index },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                val currentItems = when (selectedCategory) {
                    0 -> emojiList
                    1 -> badgeList
                    else -> shapeList
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    currentItems.forEach { sticker ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable { onSelectSticker(sticker) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sticker,
                                fontSize = if (selectedCategory == 1) 14.sp else 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportStudioDialog(
    clipDurationMs: Long,
    onExport: (format: String, gifFps: Int, gifWidth: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf("MP4") }
    var gifFps by remember { mutableIntStateOf(15) }
    var gifWidth by remember { mutableIntStateOf(480) }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Export Media Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "Clip Duration: ${formatTimeShort(clipDurationMs)} • Destination: MediaNest Studio",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.7f)
                )

                // Format Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("MP4", "GIF", "WEBP").forEach { fmt ->
                        val isSel = selectedFormat == fmt
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedFormat = fmt },
                            label = { Text(if (fmt == "MP4") "Video (MP4)" else if (fmt == "GIF") "Animated GIF" else "Animated WebP", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                if (selectedFormat == "GIF" || selectedFormat == "WEBP") {
                    Text("Framerate: ${gifFps}fps • Width: ${gifWidth}px", fontSize = 11.sp, color = Color(0xFFFFD54F))
                    AppSlider(
                        value = gifFps.toFloat(),
                        onValueChange = { gifFps = it.roundToInt() },
                        valueRange = 10f..30f,
                        steps = 3,
                        accentColor = Color(0xFFFFD54F)
                    )
                }

                Button(
                    onClick = { onExport(selectedFormat, gifFps, gifWidth) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Export", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// MODELS & ENUMS
// ----------------------------------------------------
enum class StudioTool {
    TRIM, CROP, FILTERS, ADJUST, TEXT, AUDIO
}

enum class CropPreset(val label: String) {
    ORIGINAL("Original"),
    P_16_9("16:9"),
    P_9_16("9:16"),
    P_1_1("1:1"),
    P_4_3("4:3"),
    P_4_5("4:5"),
    P_21_9("21:9")
}

enum class VideoStudioFilter(val label: String, val previewColor: Color) {
    ORIGINAL("Original", Color(0xFF333333)),
    CINEMA("Cinema 35mm", Color(0xFF1B4965)),
    VIVID("Vivid", Color(0xFFE63946)),
    NOIR("Noir B&W", Color(0xFF555555)),
    VINTAGE("Vintage", Color(0xFFC4A482)),
    WARM("Warm Sun", Color(0xFFFFA500)),
    COOL("Cool Cyan", Color(0xFF00BFFF)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF007F)),
    DREAMY("Dreamy", Color(0xFFB388FF))
}

enum class StudioBlurMode(val label: String) {
    NONE("No Blur"),
    GAUSSIAN("Gaussian"),
    RADIAL_FOCUS("Tilt-Shift / Focus"),
    PIXELATE("Mosaic / Pixelate"),
    MOTION_BLUR("Motion Blur")
}

enum class StudioFontFamily(val label: String) {
    SANS_SERIF("Modern"),
    SERIF("Classic Serif"),
    MONOSPACE("Code Mono"),
    CURSIVE("Handwritten")
}

enum class TextBackgroundStyle(val label: String) {
    TRANSLUCENT("Glass"),
    SOLID_BLACK("Solid Dark"),
    NEON_PILL("Neon Badge"),
    NONE("No Background")
}

data class StudioClipItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val isVideo: Boolean,
    val durationMs: Long,
    var thumbnail: Bitmap? = null
)

class StudioTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontFamilyStyle: StudioFontFamily = StudioFontFamily.SANS_SERIF,
    val color: Color = Color.White,
    val backgroundStyle: TextBackgroundStyle = TextBackgroundStyle.TRANSLUCENT,
    val fontSizeSp: Float = 20f,
    var offsetX: Float = 50f,
    var offsetY: Float = 100f
)

class StudioEmojiSticker(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    var offsetX: Float = 100f,
    var offsetY: Float = 150f
)

class StudioImageSticker(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val isGif: Boolean = false,
    var offsetX: Float = 100f,
    var offsetY: Float = 150f,
    var widthDp: Float = 120f
)

private fun formatTimeShort(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
