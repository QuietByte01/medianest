@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.medianest.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.HiddenFolderDao
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.data.settings.SettingsManager
import com.medianest.ui.components.AppSlider
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.dismissKeyboardOnOutsideTap
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    hiddenFolderDao: HiddenFolderDao,
    mediaStoreRepository: MediaStoreRepository,
    onClearHistory: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val theme by settingsManager.theme.collectAsState(initial = "DARK")
    val gridGapDp by settingsManager.gridGapDp.collectAsState(initial = 8)
    val gridSizeLevel by settingsManager.gridSizeLevel.collectAsState(initial = 1)
    val roundedCornersEnabled by settingsManager.roundedCornersEnabled.collectAsState(initial = true)
    val glassmorphism by settingsManager.glassmorphismEnabled.collectAsState(initial = true)

    val showPlaybackNotification by settingsManager.showPlaybackNotification.collectAsState(initial = true)
    val showVideoNotification by settingsManager.showVideoNotification.collectAsState(initial = true)
    val hwAccelEnabled by settingsManager.hardwareAccelerationEnabled.collectAsState(initial = true)
    val keepScreenOn by settingsManager.keepScreenOn.collectAsState(initial = true)

    val currentPictureMode by settingsManager.pictureMode.collectAsState(initial = "Vivid Color Accent")

    val autoFetchLyrics by settingsManager.autoFetchLyrics.collectAsState(initial = true)
    val uninterruptedMode by settingsManager.uninterruptedMode.collectAsState(initial = false)
    val autoResumeOnBluetooth by settingsManager.autoResumeOnBluetooth.collectAsState(initial = false)
    val audioBackgroundPlay by settingsManager.audioBackgroundPlay.collectAsState(initial = true)
    val videoBackgroundPlay by settingsManager.videoBackgroundPlay.collectAsState(initial = false)
    val dailySubtitleCount by settingsManager.dailySubtitleSearchCount.collectAsState(initial = 0)
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = "AUTO")
    val developerModeEnabled by settingsManager.developerModeEnabled.collectAsState(initial = false)

    var versionTapCount by remember { mutableIntStateOf(0) }
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionText = "v${packageInfo.versionName} (${packageInfo.versionCode})"

    // Selective Hidden Folders State
    var folderCategoryTab by remember { mutableIntStateOf(0) } // 0: Audio, 1: Images, 2: Videos
    val selectiveHiddenDao = remember { com.medianest.MediaNestApp.instance.database.selectiveHiddenFolderDao() }
    val allSelectiveFolders by selectiveHiddenDao.getAllHiddenFolders().collectAsState(initial = emptyList())

    var imageItems by remember { mutableStateOf<List<com.medianest.data.model.MediaItem>>(emptyList()) }
    var videoItems by remember { mutableStateOf<List<com.medianest.data.model.MediaItem>>(emptyList()) }
    var audioItems by remember { mutableStateOf<List<com.medianest.data.model.MediaItem>>(emptyList()) }
    var isLoadingFolders by remember { mutableStateOf(false) }

    LaunchedEffect(folderCategoryTab) {
        isLoadingFolders = true
        when (folderCategoryTab) {
            0 -> audioItems = mediaStoreRepository.getAudio(emptySet(), showHidden = true)
            1 -> imageItems = mediaStoreRepository.getImages(emptySet(), showHidden = true)
            2 -> videoItems = mediaStoreRepository.getVideos(emptySet(), showHidden = true)
        }
        isLoadingFolders = false
    }

    /*
    var hwAccelMode by remember { mutableStateOf("Enabled (Full GPU/DSP)") }

    val m3uPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = com.medianest.MediaNestApp.instance.database
                    val audioList = mediaStoreRepository.getAudio(emptySet(), true)
                    val contentResolver = context.contentResolver
                    var playlistName = "Imported Playlist"
                    val matchedUris = mutableListOf<String>()

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val fileName = cursor.getString(nameIndex)
                            if (!fileName.isNullOrBlank()) {
                                playlistName = fileName.removeSuffix(".m3u").removeSuffix(".m3u8").removeSuffix(".pls")
                            }
                        }
                    }

                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { rawLine ->
                                val line = rawLine.trim()
                                if (line.isNotBlank() && !line.startsWith("#")) {
                                    val fileName = line.substringAfterLast('/').substringAfterLast('\\')
                                    val matchedTrack = audioList.firstOrNull { track ->
                                        track.title.equals(fileName.substringBeforeLast('.'), ignoreCase = true) ||
                                        track.title.equals(fileName, ignoreCase = true) ||
                                        track.uri.toString().endsWith(fileName)
                                    }
                                    if (matchedTrack != null) {
                                        matchedUris.add(matchedTrack.uri.toString())
                                    }
                                }
                            }
                        }
                    }

                    val catId = db.categoryDao().insertCategory(
                        com.medianest.data.db.MediaCategory(name = playlistName, type = "AUDIO", iconName = "QueueMusic")
                    )
                    matchedUris.distinct().forEach { mediaUri ->
                        db.categoryDao().insertCategoryCrossRef(
                            com.medianest.data.db.CategoryMediaCrossRef(categoryId = catId, mediaUri = mediaUri)
                        )
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Imported '$playlistName' (${matchedUris.size} tracks matched)", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import playlist file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    */

    BackHandler { onClose() }

    val darkBackgroundGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF08090C), // Deep Obsidian Start
                Color(0xFF040507), // Obsidian Center
                Color(0xFF020203)  // Deep Obsidian End
            )
        )
    }

    val customSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = Color(0xFF6366F1), // Bright indigo/purple from screenshot
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color(0xFF717D96),
        uncheckedTrackColor = Color(0x3D2D3748),
        uncheckedBorderColor = Color.Transparent
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackgroundGradient)
            .drawBehind {
                val gridSpacing = 16.dp.toPx()
                val lineWeight = 1.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.04f)

                // Vertical lines
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), lineWeight)
                    x += gridSpacing
                }

                // Horizontal lines
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), lineWeight)
                    y += gridSpacing
                }
            }
            .dismissKeyboardOnOutsideTap()
    ) {
            val isPhoneScreen = LocalConfiguration.current.screenWidthDp < 600
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = if (isPhoneScreen) 12.dp else 24.dp,
                        vertical = if (isPhoneScreen) 12.dp else 20.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isPhoneScreen) 14.dp else 20.dp)
            ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            // SECTION 1: DISPLAY & INTERFACE
            SettingsGlassCard(title = "DISPLAY & INTERFACE") {
                // Grid Spacing (Gap) Discrete Slider
                val isPhoneScreen = LocalConfiguration.current.screenWidthDp < 600
                SettingsRowItem(
                    title = "Grid Spacing (Gap)",
                    subtitle = "Adjust padding gap between media grid tiles",
                    stackedOnPhone = true,
                    control = {
                        Column(
                            horizontalAlignment = if (isPhoneScreen) Alignment.Start else Alignment.End,
                            modifier = if (isPhoneScreen) Modifier.fillMaxWidth() else Modifier.widthIn(max = 240.dp)
                        ) {
                            val gapValues = listOf(8, 12, 16, 24)
                            val currentIndex = gapValues.indexOf(gridGapDp).coerceAtLeast(0)

                            AppSlider(
                                value = currentIndex.toFloat(),
                                onValueChange = { index ->
                                    val selectedGap = gapValues[index.toInt().coerceIn(0, 3)]
                                    scope.launch { settingsManager.setGridGapDp(selectedGap) }
                                },
                                valueRange = 0f..3f,
                                steps = 2,
                                headStyle = com.medianest.ui.components.AppSliderHeadStyle.Bar,
                                thickness = com.medianest.ui.components.AppSliderThickness.Thick,
                                modifier = Modifier.height(24.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                gapValues.forEach { gap ->
                                    Text(
                                        text = "${gap}dp",
                                        fontSize = 10.sp,
                                        fontWeight = if (gap == gridGapDp) FontWeight.Bold else FontWeight.Normal,
                                        color = if (gap == gridGapDp) Color(0xFF818CF8) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                )

                // Grid Size Granular Slider
                SettingsRowItem(
                    title = "Grid Item Size",
                    subtitle = "Adjust size of media tiles in the gallery",
                    stackedOnPhone = true,
                    control = {
                        Column(
                            horizontalAlignment = if (isPhoneScreen) Alignment.Start else Alignment.End,
                            modifier = if (isPhoneScreen) Modifier.fillMaxWidth() else Modifier.widthIn(max = 240.dp)
                        ) {
                            val sizeLabels = listOf("Compact", "Standard", "Large", "XL")
                            AppSlider(
                                value = gridSizeLevel.toFloat(),
                                onValueChange = { level ->
                                    scope.launch { settingsManager.setGridSizeLevel(level.toInt()) }
                                },
                                valueRange = 0f..3f,
                                steps = 2,
                                headStyle = com.medianest.ui.components.AppSliderHeadStyle.Bar,
                                thickness = com.medianest.ui.components.AppSliderThickness.Thick,
                                modifier = Modifier.height(24.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                sizeLabels.forEachIndexed { index, label ->
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (index == gridSizeLevel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (index == gridSizeLevel) Color(0xFF818CF8) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                )

                // Rounded Grid Tiles
                SettingsRowItem(
                    title = "Rounded Grid Tiles",
                    subtitle = "Apply corner curvature to album & artist tiles",
                    control = {
                        Switch(
                            checked = roundedCornersEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setRoundedCornersEnabled(it) } },
                            colors = customSwitchColors
                        )
                    }
                )
            }

            // SECTION 2: LIBRARY & FOLDER FILTERS
            SettingsGlassCard(title = "LIBRARY & FOLDER FILTERS") {
                // Show Hidden Files & Folders
                SettingsRowItem(
                    title = "Show Hidden Files & Folders",
                    subtitle = "Display files starting with a dot (.) in directory views",
                    control = {
                        Switch(
                            checked = showHiddenFiles,
                            onCheckedChange = { checked ->
                                scope.launch { settingsManager.setShowHiddenFiles(checked) }
                            },
                            colors = customSwitchColors
                        )
                    }
                )

                // Folders to Hide From Library
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Folders to Hide From Library",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Toggle folders to exclude them from scanning",
                        fontSize = 12.sp,
                        color = Color(0xFF8E95A5)
                    )

                    // Category Pills Row: Audio, Images, Videos
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    ) {
                        listOf("Audio", "Images", "Videos").forEachIndexed { index, catName ->
                            val isSelected = folderCategoryTab == index
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color(0xFF4F46E5) else Color(0x22FFFFFF),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { folderCategoryTab = index }
                            ) {
                                Text(
                                    text = catName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Selected Folder items list
                    val currentItems = when (folderCategoryTab) {
                        0 -> audioItems
                        1 -> imageItems
                        else -> videoItems
                    }
                    val mediaTypeStr = when (folderCategoryTab) {
                        0 -> "AUDIO"
                        1 -> "IMAGE"
                        else -> "VIDEO"
                    }

                    val fallbackFolder = if (folderCategoryTab == 0) "Music" else if (folderCategoryTab == 1) "Pictures" else "Movies"
                    val folderGroups = remember(currentItems, folderCategoryTab) {
                        currentItems.groupBy { item ->
                            item.bucketName ?: item.relativePath?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallbackFolder
                        }.mapValues { it.value.size }
                    }

                    val hiddenForType = remember(allSelectiveFolders, mediaTypeStr) {
                        allSelectiveFolders.filter { it.mediaType == mediaTypeStr && it.isHidden }
                            .flatMap { listOf(it.folderPath, it.folderName) }
                            .filter { it.isNotBlank() }
                            .toSet()
                    }

                    val folderNamesList = remember(folderGroups, hiddenForType) {
                        (folderGroups.keys + hiddenForType).sorted()
                    }

                    if (isLoadingFolders) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF6366F1),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (folderNamesList.isEmpty()) {
                        Text(
                            text = "No folders found for this category.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            folderNamesList.forEach { folderName ->
                                val isHidden = hiddenForType.contains(folderName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1AFFFFFF))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "/Internal Storage/$folderName",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = !isHidden,
                                        onCheckedChange = { isChecked ->
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                selectiveHiddenDao.insertOrUpdate(
                                                    com.medianest.data.db.SelectiveHiddenFolder(
                                                        folderPath = folderName,
                                                        folderName = folderName,
                                                        mediaType = mediaTypeStr,
                                                        isHidden = !isChecked
                                                    )
                                                )
                                            }
                                        },
                                        colors = customSwitchColors
                                    )
                                }
                            }
                        }
                    }
                }

                /*
                // Import External Playlists
                SettingsRowItem(
                    title = "Import External Playlists",
                    subtitle = "Scan and import playlist files (.m3u, .m3u8, .pls)",
                    stackedOnPhone = true,
                    control = {
                        GlassSurface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { },
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = Color(0x33FFFFFF),
                            borderColor = Color(0x33FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text("Import Files", fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
                */
            }

            // SECTION 3: PLAYBACK & ENGINE
            SettingsGlassCard(title = "PLAYBACK & ENGINE") {
                
                // Keep Screen On During Playback
                SettingsRowItem(
                    title = "Keep Screen On During Playback",
                    subtitle = "Prevent device sleep timer while player is active",
                    control = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { scope.launch { settingsManager.setKeepScreenOn(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Auto-Fetch Synced Lyrics
                SettingsRowItem(
                    title = "Auto-Fetch Synced Lyrics",
                    subtitle = "Automatically download time-synced lyrics over network",
                    control = {
                        Switch(
                            checked = autoFetchLyrics,
                            onCheckedChange = { scope.launch { settingsManager.setAutoFetchLyrics(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Uninterrupted Mode
                SettingsRowItem(
                    title = "Uninterrupted Mode",
                    subtitle = "Don't pause or duck for notifications. Phone calls will still pause playback.",
                    control = {
                        Switch(
                            checked = uninterruptedMode,
                            onCheckedChange = { scope.launch { settingsManager.setUninterruptedMode(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Auto-resume on Bluetooth
                SettingsRowItem(
                    title = "Auto-resume on Bluetooth",
                    subtitle = "Automatically start playback when Bluetooth headphones connect.",
                    control = {
                        Switch(
                            checked = autoResumeOnBluetooth,
                            onCheckedChange = { scope.launch { settingsManager.setAutoResumeOnBluetooth(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Audio Background Playback
                SettingsRowItem(
                    title = "Audio Background Playback",
                    subtitle = "Continue playing audio when app is minimized or screen is off",
                    control = {
                        Switch(
                            checked = audioBackgroundPlay,
                            onCheckedChange = { scope.launch { settingsManager.setAudioBackgroundPlay(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Video Background Playback
                SettingsRowItem(
                    title = "Video Background Playback",
                    subtitle = "Continue playing video audio when app is minimized",
                    control = {
                        Switch(
                            checked = videoBackgroundPlay,
                            onCheckedChange = { scope.launch { settingsManager.setVideoBackgroundPlay(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Subtitle Search Quota
                SettingsRowItem(
                    title = "Subtitle Search Limit",
                    subtitle = "OpenSubtitles daily limit: $dailySubtitleCount / 5 searches used today.",
                    control = {
                        Text(
                            text = if (dailySubtitleCount >= 5) "LIMIT REACHED" else "OK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dailySubtitleCount >= 5) Color.Red else Color(0xFF34D399)
                        )
                    }
                )

                // Offline Mode
                SettingsRowItem(
                    title = "Offline Mode",
                    subtitle = "Block all online network calls and lyrics fetching",
                    control = {
                        Switch(
                            checked = offlineMode,
                            onCheckedChange = { scope.launch { settingsManager.setOfflineMode(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Storage & All Files Access Permission
                val hasAllFiles = com.medianest.util.PermissionUtils.hasAllFilesAccess()
                val hasStandard = com.medianest.util.PermissionUtils.hasStandardMediaPermissions(context)
                SettingsRowItem(
                    title = "Storage & All Files Access",
                    subtitle = if (hasAllFiles) "Full access granted (all storage directories and files)" else if (hasStandard) "Standard MediaStore access granted. Tap to allow All Files Access for hidden folders" else "Storage permission required to load files",
                    stackedOnPhone = true,
                    control = {
                        Button(
                            onClick = { com.medianest.util.PermissionUtils.openStorageAccessSettings(context) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (hasAllFiles) Color(0x2234D399) else Color(0x3338BDF8))
                        ) {
                            Text(
                                text = if (hasAllFiles) "Granted (Settings)" else "Open Settings",
                                fontSize = 12.sp,
                                color = if (hasAllFiles) Color(0xFF34D399) else Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            }

            // SECTION 4: NOTIFICATIONS & DATA
            SettingsGlassCard(title = "NOTIFICATIONS & DATA") {
                // Audio Playback Notifications
                SettingsRowItem(
                    title = "Audio Playback Notifications",
                    subtitle = "Show control widget for music on lock screen",
                    control = {
                        Switch(
                            checked = showPlaybackNotification,
                            onCheckedChange = { scope.launch { settingsManager.setShowPlaybackNotification(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Video Playback Notifications
                SettingsRowItem(
                    title = "Video Playback Notifications",
                    subtitle = "Show notification for video background playback",
                    control = {
                        Switch(
                            checked = showVideoNotification,
                            onCheckedChange = { scope.launch { settingsManager.setShowVideoNotification(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Playback History & Cache removed as requested - merged into Clear All Cache button below

                // Recycle Bin (Trash) commented out
                // val enableTrash by settingsManager.enableTrash.collectAsState(initial = true)
                // SettingsRowItem(
                //     title = "Recycle Bin (Trash)",
                //     subtitle = "Store deleted media files in app trash bin before permanent removal",
                //     control = {
                //         Switch(
                //             checked = enableTrash,
                //             onCheckedChange = { scope.launch { settingsManager.setEnableTrash(it) } },
                //             colors = customSwitchColors
                //         )
                //     }
                // )
                // if (enableTrash) {
                //     val trashedCount = remember { com.medianest.util.TrashManager.getTrashedItems(context).size }
                //     SettingsRowItem(
                //         title = "Manage Recycle Bin ($trashedCount items)",
                //         subtitle = "View, restore or empty trashed media files",
                //         stackedOnPhone = true,
                //         control = {
                //             Button(
                //                 onClick = { showCombinedTrashSheet = true },
                //                 shape = RoundedCornerShape(12.dp),
                //                 colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8))
                //             ) {
                //                 Text("Open Trash", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                //             }
                //         }
                //     )
                // }

                // SECTION: PICTURE MODE
                val pictureModeEnabled by settingsManager.pictureModeEnabled.collectAsState(initial = true)
                SettingsRowItem(
                    title = "Enable Picture Mode",
                    subtitle = "Enhance photo & video display colors",
                    control = {
                        Switch(
                            checked = pictureModeEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setPictureModeEnabled(it) } },
                            colors = customSwitchColors
                        )
                    }
                )

                // Clear All Cache & Playback History
                SettingsRowItem(
                    title = "Clean Storage & History",
                    subtitle = "Clear playback history, search logs, generated thumbnails and cached files",
                    stackedOnPhone = true,
                    control = {
                        Button(
                            onClick = {
                                onClearHistory()
                                com.medianest.player.ExoPlayerManager.getInstance(context).clearAllCache {
                                    Toast.makeText(context, "Cache and playback history cleared!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                            border = BorderStroke(1.dp, Color(0x66EF4444))
                        ) {
                            Text("Clear All Cache & History", fontSize = 13.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            // Developer Options
            DeveloperSettingsSection(
                settingsManager = settingsManager,
                onDisableDevMode = { versionTapCount = 0 }
            )

            // Version info at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = versionText,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!developerModeEnabled) {
                                versionTapCount++
                                if (versionTapCount >= 3) {
                                    scope.launch {
                                        settingsManager.setDeveloperModeEnabled(true)
                                        Toast.makeText(context, "Developer Mode Enabled!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val remaining = 3 - versionTapCount
                                    Toast.makeText(context, "Tap $remaining more times for dev mode", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Developer Mode is already active", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                
                if (developerModeEnabled) {
                    Text(
                        text = "Developer Mode Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGlassCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
    val isPhoneScreen = LocalConfiguration.current.screenWidthDp < 600
    
    // Obsidian Tint for Dark Mode, Light Glossy for Light Mode
    val cardBg = if (isDark) Color(0xBF0F1015) else Color(0xA6FFFFFF)
    val cardBorder = if (isDark) Color(0x26FFFFFF) else Color(0x33000000)

    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardBg,
        borderColor = cardBorder,
        enableBlur = true,
        blurRadius = 24.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhoneScreen) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (isPhoneScreen) 14.dp else 18.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B92A5),
                letterSpacing = 0.8.sp
            )
            content()
        }
    }
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    stackedOnPhone: Boolean = false,
    control: @Composable () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isPhoneScreen = configuration.screenWidthDp < 600

    if (isPhoneScreen && stackedOnPhone) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF8E95A5),
                    lineHeight = 16.sp
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                control()
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF8E95A5),
                    lineHeight = 16.sp
                )
            }
            control()
        }
    }
}

@Composable
fun SettingsDropdownPill(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x2A1E2230),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
