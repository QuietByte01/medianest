package com.medianest.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.util.Logger

class MediaPagingSource(
    private val repository: MediaStoreRepository,
    private val mediaType: MediaType,
    private val hiddenFolders: Set<String>,
    private val showHidden: Boolean
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val position = params.key ?: 0
        Logger.v("MediaPagingSource", "LOAD START: type=$mediaType, pos=$position, size=${params.loadSize}")
        return try {
            val items = when (mediaType) {
                MediaType.IMAGE -> repository.getImages(hiddenFolders, showHidden, params.loadSize, position)
                MediaType.VIDEO -> repository.getVideos(hiddenFolders, showHidden, params.loadSize, position)
                MediaType.AUDIO -> repository.getAudio(hiddenFolders, showHidden, params.loadSize, position)
            }
            Logger.v("MediaPagingSource", "LOAD SUCCESS: type=$mediaType, items=${items.size}")
            LoadResult.Page(
                data = items,
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (items.isEmpty() || items.size < params.loadSize) null else position + params.loadSize
            )
        } catch (e: Exception) {
            Logger.e("MediaPagingSource", "LOAD ERROR: type=$mediaType, pos=$position", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}
