package com.medianest.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

object MediaMetadataUtils {

    private val metadataCache = android.util.LruCache<String, MetadataResult>(1000)

    fun clearCache() {
        metadataCache.evictAll()
    }

    data class MetadataResult(
        val title: String? = null,
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val artist: String? = null,
        val album: String? = null,
        val dateModified: Long = 0L,
        val dateCreated: Long = 0L,
        val embeddedPicture: ByteArray? = null,
        val genre: String? = null,
        val year: String? = null,
        val composer: String? = null
    )

    fun extractBasicMetadata(context: Context, uri: Uri, includePicture: Boolean = false): MetadataResult {
        val pathKey = if (uri.scheme == "file") uri.path else uri.toString()
        val fileForDate = if (uri.scheme == "file" && uri.path != null) File(uri.path!!) else null
        val lastMod = fileForDate?.lastModified() ?: 0L
        val cacheKey = "$pathKey:$lastMod"

        metadataCache.get(cacheKey)?.let { 
            // If cached result has no picture but we need one, we must re-extract
            if (includePicture && it.embeddedPicture == null) {
                // fall through to extraction
            } else {
                return it 
            }
        }

        var title: String? = null
        var durationMs = 0L
        var width = 0
        var height = 0
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var year: String? = null
        var composer: String? = null
        var dateModified = 0L
        var dateCreated = 0L
        var embeddedPicture: ByteArray? = null

        // 1. Extract technical metadata using MediaMetadataRetriever (Skip for images for performance)
        val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        val isImage = mimeType?.startsWith("image/") == true ||
                uri.path?.let { p -> listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp").any { p.lowercase().endsWith(it) } } == true

        if (!isImage) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSourceSafe(context, uri)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) 
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                if (includePicture) {
                    embeddedPicture = retriever.embeddedPicture
                }
            } catch (e: Exception) {
                Logger.e("MediaMetadataUtils", "Failed to retrieve retriever metadata for $uri", e)
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        } else {
            // Fast extraction for images
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(uri.path, options)
                    width = options.outWidth
                    height = options.outHeight
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 2. Extract file system attributes (Dates)
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    dateModified = file.lastModified() / 1000 // In seconds to match MediaStore

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                            dateCreated = attrs.creationTime().toMillis() / 1000
                        } catch (e: Exception) {
                            Logger.e("MediaMetadataUtils", "Failed to read creation time for $path", e)
                            dateCreated = dateModified
                        }
                    } else {
                        dateCreated = dateModified
                    }
                }
            }
        } else {
            // For content URIs, we might not be able to get created/modified dates easily without MediaStore
            // but for hidden folders scanned via File API, it should be a file scheme Uri.
        }

        val result = MetadataResult(
            title = title,
            durationMs = durationMs,
            width = width,
            height = height,
            artist = artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
            album = album?.takeIf { it.isNotBlank() && it != "<unknown>" },
            genre = genre?.takeIf { it.isNotBlank() && it != "<unknown>" },
            year = year?.takeIf { it.isNotBlank() },
            composer = composer?.takeIf { it.isNotBlank() && it != "<unknown>" },
            dateModified = dateModified,
            dateCreated = dateCreated,
            embeddedPicture = embeddedPicture
        )
        
        // FIXME: Cache strategy issue. This method nulls out the heavy embeddedPicture to save memory,
        // but this forces a complete re-extraction from the file every time a thumbnail is needed.
        // Cache the result but NULL OUT the heavy ByteArray to prevent OOM
        metadataCache.put(cacheKey, result.copy(embeddedPicture = null))
        return result
    }
}

/**
 * Safely sets the data source for a MediaMetadataRetriever, preferring FileDescriptors via ContentResolver
 * to prevent permission and cross-process mediaserver failures (0x80000000).
 */
fun MediaMetadataRetriever.setDataSourceSafe(context: Context, uri: Uri) {
    val path = uri.path
    if (uri.scheme == "file" && path != null) {
        setDataSource(path)
        return
    }

    val pfd = try {
        context.contentResolver.openFileDescriptor(uri, "r")
    } catch (e: Exception) {
        null
    }

    if (pfd != null) {
        try {
            setDataSource(pfd.fileDescriptor)
            return
        } catch (e: Exception) {
            Logger.w("MediaMetadataUtils", "setDataSource via FileDescriptor failed for $uri, falling back", e)
        } finally {
            try {
                pfd.close()
            } catch (_: Exception) {}
        }
    }

    // Fallback to standard setDataSource
    setDataSource(context, uri)
}

/**
 * Safely sets the data source for a MediaExtractor, preferring FileDescriptors via ContentResolver.
 */
fun android.media.MediaExtractor.setDataSourceSafe(context: Context, uri: Uri) {
    val path = uri.path
    if (uri.scheme == "file" && path != null) {
        setDataSource(path)
        return
    }

    val pfd = try {
        context.contentResolver.openFileDescriptor(uri, "r")
    } catch (e: Exception) {
        null
    }

    if (pfd != null) {
        try {
            setDataSource(pfd.fileDescriptor)
            return
        } catch (e: Exception) {
            Logger.w("MediaMetadataUtils", "MediaExtractor setDataSource via FileDescriptor failed for $uri", e)
        } finally {
            try {
                pfd.close()
            } catch (_: Exception) {}
        }
    }

    setDataSource(context, uri, null)
}
