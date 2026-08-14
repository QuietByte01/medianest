package com.medianest.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.util.MediaProcessorEngine
import com.medianest.util.MediaProcessorEngine.ProcessingState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class StudioTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CONVERT("Convert", Icons.Default.Transform),
    COMPRESS("Compress", Icons.Default.Compress),
    CROP("Crop", Icons.Default.Crop),
    EXTRACT("Extract", Icons.Default.AudioFile),
    REPAIR("Repair", Icons.Default.Build)
}

@Composable
fun MediaConverterStudioDialog(
    initialMediaItem: MediaItem? = null,
    initialTab: StudioTab = StudioTab.CONVERT,
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>,
    onDismissRequest: () -> Unit,
    onOpenVideoPlayer: (MediaItem) -> Unit,
    onOpenAudioPlayer: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(initialMediaItem ?: videosList.firstOrNull() ?: audioList.firstOrNull() ?: imagesList.firstOrNull()) }
    var selectedBatchItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isBatchMode by remember { mutableStateOf(false) }

    var showMediaPickerSheet by remember { mutableStateOf(false) }
    var showBatchPickerSheet by remember { mutableStateOf(false) }

    val processingState by MediaProcessorEngine.processingState.collectAsState()

    // Single external file picker
    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val item = com.medianest.util.AudioMetadataUtils.extractMetadata(context, uri, rawTitleHint = uri.lastPathSegment ?: "External Media")
            selectedMediaItem = item
            isBatchMode = false
        }
    }

    // Multiple external files picker for batch compressor
    val multipleFilesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                com.medianest.util.AudioMetadataUtils.extractMetadata(context, uri, rawTitleHint = uri.lastPathSegment ?: "Image")
            }
            selectedBatchItems = items
            isBatchMode = true
        }
    }

    // Output Safety & Policy
    var isCreateNewFile by remember { mutableStateOf(true) }

    // Parameters - Convert
    var convertOutputFormat by remember { mutableStateOf("mp4") }
    var isLosslessCopy by remember { mutableStateOf(false) }
    var videoCodec by remember { mutableStateOf("libx264") }
    var qualityPreset by remember { mutableStateOf("HIGH") }
    var audioBitrate by remember { mutableIntStateOf(256) }

    // Parameters - Compress
    var compressTargetMode by remember { mutableStateOf("PERCENT") }
    var compressPercent by remember { mutableIntStateOf(50) }
    var compressTargetMb by remember { mutableIntStateOf(25) }
    var compressResolution by remember { mutableStateOf("ORIGINAL") }
    var imageQuality by remember { mutableIntStateOf(80) }
    var imageFormat by remember { mutableStateOf("webp") }

    // Parameters - Crop
    var cropPreset by remember { mutableStateOf("9_16") }

    // Parameters - Extract
    var extractType by remember { mutableStateOf("AUDIO") }
    var extractAudioFormat by remember { mutableStateOf("original") }

    // Parameters - Repair
    var repairLossless by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = {
            if (processingState !is ProcessingState.Processing && processingState !is ProcessingState.BatchProcessing) {
                MediaProcessorEngine.resetState()
                onDismissRequest()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC05070E))
                .padding(if (isTablet) 24.dp else 8.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth(if (isTablet) 0.88f else 1f)
                    .fillMaxHeight(if (isTablet) 0.92f else 0.98f),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xEB0A0D18),
                borderColor = Color(0x3338BDF8)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTablet) 20.dp else 12.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x2E38BDF8))
                                    .border(1.dp, Color(0x6638BDF8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = "FFmpeg Media Studio",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Lossless Converter · Batch Compressor · Extractor · Repair",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (processingState !is ProcessingState.Processing && processingState !is ProcessingState.BatchProcessing) {
                                    MediaProcessorEngine.resetState()
                                    onDismissRequest()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Glossy Tab Navigation Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            GlossyPillButton(
                                text = tab.title,
                                icon = tab.icon,
                                isSelected = isSelected,
                                onClick = { selectedTab = tab }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Studio Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Source Media Card with Single/Batch Switch
                        if (selectedTab == StudioTab.COMPRESS && isBatchMode && selectedBatchItems.isNotEmpty()) {
                            BatchSourceMediaCard(
                                batchItems = selectedBatchItems,
                                onChangeClick = { showBatchPickerSheet = true },
                                onPickStorage = { multipleFilesPickerLauncher.launch("image/*") },
                                onSwitchToSingle = { isBatchMode = false }
                            )
                        } else {
                            SourceMediaSelectorCard(
                                selectedItem = selectedMediaItem,
                                isBatchAvailable = selectedTab == StudioTab.COMPRESS,
                                onChangeClick = { showMediaPickerSheet = true },
                                onPickStorage = { singleFilePickerLauncher.launch("*/*") },
                                onSwitchToBatch = {
                                    val curItem = selectedMediaItem
                                    if (curItem != null && curItem.type == MediaType.IMAGE) {
                                        selectedBatchItems = listOf(curItem)
                                    } else if (imagesList.isNotEmpty()) {
                                        selectedBatchItems = listOf(imagesList.first())
                                    }
                                    isBatchMode = true
                                    showBatchPickerSheet = true
                                }
                            )
                        }

                        // 2. Output File Creation & Safety Banner
                        OutputFileSafetyCard(
                            isCreateNewFile = isCreateNewFile,
                            onToggle = { isCreateNewFile = it }
                        )

                        // 3. Tab Specific Controls
                        when (selectedTab) {
                            StudioTab.CONVERT -> {
                                ConvertControlsCard(
                                    selectedItem = selectedMediaItem,
                                    outputFormat = convertOutputFormat,
                                    onFormatChange = { convertOutputFormat = it },
                                    isLossless = isLosslessCopy,
                                    onLosslessChange = { isLosslessCopy = it },
                                    qualityPreset = qualityPreset,
                                    onQualityChange = { qualityPreset = it },
                                    videoCodec = videoCodec,
                                    onCodecChange = { videoCodec = it },
                                    audioBitrate = audioBitrate,
                                    onBitrateChange = { audioBitrate = it }
                                )
                            }
                            StudioTab.COMPRESS -> {
                                CompressControlsCard(
                                    selectedItem = selectedMediaItem,
                                    isBatchMode = isBatchMode,
                                    batchCount = selectedBatchItems.size,
                                    targetMode = compressTargetMode,
                                    onModeChange = { compressTargetMode = it },
                                    targetPercent = compressPercent,
                                    onPercentChange = { compressPercent = it },
                                    targetMb = compressTargetMb,
                                    onMbChange = { compressTargetMb = it },
                                    resolution = compressResolution,
                                    onResolutionChange = { compressResolution = it },
                                    imageQuality = imageQuality,
                                    onImageQualityChange = { imageQuality = it },
                                    imageFormat = imageFormat,
                                    onImageFormatChange = { imageFormat = it }
                                )
                            }
                            StudioTab.CROP -> {
                                CropControlsCard(
                                    selectedItem = selectedMediaItem,
                                    cropPreset = cropPreset,
                                    onPresetChange = { cropPreset = it }
                                )
                            }
                            StudioTab.EXTRACT -> {
                                ExtractControlsCard(
                                    selectedItem = selectedMediaItem,
                                    extractType = extractType,
                                    onExtractTypeChange = { extractType = it },
                                    audioFormat = extractAudioFormat,
                                    onAudioFormatChange = { extractAudioFormat = it }
                                )
                            }
                            StudioTab.REPAIR -> {
                                RepairControlsCard(
                                    selectedItem = selectedMediaItem,
                                    isLossless = repairLossless,
                                    onLosslessChange = { repairLossless = it }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Glossy Action Trigger Button
                    GlossyActionButton(
                        text = when (selectedTab) {
                            StudioTab.CONVERT -> if (isLosslessCopy) "Start Lossless Remux" else "Start Video/Audio Conversion"
                            StudioTab.COMPRESS -> if (isBatchMode) "Compress ${selectedBatchItems.size} Files (Batch)" else "Start Smart Compression"
                            StudioTab.CROP -> "Start Video Crop & Reframe"
                            StudioTab.EXTRACT -> if (extractAudioFormat == "original") "Extract Original Lossless Audio" else "Extract ${extractType.lowercase().replaceFirstChar { it.uppercase() }}"
                            StudioTab.REPAIR -> "Run Diagnostic Bitstream Repair"
                        },
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            if (selectedTab == StudioTab.COMPRESS && isBatchMode) {
                                if (selectedBatchItems.isEmpty()) {
                                    Toast.makeText(context, "Please select images to compress", Toast.LENGTH_SHORT).show()
                                    return@GlossyActionButton
                                }
                                scope.launch {
                                    val pairs = selectedBatchItems.map { it.uri.path.orEmpty() to it.uri }
                                    MediaProcessorEngine.batchCompressMedia(
                                        context = context,
                                        inputList = pairs,
                                        imageQuality = imageQuality,
                                        imageFormat = imageFormat,
                                        isCreateNewFile = isCreateNewFile
                                    )
                                }
                                return@GlossyActionButton
                            }

                            val item = selectedMediaItem
                            if (item == null) {
                                Toast.makeText(context, "Please select a media file first", Toast.LENGTH_SHORT).show()
                                return@GlossyActionButton
                            }

                            val path = item.uri.path ?: item.uri.toString()
                            scope.launch {
                                when (selectedTab) {
                                    StudioTab.CONVERT -> {
                                        val crf = when (qualityPreset) {
                                            "LOSSLESS" -> 0
                                            "MASTER" -> 14
                                            "HIGH" -> 18
                                            else -> 23
                                        }
                                        MediaProcessorEngine.convertMedia(
                                            context = context,
                                            inputPath = path,
                                            inputUri = item.uri,
                                            outputFormat = convertOutputFormat,
                                            isLosslessCopy = isLosslessCopy,
                                            videoCodec = videoCodec,
                                            audioCodec = if (isLosslessCopy) "copy" else "aac",
                                            qualityCrf = crf,
                                            audioBitrateKbps = audioBitrate,
                                            isCreateNewFile = isCreateNewFile
                                        )
                                    }
                                    StudioTab.COMPRESS -> {
                                        MediaProcessorEngine.compressMedia(
                                            context = context,
                                            inputPath = path,
                                            inputUri = item.uri,
                                            targetMode = compressTargetMode,
                                            targetPercentage = compressPercent,
                                            targetLimitMb = compressTargetMb,
                                            resolutionScale = compressResolution,
                                            isCreateNewFile = isCreateNewFile
                                        )
                                    }
                                    StudioTab.CROP -> {
                                        MediaProcessorEngine.cropMedia(
                                            context = context,
                                            inputPath = path,
                                            inputUri = item.uri,
                                            cropPreset = cropPreset,
                                            isCreateNewFile = isCreateNewFile
                                        )
                                    }
                                    StudioTab.EXTRACT -> {
                                        MediaProcessorEngine.extractMedia(
                                            context = context,
                                            inputPath = path,
                                            inputUri = item.uri,
                                            extractType = extractType,
                                            audioOutputFormat = extractAudioFormat,
                                            isCreateNewFile = isCreateNewFile
                                        )
                                    }
                                    StudioTab.REPAIR -> {
                                        MediaProcessorEngine.repairMedia(
                                            context = context,
                                            inputPath = path,
                                            inputUri = item.uri,
                                            isLosslessRepackage = repairLossless,
                                            isCreateNewFile = isCreateNewFile
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Live Single Processing HUD
            if (processingState is ProcessingState.Processing) {
                val state = processingState as ProcessingState.Processing
                LiveProcessingHUD(
                    progress = state.progressPercent,
                    speed = state.speed,
                    statusText = state.statusText,
                    recentLog = state.recentLog,
                    onCancel = { MediaProcessorEngine.cancelCurrent() }
                )
            }

            // Live Batch Processing HUD
            if (processingState is ProcessingState.BatchProcessing) {
                val state = processingState as ProcessingState.BatchProcessing
                LiveBatchProcessingHUD(
                    currentIndex = state.currentIndex,
                    totalFiles = state.totalFiles,
                    fileName = state.currentFileName,
                    overallProgress = state.overallProgressPercent,
                    speed = state.speed,
                    statusText = state.statusText,
                    onCancel = { MediaProcessorEngine.cancelCurrent() }
                )
            }

            // Single File Completion Dialog
            if (processingState is ProcessingState.Completed) {
                val state = processingState as ProcessingState.Completed
                ProcessingResultDialog(
                    completed = state,
                    onDismiss = { MediaProcessorEngine.resetState() },
                    onPlay = { file ->
                        MediaProcessorEngine.resetState()
                        onDismissRequest()
                        val mediaItem = MediaItem(
                            id = file.hashCode().toLong(),
                            title = file.name,
                            uri = Uri.fromFile(file),
                            mimeType = if (selectedTab == StudioTab.EXTRACT && extractType == "AUDIO") "audio/*" else "video/*",
                            size = file.length(),
                            type = if (selectedTab == StudioTab.EXTRACT && extractType == "AUDIO") MediaType.AUDIO else MediaType.VIDEO
                        )
                        if (mediaItem.type == MediaType.AUDIO) {
                            onOpenAudioPlayer(mediaItem)
                        } else {
                            onOpenVideoPlayer(mediaItem)
                        }
                    },
                    context = context
                )
            }

            // Batch Completion Dialog
            if (processingState is ProcessingState.BatchCompleted) {
                val state = processingState as ProcessingState.BatchCompleted
                BatchResultDialog(
                    completed = state,
                    onDismiss = { MediaProcessorEngine.resetState() },
                    context = context
                )
            }

            // Error Dialog
            if (processingState is ProcessingState.Failed) {
                val state = processingState as ProcessingState.Failed
                AlertDialog(
                    onDismissRequest = { MediaProcessorEngine.resetState() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF87171))
                            Text("Processing Error", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            Text(state.errorMessage, color = Color.White, fontSize = 13.sp)
                            if (state.fullLog.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(state.fullLog.takeLast(400), color = Color(0xFF94A3B8), fontSize = 10.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { MediaProcessorEngine.resetState() }) {
                            Text("Dismiss", color = Color(0xFF38BDF8))
                        }
                    },
                    containerColor = Color(0xFA0F172A),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            // Single Item Picker Sheet
            if (showMediaPickerSheet) {
                MediaItemPickerModal(
                    imagesList = imagesList,
                    videosList = videosList,
                    audioList = audioList,
                    onSelect = { item ->
                        selectedMediaItem = item
                        isBatchMode = false
                        showMediaPickerSheet = false
                    },
                    onPickExternal = {
                        showMediaPickerSheet = false
                        singleFilePickerLauncher.launch("*/*")
                    },
                    onDismiss = { showMediaPickerSheet = false }
                )
            }

            // Multi-Select Batch Picker Sheet
            if (showBatchPickerSheet) {
                MultiMediaItemPickerModal(
                    items = imagesList.ifEmpty { videosList },
                    initialSelected = selectedBatchItems,
                    onDone = { selected ->
                        selectedBatchItems = selected
                        isBatchMode = true
                        showBatchPickerSheet = false
                    },
                    onPickExternal = {
                        showBatchPickerSheet = false
                        multipleFilesPickerLauncher.launch("image/*")
                    },
                    onDismiss = { showBatchPickerSheet = false }
                )
            }
        }
    }
}

// ==========================================
// GLOSSY BUTTON COMPONENTS
// ==========================================
@Composable
private fun GlossyPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0x3338BDF8) else Color(0x14FFFFFF),
        border = BorderStroke(
            if (isSelected) 1.0.dp else 0.5.dp,
            if (isSelected) Color(0xFF38BDF8) else Color(0x22FFFFFF)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFFC0C7D5),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF38BDF8) else Color.White
            )
        }
    }
}

@Composable
private fun GlossyCardButton(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0x2E38BDF8) else Color(0x12FFFFFF),
        border = BorderStroke(
            if (isSelected) 1.0.dp else 0.5.dp,
            if (isSelected) Color(0xFF38BDF8) else Color(0x1FFFFFFF)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFF38BDF8) else Color.White
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = if (isSelected) Color(0xFFBAE6FD) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun GlossyActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0x3838BDF8),
        border = BorderStroke(1.0.dp, Color(0xFF38BDF8)),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ==========================================
// SOURCE MEDIA CARDS
// ==========================================
@Composable
private fun SourceMediaSelectorCard(
    selectedItem: MediaItem?,
    isBatchAvailable: Boolean,
    onChangeClick: () -> Unit,
    onPickStorage: () -> Unit,
    onSwitchToBatch: () -> Unit
) {
    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color(0x221E293B),
        borderColor = Color(0x33FFFFFF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33000000))
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedItem?.albumArtUri != null) {
                    AsyncImage(
                        model = selectedItem.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when (selectedItem?.type) {
                            MediaType.AUDIO -> Icons.Default.MusicNote
                            MediaType.IMAGE -> Icons.Default.Image
                            else -> Icons.Default.Movie
                        },
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = selectedItem?.title ?: "No File Selected",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${com.medianest.util.formatBytesReport(selectedItem?.size ?: 0L)} · ${selectedItem?.mimeType ?: "media"}",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onChangeClick,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x3338BDF8),
                    border = BorderStroke(1.dp, Color(0x4438BDF8))
                ) {
                    Text(
                        text = "Library",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (isBatchAvailable) {
                    Surface(
                        onClick = onSwitchToBatch,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x2238BDF8),
                        border = BorderStroke(1.dp, Color(0x3338BDF8))
                    ) {
                        Text(
                            text = "Batch (Multi)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBAE6FD),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    Surface(
                        onClick = onPickStorage,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x1FFFFFFF),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Text(
                            text = "Browse",
                            fontSize = 11.5.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchSourceMediaCard(
    batchItems: List<MediaItem>,
    onChangeClick: () -> Unit,
    onPickStorage: () -> Unit,
    onSwitchToSingle: () -> Unit
) {
    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color(0x2E1E293B),
        borderColor = Color(0x4438BDF8),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Text(
                        text = "Batch Queue (${batchItems.size} Selected Files)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                TextButton(onClick = onSwitchToSingle) {
                    Text("Single Mode", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                }
            }

            // Horizontal thumbnail strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                batchItems.take(50).forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33000000))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.albumArtUri != null) {
                            AsyncImage(
                                model = item.albumArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }
                    }
                }
                if (batchItems.size > 50) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x3338BDF8))
                            .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+${batchItems.size - 50}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onChangeClick,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x3338BDF8),
                    border = BorderStroke(1.dp, Color(0x4438BDF8)),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Select From Library", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    }
                }
                Surface(
                    onClick = onPickStorage,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1FFFFFFF),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Browse Multi...", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// OUTPUT SAFETY CARD
// ==========================================
@Composable
private fun OutputFileSafetyCard(
    isCreateNewFile: Boolean,
    onToggle: (Boolean) -> Unit
) {
    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0x1838BDF8),
        borderColor = Color(0x3338BDF8),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
//                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                Column {
                    Text(
                        text = "Output: Create New File",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Original file is untouched.",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Switch(
                checked = isCreateNewFile,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF38BDF8))
            )
        }
    }
}

// ==========================================
// 1. CONVERT CONTROLS
// ==========================================
@Composable
private fun ConvertControlsCard(
    selectedItem: MediaItem?,
    outputFormat: String,
    onFormatChange: (String) -> Unit,
    isLossless: Boolean,
    onLosslessChange: (Boolean) -> Unit,
    qualityPreset: String,
    onQualityChange: (String) -> Unit,
    videoCodec: String,
    onCodecChange: (String) -> Unit,
    audioBitrate: Int,
    onBitrateChange: (Int) -> Unit
) {
    val isAudioSource = selectedItem?.type == MediaType.AUDIO
    val videoFormats = listOf("mp4", "mkv", "webm", "mov", "avi", "gif")
    val audioFormats = listOf("mp3", "flac", "wav", "aac", "m4a", "opus", "ogg")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Target Output Format", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val formatList = if (isAudioSource) audioFormats else (videoFormats + audioFormats)
            formatList.forEach { fmt ->
                val isSelected = outputFormat.equals(fmt, ignoreCase = true)
                GlossyPillButton(
                    text = fmt.uppercase(),
                    isSelected = isSelected,
                    onClick = { onFormatChange(fmt) }
                )
            }
        }

        // Lossless Remux Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Lossless Stream Copy (Remux)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Zero re-encoding, instant container repackaging", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            Switch(
                checked = isLossless,
                onCheckedChange = onLosslessChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF38BDF8))
            )
        }

        if (!isLossless) {
            // Quality Presets
            Text("Encoding Quality Preset", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "MASTER" to ("Master" to "CRF 14"),
                    "HIGH" to ("High" to "CRF 18"),
                    "STANDARD" to ("Standard" to "CRF 23")
                ).forEach { (key, pair) ->
                    val (title, sub) = pair
                    val isSel = qualityPreset == key
                    GlossyCardButton(
                        title = title,
                        subtitle = sub,
                        isSelected = isSel,
                        onClick = { onQualityChange(key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Uniform Scrollable Video Codec Engine Row
            if (!listOf("mp3", "flac", "wav", "aac", "m4a", "opus").contains(outputFormat.lowercase())) {
                Text("Video Codec Engine", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "libx264" to ("H.264" to "Universal"),
                        "libx265" to ("H.265" to "HEVC 50% Size"),
                        "libvpx-vp9" to ("VP9" to "WebM Format"),
                        "copy" to ("Stream Copy" to "Direct Passthrough")
                    ).forEach { (codec, pair) ->
                        val (title, sub) = pair
                        val isSel = videoCodec == codec
                        GlossyCardButton(
                            title = title,
                            subtitle = sub,
                            isSelected = isSel,
                            onClick = { onCodecChange(codec) },
                            modifier = Modifier.width(130.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. COMPRESS CONTROLS
// ==========================================
@Composable
private fun CompressControlsCard(
    selectedItem: MediaItem?,
    isBatchMode: Boolean,
    batchCount: Int,
    targetMode: String,
    onModeChange: (String) -> Unit,
    targetPercent: Int,
    onPercentChange: (Int) -> Unit,
    targetMb: Int,
    onMbChange: (Int) -> Unit,
    resolution: String,
    onResolutionChange: (String) -> Unit,
    imageQuality: Int,
    onImageQualityChange: (Int) -> Unit,
    imageFormat: String,
    onImageFormatChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isBatchMode) {
            Text("Batch Image Compression Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            
            Text("Target Format", fontSize = 12.sp, color = Color(0xFF94A3B8))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("webp" to "WebP (Ultra Efficient)", "jpg" to "JPEG", "png" to "PNG Lossless").forEach { (fmt, label) ->
                    val isSel = imageFormat == fmt
                    GlossyPillButton(
                        text = label,
                        isSelected = isSel,
                        onClick = { onImageFormatChange(fmt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Image Quality / Compression", fontSize = 13.sp, color = Color.White)
                Text(if (imageQuality >= 100) "Lossless (100%)" else "$imageQuality%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
            AppSlider(
                value = imageQuality.toFloat(),
                onValueChange = { onImageQualityChange(it.toInt()) },
                valueRange = 40f..100f,
                steps = 12,
                accentColor = Color(0xFF38BDF8)
            )
            return
        }

        Text("Compression Strategy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "PERCENT" to ("Percentage" to "50% / 75% Scale"),
                "LIMIT_SIZE" to ("Target Limit" to "Discord / Email"),
                "HEVC" to ("Smart HEVC" to "H.265 CRF")
            ).forEach { (mode, pair) ->
                val (title, sub) = pair
                val isSel = targetMode == mode
                GlossyCardButton(
                    title = title,
                    subtitle = sub,
                    isSelected = isSel,
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (targetMode) {
            "LIMIT_SIZE" -> {
                Text("Target Maximum Size", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        16 to "16 MB (WhatsApp)",
                        25 to "25 MB (Discord)",
                        50 to "50 MB",
                        100 to "100 MB"
                    ).forEach { (mb, label) ->
                        val isSel = targetMb == mb
                        GlossyPillButton(
                            text = label,
                            isSelected = isSel,
                            onClick = { onMbChange(mb) }
                        )
                    }
                }
            }
            "PERCENT" -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Size Reduction", fontSize = 13.sp, color = Color.White)
                    Text("$targetPercent% Smaller", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
                AppSlider(
                    value = targetPercent.toFloat(),
                    onValueChange = { onPercentChange(it.toInt()) },
                    valueRange = 25f..80f,
                    steps = 10,
                    accentColor = Color(0xFF38BDF8)
                )
            }
            else -> {
                Text("High Efficiency H.265 encoding with near-zero visual degradation.", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }

        // Resolution Downscaler
        Text("Downscale Resolution (Optional)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ORIGINAL" to "Original",
                "1080P" to "1080p FHD",
                "720P" to "720p HD",
                "480P" to "480p SD"
            ).forEach { (res, label) ->
                val isSel = resolution == res
                GlossyPillButton(
                    text = label,
                    isSelected = isSel,
                    onClick = { onResolutionChange(res) }
                )
            }
        }
    }
}

// ==========================================
// 3. CROP CONTROLS
// ==========================================
@Composable
private fun CropControlsCard(
    selectedItem: MediaItem?,
    cropPreset: String,
    onPresetChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Aspect Ratio & Social Framing", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

        val presets = listOf(
            "9_16" to "9:16 (Instagram Reels / YouTube Shorts / TikTok)",
            "1_1" to "1:1 (Square Post)",
            "16_9" to "16:9 (Standard Widescreen)",
            "21_9" to "21:9 (Cinematic Ultra-Wide)",
            "4_3" to "4:3 (Classic Television)"
        )

        presets.forEach { (preset, label) ->
            val isSel = cropPreset == preset
            Surface(
                onClick = { onPresetChange(preset) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSel) Color(0x2E38BDF8) else Color(0x12FFFFFF),
                border = BorderStroke(if (isSel) 1.0.dp else 0.5.dp, if (isSel) Color(0xFF38BDF8) else Color(0x1FFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = Color.White)
                    if (isSel) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. EXTRACT CONTROLS
// ==========================================
@Composable
private fun ExtractControlsCard(
    selectedItem: MediaItem?,
    extractType: String,
    onExtractTypeChange: (String) -> Unit,
    audioFormat: String,
    onAudioFormatChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Extract Component", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "AUDIO" to "Audio Track",
                "GIF" to "Cinema GIF",
                "FRAME" to "Keyframe",
                "SUBTITLE" to "Subtitles"
            ).forEach { (type, label) ->
                val isSel = extractType == type
                GlossyPillButton(
                    text = label,
                    isSelected = isSel,
                    onClick = { onExtractTypeChange(type) }
                )
            }
        }

        if (extractType == "AUDIO") {
            Text("Audio Codec & Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "original" to ("Original Quality" to "Direct Copy (0 Transcoding)"),
                    "flac" to ("FLAC" to "24-bit Lossless"),
                    "wav" to ("WAV" to "Uncompressed PCM"),
                    "mp3" to ("MP3 Master" to "320 kbps High"),
                    "aac" to ("AAC / M4A" to "256 kbps")
                ).forEach { (fmt, pair) ->
                    val (title, sub) = pair
                    val isSel = audioFormat == fmt
                    GlossyCardButton(
                        title = title,
                        subtitle = sub,
                        isSelected = isSel,
                        onClick = { onAudioFormatChange(fmt) },
                        modifier = Modifier.width(170.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. REPAIR CONTROLS
// ==========================================
@Composable
private fun RepairControlsCard(
    selectedItem: MediaItem?,
    isLossless: Boolean,
    onLosslessChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Corrupted Bitstream & Container Recovery", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            text = "Rebuilds damaged MP4/MKV/AVI container indexes, repairs unfinalized recordings (missing MOOV atom), and eliminates timestamp errors.",
            fontSize = 11.5.sp,
            color = Color(0xFF94A3B8)
        )

        Surface(
            onClick = { onLosslessChange(true) },
            shape = RoundedCornerShape(14.dp),
            color = if (isLossless) Color(0x2E38BDF8) else Color(0x12FFFFFF),
            border = BorderStroke(if (isLossless) 1.0.dp else 0.5.dp, if (isLossless) Color(0xFF38BDF8) else Color(0x1FFFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Lossless Index & Timestamp Rebuild (Recommended)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Copies healthy streams directly. Takes just seconds with zero quality loss.", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }

        Surface(
            onClick = { onLosslessChange(false) },
            shape = RoundedCornerShape(14.dp),
            color = if (!isLossless) Color(0x2E38BDF8) else Color(0x12FFFFFF),
            border = BorderStroke(if (!isLossless) 1.0.dp else 0.5.dp, if (!isLossless) Color(0xFF38BDF8) else Color(0x1FFFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Deep Bitstream Transcode Recovery", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Decodes every single packet, drops unrecoverable frames, and generates clean H.264 video.", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

// ==========================================
// PROGRESS HUD MODALS
// ==========================================
@Composable
private fun LiveProcessingHUD(
    progress: Int,
    speed: Double,
    statusText: String,
    recentLog: String,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xF0080B14),
            borderColor = Color(0x4438BDF8)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0x22FFFFFF),
                        strokeWidth = 6.dp
                    )
                    Text("$progress%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(statusText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(recentLog, fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                }

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("Cancel Processing", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LiveBatchProcessingHUD(
    currentIndex: Int,
    totalFiles: Int,
    fileName: String,
    overallProgress: Int,
    speed: Double,
    statusText: String,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xF0080B14),
            borderColor = Color(0x4438BDF8)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                    CircularProgressIndicator(
                        progress = { overallProgress / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0x22FFFFFF),
                        strokeWidth = 6.dp
                    )
                    Text("$overallProgress%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Batch Processing: $currentIndex of $totalFiles", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(fileName, fontSize = 12.sp, color = Color(0xFF38BDF8), maxLines = 1)
                    Text(statusText, fontSize = 11.sp, color = Color(0xFF94A3B8))
                }

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("Cancel Batch", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// RESULT DIALOGS
// ==========================================
@Composable
private fun ProcessingResultDialog(
    completed: ProcessingState.Completed,
    onDismiss: () -> Unit,
    onPlay: (File) -> Unit,
    context: Context
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                Text("Processing Complete!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(completed.outputFile.name, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text("Saved to: ${completed.outputFile.parent}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                
                if (completed.originalSizeBytes > 0) {
                    val saved = completed.originalSizeBytes - completed.newSizeBytes
                    val savedPercent = (saved.toFloat() / completed.originalSizeBytes.toFloat() * 100).toInt()
                    Text(
                        text = "Original: ${com.medianest.util.formatBytesReport(completed.originalSizeBytes)} ➔ New: ${com.medianest.util.formatBytesReport(completed.newSizeBytes)} (${if (saved >= 0) "-$savedPercent%" else "+${-savedPercent}%"})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_STREAM, completed.outputUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Converted Media"))
                }) {
                    Text("Share", color = Color(0xFF38BDF8))
                }
                Button(
                    onClick = { onPlay(completed.outputFile) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFA0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun BatchResultDialog(
    completed: ProcessingState.BatchCompleted,
    onDismiss: () -> Unit,
    context: Context
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                Text("Batch Compression Complete!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Successfully processed ${completed.outputFiles.size} files", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                val saved = completed.totalOriginalBytes - completed.totalNewBytes
                val savedPercent = if (completed.totalOriginalBytes > 0) (saved.toFloat() / completed.totalOriginalBytes.toFloat() * 100).toInt() else 0
                Text(
                    text = "Total Size: ${com.medianest.util.formatBytesReport(completed.totalOriginalBytes)} ➔ ${com.medianest.util.formatBytesReport(completed.totalNewBytes)} (-$savedPercent%)",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFA0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}

// ==========================================
// MEDIA ITEM PICKER MODALS
// ==========================================
@Composable
private fun MediaItemPickerModal(
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onPickExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    var pickerFilter by remember { mutableStateOf("ALL") }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xF2080C18),
            borderColor = Color(0x3338BDF8)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Media Source", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All Media", "VIDEO" to "Videos", "AUDIO" to "Audio", "IMAGE" to "Images").forEach { (f, label) ->
                        val isSel = pickerFilter == f
                        GlossyPillButton(
                            text = label,
                            isSelected = isSel,
                            onClick = { pickerFilter = f },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val allList = remember(pickerFilter, imagesList, videosList, audioList) {
                    when (pickerFilter) {
                        "VIDEO" -> videosList
                        "AUDIO" -> audioList
                        "IMAGE" -> imagesList
                        else -> (videosList + audioList + imagesList).sortedByDescending { it.id }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allList) { item ->
                        Surface(
                            onClick = { onSelect(item) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x14FFFFFF),
                            border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = when (item.type) {
                                        MediaType.AUDIO -> Icons.Default.MusicNote
                                        MediaType.IMAGE -> Icons.Default.Image
                                        else -> Icons.Default.Movie
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    Text("${com.medianest.util.formatBytesReport(item.size)} · ${item.mimeType}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onPickExternal,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x2EFFFFFF)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse Storage / SD Card...", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MultiMediaItemPickerModal(
    items: List<MediaItem>,
    initialSelected: List<MediaItem>,
    onDone: (List<MediaItem>) -> Unit,
    onPickExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedList by remember { mutableStateOf(initialSelected.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xF2080C18),
            borderColor = Color(0x3338BDF8)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Multiple Files (${selectedList.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row {
                        TextButton(onClick = {
                            selectedList = if (selectedList.size == items.size) emptySet() else items.toSet()
                        }) {
                            Text(if (selectedList.size == items.size) "Deselect All" else "Select All", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        val isChecked = selectedList.contains(item)
                        Surface(
                            onClick = {
                                selectedList = if (isChecked) selectedList - item else selectedList + item
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChecked) Color(0x2A38BDF8) else Color(0x14FFFFFF),
                            border = BorderStroke(1.dp, if (isChecked) Color(0xFF38BDF8) else Color(0x1AFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedList = if (checked) selectedList + item else selectedList - item
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF38BDF8), checkmarkColor = Color.Black)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    Text("${com.medianest.util.formatBytesReport(item.size)} · ${item.mimeType}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPickExternal,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x2EFFFFFF)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Browse Multi", color = Color.White, fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = { onDone(selectedList.toList()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Confirm (${selectedList.size})", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
