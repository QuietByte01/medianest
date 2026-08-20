package com.medianest.ui.videoeditor.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.medianest.ui.videoeditor.model.VideoThumbnail
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class ThumbnailRepository(
    private val context: Context,
    private val videoUri: Uri
) {
    private val cache = ThumbnailCache(32 * 1024 * 1024) // 32MB cache
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ongoingRequests = ConcurrentHashMap<Long, Job>()
    private val thumbnailsMutex = Mutex()
    private val retrieverMutex = Mutex()
    private var retriever: MediaMetadataRetriever? = null

    private val _thumbnails = MutableStateFlow<Map<Long, VideoThumbnail>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, VideoThumbnail>> = _thumbnails

    fun getThumbnail(timestampMs: Long, width: Int, height: Int) {
        if (cache.get(timestampMs) != null) {
            if (!_thumbnails.value.containsKey(timestampMs)) {
                updateThumbnailState(timestampMs, cache.get(timestampMs)!!)
            }
            return
        }
        if (ongoingRequests.containsKey(timestampMs)) return

        val job = scope.launch {
            try {
                val bitmap = extractFrame(timestampMs, width, height)
                if (bitmap != null) {
                    cache.put(timestampMs, bitmap)
                    updateThumbnailState(timestampMs, bitmap)
                }
            } catch (e: Exception) {
                // Extraction failed
            } finally {
                ongoingRequests.remove(timestampMs)
            }
        }
        ongoingRequests[timestampMs] = job
    }

    private fun updateThumbnailState(timestampMs: Long, bitmap: Bitmap) {
        scope.launch {
            thumbnailsMutex.withLock {
                val current = _thumbnails.value.toMutableMap()
                current[timestampMs] = VideoThumbnail(timestampMs, bitmap)
                _thumbnails.value = current
            }
        }
    }

    private suspend fun extractFrame(timestampMs: Long, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        retrieverMutex.withLock {
            if (retriever == null) {
                retriever = MediaMetadataRetriever().apply {
                    setDataSource(context, videoUri)
                }
            }
            try {
                retriever?.getScaledFrameAtTime(
                    timestampMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun release() {
        scope.cancel()
        cache.clear()
        ongoingRequests.clear()
        GlobalScope.launch(Dispatchers.IO) {
            retrieverMutex.withLock {
                retriever?.release()
                retriever = null
            }
        }
    }
}
