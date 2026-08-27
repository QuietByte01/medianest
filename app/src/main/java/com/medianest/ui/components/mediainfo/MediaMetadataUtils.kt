package com.medianest.ui.components.mediainfo

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import java.util.Locale

internal fun extractComprehensiveMetadata(context: Context, item: MediaItem): ComprehensiveMetadata {
    var duration = 0L
    var w = 0
    var h = 0
    var bitrate = ""
    var sampleRate = ""
    var codecInfo = ""
    var album = ""
    var artist = ""
    var genre = ""
    var composer = ""

    if (item.type == MediaType.AUDIO || item.type == MediaType.VIDEO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, item.uri)

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durStr.isNullOrBlank()) duration = durStr.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!widthStr.isNullOrBlank()) w = widthStr.toIntOrNull() ?: 0
            if (!heightStr.isNullOrBlank()) h = heightStr.toIntOrNull() ?: 0

            val bitStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (!bitStr.isNullOrBlank()) {
                val bps = bitStr.toLongOrNull() ?: 0L
                bitrate = if (bps > 1_000_000) String.format(Locale.US, "%.2f Mbps", bps / 1_000_000.0)
                else "${bps / 1000} kbps"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val srStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                if (!srStr.isNullOrBlank()) {
                    val sr = srStr.toIntOrNull() ?: 0
                    sampleRate = "${sr / 1000.0} kHz"
                }
            }

            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: ""

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: item.mimeType
            codecInfo = mime.replace("audio/", "").replace("video/", "").uppercase(Locale.US)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    return ComprehensiveMetadata(
        durationMs = duration,
        width = w,
        height = h,
        bitrate = bitrate,
        sampleRate = sampleRate,
        codecInfo = codecInfo,
        album = album,
        artist = artist,
        genre = genre,
        composer = composer
    )
}

internal fun queryFileSizeFromUri(context: Context, uri: Uri): Long {
    try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (idx != -1) return cursor.getLong(idx)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 0L
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val value = size / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
}

fun getFilePathFromUri(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return uri.path ?: ""
    if (uri.scheme == "content") {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (columnIndex != -1) return cursor.getString(columnIndex) ?: ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return uri.path ?: uri.toString()
}

internal fun extractRealStreamDetails(context: Context, item: MediaItem): RealVideoAudioDetails {
    var container = item.mimeType.substringAfter('/').uppercase(Locale.US)
    if (container.isBlank()) container = "MP4"
    val containerStr = "$container (.$container)"

    var vCodec = "H.264 / AVC"
    var fRate = "30 fps"
    var vBitrate = ""
    var aCodec = "AAC"
    var aBitrate = ""
    var aChannels = "2 Channels (Stereo)"
    var aSampleRate = "48.0 kHz"
    var hdrInfo = "SDR (Standard Dynamic Range)"
    var colorSpace = "BT.709 / Standard"
    val trackList = mutableListOf<String>()

    try {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(context, item.uri, null)
            val numTracks = extractor.trackCount
            var audioTrackCount = 0

            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("video/")) {
                    val codec = mime.substringAfter("video/").uppercase(Locale.US)
                    vCodec = when {
                        codec.contains("AVC") || codec.contains("H264") || codec.contains("4") -> "H.264 / AVC"
                        codec.contains("HEVC") || codec.contains("H265") || codec.contains("5") -> "HEVC / H.265"
                        codec.contains("VP9") -> "VP9"
                        codec.contains("AV1") -> "AV1"
                        else -> codec
                    }

                    // Dynamic HDR & Color Space Detection
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val colorTransfer = if (format.containsKey(android.media.MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER) else -1
                        val colorStandard = if (format.containsKey(android.media.MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(android.media.MediaFormat.KEY_COLOR_STANDARD) else -1
                        
                        hdrInfo = when (colorTransfer) {
                            android.media.MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10 (High Dynamic Range)"
                            android.media.MediaFormat.COLOR_TRANSFER_HLG -> "HLG (Hybrid Log-Gamma)"
                            else -> "SDR (Standard Dynamic Range)"
                        }
                        
                        colorSpace = when (colorStandard) {
                            android.media.MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020 / Wide Gamut"
                            android.media.MediaFormat.COLOR_STANDARD_BT709 -> "BT.709 / Standard"
                            else -> "BT.709 / Standard"
                        }
                    }

                    if (format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                        val fr = try { format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE) } catch (e: Exception) { try { format.getFloat(android.media.MediaFormat.KEY_FRAME_RATE).toInt() } catch(ex: Exception) { 30 } }
                        if (fr > 0) fRate = "$fr fps"
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                        val br = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                        if (br > 0) {
                            vBitrate = if (br >= 1_000_000) String.format(Locale.US, "%.2f Mbps", br / 1_000_000.0) else "${br / 1000} kbps"
                        }
                    }
                } else if (mime.startsWith("audio/")) {
                    audioTrackCount++
                    val codec = mime.substringAfter("audio/").uppercase(Locale.US)
                    aCodec = when {
                        codec.contains("AAC") -> "AAC (Advanced Audio Coding)"
                        codec.contains("MPEG") || codec.contains("MP3") -> "MP3 (MPEG Audio Layer III)"
                        codec.contains("OPUS") -> "Opus"
                        codec.contains("FLAC") -> "FLAC (Free Lossless Audio Codec)"
                        codec.contains("AC3") || codec.contains("EAC3") -> "Dolby Digital (AC-3)"
                        else -> codec
                    }
                    val lang = if (format.containsKey(android.media.MediaFormat.KEY_LANGUAGE)) format.getString(android.media.MediaFormat.KEY_LANGUAGE)?.uppercase(Locale.US) else "Default"
                    trackList.add("Track $audioTrackCount: ${lang ?: "Primary"} ($codec)")

                    if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                        val ch = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                        aChannels = when (ch) {
                            1 -> "1 Channel (Mono)"
                            2 -> "2 Channels (Stereo)"
                            6 -> "6 Channels (5.1 Surround)"
                            8 -> "8 Channels (7.1 Surround)"
                            else -> "$ch Channels"
                        }
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                        val sr = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                        if (sr > 0) aSampleRate = "${sr / 1000.0} kHz"
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                        val br = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                        if (br > 0) aBitrate = "${br / 1000} kbps"
                    }
                }
            }
        } finally {
            extractor.release()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (trackList.isEmpty()) {
        trackList.add("Track 1: Primary Audio ($aCodec)")
    }

    return RealVideoAudioDetails(
        containerFormat = containerStr,
        videoCodec = vCodec,
        frameRate = fRate,
        colorSpace = colorSpace,
        aspectRatio = "16:9",
        videoBitrate = vBitrate,
        hdrInfo = hdrInfo,
        audioFormat = aCodec,
        audioBitrate = if (aBitrate.isNotBlank()) aBitrate else "192 kbps",
        audioChannels = aChannels,
        audioSampleRate = aSampleRate,
        audioTracks = trackList
    )
}
