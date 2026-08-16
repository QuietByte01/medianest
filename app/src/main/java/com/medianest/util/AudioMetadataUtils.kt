package com.medianest.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.medianest.data.model.MediaItem
import java.io.File

object AudioMetadataUtils {

    fun extractMetadata(context: Context, uri: Uri, rawTitleHint: String? = null, mimeTypeHint: String = "audio/*"): MediaItem {
        val meta = MediaMetadataUtils.extractBasicMetadata(context, uri, includePicture = true)
        
        var albumArtUri: Uri? = null
        if (meta.embeddedPicture != null && meta.embeddedPicture.isNotEmpty()) {
            val cacheFile = File(context.cacheDir, "album_art_${uri.toString().hashCode()}.jpg")
            if (!cacheFile.exists()) {
                cacheFile.writeBytes(meta.embeddedPicture)
            }
            albumArtUri = Uri.fromFile(cacheFile)
        }

        val cleanedTitle = resolveTitle(context, uri, meta.title, rawTitleHint)

        // Dynamically infer MIME type if generic or missing
        val effectiveMime = if (mimeTypeHint.isBlank() || mimeTypeHint == "*/*" || mimeTypeHint == "application/octet-stream") {
            val fromResolver = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (!fromResolver.isNullOrBlank() && fromResolver != "*/*") {
                fromResolver
            } else {
                val path = (uri.path ?: uri.toString()).lowercase()
                when {
                    path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".3gp") || path.endsWith(".avi") || path.endsWith(".mov") || path.endsWith(".ts") || path.endsWith(".flv") || path.endsWith(".wmv") || path.endsWith(".m4v") -> "video/mp4"
                    path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".bmp") || path.endsWith(".heic") -> "image/jpeg"
                    path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".flac") || path.endsWith(".aac") || path.endsWith(".m4a") || path.endsWith(".ogg") || path.endsWith(".opus") || path.endsWith(".wma") -> "audio/mpeg"
                    else -> mimeTypeHint
                }
            }
        } else {
            mimeTypeHint
        }

        // Accurately determine MediaType (VIDEO vs AUDIO vs IMAGE)
        val determinedType = when {
            effectiveMime.startsWith("video/", ignoreCase = true) -> com.medianest.data.db.MediaType.VIDEO
            effectiveMime.startsWith("image/", ignoreCase = true) -> com.medianest.data.db.MediaType.IMAGE
            effectiveMime.startsWith("audio/", ignoreCase = true) -> com.medianest.data.db.MediaType.AUDIO
            else -> {
                val path = (uri.path ?: uri.toString()).lowercase()
                if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".3gp") || path.endsWith(".avi") || path.endsWith(".mov") || path.endsWith(".ts") || path.endsWith(".flv") || path.endsWith(".wmv") || path.endsWith(".m4v")) {
                    com.medianest.data.db.MediaType.VIDEO
                } else if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".bmp") || path.endsWith(".heic")) {
                    com.medianest.data.db.MediaType.IMAGE
                } else {
                    com.medianest.data.db.MediaType.AUDIO
                }
            }
        }

        return MediaItem(
            id = uri.hashCode().toLong(),
            uri = uri,
            title = cleanedTitle,
            mimeType = effectiveMime,
            type = determinedType,
            durationMs = meta.durationMs,
            dateAdded = meta.dateCreated,
            dateModified = meta.dateModified,
            dateCreated = meta.dateCreated,
            artist = meta.artist,
            album = meta.album,
            albumArtUri = albumArtUri
        )
    }

    private fun resolveTitle(context: Context, uri: Uri, metaTitle: String?, rawTitleHint: String?): String {
        // 1. If ID3 metaTitle is valid
        if (!metaTitle.isNullOrBlank()) {
            return metaTitle.trim()
        }

        // 2. Query content resolver display name (e.g. OpenableColumns.DISPLAY_NAME)
        val displayName = getDisplayName(context, uri)
        if (!displayName.isNullOrBlank()) {
            val cleanName = if (displayName.contains('.')) displayName.substringBeforeLast('.') else displayName
            if (cleanName.isNotBlank() && !cleanName.startsWith("audio:", ignoreCase = true) && !cleanName.startsWith("document", ignoreCase = true)) {
                return cleanName
            }
        }

        // 3. Try rawTitleHint if provided
        if (!rawTitleHint.isNullOrBlank()) {
            val cleanHint = if (rawTitleHint.contains('.')) rawTitleHint.substringBeforeLast('.') else rawTitleHint
            if (cleanHint.isNotBlank() && !cleanHint.startsWith("audio:", ignoreCase = true) && !cleanHint.startsWith("document", ignoreCase = true)) {
                return cleanHint
            }
        }

        // 4. Try Uri lastPathSegment
        val lastSeg = uri.lastPathSegment
        if (!lastSeg.isNullOrBlank()) {
            val cleanSeg = if (lastSeg.contains('.')) lastSeg.substringBeforeLast('.') else lastSeg
            if (cleanSeg.isNotBlank() && !cleanSeg.startsWith("audio:", ignoreCase = true) && !cleanSeg.startsWith("document", ignoreCase = true)) {
                return cleanSeg
            }
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
