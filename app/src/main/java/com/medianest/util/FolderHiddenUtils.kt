package com.medianest.util

import android.content.Context
import android.net.Uri
import java.io.File

object FolderHiddenUtils {
    val DEFAULT_EXCLUDED_FOLDERS = setOf(
        ".recycle_bin", "recycle.bin", ".recyclebin", "recyclebin", "\$recycle.bin", ".recycle",
        ".trash", "trash", ".trashed", ".trash-1000",
        ".thumbnails", "thumbnails", ".thumb", ".thumbs", ".thumbnail",
        ".cache", "cache", ".caches", "caches",
        ".gallery_cache", ".album_thumbs"
    )

    fun isFolderExcludedByDefault(folderPath: String?, folderName: String?): Boolean {
        val name = (folderName ?: "").lowercase().trim('/')
        val path = (folderPath ?: "").lowercase().trim('/')
        if (DEFAULT_EXCLUDED_FOLDERS.contains(name)) return true
        if (path.isNotEmpty() && path.split('/').any { part -> DEFAULT_EXCLUDED_FOLDERS.contains(part) }) return true
        return false
    }

    fun isSystemHiddenFolder(folderPath: String, folderName: String): Boolean {
        val name = folderName.lowercase().trim('/')
        val path = folderPath.lowercase().trim('/')
        if (name.startsWith(".")) return true
        if (path.split('/').any { it.startsWith(".") && it.length > 1 }) return true
        return false
    }

    fun isItemExcluded(item: com.medianest.data.model.MediaItem): Boolean {
        if (item.isExcluded) return true
        val name = item.title.lowercase()
        val bucket = (item.bucketName ?: "").lowercase()
        val relPath = (item.relativePath ?: "").lowercase()

        if (isFolderExcludedByDefault(relPath, bucket)) return true

        return DEFAULT_EXCLUDED_FOLDERS.any { hn ->
            bucket == hn || name.contains(hn) || relPath.split('/').any { it == hn }
        }
    }

    fun isItemHidden(item: com.medianest.data.model.MediaItem): Boolean {
        if (item.isHidden) return true
        val name = item.title.lowercase()
        val bucket = (item.bucketName ?: "").lowercase()
        val relPath = (item.relativePath ?: "").lowercase()

        return name.startsWith(".") || bucket.startsWith(".") || relPath.split('/').any { it.startsWith(".") && it.length > 1 }
    }

    fun isItemHiddenOrExcluded(item: com.medianest.data.model.MediaItem): Boolean {
        return isItemExcluded(item) || isItemHidden(item)
    }

    fun deleteMediaUri(context: Context, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val f = File(path)
                    if (f.exists()) f.delete() else true
                } else false
            } else {
                val rows = context.contentResolver.delete(uri, null, null)
                rows > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
