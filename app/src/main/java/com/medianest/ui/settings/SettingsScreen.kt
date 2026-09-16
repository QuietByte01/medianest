@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.medianest.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.MediaNestApp
import com.medianest.data.db.HiddenFolderDao
import com.medianest.data.repository.AnalyticsRepository
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.data.settings.SettingsManager
import com.medianest.ui.components.AppSlider
import com.medianest.ui.components.AppSliderHeadStyle
import com.medianest.ui.components.AppSliderThickness
import com.medianest.ui.components.AppSwitch
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.BackdropGlassSurface
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.components.backdropSource
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.ui.components.dismissKeyboardOnOutsideTap
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.ui.auth.AuthViewModel
import com.medianest.ui.auth.UserProfileCard
import com.medianest.util.AppCacheCleaner
import com.medianest.util.InitialIndexingManager
import com.medianest.util.PermissionUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    hiddenFolderDao: HiddenFolderDao,
    mediaStoreRepository: MediaStoreRepository,
    onClearHistory: () -> Unit,
    onClose: () -> Unit,
    backdropState: BackdropBlurState? = null,
    authViewModel: AuthViewModel? = null,
    onOpenAuthScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.medianest.MediaNestApp.instance.analyticsService.logScreenView("SettingsScreen")
    }

    val theme by settingsManager.theme.collectAsState(initial = "DARK")
    val gridGapDp by settingsManager.gridGapDp.collectAsState(initial = 8)
    val gridSizeLevel by settingsManager.gridSizeLevel.collectAsState(initial = 1)
    val roundedCornersEnabled by settingsManager.roundedCornersEnabled.collectAsState(initial = true)
    val glassmorphism by settingsManager.glassmorphismEnabled.collectAsState(initial = true)
    val dynamicAmbientBackground by settingsManager.dynamicAmbientBackground.collectAsState(initial = false)

    val showPlaybackNotification by settingsManager.showPlaybackNotification.collectAsState(initial = true)
    val showVideoNotification by settingsManager.showVideoNotification.collectAsState(initial = true)
    val hwAccelEnabled by settingsManager.hardwareAccelerationEnabled.collectAsState(initial = true)
    val keepScreenOn by settingsManager.keepScreenOn.collectAsState(initial = true)

    val currentPictureMode by settingsManager.pictureMode.collectAsState(initial = "Vivid Color Accent")

    val uninterruptedMode by settingsManager.uninterruptedMode.collectAsState(initial = false)
    val autoResumeOnBluetooth by settingsManager.autoResumeOnBluetooth.collectAsState(initial = false)
    val autoPlayVideoPreviews by settingsManager.autoPlayVideoPreviews.collectAsState(initial = false)
    val autoPlayGifPreviews by settingsManager.autoPlayGifPreviews.collectAsState(initial = false)
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    val anonymousAnalyticsEnabled by settingsManager.anonymousAnalyticsEnabled.collectAsState(initial = true)
    val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = "AUTO")
    val useSurfaceView by settingsManager.useSurfaceView.collectAsState(initial = true)
    val developerModeEnabled by settingsManager.developerModeEnabled.collectAsState(initial = false)

    var showAutoPlayVideoWarning by remember { mutableStateOf(false) }
    var showAutoPlayGifWarning by remember { mutableStateOf(false) }
    var phoneSizeWarningText by remember { mutableStateOf<String?>(null) }
    var showSelectiveCacheCleanDialog by remember { mutableStateOf(false) }
    var showPlayStoreScreenshots by remember { mutableStateOf(false) }

    var versionTapCount by remember { mutableIntStateOf(0) }
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionText = "v${packageInfo.versionName} (${packageInfo.versionCode})"

    /* Selective Hidden Folders State (Commented out - exclude option available in library)
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
    */

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

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        window?.let { w ->
            w.setDimAmount(0.25f)
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                w.setBackgroundBlurRadius(80)
            }
        }
        onDispose {
            window?.let { w ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    w.setBackgroundBlurRadius(0)
                }
            }
        }
    }

    val rootModifier = if (backdropState != null) {
        Modifier.backdropReceiver(
            state = backdropState,
            blurRadius = 28.dp,
            tint = Color(0x7708090C),
            baseColor = Color.Transparent,
            showTopBorder = false
        )
    } else {
        Modifier.background(darkBackgroundGradient)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(rootModifier)
            .dismissKeyboardOnOutsideTap()
    ) {
        // 1. Settings Background Grid Layer (Rendered purely in drawBehind without backdrop recording overhead)
        Box(
            modifier = Modifier
                .fillMaxSize()
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
        )

        // 2. Settings Content & Cards
        val isPhoneScreen = LocalConfiguration.current.screenWidthDp < 600
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = if (isPhoneScreen) 10.dp else 20.dp,
                    vertical = if (isPhoneScreen) 10.dp else 18.dp
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

            // USER ACCOUNT & AUTH PROFILE CARD
            if (authViewModel != null) {
                val authState by authViewModel.authState.collectAsState()
                val syncState by authViewModel.syncState.collectAsState()

                UserProfileCard(
                    authState = authState,
                    syncState = syncState,
                    onOpenAuthScreen = onOpenAuthScreen,
                    onLogout = { authViewModel.logout() },
                    onSyncNow = { authViewModel.triggerSync() }
                )
            }

            // SECTION 1: DISPLAY & INTERFACE
            SettingsGlassCard(title = "DISPLAY & INTERFACE") {
                // Grid Spacing Discrete Slider
                val isPhoneScreen = LocalConfiguration.current.screenWidthDp < 600
                SettingsRowItem(
                    title = "Grid Spacing",
                    subtitle = "Adjust padding gap between media grid tiles",
                    stackedOnPhone = true,
                    control = {
                        Column(
                            horizontalAlignment = if (isPhoneScreen) Alignment.Start else Alignment.End,
                            modifier = if (isPhoneScreen) Modifier.fillMaxWidth() else Modifier.widthIn(max = 240.dp)
                        ) {
                            val gapValues = listOf(0, 4, 8, 12)
                            val currentIndex = gapValues.indexOf(gridGapDp).coerceAtLeast(0)

                            AppSlider(
                                value = currentIndex.toFloat(),
                                onValueChange = { index ->
                                    val selectedGap = gapValues[index.toInt().coerceIn(0, 3)]
                                    scope.launch { settingsManager.setGridGapDp(selectedGap) }
                                },
                                valueRange = 0f..3f,
                                steps = 2,
                                drawTicks = true,
                                headStyle = AppSliderHeadStyle.Bar,
                                thickness = AppSliderThickness.Thick,
                                thumbColor = Color.White.copy(alpha = 0.85f),
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
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
                        val currentScreenWidthDp = LocalConfiguration.current.screenWidthDp
                        Column(
                            horizontalAlignment = if (isPhoneScreen) Alignment.Start else Alignment.End,
                            modifier = if (isPhoneScreen) Modifier.fillMaxWidth() else Modifier.widthIn(max = 240.dp)
                        ) {
                            AnimatedVisibility(
                                visible = phoneSizeWarningText != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Text(
                                    text = phoneSizeWarningText ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF87171),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            val sizeLabels = listOf("Compact", "Standard", "Large", "XL", "XXL")
                            AppSlider(
                                value = gridSizeLevel.toFloat().coerceIn(0f, 4f),
                                onValueChange = { levelFloat ->
                                    val targetLevel = levelFloat.toInt().coerceIn(0, 4)

                                    if (targetLevel == 4 && currentScreenWidthDp < 600) {
                                        if (currentScreenWidthDp >= 480) {
                                            phoneSizeWarningText = "⚠️ XXL size exceeds screen width and is designed for tablets. Switched to XL."
                                            scope.launch { settingsManager.setGridSizeLevel(3) }
                                        } else {
                                            phoneSizeWarningText = "⚠️ XXL size exceeds screen width and is designed for tablets. Switched to Large."
                                            scope.launch { settingsManager.setGridSizeLevel(2) }
                                        }
                                    } else if (targetLevel == 3 && currentScreenWidthDp < 480) {
                                        phoneSizeWarningText = "⚠️ XL size exceeds screen width and is designed for tablets. Switched to Large."
                                        scope.launch { settingsManager.setGridSizeLevel(2) }
                                    } else {
                                        phoneSizeWarningText = null
                                        scope.launch { settingsManager.setGridSizeLevel(targetLevel) }
                                    }
                                },
                                valueRange = 0f..4f,
                                steps = 3,
                                drawTicks = true,
                                headStyle = AppSliderHeadStyle.Bar,
                                thickness = AppSliderThickness.Thick,
                                thumbColor = Color.White.copy(alpha = 0.85f),
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.height(24.dp)
                            )

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                val totalWidth = maxWidth
                                val thumbRadius = 4.dp // Bar headStyle thumbSizeDp.width is 8.dp / 2 = 4.dp
                                val usableWidth = totalWidth - (thumbRadius * 2)

                                sizeLabels.forEachIndexed { index, label ->
                                    val frac = index.toFloat() / (sizeLabels.size - 1)
                                    val centerOffset = thumbRadius + (usableWidth * frac)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (index == gridSizeLevel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (index == gridSizeLevel) Color(0xFF818CF8) else Color(0xFF64748B),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .offset(x = centerOffset)
                                                .layout { measurable, constraints ->
                                                    val placeable = measurable.measure(constraints)
                                                    layout(placeable.width, placeable.height) {
                                                        placeable.placeRelative(-placeable.width / 2, 0)
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )

                // Rounded Grid Tiles
                SettingsRowItem(
                    title = "Rounded Grid Tiles",
                    subtitle = "Apply corner curvature to album & artist tiles",
                    onClick = { scope.launch { settingsManager.setRoundedCornersEnabled(!roundedCornersEnabled) } },
                    control = {
                        AppSwitch(
                            checked = roundedCornersEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setRoundedCornersEnabled(it) } }
                        )
                    }
                )

                // Dynamic Ambient Background
                SettingsRowItem(
                    title = "Dynamic Ambient Background",
                    subtitle = "Reactively extracts vibrant colors and blurred album art from currently playing or visible media. Disable for static gradient spheres with maximum smoothness and battery efficiency.",
                    onClick = { scope.launch { settingsManager.setDynamicAmbientBackground(!dynamicAmbientBackground) } },
                    control = {
                        AppSwitch(
                            checked = dynamicAmbientBackground,
                            onCheckedChange = { scope.launch { settingsManager.setDynamicAmbientBackground(it) } }
                        )
                    }
                )
            }

            // SECTION 2: LIBRARY & FOLDER FILTERS
            SettingsGlassCard(title = "LIBRARY & FOLDER FILTERS") {
                /*
                // Show Hidden Files & Folders (Feature disabled due to MediaStore scoped storage policy)
                SettingsRowItem(
                    title = "Show Hidden Files & Folders",
                    subtitle = "Display files starting with a dot (.) in directory views",
                    onClick = {
                        val next = !showHiddenFiles
                        scope.launch { settingsManager.setShowHiddenFiles(next) }
                        if (next && !PermissionUtils.hasStandardMediaPermissions(context)) {
                            PermissionUtils.openAppSettings(context)
                        }
                    },
                    control = {
                        AppSwitch(
                            checked = showHiddenFiles,
                            onCheckedChange = { checked ->
                                scope.launch { settingsManager.setShowHiddenFiles(checked) }
                                if (checked && !PermissionUtils.hasStandardMediaPermissions(context)) {
                                    PermissionUtils.openAppSettings(context)
                                }
                            }
                        )
                    }
                )
                */

                // Rescan All Media (Separate Reusable Component)
                RescanAllMediaSettingItem(mediaStoreRepository = mediaStoreRepository)

                /*
                // Folders to Hide From Library (Commented out - exclude option available in library)
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
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            folderNamesList.forEach { folderName ->
                                val isHidden = hiddenForType.contains(folderName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x1AFFFFFF))
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                */

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
                    onClick = { scope.launch { settingsManager.setKeepScreenOn(!keepScreenOn) } },
                    control = {
                        AppSwitch(
                            checked = keepScreenOn,
                            onCheckedChange = { scope.launch { settingsManager.setKeepScreenOn(it) } }
                        )
                    }
                )

                // Uninterrupted Mode
                SettingsRowItem(
                    title = "Uninterrupted Mode",
                    subtitle = "Don't pause or duck for notifications. Phone calls will still pause playback.",
                    onClick = { scope.launch { settingsManager.setUninterruptedMode(!uninterruptedMode) } },
                    control = {
                        AppSwitch(
                            checked = uninterruptedMode,
                            onCheckedChange = { scope.launch { settingsManager.setUninterruptedMode(it) } }
                        )
                    }
                )

                // Auto-resume on Bluetooth
                SettingsRowItem(
                    title = "Auto-resume on Bluetooth",
                    subtitle = "Automatically start playback when Bluetooth headphones connect.",
                    onClick = { scope.launch { settingsManager.setAutoResumeOnBluetooth(!autoResumeOnBluetooth) } },
                    control = {
                        AppSwitch(
                            checked = autoResumeOnBluetooth,
                            onCheckedChange = { scope.launch { settingsManager.setAutoResumeOnBluetooth(it) } }
                        )
                    }
                )

                // Auto-Play Video Previews
                SettingsRowItem(
                    title = "Auto-Play Video Previews",
                    subtitle = "Play in-place muted video previews for visible items as you scroll",
                    onClick = {
                        if (!autoPlayVideoPreviews) {
                            showAutoPlayVideoWarning = true
                        } else {
                            scope.launch { settingsManager.setAutoPlayVideoPreviews(false) }
                        }
                    },
                    control = {
                        AppSwitch(
                            checked = autoPlayVideoPreviews,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    showAutoPlayVideoWarning = true
                                } else {
                                    scope.launch { settingsManager.setAutoPlayVideoPreviews(false) }
                                }
                            }
                        )
                    }
                )

                // Auto-Play GIFs
                SettingsRowItem(
                    title = "Auto-Play Animated GIFs",
                    subtitle = "Play animated GIFs in-place for visible items in library",
                    onClick = {
                        if (!autoPlayGifPreviews) {
                            showAutoPlayGifWarning = true
                        } else {
                            scope.launch { settingsManager.setAutoPlayGifPreviews(false) }
                        }
                    },
                    control = {
                        AppSwitch(
                            checked = autoPlayGifPreviews,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    showAutoPlayGifWarning = true
                                } else {
                                    scope.launch { settingsManager.setAutoPlayGifPreviews(false) }
                                }
                            }
                        )
                    }
                )

                // Offline Mode
                SettingsRowItem(
                    title = "Offline Mode",
                    subtitle = "Block all online network calls and lyrics fetching",
                    onClick = { scope.launch { settingsManager.setOfflineMode(!offlineMode) } },
                    control = {
                        AppSwitch(
                            checked = offlineMode,
                            onCheckedChange = { scope.launch { settingsManager.setOfflineMode(it) } }
                        )
                    }
                )

                // Anonymous Analytics & Privacy
                SettingsRowItem(
                    title = "Telemetry & Analytics",
                    subtitle = "Collect device specs, playback metrics, and performance data to improve the app",
                    onClick = { scope.launch { settingsManager.setAnonymousAnalyticsEnabled(!anonymousAnalyticsEnabled) } },
                    control = {
                        AppSwitch(
                            checked = anonymousAnalyticsEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setAnonymousAnalyticsEnabled(it) } }
                        )
                    }
                )

                // Privacy Policy
                SettingsRowItem(
                    title = "Privacy Policy",
                    subtitle = "View MediaNest privacy policy and data practices",
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://QuietByte01.github.io/medianest/"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    control = {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Privacy Policy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // Storage & Media Permissions
                val hasStandard = PermissionUtils.hasStandardMediaPermissions(context)
                SettingsRowItem(
                    title = "Storage & Media Permissions",
                    subtitle = if (hasStandard) "MediaStore access granted (Images, Videos & Audio)" else "Storage permissions required to load media files",
                    stackedOnPhone = true,
                    onClick = { PermissionUtils.openAppSettings(context) },
                    control = {
                        Button(
                            onClick = { PermissionUtils.openAppSettings(context) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (hasStandard) Color(0x2234D399) else Color(0x3338BDF8))
                        ) {
                            Text(
                                text = if (hasStandard) "Granted (Settings)" else "Open Settings",
                                fontSize = 12.sp,
                                color = if (hasStandard) Color(0xFF34D399) else Color(0xFF38BDF8),
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
                    onClick = { scope.launch { settingsManager.setShowPlaybackNotification(!showPlaybackNotification) } },
                    control = {
                        AppSwitch(
                            checked = showPlaybackNotification,
                            onCheckedChange = { scope.launch { settingsManager.setShowPlaybackNotification(it) } }
                        )
                    }
                )

                // Video Playback Notifications
                SettingsRowItem(
                    title = "Video Playback Notifications",
                    subtitle = "Show notification for video background playback",
                    onClick = { scope.launch { settingsManager.setShowVideoNotification(!showVideoNotification) } },
                    control = {
                        AppSwitch(
                            checked = showVideoNotification,
                            onCheckedChange = { scope.launch { settingsManager.setShowVideoNotification(it) } }
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

                // Clean Storage & History
                SettingsRowItem(
                    title = "Clean Storage & History",
                    subtitle = "Clear playback history, search logs, generated thumbnails and cached files",
                    stackedOnPhone = true,
                    control = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showSelectiveCacheCleanDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0x66818CF8)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF818CF8))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Selective Clean", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val app = MediaNestApp.instance
                                        val db = app.database
                                        val analyticsRepo = AnalyticsRepository(
                                            db.analyticsDao(),
                                            mediaStoreRepository,
                                            db.selectiveHiddenFolderDao()
                                        )
                                        AppCacheCleaner.clearAllAppCacheAndHistory(context, db)
                                        settingsManager.invalidateCacheToken()
                                        InitialIndexingManager.resetState()
                                        InitialIndexingManager.startIndexing(
                                            context = context,
                                            mediaStoreRepository = mediaStoreRepository,
                                            analyticsRepository = analyticsRepo,
                                            settingsManager = settingsManager
                                        )
                                        Toast.makeText(context, "All cache cleared! Re-indexing media and generating thumbnails...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                                border = BorderStroke(1.dp, Color(0x66EF4444))
                            ) {
                                Text("Clear All", fontSize = 12.5.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                )
            }

            // Developer Options
            DeveloperSettingsSection(
                settingsManager = settingsManager,
                onDisableDevMode = { versionTapCount = 0 },
                onOpenPlayStoreScreenshots = { showPlayStoreScreenshots = true }
            )

            // Banner Ad at the bottom of Settings
            com.medianest.ads.BannerAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
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

        // Auto-Play Video Previews Confirmation Warning Dialog
        if (showAutoPlayVideoWarning) {
            val shape = RoundedCornerShape(20.dp)
            val isDark = com.medianest.ui.theme.LocalDarkTheme.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showAutoPlayVideoWarning = false },
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.BackdropGlassSurface(
                    shape = shape,
                    enableBlur = true,
                    blurRadius = 24.dp,
                    tint = Color(0x770A0C10),
                    baseColor = Color.Transparent,
                    borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x28000000),
                    borderWidth = 0.5.dp,
                    backdropState = backdropState,
                    modifier = Modifier
                        .widthIn(max = minOf(420.dp, (LocalConfiguration.current.screenWidthDp * 0.92f).dp))
                        .wrapContentHeight()
                        .padding(16.dp)
                        .clickable(enabled = false) {}
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33F59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Auto-Play Video Previews",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "High Battery & CPU Consumption",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { showAutoPlayVideoWarning = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x1AFFFFFF),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Playing live video previews inside the library grid actively runs ExoPlayer video codecs for multiple visible items as you scroll.",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    text = "• Increased device heating & thermal throttling\n• Significantly faster battery discharge\n• High RAM & GPU memory footprint during fast scrolling",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.medianest.ui.components.AppActionButton(
                                text = "Cancel",
                                onClick = { showAutoPlayVideoWarning = false },
                                modifier = Modifier.weight(1f),
                                style = com.medianest.ui.components.AppButtonStyle.Glossy,
                                accentColor = Color(0xFF94A3B8)
                            )
                            com.medianest.ui.components.AppCriticalButton(
                                text = "Enable Anyway",
                                onClick = {
                                    showAutoPlayVideoWarning = false
                                    scope.launch { settingsManager.setAutoPlayVideoPreviews(true) }
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PlayArrow,
                                accentColor = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Auto-Play Animated GIFs Confirmation Warning Dialog
        if (showAutoPlayGifWarning) {
            val shape = RoundedCornerShape(20.dp)
            val isDark = com.medianest.ui.theme.LocalDarkTheme.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showAutoPlayGifWarning = false },
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.BackdropGlassSurface(
                    shape = shape,
                    enableBlur = true,
                    blurRadius = 24.dp,
                    tint = Color(0x770A0C10),
                    baseColor = Color.Transparent,
                    borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x28000000),
                    borderWidth = 0.5.dp,
                    backdropState = backdropState,
                    modifier = Modifier
                        .widthIn(max = minOf(420.dp, (LocalConfiguration.current.screenWidthDp * 0.92f).dp))
                        .wrapContentHeight()
                        .padding(16.dp)
                        .clickable(enabled = false) {}
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33F59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Auto-Play Animated GIFs",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "High RAM & CPU Decoding Load",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { showAutoPlayGifWarning = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x1AFFFFFF),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Animated GIFs continuously decode frame buffers in memory for every visible image tile simultaneously.",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    text = "• CPU spikes when many animated GIFs are on-screen\n• Higher memory usage & garbage collection pauses\n• Can introduce scroll micro-stutters on large galleries",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.medianest.ui.components.AppActionButton(
                                text = "Cancel",
                                onClick = { showAutoPlayGifWarning = false },
                                modifier = Modifier.weight(1f),
                                style = com.medianest.ui.components.AppButtonStyle.Glossy,
                                accentColor = Color(0xFF94A3B8)
                            )
                            com.medianest.ui.components.AppCriticalButton(
                                text = "Enable Anyway",
                                onClick = {
                                    showAutoPlayGifWarning = false
                                    scope.launch { settingsManager.setAutoPlayGifPreviews(true) }
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Animation,
                                accentColor = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }
    }

    // Play Store Screenshots Dialog
    if (showPlayStoreScreenshots) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPlayStoreScreenshots = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.medianest.ui.playstore.PlayStoreExportScreen(
                onBack = { showPlayStoreScreenshots = false }
            )
        }
    }

    // Selective Cache Clean Dialog
    if (showSelectiveCacheCleanDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearThumbnails by remember { mutableStateOf(true) }
        var clearNetworkMetadata by remember { mutableStateOf(true) }
        var clearTempFiles by remember { mutableStateOf(true) }
        var isCleaningProgress by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isCleaningProgress) showSelectiveCacheCleanDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Selective Cache Clean", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Select the specific cache categories you would like to remove:",
                        fontSize = 13.sp,
                        color = Color(0xFFC0C5D0)
                    )

                    // Option 1: Playback & Search History
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { clearHistory = !clearHistory }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = clearHistory,
                            onCheckedChange = { clearHistory = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF818CF8))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Playback & Search History", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Recently played tracks, video positions, and search logs", fontSize = 11.5.sp, color = Color(0xFF8E95A5))
                        }
                    }

                    // Option 2: Image & Video Thumbnails
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { clearThumbnails = !clearThumbnails }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = clearThumbnails,
                            onCheckedChange = { clearThumbnails = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF818CF8))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Image & Video Thumbnails", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Coil disk image cache and WebP video frame thumbnails", fontSize = 11.5.sp, color = Color(0xFF8E95A5))
                        }
                    }

                    // Option 3: Online Network & Artist Metadata
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { clearNetworkMetadata = !clearNetworkMetadata }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = clearNetworkMetadata,
                            onCheckedChange = { clearNetworkMetadata = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF818CF8))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Online Network & Artist Metadata", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Wikipedia artist summaries, Unsplash portraits, and geocoding", fontSize = 11.5.sp, color = Color(0xFF8E95A5))
                        }
                    }

                    // Option 4: Temporary Buffers & Debug Logs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { clearTempFiles = !clearTempFiles }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = clearTempFiles,
                            onCheckedChange = { clearTempFiles = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF818CF8))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Temporary Buffers & Debug Logs", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("FFmpeg temporary frame dumps and diagnostic analysis reports", fontSize = 11.5.sp, color = Color(0xFF8E95A5))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCleaningProgress = true
                        scope.launch {
                            val db = MediaNestApp.instance.database
                            AppCacheCleaner.clearSelectedCache(
                                context = context,
                                db = db,
                                clearHistory = clearHistory,
                                clearThumbnails = clearThumbnails,
                                clearNetworkMetadata = clearNetworkMetadata,
                                clearTempFiles = clearTempFiles
                            )
                            if (clearThumbnails) {
                                settingsManager.invalidateCacheToken()
                            }
                            isCleaningProgress = false
                            showSelectiveCacheCleanDialog = false
                            Toast.makeText(context, "Selected cache categories cleared!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isCleaningProgress && (clearHistory || clearThumbnails || clearNetworkMetadata || clearTempFiles),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8))
                ) {
                    if (isCleaningProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Clean Selected Cache")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSelectiveCacheCleanDialog = false },
                    enabled = !isCleaningProgress
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E222D),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun SettingsGlassCard(
    title: String,
    backdropState: BackdropBlurState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
    val isPhoneScreen = LocalConfiguration.current.screenWidthDp < 600
    val cardShape = RoundedCornerShape(22.dp)

    // 90% opacity black background fill
    val cardGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.70f),
                Color(0xE60A0C10)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x99FFFFFF), // Bright top specular highlight
                Color(0x7DFFFFFF),
                Color(0x60FFFFFF)  // Frosted white glass
            )
        )
    }

    // iOS iPhone fluid glass 3D specular rim border
    val borderGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x66FFFFFF), // Crisp top rim highlight
                Color(0x26FFFFFF),
                Color(0x0FFFFFFF)  // Soft bottom rim
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xB3FFFFFF),
                Color(0x40FFFFFF),
                Color(0x1F000000)
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isPhoneScreen) 1f else 0.82f)
                .shadow(
                    elevation = if (isDark) 12.dp else 6.dp,
                    shape = cardShape,
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.08f)
                )
                .clip(cardShape)
                .background(cardGradient)
                .border(
                    width = 1.dp,
                    brush = borderGradient,
                    shape = cardShape
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isPhoneScreen) 16.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(if (isPhoneScreen) 16.dp else 20.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFA0A7B8) else Color(0xFF64748B),
                    letterSpacing = 1.0.sp
                )
                content()
            }
        }
    }
}

@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String,
    stackedOnPhone: Boolean = false,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isPhoneScreen = configuration.screenWidthDp < 600

    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    } else {
        Modifier.fillMaxWidth()
    }

    if (isPhoneScreen && stackedOnPhone) {
        Column(
            modifier = rowModifier,
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
            modifier = rowModifier,
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
