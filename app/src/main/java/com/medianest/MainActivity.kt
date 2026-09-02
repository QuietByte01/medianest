package com.medianest

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.medianest.util.Logger
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.audioplayer.AudioPlayerScreen
import com.medianest.ui.components.AppLockDialog
import com.medianest.ui.components.backdropSource
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.ui.library.LibraryScreen
import com.medianest.ui.onboarding.FirstLaunchIndexingScreen
import com.medianest.ui.quickview.QuickViewActivity
import com.medianest.ui.settings.SettingsScreen
import com.medianest.ui.theme.MediaNestTheme
import com.medianest.ui.videoplayer.VideoPlayerActivity
import com.medianest.util.InitialIndexingManager
import com.medianest.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mediaStoreRepository by lazy { MediaStoreRepository(applicationContext) }
    private val exoPlayerManager by lazy { ExoPlayerManager.getInstance(applicationContext) }
    private val networkRepository by lazy { com.medianest.data.repository.NetworkRepository(subtitleCacheDao = MediaNestApp.instance.database.subtitleCacheDao()) }
    private val analyticsRepository by lazy { com.medianest.data.repository.AnalyticsRepository(MediaNestApp.instance.database.analyticsDao(), mediaStoreRepository, MediaNestApp.instance.database.selectiveHiddenFolderDao()) }

    private val pendingScreenState = MutableStateFlow<String?>(null)
    private val targetMainTab = MutableStateFlow(0)
    private val targetAudioSubTab = MutableStateFlow(0)
    private val targetAudioAlbum = MutableStateFlow<String?>(null)
    private val targetAudioArtist = MutableStateFlow<String?>(null)
    private val targetAudioFolder = MutableStateFlow<String?>(null)
    private val targetVideoFolder = MutableStateFlow<String?>(null)
    private val targetImageFolder = MutableStateFlow<String?>(null)
    private val targetMediaItemUri = MutableStateFlow<String?>(null)

    override fun onResume() {
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_HDR
        }
        
        // Edge-to-edge appearance with transparent system bars (ensures immediate edge swipe gestures)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        requestMediaPermissions()

        val db = MediaNestApp.instance.database
        val settingsManager = MediaNestApp.instance.settingsManager

        setContent {
            val themeMode by settingsManager.theme.collectAsState(initial = "DARK")

            MediaNestTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val autoPlayVideoPreviews by settingsManager.autoPlayVideoPreviews.collectAsState(initial = true)
                    val autoPlayGifPreviews by settingsManager.autoPlayGifPreviews.collectAsState(initial = true)

                    CompositionLocalProvider(
                        com.medianest.ui.components.LocalAutoPlayVideoPreviews provides autoPlayVideoPreviews,
                        com.medianest.ui.components.LocalAutoPlayGifPreviews provides autoPlayGifPreviews
                    ) {
                        var imagesList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                        var videosList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                        var audioList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

                        var videoCategories by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
                        var audioPlaylists by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
                        var imageCollections by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }

                        val gridGapDp by settingsManager.gridGapDp.collectAsState(initial = 8)
                        val gridSizeLevel by settingsManager.gridSizeLevel.collectAsState(initial = 1)
                        val cornerRadiusDp by settingsManager.gridCornerRadiusDp.collectAsState(initial = 8)
                        val roundedCornersEnabled by settingsManager.roundedCornersEnabled.collectAsState(initial = true)

                        val appLockEnabled by settingsManager.appLockEnabled.collectAsState(initial = false)
                        val appLockPin by settingsManager.appLockPin.collectAsState(initial = "")
                        val hideFromRecents by settingsManager.hideFromRecents.collectAsState(initial = false)
                        val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
                        val hiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())
                        val enableAnalyticsTab by settingsManager.enableAnalyticsTab.collectAsState(initial = true)
                        val isFirstLaunchCompleted by settingsManager.isFirstLaunchCompleted.collectAsState(initial = true)
                        val indexingState by InitialIndexingManager.progressState.collectAsState()
                        val scope = rememberCoroutineScope()

                        LaunchedEffect(isFirstLaunchCompleted, hiddenFolders) {
                            if (!isFirstLaunchCompleted) {
                                InitialIndexingManager.startIndexing(
                                    context = applicationContext,
                                    mediaStoreRepository = mediaStoreRepository,
                                    analyticsRepository = analyticsRepository,
                                    settingsManager = settingsManager,
                                    hiddenFolders = hiddenFolders
                                )
                            }
                        }

                        val analyticsSnapshot by analyticsRepository.snapshot.collectAsState(initial = null)
                    val analyticsFormatStats by analyticsRepository.formatStats.collectAsState(initial = emptyList())
                    var isAnalyticsRefreshing by remember { mutableStateOf(false) }
                    var isScanLoading by remember { mutableStateOf(true) }
                    var isScanningHidden by remember { mutableStateOf(false) }

                    var isUnlocked by remember { mutableStateOf(!appLockEnabled) }
                    var currentScreen by rememberSaveable { mutableStateOf("LIBRARY") } // LIBRARY, SETTINGS, AUDIO_PLAYER

                    LaunchedEffect(currentScreen) {
                        Logger.i("MainActivity", "Screen changed to: $currentScreen")
                    }

                    val pendingScreen by pendingScreenState.collectAsState()
                    LaunchedEffect(pendingScreen) {
                        pendingScreen?.let { screen ->
                            currentScreen = screen
                            pendingScreenState.value = null
                        }
                    }

                    val mainTab by targetMainTab.collectAsState()
                    val audioSub by targetAudioSubTab.collectAsState()
                    val audioAlb by targetAudioAlbum.collectAsState()
                    val audioArt by targetAudioArtist.collectAsState()
                    val audioFld by targetAudioFolder.collectAsState()
                    val videoFld by targetVideoFolder.collectAsState()
                    val imageFld by targetImageFolder.collectAsState()
                    val mediaTargetUri by targetMediaItemUri.collectAsState()

                    // Prevent back press from exiting app when in sub-screens
                    BackHandler(enabled = currentScreen != "LIBRARY") {
                        currentScreen = "LIBRARY"
                    }

                    // FLAG_SECURE check
                    DisposableEffect(hideFromRecents) {
                        if (hideFromRecents) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                        onDispose {}
                    }

                    // Observe hidden folders, MediaStore changes, and Room metadata cache (Fast immediate load + reactive updates)
                    LaunchedEffect(showHiddenFiles) {
                        combine(
                            db.selectiveHiddenFolderDao().getAllHiddenFolders(),
                            settingsManager.hiddenFolders,
                            mediaStoreRepository.observeMediaStoreChanges(),
                            db.metadataCacheDao().getAllCacheFlow()
                        ) { selectiveHidden, appHiddenFolders, _, _ ->
                            val selectiveImage = selectiveHidden.filter { it.mediaType == "IMAGE" && it.isHidden }
                                .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()
                            val selectiveVideo = selectiveHidden.filter { it.mediaType == "VIDEO" && it.isHidden }
                                .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()
                            val selectiveAudio = selectiveHidden.filter { it.mediaType == "AUDIO" && it.isHidden }
                                .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()

                            Triple(selectiveImage + appHiddenFolders, selectiveVideo + appHiddenFolders, selectiveAudio + appHiddenFolders)
                        }.collectLatest { (imageHiddenPaths, videoHiddenPaths, audioHiddenPaths) ->
                            Logger.i("MainActivity", "DATA REFRESH START: showHidden=$showHiddenFiles")
                            // 1. Fast immediate fetch from MediaStore (<50ms)
                            if (imagesList.isEmpty() && videosList.isEmpty() && audioList.isEmpty()) {
                                isScanLoading = true
                            }
                            val msImages = mediaStoreRepository.getImages(imageHiddenPaths, showHidden = true, includeFileSystemScan = false)
                            val msVideos = mediaStoreRepository.getVideos(videoHiddenPaths, showHidden = true, includeFileSystemScan = false)
                            val msAudio = mediaStoreRepository.getAudio(audioHiddenPaths, showHidden = true, includeFileSystemScan = false)

                            // Seamlessly merge without dropping previously scanned hidden files, while pruning deleted/renamed paths
                            imagesList = (imagesList.filter { it.isHidden && (it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists())) } + msImages).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                            videosList = (videosList.filter { it.isHidden && (it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists())) } + msVideos).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                            audioList = (audioList.filter { it.isHidden && (it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists())) } + msAudio).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                            isScanLoading = false
                            
                            Logger.i("MainActivity", "MediaStore Load DONE: imgs=${imagesList.size}, vids=${videosList.size}, audio=${audioList.size}")

                            // 2. Background filesystem scan for hidden & excluded folders (only show full spinner if nothing in memory)
                            val hasHiddenInMemory = imagesList.any { it.isHidden } || videosList.any { it.isHidden } || audioList.any { it.isHidden }
                            if (!hasHiddenInMemory) {
                                isScanningHidden = true
                            }
                            Logger.i("MainActivity", "Hidden File Scan STARTING...")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                kotlinx.coroutines.delay(100) // Yield to allow initial compose of categories and filters
                                val hiddenImages = mediaStoreRepository.scanHiddenMedia(com.medianest.data.db.MediaType.IMAGE, imageHiddenPaths).filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) }
                                val hiddenVideos = mediaStoreRepository.scanHiddenMedia(com.medianest.data.db.MediaType.VIDEO, videoHiddenPaths).filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) }
                                val hiddenAudio = mediaStoreRepository.scanHiddenMedia(com.medianest.data.db.MediaType.AUDIO, audioHiddenPaths).filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) }

                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Logger.i("MainActivity", "Hidden File Scan COMPLETE: imgs=${hiddenImages.size}, vids=${hiddenVideos.size}, audio=${hiddenAudio.size}")
                                    if (hiddenImages.isNotEmpty()) {
                                        imagesList = (imagesList.filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) } + hiddenImages).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                                    }
                                    if (hiddenVideos.isNotEmpty()) {
                                        videosList = (videosList.filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) } + hiddenVideos).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                                    }
                                    if (hiddenAudio.isNotEmpty()) {
                                        audioList = (audioList.filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) } + hiddenAudio).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                                    }
                                    isScanningHidden = false
                                    Logger.i("MainActivity", "Total Merged List: imgs=${imagesList.size}, vids=${videosList.size}, audio=${audioList.size}")
                                }
                            }
                        }
                    }

                    // Auto background scan for analytics (debounced to avoid circular DB update loops)
                    LaunchedEffect(imagesList.size, videosList.size, audioList.size) {
                        if (imagesList.isNotEmpty() || videosList.isNotEmpty() || audioList.isNotEmpty()) {
                            kotlinx.coroutines.delay(2000)
                            analyticsRepository.scanAndSaveAnalytics(imagesList, videosList, audioList)
                        }
                    }

                    // Observe categories from Room
                    LaunchedEffect(Unit) {
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val existingVideoCats = db.categoryDao().getCategoriesByTypeSync("VIDEO")
                                val defaultNames = listOf("Training Videos", "Birthday Parties", "Travel & Vlogs")
                                val existingNames = existingVideoCats.map { it.name.lowercase() }.toSet()
                                if ("training videos" !in existingNames) {
                                    db.categoryDao().insertCategory(MediaCategory(name = "Training Videos", type = "VIDEO", iconName = "school"))
                                }
                                if ("birthday parties" !in existingNames) {
                                    db.categoryDao().insertCategory(MediaCategory(name = "Birthday Parties", type = "VIDEO", iconName = "cake"))
                                }
                                if ("travel & vlogs" !in existingNames) {
                                    db.categoryDao().insertCategory(MediaCategory(name = "Travel & Vlogs", type = "VIDEO", iconName = "flight"))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        lifecycleScope.launch {
                            db.categoryDao().getCategoriesByType("VIDEO").collectLatest {
                                videoCategories = it
                            }
                        }
                        lifecycleScope.launch {
                            db.categoryDao().getCategoriesByType("AUDIO").collectLatest {
                                audioPlaylists = it
                            }
                        }
                        lifecycleScope.launch {
                            db.categoryDao().getCategoriesByType("IMAGE").collectLatest {
                                imageCollections = it
                            }
                        }
                    }

                    val settingsBackdropState = rememberBackdropBlurState()

                    if (!isFirstLaunchCompleted) {
                        FirstLaunchIndexingScreen(
                            progressState = indexingState,
                            onSkip = {
                                scope.launch {
                                    settingsManager.setFirstLaunchCompleted(true)
                                }
                            }
                        )
                    } else if (appLockEnabled && !isUnlocked && appLockPin.isNotEmpty()) {
                        AppLockDialog(
                            correctPin = appLockPin,
                            onUnlocked = { isUnlocked = true }
                        )
                    } else {
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.medianest.ui.components.LocalBackdropState provides settingsBackdropState
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Persistent Base Library Screen (never destroyed on navigation to Settings/Player)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .backdropSource(
                                            state = settingsBackdropState,
                                            backgroundColor = MaterialTheme.colorScheme.background
                                        )
                                ) {
                                LibraryScreen(
                                imagesList = imagesList,
                                videosList = videosList,
                                audioList = audioList,
                                videoCategories = videoCategories,
                                audioPlaylists = audioPlaylists,
                                imageCollections = imageCollections,
                                gridGapDp = gridGapDp,
                                gridSizeLevel = gridSizeLevel,
                                cornerRadiusDp = cornerRadiusDp,
                                roundedCornersEnabled = roundedCornersEnabled,
                                enableAnalyticsTab = enableAnalyticsTab,
                                isLoading = isScanLoading,
                                isScanningHidden = isScanningHidden,
                                analyticsSnapshot = analyticsSnapshot,
                                analyticsFormatStats = analyticsFormatStats,
                                isAnalyticsRefreshing = isAnalyticsRefreshing,
                                onRefreshAnalytics = {
                                    lifecycleScope.launch {
                                        isAnalyticsRefreshing = true
                                        analyticsRepository.refreshAnalytics(showHiddenFiles)
                                        isAnalyticsRefreshing = false
                                    }
                                },
                                onRescanHiddenMedia = {
                                    lifecycleScope.launch {
                                        isScanningHidden = true
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.medianest.data.repository.MediaStoreRepository.clearHiddenMediaCache(this@MainActivity)
                                            val hiddenImages = mediaStoreRepository.scanHiddenMedia(com.medianest.data.db.MediaType.IMAGE, emptySet(), forceRescan = true).filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) }
                                            val hiddenVideos = mediaStoreRepository.scanHiddenMedia(com.medianest.data.db.MediaType.VIDEO, emptySet(), forceRescan = true).filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) }
                                            val hiddenAudio = mediaStoreRepository.scanHiddenMedia(com.medianest.data.db.MediaType.AUDIO, emptySet(), forceRescan = true).filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) }

                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                imagesList = (imagesList.filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) } + hiddenImages).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                                                videosList = (videosList.filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) } + hiddenVideos).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                                                audioList = (audioList.filter { it.uri.scheme != "file" || (it.uri.path != null && java.io.File(it.uri.path!!).exists()) } + hiddenAudio).distinctBy { if (it.size > 0) "${it.title.substringBeforeLast('.').lowercase().trim()}_${it.size}_${it.bucketName}" else it.id.toString() }
                                                isScanningHidden = false
                                                android.widget.Toast.makeText(this@MainActivity, "Hidden & excluded folders refreshed!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                exoPlayerManager = exoPlayerManager,
                                initialTab = mainTab,
                                audioSubTab = audioSub,
                                audioAlbum = audioAlb,
                                audioArtist = audioArt,
                                audioFolder = audioFld,
                                initialVideoFolder = videoFld,
                                initialImageFolder = imageFld,
                                targetMediaUri = mediaTargetUri,
                                onOpenQuickView = { item, currentList ->
                                    val index = currentList.indexOfFirst { it.uri == item.uri }
                                    com.medianest.ui.quickview.QuickViewActivity.activeList = currentList
                                    val intent = Intent(this@MainActivity, QuickViewActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        setDataAndType(item.uri, item.mimeType)
                                        putExtra("start_index", if (index >= 0) index else 0)
                                    }
                                    val options = ActivityOptionsCompat.makeCustomAnimation(
                                        this@MainActivity,
                                        R.anim.viewer_open_enter,
                                        R.anim.viewer_open_exit
                                    )
                                    startActivity(intent, options.toBundle())
                                },
                                onOpenVideoPlayer = { item, currentList, contextTitle ->
                                    if (item.mimeType.contains("gif", ignoreCase = true)) {
                                        val intent = Intent(this@MainActivity, QuickViewActivity::class.java).apply {
                                            action = Intent.ACTION_VIEW
                                            setDataAndType(item.uri, item.mimeType)
                                        }
                                        val options = ActivityOptionsCompat.makeCustomAnimation(
                                            this@MainActivity,
                                            R.anim.viewer_open_enter,
                                            R.anim.viewer_open_exit
                                        )
                                        startActivity(intent, options.toBundle())
                                    } else {
                                        val playlist = if (!currentList.isNullOrEmpty()) {
                                            currentList
                                        } else {
                                            val currentFolder = item.relativePath ?: item.bucketName ?: ""
                                            val filtered = videosList.filter { 
                                                (it.relativePath ?: it.bucketName ?: "") == currentFolder 
                                            }
                                            if (filtered.isNotEmpty()) filtered else videosList
                                        }
                                        val index = playlist.indexOfFirst { it.uri == item.uri }
                                        val effectiveTitle = contextTitle ?: (item.bucketName ?: item.relativePath?.trim('/')?.substringAfterLast('/') ?: "Videos")
                                        
                                        com.medianest.ui.videoplayer.VideoPlayerActivity.activeList = playlist
                                        com.medianest.ui.videoplayer.VideoPlayerActivity.activeContextTitle = effectiveTitle
                                        val intent = Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                                            putExtra("media_uri", item.uri.toString())
                                            putExtra("media_title", item.title)
                                            putExtra("mime_type", item.mimeType)
                                            putExtra("context_title", effectiveTitle)
                                            putExtra("start_index", if (index >= 0) index else 0)
                                        }
                                        val options = ActivityOptionsCompat.makeCustomAnimation(
                                            this@MainActivity,
                                            R.anim.viewer_open_enter,
                                            R.anim.viewer_open_exit
                                        )
                                        startActivity(intent, options.toBundle())
                                    }
                                },
                                onOpenAudioPlayer = { activeTab ->
                                    targetMainTab.value = activeTab
                                    currentScreen = "AUDIO_PLAYER"
                                },
                                onOpenSettings = { currentScreen = "SETTINGS" },
                                onCreateCategory = { name, type, iconName ->
                                    lifecycleScope.launch {
                                        db.categoryDao().insertCategory(
                                            MediaCategory(name = name, type = type, iconName = iconName)
                                        )
                                    }
                                },
                                onCreateImageCollection = { name, folders, coverUri ->
                                    lifecycleScope.launch {
                                        val catId = db.categoryDao().insertCategory(
                                            MediaCategory(name = name, type = "IMAGE", coverUri = coverUri)
                                        )
                                        val refs = folders.map { CategoryMediaCrossRef(categoryId = catId, mediaUri = it) }
                                        db.categoryDao().insertCategoryCrossRefs(refs)
                                    }
                                },
                                onUpdateImageCollection = { catId, name, folders, coverUri ->
                                    lifecycleScope.launch {
                                        db.categoryDao().updateCategory(
                                            MediaCategory(id = catId, name = name, type = "IMAGE", coverUri = coverUri)
                                        )
                                        db.categoryDao().clearCategoryMedia(catId)
                                        val refs = folders.map { CategoryMediaCrossRef(categoryId = catId, mediaUri = it) }
                                        db.categoryDao().insertCategoryCrossRefs(refs)
                                    }
                                },
                                onDeleteImageCollection = { catId ->
                                    lifecycleScope.launch {
                                        db.categoryDao().deleteCategoryById(catId)
                                        db.categoryDao().clearCategoryMedia(catId)
                                    }
                                },
                                isSettingsOpen = currentScreen == "SETTINGS"
                            )
                            }

                            // Settings Overlay Screen
                            AnimatedVisibility(
                                visible = currentScreen == "SETTINGS",
                                enter = fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 6 },
                                exit = fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 6 }
                            ) {
                                SettingsScreen(
                                    settingsManager = settingsManager,
                                    hiddenFolderDao = db.hiddenFolderDao(),
                                    mediaStoreRepository = mediaStoreRepository,
                                    onClearHistory = {
                                        lifecycleScope.launch {
                                            db.playbackStateDao().clearAllHistory()
                                            db.metadataCacheDao().clearCache()
                                        }
                                    },
                                    onClose = { currentScreen = "LIBRARY" },
                                    backdropState = settingsBackdropState
                                )
                            }

                            // Audio Player Overlay Screen
                            AnimatedVisibility(
                                visible = currentScreen == "AUDIO_PLAYER",
                                enter = slideInVertically(animationSpec = tween(340, easing = FastOutSlowInEasing)) { height -> height } + fadeIn(animationSpec = tween(260)),
                                exit = slideOutVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { height -> height } + fadeOut(animationSpec = tween(240))
                            ) {
                                AudioPlayerScreen(
                                    playerManager = exoPlayerManager,
                                    networkRepository = networkRepository,
                                    onClose = { currentScreen = "LIBRARY" },
                                    onOpenAlbum = { album ->
                                        targetMainTab.value = if (enableAnalyticsTab) 3 else 2
                                        targetAudioSubTab.value = 3
                                        targetAudioAlbum.value = album
                                        currentScreen = "LIBRARY"
                                    },
                                    onOpenArtist = { artist ->
                                        targetMainTab.value = if (enableAnalyticsTab) 3 else 2
                                        targetAudioSubTab.value = 4
                                        targetAudioArtist.value = artist
                                        currentScreen = "LIBRARY"
                                    },
                                    onOpenFolder = { folder, targetUri ->
                                        targetMainTab.value = if (enableAnalyticsTab) 3 else 2
                                        targetAudioSubTab.value = 5
                                        targetAudioFolder.value = folder
                                        targetMediaItemUri.value = targetUri
                                        currentScreen = "LIBRARY"
                                    },
                                    onOpenSettings = { currentScreen = "SETTINGS" },
                                    allAudioItems = audioList,
                                    showHidden = showHiddenFiles,
                                    hiddenFolders = hiddenFolders
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }

    private fun requestMediaPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // RECORD_AUDIO is required by the Android Visualizer API to attach to any audio session.
        // Without it, Visualizer creation is blocked and all FFT-based visualizers show nothing.
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), 100)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !com.medianest.util.PermissionUtils.hasAllFilesAccess()) {
            com.medianest.util.PermissionUtils.openStorageAccessSettings(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !com.medianest.util.PermissionUtils.hasAllFilesAccess()) {
                com.medianest.util.PermissionUtils.openStorageAccessSettings(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_AUDIO_PLAYER" || intent?.getStringExtra("open_screen") == "AUDIO_PLAYER") {
            pendingScreenState.value = "AUDIO_PLAYER"
        } else if (intent?.getStringExtra("open_screen") == "SETTINGS") {
            pendingScreenState.value = "SETTINGS"
        } else if (intent?.getStringExtra("open_screen") == "VIDEOS_FOLDER") {
            val folder = intent.getStringExtra("folder_name")
            val mediaUri = intent.getStringExtra("target_media_uri")
            targetVideoFolder.value = folder
            targetMediaItemUri.value = mediaUri
            targetMainTab.value = 2 // Videos tab
            pendingScreenState.value = "LIBRARY"
        } else if (intent?.getStringExtra("open_screen") == "IMAGES_FOLDER") {
            val folder = intent.getStringExtra("folder_name")
            val mediaUri = intent.getStringExtra("target_media_uri")
            targetImageFolder.value = folder
            targetMediaItemUri.value = mediaUri
            targetMainTab.value = 1 // Images tab
            pendingScreenState.value = "LIBRARY"
        } else if (intent?.getStringExtra("open_screen") == "AUDIO_FOLDER") {
            val folder = intent.getStringExtra("folder_name")
            val mediaUri = intent.getStringExtra("target_media_uri")
            targetAudioFolder.value = folder
            targetMediaItemUri.value = mediaUri
            targetAudioSubTab.value = 5
            targetMainTab.value = 3 // Audio tab
            pendingScreenState.value = "LIBRARY"
        }
    }

    override fun onStop() {
        super.onStop()
        // If we are playing a video, don't pause when activity stops (might be transitioning to VideoPlayerActivity)
        if (exoPlayerManager.playerState.value.currentItem?.type == com.medianest.data.db.MediaType.VIDEO) return
        val state = exoPlayerManager.playerState.value
        val currentItem = state.currentItem
        val player = exoPlayerManager.exoPlayer
        if (currentItem != null && player != null && player.isPlaying && !isFinishing && !isChangingConfigurations) {
            val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.medianest.data.db.MediaType.VIDEO
            val bgPlayEnabled = if (isVideo) state.isVideoBackgroundPlayEnabled else state.isAudioBackgroundPlayEnabled
            
            if (!bgPlayEnabled) {
                player.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val state = exoPlayerManager.playerState.value
            if (!state.isAudioBackgroundPlayEnabled || !state.isPlaying) {
                exoPlayerManager.release()
            }
        }
    }
}
