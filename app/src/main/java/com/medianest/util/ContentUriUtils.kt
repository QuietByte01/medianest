package com.medianest.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * Utility for fast, non-blocking ContentResolver URI resolution.
 * Resolves content:// URIs to direct local file paths (file://) whenever accessible,
 * eliminating ContentResolver IPC Binder transaction overhead during media extraction & playback.
 */
object ContentUriUtils {

    /**
     * Resolves a content:// or file:// URI to an optimized Uri for playback and media extraction.
     * Must be called off the Main UI thread (e.g. on Dispatchers.IO).
     */
    fun resolveOptimizedUri(context: Context, uri: Uri): Uri {
        if (uri.scheme == "file") return uri
        if (uri.scheme != "content") return uri

        try {
            // 1. Check MediaStore _data column
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx >= 0) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrEmpty()) {
                            val file = File(path)
                            if (file.exists() && file.canRead()) {
                                return Uri.fromFile(file)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back safely to original URI if query is restricted or fails
        }

        return uri
    }

    /**
     * Resolves a URI to a safe content:// URI for sharing with external apps.
     * Uses FileProvider for file:// URIs.
     */
    fun getSharingUri(context: Context, uri: Uri): Uri {
        if (uri.scheme == "content") return uri
        if (uri.scheme != "file") return uri

        return try {
            val file = File(uri.path ?: return uri)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            uri
        }
    }
}
