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
    val composer: String? = null,
    val albumArtist: String? = null,
    val trackNumber: String? = null
)

data class CachedMediaItemProxy(
    val id: Long,
    val uriString: String,
    val title: String,
    val mimeType: String,
    val typeName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val dateCreated: Long,
    val bucketId: String?,
    val bucketName: String?,
    val relativePath: String?,
    val isHidden: Boolean,
    val isExcluded: Boolean,
    val artist: String?,
    val album: String?,
    val albumArtUriString: String?,
    val genre: String?,
    val year: String?,
    val composer: String?,
    val albumArtist: String?,
    val trackNumber: String?
) : java.io.Serializable {
    fun toMediaItem() = MediaItem(
        id = id,
        uri = Uri.parse(uriString),
        title = title,
        mimeType = mimeType,
        type = com.medianest.data.db.MediaType.valueOf(typeName),
        width = width,
        height = height,
        durationMs = durationMs,
        size = size,
        dateAdded = dateAdded,
        dateModified = dateModified,
        dateCreated = dateCreated,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        isHidden = isHidden,
        isExcluded = isExcluded,
        artist = artist,
        album = album,
        albumArtUri = albumArtUriString?.let { Uri.parse(it) },
        genre = genre,
        year = year,
        composer = composer,
        albumArtist = albumArtist,
        trackNumber = trackNumber
    )

    companion object {
        fun fromMediaItem(item: MediaItem) = CachedMediaItemProxy(
            id = item.id,
            uriString = item.uri.toString(),
            title = item.title,
            mimeType = item.mimeType,
            typeName = item.type.name,
            width = item.width,
            height = item.height,
            durationMs = item.durationMs,
            size = item.size,
            dateAdded = item.dateAdded,
            dateModified = item.dateModified,
            dateCreated = item.dateCreated,
            bucketId = item.bucketId,
            bucketName = item.bucketName,
            relativePath = item.relativePath,
            isHidden = item.isHidden,
            isExcluded = item.isExcluded,
            artist = item.artist,
            album = item.album,
            albumArtUriString = item.albumArtUri?.toString(),
            genre = item.genre,
            year = item.year,
            composer = item.composer,
            albumArtist = item.albumArtist,
            trackNumber = item.trackNumber
        )
    }
}
