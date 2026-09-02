package com.medianest.util

import android.content.Context
import coil.Coil
import coil.request.ImageRequest
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.AnalyticsRepository
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class IndexingProgressState(
    val isRunning: Boolean = false,
    val stageIndex: Int = 0,
    val stageName: String = "",
    val statusDetail: String = "",
    val currentItem: Int = 0,
    val totalItems: Int = 0,
    val progressFraction: Float = 0f,
    val isCompleted: Boolean = false
)

object InitialIndexingManager {

    private const val TAG = "InitialIndexingManager"

    private val _progressState = MutableStateFlow(IndexingProgressState())
    val progressState: StateFlow<IndexingProgressState> = _progressState.asStateFlow()

    suspend fun startIndexing(
        context: Context,
        mediaStoreRepository: MediaStoreRepository,
        analyticsRepository: AnalyticsRepository,
        settingsManager: SettingsManager,
        hiddenFolders: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        if (_progressState.value.isRunning) return@withContext

        _progressState.value = IndexingProgressState(
            isRunning = true,
            stageIndex = 1,
            stageName = "Scanning Storage",
            statusDetail = "Discovering photos, videos, and music...",
            progressFraction = 0.05f
        )

        try {
            // Stage 1: Fast MediaStore Discovery (0.05f -> 0.20f)
            Logger.i(TAG, "Stage 1: MediaStore Discovery")
            val images = mediaStoreRepository.getImages(hiddenFolders, showHidden = true, includeFileSystemScan = false)
            val videos = mediaStoreRepository.getVideos(hiddenFolders, showHidden = true, includeFileSystemScan = false)
            val audio = mediaStoreRepository.getAudio(hiddenFolders, showHidden = true, includeFileSystemScan = false)

            val totalMediaCount = images.size + videos.size + audio.size
            _progressState.value = _progressState.value.copy(
                stageIndex = 1,
                stageName = "Scanning Storage",
                statusDetail = "Discovered $totalMediaCount media items across device storage",
                currentItem = totalMediaCount,
                totalItems = totalMediaCount,
                progressFraction = 0.20f
            )

            // Stage 2: Deep Filesystem Scan for Hidden & Excluded Folders (0.20f -> 0.40f)
            Logger.i(TAG, "Stage 2: Hidden Filesystem Scan")
            _progressState.value = _progressState.value.copy(
                stageIndex = 2,
                stageName = "Filesystem & Hidden Scan",
                statusDetail = "Scanning hidden dot-folders and excluded directories...",
                progressFraction = 0.25f
            )

            val hiddenImages = mediaStoreRepository.scanHiddenMedia(MediaType.IMAGE, hiddenFolders) { path, _ ->
                _progressState.value = _progressState.value.copy(
                    statusDetail = "Scanning $path"
                )
            }
            _progressState.value = _progressState.value.copy(progressFraction = 0.30f)

            val hiddenVideos = mediaStoreRepository.scanHiddenMedia(MediaType.VIDEO, hiddenFolders) { path, _ ->
                _progressState.value = _progressState.value.copy(
                    statusDetail = "Scanning $path"
                )
            }
            _progressState.value = _progressState.value.copy(progressFraction = 0.35f)

            val hiddenAudio = mediaStoreRepository.scanHiddenMedia(MediaType.AUDIO, hiddenFolders) { path, _ ->
                _progressState.value = _progressState.value.copy(
                    statusDetail = "Scanning $path"
                )
            }

            val allImages = (images + hiddenImages).distinctBy { it.uri.toString() }
            val allVideos = (videos + hiddenVideos).distinctBy { it.uri.toString() }
            val allAudio = (audio + hiddenAudio).distinctBy { it.uri.toString() }

            _progressState.value = _progressState.value.copy(
                stageIndex = 2,
                stageName = "Filesystem & Hidden Scan",
                statusDetail = "Total merged index: ${allImages.size} photos, ${allVideos.size} videos, ${allAudio.size} songs",
                progressFraction = 0.40f
            )

            // Stage 3: Computing Filter Tabs, Collections & Categories (0.40f -> 0.60f)
            Logger.i(TAG, "Stage 3: Computing Filters & Categories")
            _progressState.value = _progressState.value.copy(
                stageIndex = 3,
                stageName = "Computing Filters & Categories",
                statusDetail = "Indexing WhatsApp, Camera, Screenshots, Wallpapers & Albums...",
                progressFraction = 0.45f
            )

            // Categorize image & video folders
            val imageFolders = allImages.mapNotNull { it.bucketName }.distinct()
            val videoFolders = allVideos.mapNotNull { it.bucketName }.distinct()
            val audioAlbums = allAudio.mapNotNull { it.album }.distinct()
            val audioArtists = allAudio.mapNotNull { it.artist }.distinct()

            _progressState.value = _progressState.value.copy(
                stageIndex = 3,
                stageName = "Computing Filters & Categories",
                statusDetail = "Indexed ${imageFolders.size} photo folders, ${videoFolders.size} video folders, ${audioAlbums.size} albums, ${audioArtists.size} artists",
                progressFraction = 0.60f
            )

            // Stage 4: Pre-generating Thumbnail Caches for Fast Scrolling (0.60f -> 0.90f)
            Logger.i(TAG, "Stage 4: Pre-generating Thumbnail Cache")
            val maxVideoThumbs = allVideos.take(40) // Pre-cache up to 40 most recent video frames
            val maxImageThumbs = allImages.take(60) // Pre-cache up to 60 most recent images via Coil

            val totalThumbsToWarm = maxVideoThumbs.size + maxImageThumbs.size
            var warmedCount = 0

            _progressState.value = _progressState.value.copy(
                stageIndex = 4,
                stageName = "Pre-caching Thumbnails",
                statusDetail = "Extracting video frames and pre-warming gallery thumbnails...",
                currentItem = warmedCount,
                totalItems = totalThumbsToWarm,
                progressFraction = 0.62f
            )

            // 4a. Video Frame Extraction into WebP Disk Cache
            for (vid in maxVideoThumbs) {
                try {
                    ThumbnailManager.getThumbnail(context, vid.uri, rebuildToken = 0)
                } catch (_: Exception) {}
                warmedCount++
                val fraction = 0.60f + 0.20f * (warmedCount.toFloat() / totalThumbsToWarm.coerceAtLeast(1))
                _progressState.value = _progressState.value.copy(
                    statusDetail = "Pre-caching video thumbnail: ${vid.title}",
                    currentItem = warmedCount,
                    progressFraction = fraction
                )
            }

            // 4b. Image Prefetching via Coil
            val imageLoader = Coil.imageLoader(context)
            for (img in maxImageThumbs) {
                try {
                    val req = ImageRequest.Builder(context)
                        .data(img.uri)
                        .size(400)
                        .build()
                    imageLoader.enqueue(req)
                } catch (_: Exception) {}
                warmedCount++
                val fraction = 0.60f + 0.30f * (warmedCount.toFloat() / totalThumbsToWarm.coerceAtLeast(1))
                _progressState.value = _progressState.value.copy(
                    statusDetail = "Pre-caching photo thumbnail: ${img.title}",
                    currentItem = warmedCount,
                    progressFraction = fraction
                )
            }

            _progressState.value = _progressState.value.copy(
                stageIndex = 4,
                stageName = "Pre-caching Thumbnails",
                statusDetail = "Pre-cached $warmedCount thumbnail frames in memory and disk",
                progressFraction = 0.90f
            )

            // Stage 5: Finalizing Database & Analytics Snapshot (0.90f -> 1.00f)
            Logger.i(TAG, "Stage 5: Finalizing Indexing & Analytics")
            _progressState.value = _progressState.value.copy(
                stageIndex = 5,
                stageName = "Finalizing Index",
                statusDetail = "Building local media analytics & completing setup...",
                progressFraction = 0.95f
            )

            try {
                analyticsRepository.scanAndSaveAnalytics(allImages, allVideos, allAudio)
            } catch (_: Exception) {}

            settingsManager.setFirstLaunchCompleted(true)

            _progressState.value = IndexingProgressState(
                isRunning = false,
                stageIndex = 5,
                stageName = "Completed",
                statusDetail = "Setup finished! Enjoy MediaNest.",
                progressFraction = 1.0f,
                isCompleted = true
            )
            Logger.i(TAG, "Initial Indexing Completed Successfully!")
        } catch (e: Exception) {
            Logger.e(TAG, "Error during initial indexing", e)
            settingsManager.setFirstLaunchCompleted(true)
            _progressState.value = IndexingProgressState(
                isRunning = false,
                isCompleted = true,
                progressFraction = 1.0f
            )
        }
    }

    fun resetState() {
        _progressState.value = IndexingProgressState()
    }
}
