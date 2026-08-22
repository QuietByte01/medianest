package com.medianest.ui.components.mediainfo

data class ComprehensiveMetadata(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: String = "",
    val sampleRate: String = "",
    val volumeDb: String = "",
    val frameRate: String = "",
    val codecInfo: String = "",
    val album: String = "",
    val artist: String = "",
    val genre: String = "",
    val composer: String = "",
    val location: String = "",
    val dateTaken: String = "",
    val cameraMakeModel: String = "",
    val exifDetails: String = ""
)

data class RealVideoAudioDetails(
    val containerFormat: String = "MP4 (.mp4)",
    val videoCodec: String = "H.264 / AVC",
    val frameRate: String = "30 fps",
    val colorSpace: String = "BT.709 / Standard",
    val aspectRatio: String = "16:9",
    val videoBitrate: String = "Auto",
    val hdrInfo: String = "SDR (Standard Dynamic Range)",
    val audioFormat: String = "AAC (Advanced Audio Coding)",
    val audioBitrate: String = "192 kbps",
    val audioChannels: String = "2 Channels (Stereo)",
    val audioSampleRate: String = "48.0 kHz",
    val audioTracks: List<String> = listOf("Track 1: Primary Audio")
)
