package com.medianest.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.medianest.data.model.MediaItem

object IntentUtils {

    /**
     * Opens the specific media item in the system's default gallery or media player.
     */
    fun openInGallery(context: Context, item: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
