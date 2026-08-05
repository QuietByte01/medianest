package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    IMAGE, VIDEO, AUDIO
}

@Entity(tableName = "media_categories")
data class MediaCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // user-defined, e.g. "Anime", "Lectures"
    val type: String,              // VIDEO or AUDIO
    val coverUri: String? = null,   // optional custom thumbnail
    val iconName: String? = null,   // optional custom icon name
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Entity(
    tableName = "category_media_cross_ref",
    primaryKeys = ["categoryId", "mediaUri"]
)
data class CategoryMediaCrossRef(
    val categoryId: Long,
    val mediaUri: String,          // content:// URI or MediaStore ID
    val positionInCategory: Int = 0  // for manual reordering / playlist order
)

@Entity(tableName = "playback_states")
data class PlaybackState(
    @PrimaryKey val mediaUri: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playbackSpeed: Float = 1.0f,
    val savedBrightness: Float? = null,   // per-video brightness memory
    val cropMode: String = "FIT",         // FIT, CROP, STRETCH
    val subtitleUri: String? = null,
    val subtitleOffsetMs: Long = 0L,
    val playCount: Int = 1
)

@Entity(tableName = "audio_metadata_cache")
data class AudioMetadataCache(
    @PrimaryKey val audioUri: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: String? = null,      // cached local copy of fetched art
    val lyricsPlain: String? = null,
    val lyricsSyncedLrc: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "hidden_folders",
    primaryKeys = ["folderPath", "mediaType"]
)
data class HiddenFolder(
    val folderPath: String,
    val mediaType: String // IMAGE, VIDEO, AUDIO
)

@Entity(tableName = "selective_hidden_folders")
data class SelectiveHiddenFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderPath: String,           // e.g. "/storage/emulated/0/DCIM/Camera" or folder name
    val folderName: String,           // e.g. "Camera" (for UI display)
    val mediaType: String,            // IMAGE / VIDEO / AUDIO
    val isHidden: Boolean = true,     // true = folder is hidden
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_stat_snapshots")
data class FileStatSnapshot(
    @PrimaryKey val id: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val totalCount: Int = 0,
    val totalSize: Long = 0L,
    val imageCount: Int = 0,
    val imageSize: Long = 0L,
    val videoCount: Int = 0,
    val videoSize: Long = 0L,
    val audioCount: Int = 0,
    val audioSize: Long = 0L
)

@Entity(tableName = "format_stats")
data class FormatStat(
    @PrimaryKey val extension: String,
    val fileCount: Int,
    val sizeBytes: Long,
    val category: String // IMAGE, VIDEO, AUDIO
)

