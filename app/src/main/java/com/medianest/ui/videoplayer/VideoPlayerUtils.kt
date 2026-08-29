package com.medianest.ui.videoplayer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.formatDuration
import com.medianest.util.setDataSourceSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun safeFormatDuration(context: Context, item: MediaItem): String {
    if (item.durationMs > 0) return formatDuration(item.durationMs)
    var retriever: MediaMetadataRetriever? = null
    return try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSourceSafe(context, item.uri)
        val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val duration = timeStr?.toLongOrNull() ?: 0L
        if (duration > 0) formatDuration(duration) else "00:00"
    } catch (e: Exception) {
        "00:00"
    } finally {
        try { retriever?.release() } catch (_: Exception) {}
    }
}

fun captureVideoFrame(
    context: Context,
    item: MediaItem?,
    currentPositionMs: Long
) {
    if (item?.uri == null) return
    CoroutineScope(Dispatchers.IO).launch {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSourceSafe(context, item.uri)
            val positionUs = currentPositionMs * 1000L
            val bitmap = retriever.getFrameAtTime(
                positionUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            if (bitmap != null) {
                saveScreenshot(context, bitmap)
            } else {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Frame extraction failed", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Unable to extract frame", Toast.LENGTH_SHORT).show()
            }
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }
}

fun saveScreenshot(context: Context, bitmap: Bitmap) {
    val filename = "Video_Screenshot_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
        }
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
    (context as? Activity)?.runOnUiThread {
        Toast.makeText(context, "Screenshot captured & saved!", Toast.LENGTH_SHORT).show()
    }
}

fun getBreadcrumbParts(context: Context, item: MediaItem?): List<String> {
    val realPath = item?.uri?.let { com.medianest.ui.components.mediainfo.getFilePathFromUri(context, it) } ?: ""
    return if (realPath.isNotBlank()) {
        val storagePrefix = "/storage/emulated/0/"
        val cleanPath = if (realPath.startsWith(storagePrefix)) {
            realPath.removePrefix(storagePrefix)
        } else {
            realPath.trim('/')
        }
        val folderPath = if (cleanPath.contains('/')) cleanPath.substringBeforeLast('/') else cleanPath
        val segments = folderPath.split('/').filter { it.isNotBlank() }
        if (segments.isNotEmpty()) {
            listOf("Internal Storage") + segments
        } else {
            listOf("Internal Storage", "Download")
        }
    } else {
        val rel = item?.relativePath?.trim('/')
        if (!rel.isNullOrBlank()) {
            listOf("Internal Storage") + rel.split('/').filter { it.isNotBlank() }
        } else if (!item?.bucketName.isNullOrBlank()) {
            listOf("Internal Storage", item!!.bucketName!!)
        } else {
            listOf("Internal Storage", "Download", "Movies")
        }
    }
}
