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
    val dateModified: Long = 0L,
    val dateCreated: Long = 0L,
    val bucketId: String? = null,
    val bucketName: String? = null,
    val relativePath: String? = null,
    val isHidden: Boolean = false,
    val isExcluded: Boolean = false,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: Uri? = null,
    val genre: String? = null,
    val year: String? = null,
    val composer: String? = null,
    val albumArtist: String? = null,
    val trackNumber: String? = null
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1.0f
}
