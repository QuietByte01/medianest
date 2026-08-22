package com.medianest.data.repository

import com.medianest.data.db.AnalyticsDao
import com.medianest.data.db.FileStatSnapshot
import com.medianest.data.db.FormatStat
import com.medianest.data.db.SelectiveHiddenFolderDao
import com.medianest.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AnalyticsRepository(
    private val analyticsDao: AnalyticsDao,
    private val mediaStoreRepository: MediaStoreRepository,
    private val selectiveHiddenFolderDao: SelectiveHiddenFolderDao
) {

    val snapshot: Flow<FileStatSnapshot?> = analyticsDao.getLatestSnapshot()
    val formatStats: Flow<List<FormatStat>> = analyticsDao.getAllFormatStats()

    suspend fun refreshAnalytics(showHidden: Boolean) = withContext(Dispatchers.IO) {
        val selectiveHidden = selectiveHiddenFolderDao.getAllHiddenFoldersList()
        val imageHidden = selectiveHidden.filter { it.mediaType == "IMAGE" && it.isHidden }
            .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()
        val videoHidden = selectiveHidden.filter { it.mediaType == "VIDEO" && it.isHidden }
            .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()
        val audioHidden = selectiveHidden.filter { it.mediaType == "AUDIO" && it.isHidden }
            .flatMap { listOf(it.folderPath, it.folderName) }.filter { it.isNotBlank() }.toSet()

        val images = mediaStoreRepository.getImages(imageHidden, showHidden = showHidden)
        val videos = mediaStoreRepository.getVideos(videoHidden, showHidden = showHidden)
        val audio = mediaStoreRepository.getAudio(audioHidden, showHidden = showHidden)

        scanAndSaveAnalytics(images, videos, audio)
    }

    suspend fun scanAndSaveAnalytics(
        images: List<MediaItem>,
        videos: List<MediaItem>,
        audio: List<MediaItem>
    ) = withContext(Dispatchers.IO) {
        val imageCount = images.size
        val imageSize = images.sumOf { it.size }

        val videoCount = videos.size
        val videoSize = videos.sumOf { it.size }

        val audioCount = audio.size
        val audioSize = audio.sumOf { it.size }

        val totalCount = imageCount + videoCount + audioCount
        val totalSize = imageSize + videoSize + audioSize

        val snapshot = FileStatSnapshot(
            id = 1,
            timestamp = System.currentTimeMillis(),
            totalCount = totalCount,
            totalSize = totalSize,
            imageCount = imageCount,
            imageSize = imageSize,
            videoCount = videoCount,
            videoSize = videoSize,
            audioCount = audioCount,
            audioSize = audioSize
        )

        // Group by format extension
        val formatMap = mutableMapOf<Pair<String, String>, Pair<Int, Long>>() // (extension, category) -> (count, size)

        fun processItems(items: List<MediaItem>, category: String) {
            for (item in items) {
                val ext = extractExtension(item, category)
                val key = Pair(ext, category)
                val current = formatMap.getOrDefault(key, Pair(0, 0L))
                formatMap[key] = Pair(current.first + 1, current.second + item.size)
            }
        }

        processItems(images, "IMAGE")
        processItems(videos, "VIDEO")
        processItems(audio, "AUDIO")

        val formatStats = formatMap.map { (key, data) ->
            FormatStat(
                extension = key.first,
                fileCount = data.first,
                sizeBytes = data.second,
                category = key.second
            )
        }.sortedWith(compareByDescending<FormatStat> { it.sizeBytes }.thenByDescending { it.fileCount })

        analyticsDao.saveAnalyticsData(snapshot, formatStats)
    }

    private fun extractExtension(item: MediaItem, defaultCategory: String): String {
        val validExtensions = setOf(
            "JPG", "JPEG", "PNG", "WEBP", "GIF", "BMP", "HEIC", "HEIF", "SVG",
            "MP4", "MKV", "WEBM", "AVI", "MOV", "3GP", "FLV", "WMV", "M4V",
            "MP3", "FLAC", "WAV", "AAC", "M4A", "OGG", "OPUS", "WMA", "MID"
        )

        val name = item.title
        if (name.contains(".")) {
            val ext = name.substringAfterLast('.').substringBefore('?').substringBefore('#').trim().uppercase()
            if (ext in validExtensions) {
                return ext
            }
        }

        val uriPath = item.relativePath ?: item.uri.lastPathSegment ?: ""
        if (uriPath.contains(".")) {
            val ext = uriPath.substringAfterLast('.').substringBefore('?').substringBefore('#').trim().uppercase()
            if (ext in validExtensions) {
                return ext
            }
        }

        val mime = item.mimeType.lowercase()
        val mimeExt = when {
            mime.contains("flac") -> "FLAC"
            mime.contains("jpeg") || mime.contains("jpg") -> "JPG"
            mime.contains("png") -> "PNG"
            mime.contains("webp") -> "WEBP"
            mime.contains("gif") -> "GIF"
            mime.contains("m4a") -> "M4A"
            mime.contains("mp4") -> if (defaultCategory == "AUDIO" || item.type == com.medianest.data.db.MediaType.AUDIO) "M4A" else "MP4"
            mime.contains("webm") -> "WEBM"
            mime.contains("matroska") || mime.contains("mkv") -> "MKV"
            mime.contains("avi") -> "AVI"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            mime.contains("wav") || mime.contains("wave") || mime.contains("x-wav") -> "WAV"
            mime.contains("aac") -> "AAC"
            mime.contains("ogg") || mime.contains("opus") -> "OGG"
            mime.contains("quicktime") || mime.contains("mov") -> "MOV"
            mime.contains("heic") || mime.contains("heif") -> "HEIC"
            else -> null
        }
        if (mimeExt != null) return mimeExt

        return when (defaultCategory) {
            "IMAGE" -> "JPG"
            "VIDEO" -> "MP4"
            "AUDIO" -> "MP3"
            else -> "FILE"
        }
    }
}
