package com.medianest.studio.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import java.io.File

object StudioMediaResolver {

    fun resolveMediaItem(context: Context, uri: Uri): MediaItem {
        var name: String? = null
        var size: Long = 0L

        // 1. Resolve size & name from ContentResolver
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w("StudioMediaResolver", "ContentResolver query error: ${e.message}")
        }

        // 2. Fallback to ParcelFileDescriptor statSize
        if (size <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize.coerceAtLeast(0L)
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback to File.length() if scheme is file
        if (size <= 0L && uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    size = file.length()
                    if (name.isNullOrBlank()) name = file.name
                }
            } catch (_: Exception) {}
        }

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "Media Item"
        }

        // 4. Extract Width, Height, Duration using MediaMetadataRetriever
        var width = 0
        var height = 0
        var durationMs = 0L
        val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (_: Exception) {}

        val type = when {
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }

        return MediaItem(
            id = System.currentTimeMillis() + uri.hashCode(),
            uri = uri,
            title = name!!,
            mimeType = mimeType,
            type = type,
            width = width,
            height = height,
            durationMs = durationMs,
            size = size
        )
    }
}
