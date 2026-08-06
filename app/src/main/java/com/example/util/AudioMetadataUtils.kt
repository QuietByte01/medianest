package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.MediaItem
import java.io.File

object AudioMetadataUtils {

    fun extractMetadata(context: Context, uri: Uri, rawTitleHint: String? = null, mimeTypeHint: String = "audio/*"): MediaItem {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs: Long = 0L
        var albumArtUri: Uri? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            val artBytes = retriever.embeddedPicture

            if (artBytes != null && artBytes.isNotEmpty()) {
                val cacheFile = File(context.cacheDir, "album_art_${uri.toString().hashCode()}.jpg")
                if (!cacheFile.exists()) {
                    cacheFile.writeBytes(artBytes)
                }
                albumArtUri = Uri.fromFile(cacheFile)
            }
        } catch (e: Exception) {
            Log.e("AudioMetadataUtils", "Failed to retrieve metadata for $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        val cleanedTitle = resolveTitle(context, uri, title, rawTitleHint)

        return MediaItem(
            id = uri.hashCode().toLong(),
            uri = uri,
            title = cleanedTitle,
            mimeType = mimeTypeHint,
            type = com.example.data.db.MediaType.AUDIO,
            durationMs = durationMs,
            artist = artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
            album = album?.takeIf { it.isNotBlank() && it != "<unknown>" },
            albumArtUri = albumArtUri
        )
    }

    private fun resolveTitle(context: Context, uri: Uri, metaTitle: String?, rawTitleHint: String?): String {
        // 1. If metaTitle is valid and not pure digits
        if (!metaTitle.isNullOrBlank() && !metaTitle.all { it.isDigit() }) {
            return metaTitle
        }

        // 2. Try rawTitleHint if provided and not pure digits
        if (!rawTitleHint.isNullOrBlank() && !rawTitleHint.all { it.isDigit() }) {
            return rawTitleHint.substringBeforeLast('.')
        }

        // 3. Query content resolver display name
        val displayName = getDisplayName(context, uri)
        if (!displayName.isNullOrBlank() && !displayName.all { it.isDigit() }) {
            return displayName.substringBeforeLast('.')
        }

        // 4. Try Uri lastPathSegment
        val lastSeg = uri.lastPathSegment
        if (!lastSeg.isNullOrBlank() && !lastSeg.all { it.isDigit() }) {
            return lastSeg.substringBeforeLast('.')
        }

        return "Audio Track"
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
