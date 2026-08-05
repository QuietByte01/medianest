package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File

object FolderHiddenUtils {
    fun isSystemHiddenFolder(folderPath: String, folderName: String): Boolean {
        // Folder name starts with "."
        if (folderName.startsWith(".")) return true
        
        // Any path component starts with "."
        if (folderPath.contains("/.")) return true
        
        return false
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

