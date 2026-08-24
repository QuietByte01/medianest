package com.medianest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medianest.MediaNestApp
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.ui.library.video.computeSharedTitleWords
import com.medianest.ui.library.video.isClipsAndRecordings
import com.medianest.ui.library.video.isDownloaded
import com.medianest.ui.library.video.isEditedVideo
import com.medianest.ui.library.video.isMovie
import com.medianest.ui.library.video.isMusicVideo
import com.medianest.ui.library.video.isShorts
import com.medianest.ui.library.video.isSocialMediaVideo
import com.medianest.ui.library.video.isTVSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class MediaCounts(
    val music: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val clips: Int = 0,
    val shorts: Int = 0,
    val social: Int = 0,
    val edited: Int = 0,
    val downloaded: Int = 0,
    val excluded: Int = 0,
    val hidden: Int = 0
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaStoreRepository(application)
    private val settingsManager = (application as MediaNestApp).settingsManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _imagesList = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _videosList = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _audioList = MutableStateFlow<List<MediaItem>>(emptyList())

    val videoFolderGroups = _videosList.combine(settingsManager.showHiddenFiles) { list, showHidden ->
        list.groupBy { item ->
            val relPath = item.relativePath?.trim('/')
            if (!relPath.isNullOrBlank()) relPath else item.bucketName ?: "Videos"
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val sharedTitleWords = _videosList.map { list ->
        withContext(Dispatchers.Default) { computeSharedTitleWords(list) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptySet<String>())

    val videoCounts = combine(_videosList, sharedTitleWords) { list, words ->
        withContext(Dispatchers.Default) {
            val visibleList = list.filter { !it.isExcluded && !it.isHidden }
            MediaCounts(
                music = visibleList.count { isMusicVideo(it) },
                movies = visibleList.count { isMovie(it, words) },
                series = visibleList.count { isTVSeries(it, words) },
                clips = visibleList.count { isClipsAndRecordings(it) },
                shorts = visibleList.count { isShorts(it) },
                social = visibleList.count { isSocialMediaVideo(it) },
                edited = visibleList.count { isEditedVideo(it) },
                downloaded = visibleList.count { isDownloaded(it) },
                excluded = list.count { it.isExcluded },
                hidden = list.count { it.isHidden && !it.isExcluded }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, MediaCounts())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMediaLists(images: List<MediaItem>, videos: List<MediaItem>, audio: List<MediaItem>) {
        _imagesList.value = images.distinctBy { if (it.size > 0) "${it.title.lowercase().trim()}_${it.size}" else it.id.toString() }
        _videosList.value = videos.distinctBy { if (it.size > 0) "${it.title.lowercase().trim()}_${it.size}" else it.id.toString() }
        _audioList.value = audio.distinctBy { if (it.size > 0) "${it.title.lowercase().trim()}_${it.size}" else it.id.toString() }
    }

    private val _videoFilterTab = MutableStateFlow("ALL")
    val videoFilterTab: StateFlow<String> = _videoFilterTab

    private val _imageFilterTab = MutableStateFlow("ALL")
    val imageFilterTab: StateFlow<String> = _imageFilterTab

    private val _audioFilterTab = MutableStateFlow("ALL")
    val audioFilterTab: StateFlow<String> = _audioFilterTab

    private val _videoSortField = MutableStateFlow("Date")
    val videoSortField: StateFlow<String> = _videoSortField

    private val _videoSortAscending = MutableStateFlow(false)
    val videoSortAscending: StateFlow<Boolean> = _videoSortAscending

    private val _imageSortField = MutableStateFlow("Date")
    val imageSortField: StateFlow<String> = _imageSortField

    private val _imageSortAscending = MutableStateFlow(false)
    val imageSortAscending: StateFlow<Boolean> = _imageSortAscending

    private val _audioSortField = MutableStateFlow("Name")
    val audioSortField: StateFlow<String> = _audioSortField

    private val _audioSortAscending = MutableStateFlow(true)
    val audioSortAscending: StateFlow<Boolean> = _audioSortAscending

    val filteredImagesList: StateFlow<List<MediaItem>> = combine(
        combine(_imagesList, _searchQuery, _imageFilterTab) { list, query, tab -> Triple(list, query, tab) },
        combine(_imageSortField, _imageSortAscending, settingsManager.showHiddenFiles) { sort, asc, showHidden -> Triple(sort, asc, showHidden) }
    ) { (list, query, tab), (sort, asc, showHidden) ->
        withContext(Dispatchers.Default) {
            val baseList = when (tab) {
                "EXCLUDED" -> list.filter { it.isExcluded || com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
                "HIDDEN" -> list.filter { it.isHidden && !it.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
                else -> list.filter { item ->
                    !item.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(item) &&
                    (showHidden || !item.isHidden)
                }
            }

            var filtered = if (query.isEmpty()) baseList else baseList.filter { it.title.contains(query, ignoreCase = true) }
            
            if (tab != "ALL" && tab != "EXCLUDED" && tab != "HIDDEN") {
                filtered = com.medianest.ui.library.image.filterImageList(filtered, tab, emptySet())
            }

            val comp = when (sort) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Type" -> compareBy<MediaItem> { it.mimeType.lowercase() }
                "Size" -> compareBy<MediaItem> { it.size }
                else -> compareBy<MediaItem> { it.dateAdded }
            }
            if (asc) filtered.sortedWith(comp) else filtered.sortedWith(comp).reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredAudioList: StateFlow<List<MediaItem>> = combine(
        combine(_audioList, _searchQuery, _audioFilterTab) { list, query, tab -> Triple(list, query, tab) },
        combine(_audioSortField, _audioSortAscending, settingsManager.showHiddenFiles) { sort, asc, showHidden -> Triple(sort, asc, showHidden) }
    ) { (list, query, tab), (sort, asc, showHidden) ->
        withContext(Dispatchers.Default) {
            val baseList = when (tab) {
                "EXCLUDED" -> list.filter { it.isExcluded || com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
                "HIDDEN" -> list.filter { it.isHidden && !it.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
                else -> list.filter { item ->
                    !item.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(item) &&
                    (showHidden || !item.isHidden)
                }
            }

            val filtered = if (query.isEmpty()) baseList else baseList.filter { 
                it.title.contains(query, ignoreCase = true) || (it.artist?.contains(query, ignoreCase = true) == true) 
            }
            val comp = when (sort) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Artist" -> compareBy<MediaItem> { (it.artist ?: "").lowercase() }
                "Date Added" -> compareBy<MediaItem> { it.dateAdded }
                else -> compareBy<MediaItem> { it.title.lowercase() }
            }
            if (asc) filtered.sortedWith(comp) else filtered.sortedWith(comp).reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredVideosList: StateFlow<List<MediaItem>> = combine(
        combine(_videosList, _searchQuery, _videoFilterTab) { list, query, tab -> Triple(list, query, tab) },
        combine(_videoSortField, _videoSortAscending, settingsManager.showHiddenFiles) { sort, asc, showHidden -> Triple(sort, asc, showHidden) }
    ) { (list, query, tab), (sort, asc, showHidden) ->
        withContext(Dispatchers.Default) {
            var filtered = if (query.isEmpty()) list else list.filter { it.title.contains(query, ignoreCase = true) }
            
            // Basic filtering logic (matching VideosTab.kt's filterVideoList)
            filtered = com.medianest.ui.library.video.filterVideoList(filtered, tab, showHidden)

            val comp = when (sort) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Type" -> compareBy<MediaItem> { it.mimeType.lowercase() }
                "Size" -> compareBy<MediaItem> { it.size }
                else -> compareBy<MediaItem> { it.dateAdded }
            }
            if (asc) filtered.sortedWith(comp) else filtered.sortedWith(comp).reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateVideoFilter(tab: String) { _videoFilterTab.value = tab }
    fun updateImageFilter(tab: String) { _imageFilterTab.value = tab }
    fun updateAudioFilter(tab: String) { _audioFilterTab.value = tab }
    fun updateVideoSort(field: String, ascending: Boolean) {
        _videoSortField.value = field
        _videoSortAscending.value = ascending
    }

    fun updateImageSort(field: String, ascending: Boolean) {
        _imageSortField.value = field
        _imageSortAscending.value = ascending
    }

    fun updateAudioSort(field: String, ascending: Boolean) {
        _audioSortField.value = field
        _audioSortAscending.value = ascending
    }
}
