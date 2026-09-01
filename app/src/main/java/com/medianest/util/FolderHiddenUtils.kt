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
        ".gallery_cache", ".album_thumbs",
        // WhatsApp generated/internal media
        "private", ".private", "whatsapp private", ".whatsapp private",
        "sent", ".sent", "whatsapp sent", ".whatsapp sent",
        "whatsapp stickers", ".whatsapp stickers", "whatsapp animated stickers", ".whatsapp animated stickers", "stickers", ".stickers",
        "whatsapp backup", "whatsapp backups", ".whatsapp backup", ".whatsapp backups", "backup", "backups", ".backup", ".backups",
        // Test folders
        "test", "tests", ".test", ".tests"
    )

    fun isFolderExcludedByDefault(folderPath: String?, folderName: String?): Boolean {
        val name = (folderName ?: "").lowercase().trim('/')
        if (name.isNotEmpty() && DEFAULT_EXCLUDED_FOLDERS.contains(name)) return true
        val path = (folderPath ?: "").lowercase().trim('/')
        if (path.isNotEmpty()) {
            for (ex in DEFAULT_EXCLUDED_FOLDERS) {
                if (path == ex || path.startsWith("$ex/") || path.endsWith("/$ex") || path.contains("/$ex/")) {
                    return true
                }
            }
        }
        return false
    }

    fun isSystemHiddenFolder(folderPath: String, folderName: String): Boolean {
        val name = folderName.lowercase().trim('/')
        if (name.startsWith(".")) return true
        val path = folderPath.lowercase().trim('/')
        if (path.startsWith(".") || path.contains("/.")) return true
        return false
    }

    fun isItemExcluded(item: com.medianest.data.model.MediaItem): Boolean {
        if (item.isExcluded) return true
        val bucket = (item.bucketName ?: "").lowercase()
        val relPath = (item.relativePath ?: "").lowercase()

        if (isFolderExcludedByDefault(relPath, bucket)) return true

        val name = item.title.lowercase()
        for (hn in DEFAULT_EXCLUDED_FOLDERS) {
            if (bucket == hn || name.contains(hn) || (relPath.isNotEmpty() && (relPath == hn || relPath.startsWith("$hn/") || relPath.endsWith("/$hn") || relPath.contains("/$hn/")))) {
                return true
            }
        }
        return false
    }

    fun isItemHidden(item: com.medianest.data.model.MediaItem): Boolean {
        if (item.isHidden) return true
        val name = item.title.lowercase()
        if (name.startsWith(".")) return true
        val bucket = (item.bucketName ?: "").lowercase()
        if (bucket.startsWith(".")) return true
        val relPath = (item.relativePath ?: "").lowercase()
        return relPath.startsWith(".") || relPath.contains("/.")
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
