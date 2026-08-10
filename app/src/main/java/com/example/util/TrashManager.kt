package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.MediaItem
import com.example.ui.components.getFilePathFromUri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashedMediaItem(
    val id: String,
    val originalPath: String,
    val trashedPath: String,
    val title: String,
    val mimeType: String,
    val size: Long,
    val trashedTimestamp: Long
)

object TrashManager {
    private const val TAG = "TrashManager"

    private fun getTrashDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, ".trash")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getManifestFile(context: Context): File {
        return File(getTrashDir(context), "trash_manifest.json")
    }

    @Synchronized
    fun getTrashedItems(context: Context): List<TrashedMediaItem> {
        val manifestFile = getManifestFile(context)
        if (!manifestFile.exists()) return emptyList()
        val list = mutableListOf<TrashedMediaItem>()
        try {
            val jsonStr = manifestFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TrashedMediaItem(
                        id = obj.optString("id"),
                        originalPath = obj.optString("originalPath"),
                        trashedPath = obj.optString("trashedPath"),
                        title = obj.optString("title"),
                        mimeType = obj.optString("mimeType"),
                        size = obj.optLong("size"),
                        trashedTimestamp = obj.optLong("trashedTimestamp")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading trash manifest", e)
        }
        return list
    }

    @Synchronized
    private fun saveManifest(context: Context, items: List<TrashedMediaItem>) {
        try {
            val array = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("originalPath", item.originalPath)
                    put("trashedPath", item.trashedPath)
                    put("title", item.title)
                    put("mimeType", item.mimeType)
                    put("size", item.size)
                    put("trashedTimestamp", item.trashedTimestamp)
                }
                array.put(obj)
            }
            getManifestFile(context).writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving trash manifest", e)
        }
    }

    fun moveToTrash(context: Context, item: MediaItem): Boolean {
        return try {
            val sourcePath = getFilePathFromUri(context, item.uri) ?: item.uri.path ?: ""
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                // MediaStore deletion if physical file path cannot be moved directly
                context.contentResolver.delete(item.uri, null, null)
                return true
            }

            val trashDir = getTrashDir(context)
            val trashedFile = File(trashDir, "${System.currentTimeMillis()}_${sourceFile.name}")
            if (sourceFile.renameTo(trashedFile)) {
                // Delete from MediaStore catalog so it doesn't show in standard queries
                try { context.contentResolver.delete(item.uri, null, null) } catch (_: Exception) {}

                val current = getTrashedItems(context).toMutableList()
                val trashedItem = TrashedMediaItem(
                    id = System.currentTimeMillis().toString(),
                    originalPath = sourceFile.absolutePath,
                    trashedPath = trashedFile.absolutePath,
                    title = item.title,
                    mimeType = item.mimeType,
                    size = item.size,
                    trashedTimestamp = System.currentTimeMillis()
                )
                current.add(0, trashedItem)
                saveManifest(context, current)
                true
            } else {
                // Safe copy fallback without deleting original unless copy succeeds
                var copySuccess = false
                try {
                    sourceFile.inputStream().use { input ->
                        trashedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    copySuccess = sourceFile.delete()
                } catch (e: Exception) {
                    trashedFile.delete()
                }

                if (copySuccess) {
                    try { context.contentResolver.delete(item.uri, null, null) } catch (_: Exception) {}

                    val current = getTrashedItems(context).toMutableList()
                    val trashedItem = TrashedMediaItem(
                        id = System.currentTimeMillis().toString(),
                        originalPath = sourceFile.absolutePath,
                        trashedPath = trashedFile.absolutePath,
                        title = item.title,
                        mimeType = item.mimeType,
                        size = item.size,
                        trashedTimestamp = System.currentTimeMillis()
                    )
                    current.add(0, trashedItem)
                    saveManifest(context, current)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move file to trash", e)
            false
        }
    }

    fun restoreItem(context: Context, item: TrashedMediaItem): Boolean {
        return try {
            val trashedFile = File(item.trashedPath)
            val originalFile = File(item.originalPath)
            originalFile.parentFile?.mkdirs()

            if (trashedFile.exists() && trashedFile.renameTo(originalFile)) {
                val current = getTrashedItems(context).filter { it.id != item.id }
                saveManifest(context, current)

                // Trigger MediaScanner so file reappears in MediaStore
                android.media.MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(originalFile.absolutePath),
                    arrayOf(item.mimeType),
                    null
                )
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore item", e)
            false
        }
    }

    fun deletePermanently(context: Context, item: TrashedMediaItem): Boolean {
        return try {
            val trashedFile = File(item.trashedPath)
            if (trashedFile.exists()) {
                trashedFile.delete()
            }
            val current = getTrashedItems(context).filter { it.id != item.id }
            saveManifest(context, current)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete item permanently", e)
            false
        }
    }

    fun emptyTrash(context: Context): Boolean {
        return try {
            val items = getTrashedItems(context)
            for (item in items) {
                val f = File(item.trashedPath)
                if (f.exists()) f.delete()
            }
            saveManifest(context, emptyList())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to empty trash", e)
            false
        }
    }
}
