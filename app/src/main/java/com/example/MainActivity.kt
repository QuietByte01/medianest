package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.db.CategoryMediaCrossRef
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.data.repository.MediaStoreRepository
import com.example.player.ExoPlayerManager
import com.example.ui.audioplayer.AudioPlayerScreen
import com.example.ui.components.AppLockDialog
import com.example.ui.library.LibraryScreen
import com.example.ui.quickview.QuickViewActivity
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MediaNestTheme
import com.example.ui.videoplayer.VideoPlayerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mediaStoreRepository by lazy { MediaStoreRepository(applicationContext) }
    private val exoPlayerManager by lazy { ExoPlayerManager.getInstance(applicationContext) }
    private val networkRepository by lazy { com.example.data.repository.NetworkRepository() }
    private val analyticsRepository by lazy { com.example.data.repository.AnalyticsRepository(MediaNestApp.instance.database.analyticsDao(), mediaStoreRepository, MediaNestApp.instance.database.selectiveHiddenFolderDao()) }

    private val pendingScreenState = MutableStateFlow<String?>(null)
    private val targetMainTab = MutableStateFlow(0)
    private val targetAudioSubTab = MutableStateFlow(0)
    private val targetAudioAlbum = MutableStateFlow<String?>(null)
    private val targetAudioArtist = MutableStateFlow<String?>(null)
    private val targetAudioFolder = MutableStateFlow<String?>(null)
    private val targetVideoFolder = MutableStateFlow<String?>(null)
    private val targetImageFolder = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
                    var imagesList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                    var videosList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                    var audioList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

                    var videoCategories by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
                    var audioPlaylists by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
                    var imageCollections by remember { mutableStateOf<List<MediaCategory>>(emptyList()) }
                    var categoryCrossRefs by remember { mutableStateOf<List<CategoryMediaCrossRef>>(emptyList()) }

                    val gridGapDp by settingsManager.gridGapDp.collectAsState(initial = 8)
                    val gridSizeLevel by settingsManager.gridSizeLevel.collectAsState(initial = 1)
                    val cornerRadiusDp by settingsManager.gridCornerRadiusDp.collectAsState(initial = 8)
                    val roundedCornersEnabled by settingsManager.roundedCornersEnabled.collectAsState(initial = true)

                    val appLockEnabled by settingsManager.appLockEnabled.collectAsState(initial = false)
                    val appLockPin by settingsManager.appLockPin.collectAsState(initial = "")
                    val hideFromRecents by settingsManager.hideFromRecents.collectAsState(initial = false)
                    val showHiddenFiles by settingsManager.showHiddenFiles.collectAsState(initial = false)
                    val enableAnalyticsTab by settingsManager.enableAnalyticsTab.collectAsState(initial = true)

                    val analyticsSnapshot by analyticsRepository.snapshot.collectAsState(initial = null)
                    val analyticsFormatStats by analyticsRepository.formatStats.collectAsState(initial = emptyList())
                    var isAnalyticsRefreshing by remember { mutableStateOf(false) }
                    var isScanLoading by remember { mutableStateOf(true) }

                    var isUnlocked by remember { mutableStateOf(!appLockEnabled) }
                    var currentScreen by rememberSaveable { mutableStateOf("LIBRARY") } // LIBRARY, SETTINGS, AUDIO_PLAYER

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

                    // Observe hidden folders and scan MediaStore
                    LaunchedEffect(showHiddenFiles) {
                        combine(
                            db.selectiveHiddenFolderDao().getAllHiddenFolders(),
                            settingsManager.hiddenFolders,
                            mediaStoreRepository.observeMediaStoreChanges()
                        ) { selectiveHidden, appHiddenFolders, _ ->
                            val selectiveImage = selectiveHidden.filter { it.mediaType == "IMAGE" && it.isHidden }
                                .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()
                            val selectiveVideo = selectiveHidden.filter { it.mediaType == "VIDEO" && it.isHidden }
                                .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()
                            val selectiveAudio = selectiveHidden.filter { it.mediaType == "AUDIO" && it.isHidden }
                                .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()

                            Triple(selectiveImage + appHiddenFolders, selectiveVideo + appHiddenFolders, selectiveAudio + appHiddenFolders)
                        }.collectLatest { (imageHiddenPaths, videoHiddenPaths, audioHiddenPaths) ->
                            isScanLoading = true
                            imagesList = mediaStoreRepository.getImages(imageHiddenPaths, showHidden = showHiddenFiles)
                            videosList = mediaStoreRepository.getVideos(videoHiddenPaths, showHidden = showHiddenFiles)
                            audioList = mediaStoreRepository.getAudio(audioHiddenPaths, showHidden = showHiddenFiles)
                            isScanLoading = false
                        }
                    }

                    // Auto background scan for analytics
                    LaunchedEffect(imagesList, videosList, audioList) {
                        analyticsRepository.scanAndSaveAnalytics(imagesList, videosList, audioList)
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
                        lifecycleScope.launch {
                            db.categoryDao().getAllCrossRefs().collectLatest {
                                categoryCrossRefs = it
                            }
                        }
                    }

                    if (appLockEnabled && !isUnlocked && appLockPin.isNotEmpty()) {
                        AppLockDialog(
                            correctPin = appLockPin,
                            onUnlocked = { isUnlocked = true }
                        )
                    } else {
                        AnimatedContent(
                            targetState = currentScreen,
                            label = "ScreenTransition",
                            transitionSpec = {
                                if (targetState == "AUDIO_PLAYER") {
                                    (slideInVertically(animationSpec = tween(360, easing = FastOutSlowInEasing)) { height -> height } + fadeIn(animationSpec = tween(280)))
                                        .togetherWith(fadeOut(animationSpec = tween(280)))
                                } else if (initialState == "AUDIO_PLAYER") {
                                    (fadeIn(animationSpec = tween(280)))
                                        .togetherWith(slideOutVertically(animationSpec = tween(360, easing = FastOutSlowInEasing)) { height -> height } + fadeOut(animationSpec = tween(280)))
                                } else {
                                    (fadeIn(animationSpec = tween(250)))
                                        .togetherWith(fadeOut(animationSpec = tween(250)))
                                }
                            }
                        ) { screen ->
                            when (screen) {
                                "SETTINGS" -> {
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
                                        onClose = { currentScreen = "LIBRARY" }
                                    )
                                }

                                "AUDIO_PLAYER" -> {
                                    AudioPlayerScreen(
                                        playerManager = exoPlayerManager,
                                        networkRepository = networkRepository,
                                        onClose = { currentScreen = "LIBRARY" },
                                        onOpenAlbum = { album ->
                                            targetMainTab.value = if (enableAnalyticsTab) 3 else 2
                                            targetAudioSubTab.value = 1
                                            targetAudioAlbum.value = album
                                            currentScreen = "LIBRARY"
                                        },
                                        onOpenArtist = { artist ->
                                            targetMainTab.value = if (enableAnalyticsTab) 3 else 2
                                            targetAudioSubTab.value = 2
                                            targetAudioArtist.value = artist
                                            currentScreen = "LIBRARY"
                                        },
                                        onOpenFolder = { folder ->
                                            targetMainTab.value = if (enableAnalyticsTab) 3 else 2
                                            targetAudioSubTab.value = 3
                                            targetAudioFolder.value = folder
                                            currentScreen = "LIBRARY"
                                        },
                                        onOpenSettings = { currentScreen = "SETTINGS" }
                                    )
                                }

                                else -> {
                                    LibraryScreen(
                                        imagesList = imagesList,
                                        videosList = videosList,
                                        audioList = audioList,
                                        videoCategories = videoCategories,
                                        audioPlaylists = audioPlaylists,
                                        imageCollections = imageCollections,
                                        categoryCrossRefs = categoryCrossRefs,
                                        gridGapDp = gridGapDp,
                                        gridSizeLevel = gridSizeLevel,
                                        cornerRadiusDp = cornerRadiusDp,
                                        roundedCornersEnabled = roundedCornersEnabled,
                                        enableAnalyticsTab = enableAnalyticsTab,
                                        isLoading = isScanLoading,
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
                                        exoPlayerManager = exoPlayerManager,
                                        initialTab = mainTab,
                                        audioSubTab = audioSub,
                                        audioAlbum = audioAlb,
                                        audioArtist = audioArt,
                                        audioFolder = audioFld,
                                        initialVideoFolder = videoFld,
                                        initialImageFolder = imageFld,
                                        onOpenQuickView = { item, currentList ->
                                            val index = currentList.indexOfFirst { it.uri == item.uri }
                                            val intent = Intent(this@MainActivity, QuickViewActivity::class.java).apply {
                                                action = Intent.ACTION_VIEW
                                                setDataAndType(item.uri, item.mimeType)
                                                if (currentList.isNotEmpty()) {
                                                    putStringArrayListExtra("media_uris", ArrayList(currentList.map { it.uri.toString() }))
                                                    putStringArrayListExtra("media_titles", ArrayList(currentList.map { it.title }))
                                                    putExtra("start_index", if (index >= 0) index else 0)
                                                }
                                            }
                                            startActivity(intent)
                                        },
                                        onOpenVideoPlayer = { item ->
                                            // Handle GIFs separately if needed, though most should go to QuickView
                                            if (item.mimeType.contains("gif", ignoreCase = true)) {
                                                val intent = Intent(this@MainActivity, QuickViewActivity::class.java).apply {
                                                    action = Intent.ACTION_VIEW
                                                    setDataAndType(item.uri, item.mimeType)
                                                }
                                                startActivity(intent)
                                            } else {
                                                // Filter list to only include videos from the same folder
                                                val currentFolder = item.relativePath ?: item.bucketName ?: ""
                                                val filteredList = videosList.filter { 
                                                    (it.relativePath ?: it.bucketName ?: "") == currentFolder 
                                                }
                                                val index = filteredList.indexOfFirst { it.uri == item.uri }
                                                
                                                val intent = Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                                                    if (filteredList.isNotEmpty()) {
                                                        putStringArrayListExtra("video_uris", ArrayList(filteredList.map { it.uri.toString() }))
                                                        putStringArrayListExtra("video_titles", ArrayList(filteredList.map { it.title }))
                                                        putExtra("start_index", if (index >= 0) index else 0)
                                                    } else {
                                                        putExtra("media_uri", item.uri.toString())
                                                        putExtra("media_title", item.title)
                                                        putExtra("mime_type", item.mimeType)
                                                    }
                                                }
                                                startActivity(intent)
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
                                        }
                                    )
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

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), 100)
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
            targetVideoFolder.value = folder
            pendingScreenState.value = "LIBRARY"
        } else if (intent?.getStringExtra("open_screen") == "IMAGES_FOLDER") {
            val folder = intent.getStringExtra("folder_name")
            targetImageFolder.value = folder
            pendingScreenState.value = "LIBRARY"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            exoPlayerManager.release()
        }
    }
}
