@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.medianest.ui.analytics

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.FileStatSnapshot
import com.medianest.data.db.FormatStat
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MediaConverterStudioDialog
import com.medianest.ui.components.StudioTab

@Composable
fun AnalyticsScreen(
    snapshot: FileStatSnapshot?,
    formatStats: List<FormatStat>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSelectCategoryFilter: (String) -> Unit, // "IMAGE", "VIDEO", "AUDIO"
    onSelectFormatFilter: (String) -> Unit,   // "JPG", "MP4", etc.
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>,
    onOpenQuickView: (MediaItem, List<MediaItem>) -> Unit,
    onOpenVideoPlayer: (MediaItem) -> Unit,
    onOpenAudioPlayer: (MediaItem) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    searchQuery: String = ""
) {
    var activeDrillDownTitle by remember { mutableStateOf<String?>(null) }
    var activeFilterCategory by remember { mutableStateOf<String?>(null) }
    var activeFilterFormat by remember { mutableStateOf<String?>(null) }
    var showStudioDialog by remember { mutableStateOf(false) }
    var activeStudioTab by remember { mutableStateOf(StudioTab.CONVERT) }

    if (searchQuery.isNotBlank()) {
        DashboardSearchResultView(
            searchQuery = searchQuery,
            imagesList = imagesList,
            videosList = videosList,
            audioList = audioList,
            onOpenQuickView = onOpenQuickView,
            onOpenVideoPlayer = onOpenVideoPlayer,
            onOpenAudioPlayer = onOpenAudioPlayer
        )
        return
    }

    if (activeDrillDownTitle != null) {
        DrillDownScreen(
            title = activeDrillDownTitle!!,
            filterCategory = activeFilterCategory,
            filterFormat = activeFilterFormat,
            allImages = imagesList,
            allVideos = videosList,
            allAudio = audioList,
            onBack = {
                activeDrillDownTitle = null
                activeFilterCategory = null
                activeFilterFormat = null
            },
            onOpenQuickView = onOpenQuickView,
            onOpenVideoPlayer = onOpenVideoPlayer,
            onOpenAudioPlayer = onOpenAudioPlayer
        )
        return
    }

    val isStale = remember(snapshot) {
        if (snapshot == null) false
        else (System.currentTimeMillis() - snapshot.timestamp) > 24 * 60 * 60 * 1000L
    }

    // Refresh button rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Calculate real Device Storage (Used vs Total)
    val storageInfo = remember(snapshot) {
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val total = statFs.blockCountLong * statFs.blockSizeLong
            val free = statFs.availableBlocksLong * statFs.blockSizeLong
            val used = (total - free).coerceAtLeast(snapshot?.totalSize ?: 0L)
            Pair(used, total)
        } catch (e: Exception) {
            val snapTotal = snapshot?.totalSize ?: 0L
            Pair(snapTotal, (snapTotal * 1.5).toLong().coerceAtLeast(64L * 1024 * 1024 * 1024))
        }
    }
    val usedStorageBytes = storageInfo.first
    val totalStorageBytes = storageInfo.second

    // Responsive columns configuration for phone vs tablet
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val columnsCount = remember(screenWidthDp) {
        when {
            screenWidthDp >= 900 -> 4
            screenWidthDp >= 600 -> 3
            else -> 2
        }
    }

    // Sort formatStats by sizeBytes descending (largest first to smallest)
    val sortedFormatStats = remember(formatStats) {
        formatStats.sortedWith(
            compareByDescending<FormatStat> { it.sizeBytes }
                .thenByDescending { it.fileCount }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Stale warning banner if data > 24 hours
        if (isStale) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Stale warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Data is stale (older than 24h)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Tap refresh to scan recent files.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Refresh", fontSize = 12.sp)
                    }
                }
            }
        }

        if (isRefreshing || snapshot == null) {
            AnalyticsSkeletonLoader()
        } else {
            // 1. Device Storage Analysis Card
            DeviceStorageAnalysisCard(
                snapshot = snapshot,
                usedBytes = usedStorageBytes,
                totalBytes = totalStorageBytes,
                isRefreshing = isRefreshing,
                rotationAngle = rotationAngle,
                onRefresh = onRefresh
            )

            // 2. All Available Formats Analysis Section (Sorted largest to smallest)
            AllAvailableFormatsSection(
                sortedFormatStats = sortedFormatStats,
                columnsCount = columnsCount,
                onSelectFormat = { stat ->
                    activeDrillDownTitle = "${stat.extension} Files"
                    activeFilterCategory = stat.category
                    activeFilterFormat = stat.extension
                }
            )

            // 3. Media Categories Section
            MediaCategoriesSection(
                snapshot = snapshot,
                columnsCount = if (screenWidthDp >= 600) 2 else 1,
                onSelectCategory = { category ->
                    activeDrillDownTitle = when (category) {
                        "IMAGE" -> "All Images"
                        "VIDEO" -> "All Videos"
                        else -> "All Audio"
                    }
                    activeFilterCategory = category
                    activeFilterFormat = null
                }
            )

            // 4. Lossless Media Studio (Converter, Compressor, Extractor, Crop, Repair)
            MediaProcessorStudioCard(
                onOpenStudioTab = { tab ->
                    activeStudioTab = tab
                    showStudioDialog = true
                }
            )
        }

        if (showStudioDialog) {
            MediaConverterStudioDialog(
                initialTab = activeStudioTab,
                imagesList = imagesList,
                videosList = videosList,
                audioList = audioList,
                onDismissRequest = { showStudioDialog = false },
                onOpenVideoPlayer = onOpenVideoPlayer,
                onOpenAudioPlayer = onOpenAudioPlayer
            )
        }
    }
}

@Composable
fun MediaProcessorStudioCard(
    onOpenStudioTab: (StudioTab) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lossless Media Studio",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Text(
                    text = "FFmpeg Engine",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        GlassSurface(
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color(0x3D181A24),
            borderColor = Color(0x2EFFFFFF),
            enableBlur = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner inside card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x2E38BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lossless Converter & Tools",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "0-Loss Remux · Smart Compress · Crop · Audio Extractor · Repair",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Action Grid
                val studioActions = listOf(
                    StudioTab.CONVERT to ("Lossless Convert" to "0-loss copy, MP4/MKV/FLAC"),
                    StudioTab.COMPRESS to ("Smart Compress" to "Discord/WhatsApp size limits"),
                    StudioTab.CROP to ("Crop & Reframe" to "9:16 Reels/Shorts, 1:1, 16:9"),
                    StudioTab.EXTRACT to ("Track Extractor" to "Extract audio/MP3, GIF, .srt"),
                    StudioTab.REPAIR to ("Corrupt File Repair" to "Rebuild container headers/index")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    studioActions.forEach { (tab, details) ->
                        val (title, subtitle) = details
                        Surface(
                            onClick = { onOpenStudioTab(tab) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x3D181A24),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x1F38BDF8)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(tab.icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                                    }
                                    Column {
                                        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(subtitle, fontSize = 11.sp, color = Color(0xFF8E9CB2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceStorageAnalysisCard(
    snapshot: FileStatSnapshot,
    usedBytes: Long,
    totalBytes: Long,
    isRefreshing: Boolean,
    rotationAngle: Float,
    onRefresh: () -> Unit
) {
    val videoSize = snapshot.videoSize
    val audioSize = snapshot.audioSize
    val imageSize = snapshot.imageSize
    val knownMediaSize = videoSize + audioSize + imageSize
    val calcOthers = (usedBytes - knownMediaSize).coerceAtLeast(0L)
    val othersSize = if (calcOthers > 0L) calcOthers else (knownMediaSize * 0.2).toLong().coerceAtLeast(1024L * 1024L * 120L)

    val colorVideos = Color(0xFF3B82F6) // Blue
    val colorAudios = Color(0xFF8B5CF6) // Purple
    val colorImages = Color(0xFFEC4899) // Pink / Magenta
    val colorOthers = Color(0xFFF59E0B) // Orange

    GlassSurface(
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x3D181A24),
        borderColor = Color(0x2EFFFFFF),
        enableBlur = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title and Storage capacity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage Analysis",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${AnalyticsColors.formatBytes(usedBytes)} / ${AnalyticsColors.formatBytes(totalBytes)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFC0C5D0)
                    )

                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = "Refresh storage analysis" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF9EA3B0),
                            modifier = if (isRefreshing) Modifier.rotate(rotationAngle) else Modifier
                        )
                    }
                }
            }

            // Multi-colored segmented progress bar
            val totalForBar = totalBytes.coerceAtLeast(1L).toFloat()
            val videoWeight = (videoSize / totalForBar).coerceAtLeast(0.01f)
            val audioWeight = (audioSize / totalForBar).coerceAtLeast(0.01f)
            val imageWeight = (imageSize / totalForBar).coerceAtLeast(0.01f)
            val othersWeight = (othersSize / totalForBar).coerceAtLeast(0.01f)
            val freeWeight = ((totalBytes - usedBytes).coerceAtLeast(0L) / totalForBar).coerceAtLeast(0.02f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x332B2F3E)),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(videoWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorVideos)
                )
                Box(
                    modifier = Modifier
                        .weight(audioWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorAudios)
                )
                Box(
                    modifier = Modifier
                        .weight(imageWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorImages)
                )
                Box(
                    modifier = Modifier
                        .weight(othersWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorOthers)
                )
                Box(
                    modifier = Modifier
                        .weight(freeWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x333F4456))
                )
            }

            // Legend grid (Videos, Audios, Images, Others)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendItemDot(
                        color = colorVideos,
                        label = "Videos (${AnalyticsColors.formatBytes(videoSize)})",
                        modifier = Modifier.weight(1f)
                    )
                    LegendItemDot(
                        color = colorAudios,
                        label = "Audios (${AnalyticsColors.formatBytes(audioSize)})",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendItemDot(
                        color = colorImages,
                        label = "Images (${AnalyticsColors.formatBytes(imageSize)})",
                        modifier = Modifier.weight(1f)
                    )
                    LegendItemDot(
                        color = colorOthers,
                        label = "Others (${AnalyticsColors.formatBytes(othersSize)})",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun LegendItemDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFD0D5E0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AllAvailableFormatsSection(
    sortedFormatStats: List<FormatStat>,
    columnsCount: Int,
    onSelectFormat: (FormatStat) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Formats Analysis",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (sortedFormatStats.isEmpty()) {
            GlassSurface(
                shape = RoundedCornerShape(18.dp),
                backgroundColor = Color(0x3B181A24),
                borderColor = Color(0x28FFFFFF),
                enableBlur = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No format statistics available.",
                        fontSize = 13.sp,
                        color = Color(0xFF9EA3AF)
                    )
                }
            }
        } else {
            // Render rows of columnsCount items to prevent stretching on wide screens / tablets
            val chunkedStats = remember(sortedFormatStats, columnsCount) {
                sortedFormatStats.chunked(columnsCount)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chunkedStats.forEach { rowStats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowStats.forEach { stat ->
                            FormatCardItem(
                                stat = stat,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onSelectFormat(stat) }
                            )
                        }

                        // Fill empty cells if the last row is incomplete
                        if (rowStats.size < columnsCount) {
                            repeat(columnsCount - rowStats.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormatCardItem(
    stat: FormatStat,
    modifier: Modifier = Modifier
) {
    val themeColor = remember(stat.extension, stat.category) {
        AnalyticsColors.getFormatColor(stat.extension, stat.category)
    }

    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x3B181A24),
        borderColor = Color(0x28FFFFFF),
        enableBlur = false,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Format Pill Badge on Left, File count on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pill badge for extension
                Surface(
                    color = themeColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stat.extension.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${AnalyticsColors.formatNumber(stat.fileCount)} files",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF9EA3AF)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large Size Text
            Text(
                text = AnalyticsColors.formatBytes(stat.sizeBytes),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun MediaCategoriesSection(
    snapshot: FileStatSnapshot,
    columnsCount: Int,
    onSelectCategory: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Media Categories",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        val categoriesList = listOf(
            Triple("Images", "JPG, PNG, WEBP, HEIC", Triple(snapshot.imageCount, snapshot.imageSize, "IMAGE")),
            Triple("Videos", "MP4, MKV, AVI, WEBM, MOV", Triple(snapshot.videoCount, snapshot.videoSize, "VIDEO")),
            Triple("Audios", "MP3, FLAC, WAV, AAC, M4A", Triple(snapshot.audioCount, snapshot.audioSize, "AUDIO"))
        )

        if (columnsCount > 1) {
            // Grid for tablet wide screen
            val chunkedCategories = categoriesList.chunked(columnsCount)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chunkedCategories.forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCategories.forEach { (title, subtitle, info) ->
                            val (count, size, type) = info
                            MediaCategoryCardItem(
                                title = title,
                                subtitle = subtitle,
                                count = count,
                                type = type,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onSelectCategory(type) }
                            )
                        }
                        if (rowCategories.size < columnsCount) {
                            repeat(columnsCount - rowCategories.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            // Single column for phones
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                categoriesList.forEach { (title, subtitle, info) ->
                    val (count, size, type) = info
                    MediaCategoryCardItem(
                        title = title,
                        subtitle = subtitle,
                        count = count,
                        type = type,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelectCategory(type) }
                    )
                }
            }
        }
    }
}

@Composable
fun MediaCategoryCardItem(
    title: String,
    subtitle: String,
    count: Int,
    type: String,
    modifier: Modifier = Modifier
) {
    val iconVector = when (type) {
        "IMAGE" -> Icons.Default.Image
        "VIDEO" -> Icons.Default.Movie
        else -> Icons.Default.Audiotrack
    }
    val iconTint = when (type) {
        "IMAGE" -> Color(0xFFEC4899)
        "VIDEO" -> Color(0xFF3B82F6)
        else -> Color(0xFF8B5CF6)
    }

    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x3B181A24),
        borderColor = Color(0x28FFFFFF),
        enableBlur = true,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Icon Box (Transparent Frosty Blur)
                GlassSurface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0x12FFFFFF),
                    borderColor = Color(0x1AFFFFFF),
                    enableBlur = true,
                    blurRadius = 8.dp
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9EA3AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = AnalyticsColors.formatNumber(count),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun AnalyticsSkeletonLoader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun DashboardSearchResultView(
    searchQuery: String,
    imagesList: List<MediaItem>,
    videosList: List<MediaItem>,
    audioList: List<MediaItem>,
    onOpenQuickView: (MediaItem, List<MediaItem>) -> Unit,
    onOpenVideoPlayer: (MediaItem) -> Unit,
    onOpenAudioPlayer: (MediaItem) -> Unit
) {
    val filteredImages = remember(searchQuery, imagesList) {
        imagesList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val filteredVideos = remember(searchQuery, videosList) {
        videosList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val filteredAudio = remember(searchQuery, audioList) {
        audioList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.artist?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.album?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val totalCount = filteredImages.size + filteredVideos.size + filteredAudio.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SEARCH RESULTS FOR \"$searchQuery\" ($totalCount)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64B5F6),
            letterSpacing = 1.2.sp
        )

        if (totalCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No matching files found across Images, Videos, or Audio", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            // Images Section
            if (filteredImages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Images (${filteredImages.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filteredImages, key = { "img_${it.id}" }) { img ->
                            GlassSurface(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenQuickView(img, filteredImages) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    coil.compose.AsyncImage(
                                        model = img.uri,
                                        contentDescription = img.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Text(
                                        text = img.title,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color(0x99000000))
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Videos Section
            if (filteredVideos.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Videos (${filteredVideos.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredVideos, key = { "vid_${it.id}" }) { vid ->
                            GlassSurface(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenVideoPlayer(vid) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    coil.compose.AsyncImage(
                                        model = vid.uri,
                                        contentDescription = vid.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Text(
                                        text = vid.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color(0x99000000))
                                            .padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Audio Section
            if (filteredAudio.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audio (${filteredAudio.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredAudio.take(15).forEach { aud ->
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenAudioPlayer(aud) },
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = Color(0x1F24293A)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = aud.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = aud.artist ?: "Unknown Artist", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(90.dp))
    }
}
