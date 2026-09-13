package com.medianest.util

import android.content.Context
import android.net.Uri
import com.medianest.data.model.MediaItem
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

    fun isItemExcluded(
        item: MediaItem,
        userExcludedFolders: Set<String> = emptySet()
    ): Boolean {
        if (item.isExcluded) return true
        val bucket = (item.bucketName ?: "").lowercase().trim('/')
        val relPath = (item.relativePath ?: "").lowercase().trim('/')

        if (isFolderExcludedByDefault(relPath, bucket)) return true

        val name = item.title.lowercase()
        for (hn in DEFAULT_EXCLUDED_FOLDERS) {
            if (bucket == hn || name.contains(hn) || (relPath.isNotEmpty() && (relPath == hn || relPath.startsWith("$hn/") || relPath.endsWith("/$hn") || relPath.contains("/$hn/")))) {
                return true
            }
        }

        if (userExcludedFolders.isNotEmpty()) {
            if (userExcludedFolders.contains(bucket) || userExcludedFolders.contains(relPath)) return true
            for (ex in userExcludedFolders) {
                val exLower = ex.lowercase().trim('/')
                if (exLower.isNotEmpty()) {
                    if (relPath == exLower || relPath.startsWith("$exLower/") || relPath.endsWith("/$exLower") || relPath.contains("/$exLower/") || bucket == exLower) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Fast system hidden check for dot-files, dot-folders, and .nomedia directories.
     */
    fun isItemHidden(item: MediaItem): Boolean {
        if (item.isHidden) return true
        val title = item.title
        if (title.isNotEmpty() && title[0] == '.') return true
        val bucket = item.bucketName
        if (!bucket.isNullOrEmpty() && bucket[0] == '.') return true
        val relPath = item.relativePath
        if (!relPath.isNullOrEmpty()) {
            return relPath.startsWith(".") || relPath.contains("/.")
        }
        return false
    }

    fun isItemHiddenOrExcluded(
        item: MediaItem,
        userExcludedFolders: Set<String> = emptySet()
    ): Boolean {
        return isItemExcluded(item, userExcludedFolders) || isItemHidden(item)
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
