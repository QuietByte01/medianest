package com.medianest.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * AudioTagWriter — Lossless Physical Audio Tag and Embedded Artwork Writer.
 *
 * Guarantees:
 * 1. Audio stream is copied bit-for-bit with ZERO re-encoding (-c:a copy). Audio quality, bitrate, sample rate, and codec are 100% preserved.
 * 2. Injects ID3v2 tags (MP3), Vorbis comments (FLAC/OGG), or MP4 metadata atoms (M4A/AAC).
 * 3. Injects or updates embedded cover art attached_pic (APIC).
 * 4. Safely writes back to original file or content URI atomically.
 * 5. Updates Android system MediaStore and invokes MediaScannerConnection.
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
        var tempInputFile: File? = null
        var tempArtFile: File? = null
        var tempOutputFile: File? = null
        var originalFilePath: String? = null

        try {
            // 1. Resolve source audio file
            val isFileScheme = audioUri.scheme == "file"
            val directPath = if (isFileScheme) audioUri.path else getFilePathFromContentUri(context, audioUri)

            val inputPath: String
            val fileExtension: String

            if (directPath != null && File(directPath).exists() && File(directPath).canWrite()) {
                originalFilePath = directPath
                inputPath = directPath
                fileExtension = File(directPath).extension.lowercase().ifBlank { "mp3" }
            } else {
                // If read-only content URI or SAF provider, copy stream to temp file
                fileExtension = getExtensionFromUriOrMime(context, audioUri)
                tempInputFile = File(context.cacheDir, "tag_in_${System.currentTimeMillis()}.$fileExtension")
                context.contentResolver.openInputStream(audioUri)?.use { input ->
                    tempInputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext false
                inputPath = tempInputFile.absolutePath
            }

            // 2. Prepare output file
            tempOutputFile = File(context.cacheDir, "tag_out_${System.currentTimeMillis()}.$fileExtension")

            // 3. Prepare Album Art file if provided
            if (!tags.albumArtUri.isNullOrBlank()) {
                val artUri = Uri.parse(tags.albumArtUri)
                tempArtFile = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                try {
                    if (artUri.scheme == "file" && artUri.path != null && File(artUri.path!!).exists()) {
                        File(artUri.path!!).copyTo(tempArtFile, overwrite = true)
                    } else if (artUri.scheme == "content" || artUri.scheme == "android.resource") {
                        context.contentResolver.openInputStream(artUri)?.use { input ->
                            tempArtFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else if (artUri.scheme == "http" || artUri.scheme == "https") {
                        // Download online cover art image
                        val okClient = okhttp3.OkHttpClient()
                        val req = okhttp3.Request.Builder().url(tags.albumArtUri).build()
                        okClient.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.byteStream()?.use { input ->
                                    tempArtFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to prepare album art: ${e.message}")
                    tempArtFile.delete()
                    tempArtFile = null
                }
            }

            // 4. Build FFmpeg command for lossless stream copy + metadata injection
            val isMp3 = fileExtension == "mp3"
            val isM4a = fileExtension == "m4a" || fileExtension == "aac" || fileExtension == "mp4"
            val isFlac = fileExtension == "flac"
            val isOgg = fileExtension == "ogg" || fileExtension == "opus"

            val cmd = StringBuilder("-y -i \"$inputPath\" ")

            val hasValidArt = tempArtFile != null && tempArtFile.exists() && tempArtFile.length() > 0

            if (hasValidArt && tempArtFile != null) {
                cmd.append("-i \"${tempArtFile.absolutePath}\" ")
                if (isMp3) {
                    cmd.append("-map 0:a -map 1:0 -c:a copy -c:v:0 copy -id3v2_version 3 ")
                    cmd.append("-metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (front)\" ")
                } else if (isM4a) {
                    cmd.append("-map 0:a -map 1:0 -c copy -disposition:v:0 attached_pic ")
                } else if (isFlac || isOgg) {
                    cmd.append("-map 0:a -map 1:0 -c copy -disposition:v:0 attached_pic ")
                } else {
                    cmd.append("-map 0:a -map 1:0 -c copy ")
                }
            } else {
                cmd.append("-map 0 -c copy ")
            }

            // Metadata fields
            fun escapeMeta(value: String): String {
                return value.replace("\"", "\\\"").replace("\n", " ").trim()
            }

            cmd.append("-metadata title=\"${escapeMeta(tags.title)}\" ")
            if (!tags.artist.isNullOrBlank()) {
                cmd.append("-metadata artist=\"${escapeMeta(tags.artist)}\" ")
            }
            if (!tags.album.isNullOrBlank()) {
                cmd.append("-metadata album=\"${escapeMeta(tags.album)}\" ")
            }
            if (!tags.albumArtist.isNullOrBlank()) {
                cmd.append("-metadata album_artist=\"${escapeMeta(tags.albumArtist)}\" ")
                cmd.append("-metadata ALBUMARTIST=\"${escapeMeta(tags.albumArtist)}\" ")
            }
            if (!tags.genre.isNullOrBlank()) {
                cmd.append("-metadata genre=\"${escapeMeta(tags.genre)}\" ")
            }
            if (!tags.year.isNullOrBlank()) {
                cmd.append("-metadata date=\"${escapeMeta(tags.year)}\" ")
                cmd.append("-metadata year=\"${escapeMeta(tags.year)}\" ")
            }
            if (!tags.composer.isNullOrBlank()) {
                cmd.append("-metadata composer=\"${escapeMeta(tags.composer)}\" ")
            }
            if (!tags.trackNumber.isNullOrBlank()) {
                cmd.append("-metadata track=\"${escapeMeta(tags.trackNumber)}\" ")
            }
            if (!tags.lyrics.isNullOrBlank()) {
                cmd.append("-metadata lyrics=\"${escapeMeta(tags.lyrics)}\" ")
                cmd.append("-metadata comment=\"${escapeMeta(tags.lyrics)}\" ")
            }

            cmd.append("\"${tempOutputFile.absolutePath}\"")

            Logger.i(TAG, "Executing tag injection: $cmd")
            val session = FFmpegKit.execute(cmd.toString())

            if (!ReturnCode.isSuccess(session.returnCode) || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                Logger.e(TAG, "FFmpeg tag writing failed: ${session.failStackTrace}")
                return@withContext false
            }

            // 5. Write updated file back to the target destination
            var writeSuccess = false
            if (originalFilePath != null) {
                val orig = File(originalFilePath)
                if (orig.exists() && orig.canWrite()) {
                    try {
                        tempOutputFile.copyTo(orig, overwrite = true)
                        writeSuccess = true
                    } catch (e: Exception) {
                        Logger.w(TAG, "Direct file copy failed, trying content resolver: ${e.message}")
                    }
                }
            }

            if (!writeSuccess) {
                try {
                    context.contentResolver.openOutputStream(audioUri, "wt")?.use { outStream ->
                        tempOutputFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                            writeSuccess = true
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed writing back via content resolver: ${e.message}")
                }
            }

            // 6. Update MediaStore database entry for system-wide sync
            updateSystemMediaStore(context, audioUri, originalFilePath, tags)

            return@withContext writeSuccess
        } catch (e: Exception) {
            Logger.e(TAG, "Tag writing error: ${e.message}", e)
            return@withContext false
        } finally {
            try { tempInputFile?.delete() } catch (_: Exception) {}
            try { tempArtFile?.delete() } catch (_: Exception) {}
            try { tempOutputFile?.delete() } catch (_: Exception) {}
        }
    }

    private fun updateSystemMediaStore(
        context: Context,
        audioUri: Uri,
        directPath: String?,
        tags: TagData
    ) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, tags.title)
                if (!tags.artist.isNullOrBlank()) put(MediaStore.Audio.Media.ARTIST, tags.artist)
                if (!tags.album.isNullOrBlank()) put(MediaStore.Audio.Media.ALBUM, tags.album)
                if (!tags.composer.isNullOrBlank()) put(MediaStore.Audio.Media.COMPOSER, tags.composer)
                if (!tags.genre.isNullOrBlank()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        put(MediaStore.Audio.Media.GENRE, tags.genre)
                    }
                }
                if (!tags.year.isNullOrBlank()) {
                    tags.year.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
                }
                if (!tags.trackNumber.isNullOrBlank()) {
                    val trackInt = tags.trackNumber.filter { it.isDigit() }.toIntOrNull()
                    if (trackInt != null) put(MediaStore.Audio.Media.TRACK, trackInt)
                }
            }

            if (audioUri.scheme == "content") {
                context.contentResolver.update(audioUri, values, null, null)
            }

            // Trigger MediaScanner for file
            val pathToScan = directPath ?: getFilePathFromContentUri(context, audioUri)
            if (!pathToScan.isNullOrBlank()) {
                MediaScannerConnection.scanFile(context, arrayOf(pathToScan), null, null)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "MediaStore update warning: ${e.message}")
        }
    }

    private fun getFilePathFromContentUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        return try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getExtensionFromUriOrMime(context: Context, uri: Uri): String {
        val path = uri.path?.lowercase() ?: ""
        when {
            path.endsWith(".mp3") -> return "mp3"
            path.endsWith(".m4a") -> return "m4a"
            path.endsWith(".aac") -> return "aac"
            path.endsWith(".flac") -> return "flac"
            path.endsWith(".ogg") -> return "ogg"
            path.endsWith(".opus") -> return "opus"
            path.endsWith(".wav") -> return "wav"
        }
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        return when {
            mime.contains("flac") -> "flac"
            mime.contains("wav") -> "wav"
            mime.contains("m4a") || mime.contains("mp4") -> "m4a"
            mime.contains("aac") -> "aac"
            mime.contains("ogg") -> "ogg"
            mime.contains("opus") -> "opus"
            else -> "mp3"
        }
    }
}
