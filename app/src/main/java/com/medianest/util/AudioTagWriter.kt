package com.medianest.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AudioTagWriter — Native Android MediaStore Audio Tag Writer.
 *
 * Updates ID3 metadata tags (title, artist, album, year) directly via Android's native
 * MediaStore API and MediaScannerConnection without external native dependencies.
 */
object AudioTagWriter {

    private const val TAG = "AudioTagWriter"

    data class TagData(
        val title: String,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val year: String? = null,
        val composer: String? = null,
        val trackNumber: String? = null,
        val lyrics: String? = null,
        val albumArtUri: String? = null
    )

    suspend fun writeAudioTags(
        context: Context,
        audioUri: Uri,
        tags: TagData
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, tags.title)
                tags.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                tags.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                tags.year?.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
            }

            val updatedRows = context.contentResolver.update(audioUri, values, null, null)
            val path = audioUri.path
            if (!path.isNullOrBlank()) {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
            Logger.i(TAG, "Successfully updated MediaStore audio tags ($updatedRows rows)")
            return@withContext true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write audio tags natively: ${e.message}", e)
            return@withContext false
        }
    }
}
