package com.medianest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.medianest.MediaNestApp
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.MediaPagingSource
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class MediaCounts(
    val music: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val clips: Int = 0,
    val shorts: Int = 0,
    val social: Int = 0,
    val edited: Int = 0,
    val downloaded: Int = 0
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
            MediaCounts(
                music = list.count { isMusicVideo(it) },
                movies = list.count { isMovie(it, words) },
                series = list.count { isTVSeries(it, words) },
                clips = list.count { isClipsAndRecordings(it) },
                shorts = list.count { isShorts(it) },
                social = list.count { isSocialMediaVideo(it) },
                edited = list.count { isEditedVideo(it) },
                downloaded = list.count { isDownloaded(it) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, MediaCounts())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMediaLists(images: List<MediaItem>, videos: List<MediaItem>, audio: List<MediaItem>) {
        _imagesList.value = images
        _videosList.value = videos
        _audioList.value = audio
    }

    private val _videoFilterTab = MutableStateFlow("ALL")
    val videoFilterTab: StateFlow<String> = _videoFilterTab

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

    val filteredImagesList = combine(
        _imagesList, _searchQuery, _imageSortField, _imageSortAscending
    ) { list, query, sort, asc ->
        withContext(Dispatchers.Default) {
            val filtered = if (query.isEmpty()) list else list.filter { it.title.contains(query, ignoreCase = true) }
            val comp = when (sort) {
                "Name" -> compareBy<MediaItem> { it.title.lowercase() }
                "Type" -> compareBy<MediaItem> { it.mimeType.lowercase() }
                "Size" -> compareBy<MediaItem> { it.size }
                else -> compareBy<MediaItem> { it.dateAdded }
            }
            if (asc) filtered.sortedWith(comp) else filtered.sortedWith(comp).reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredAudioList = combine(
        _audioList, _searchQuery, _audioSortField, _audioSortAscending
    ) { list, query, sort, asc ->
        withContext(Dispatchers.Default) {
            val filtered = if (query.isEmpty()) list else list.filter { 
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

    val filteredVideosList = combine(
        _videosList, _searchQuery, _videoFilterTab, _videoSortField, _videoSortAscending
    ) { list, query, tab, sort, asc ->
        withContext(Dispatchers.Default) {
            var filtered = if (query.isEmpty()) list else list.filter { it.title.contains(query, ignoreCase = true) }
            
            // Basic filtering logic (matching VideosTab.kt's filterVideoList)
            filtered = com.medianest.ui.library.video.filterVideoList(filtered, tab)

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedImagesFlow = filteredImagesList.flatMapLatest { list ->
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { ListPagingSource(list) }
        ).flow
    }.cachedIn(viewModelScope)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedVideosFlow = filteredVideosList.flatMapLatest { list ->
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { ListPagingSource(list) }
        ).flow
    }.cachedIn(viewModelScope)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedAudioFlow = filteredAudioList.flatMapLatest { list ->
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { ListPagingSource(list) }
        ).flow
    }.cachedIn(viewModelScope)

    private class ListPagingSource(private val list: List<MediaItem>) : PagingSource<Int, MediaItem>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
            val start = params.key ?: 0
            val end = (start + params.loadSize).coerceAtMost(list.size)
            if (start >= list.size) return LoadResult.Page(emptyList(), null, null)
            return LoadResult.Page(
                data = list.subList(start, end),
                prevKey = if (start == 0) null else start - params.loadSize,
                nextKey = if (end >= list.size) null else end
            )
        }
        override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? = null
    }
}
