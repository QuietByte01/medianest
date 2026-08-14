package com.medianest.data.model

import android.net.Uri
import com.medianest.data.db.MediaType

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val type: MediaType,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val bucketId: String? = null,
    val bucketName: String? = null,
    val relativePath: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: Uri? = null
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1.0f
}

data class SubtitleItem(
    val id: String,
    val name: String,
    val language: String,
    val downloadUrl: String? = null,
    val localUri: Uri? = null,
    val isLocal: Boolean = false
)

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class AudioTagInfo(
    val title: String,
    val artist: String,
    val album: String,
    val coverArtUrl: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val composer: String? = null
)
