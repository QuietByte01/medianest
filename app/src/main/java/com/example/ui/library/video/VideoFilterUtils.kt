package com.example.ui.library.video

import android.net.Uri
import com.example.MediaNestApp
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.util.TrashManager
import java.io.File
import java.util.Locale

fun filterVideoList(videos: List<MediaItem>, filterTab: String): List<MediaItem> {
    if (filterTab == "TRASH") {
        val trashed = TrashManager.getTrashedItems(MediaNestApp.instance)
            .filter { it.mimeType.startsWith("video") }
        return trashed.map { info ->
            MediaItem(
                id = info.originalPath.hashCode().toLong(),
                uri = Uri.fromFile(File(info.trashedPath)),
                title = info.title,
                durationMs = 0L,
                size = info.size,
                dateAdded = info.trashedTimestamp,
                mimeType = info.mimeType,
                type = MediaType.VIDEO,
                bucketName = "Trash"
            )
        }
    }
    return when (filterTab) {
        "ALL" -> videos
        "CLIPS" -> videos.filter { isClipsAndRecordings(it) }
        "SHORTS" -> videos.filter { isShorts(it) }
        "SERIES" -> videos.filter { isTVSeries(it) }
        "MUSIC" -> videos.filter { isMusicVideo(it) }
        "MOVIES" -> videos.filter { isMovie(it) }
        "DOWNLOADED" -> videos.filter { isDownloaded(it) }
        "SOCIAL" -> videos.filter { isSocialMediaVideo(it) }
        "EDITED" -> videos.filter { isEditedVideo(it) }
        else -> videos
    }
}

fun isEditedVideo(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()
    val videoEditorRegex = Regex("video\\s*editor")
    val editKeywords = listOf(
        "editor",
        "studio",
        "compress",
        "compressor",
        "capcut",
        "kinemaster",
        "vn",
        "inshot",
        "edited"
    )

    val matchesKeyword = editKeywords.any { path.contains(it) }

    return path.contains("/dcim/video editor/") ||
            videoEditorRegex.containsMatchIn(path) ||
            matchesKeyword
}

fun isClipsAndRecordings(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    val title = item.title.lowercase()

    if (path.contains("dcim/camera/") || item.bucketName?.lowercase() == "camera") {
        return true
    }

    val excludedKeywords = listOf(
        "instagram", "snapchat", "tiktok", "facebook", "whatsapp", "telegram", "twitter", "x", "reddit",
        "editor", "studio", "compress", "compressor", "gif", "capcut", "kinemaster", "vn", "inshot",
        "game", "gaming", "screenrecord", "recorder"
    )

    if (excludedKeywords.any { path.contains(it) }) {
        return false
    }

    val isInDcim = path.contains("dcim/")
    val isRecording = path.contains("recording") || path.contains("screenrecord")
    val isCameraVid = title.startsWith("vid_")

    return isInDcim || isRecording || isCameraVid
}

fun isShorts(item: MediaItem): Boolean {
    val isVerticalOrSquare = (item.height >= item.width && item.width > 0) || (item.aspectRatio in 0.01f..1.05f)
    val isUnder90s = item.durationMs in 1L..90_000L || item.durationMs == 0L

    val title = item.title.lowercase()
    val hasKeyword = title.contains("short") ||
            title.contains("reel") ||
            title.contains("tiktok") ||
            title.contains("clip") ||
            title.contains("video clip")

    return (isVerticalOrSquare && isUnder90s) || hasKeyword
}

fun extractSeriesName(item: MediaItem): String {
    val relPath = item.relativePath?.trim('/') ?: ""
    val bucket = item.bucketName ?: "Web Series"
    val parts = relPath.split('/').filter { it.isNotBlank() }
    for (i in parts.indices) {
        val p = parts[i]
        if (p.contains("season", ignoreCase = true) || p.contains("s0", ignoreCase = true) || p.contains("s1", ignoreCase = true) || p.contains("s2", ignoreCase = true)) {
            if (i > 0) return parts[i - 1]
        }
        if (p.equals("Web Series", ignoreCase = true) || p.equals("Series", ignoreCase = true) || p.equals("TV", ignoreCase = true) || p.equals("TV Shows", ignoreCase = true)) {
            if (i + 1 < parts.size) return parts[i + 1]
        }
    }
    return bucket
}

fun extractSeasonName(item: MediaItem): String {
    val path = ((item.relativePath ?: "") + "/" + item.title).lowercase()
    val match = Regex("(?i)(season\\s*\\d{1,2}|s\\d{1,2})").find(path)
    if (match != null) {
        val num = Regex("\\d+").find(match.value)?.value?.toIntOrNull() ?: 1
        return "Season %02d".format(num)
    }
    return "Season 01"
}

fun isTVSeries(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    val patternSeasonEpisode = Regex("(?i)s\\d{1,2}e\\d{1,2}")
    val patternNumberXNumber = Regex("(?i)\\d{1,2}x\\d{1,2}")
    val hasPattern = patternSeasonEpisode.containsMatchIn(path) || patternNumberXNumber.containsMatchIn(path)
    val isSeriesFolder = path.contains("/series/") || path.contains("/tv/") || path.contains("/shows/") || path.contains("/web series/")
    val isAtLeast20Min = item.durationMs >= 1_200_000L || item.durationMs == 0L
    return isAtLeast20Min && (hasPattern || isSeriesFolder || path.contains("season") || path.contains("episode"))
}

fun isMovie(item: MediaItem): Boolean {
    val title = item.title.lowercase()
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    val exclusionKeywords = listOf("recording", "screen_recording", "live", "test", "interview", "webinar", "zoom", "meeting", "tutorial", "presentation")
    if (exclusionKeywords.any { title.contains(it) || path.contains(it) }) return false

    val isLong = item.durationMs >= 2_400_000L
    val isAtLeast20Min = item.durationMs >= 1_200_000L

    if (isLong && !isTVSeries(item)) return true
    if (isTVSeries(item) || isClipsAndRecordings(item) || isShorts(item)) return false
    if (!isAtLeast20Min) return false

    val inMovieFolder = path.contains("/movies/") ||
            path.contains("/movie/") ||
            path.contains("/cinema/") ||
            path.contains("film")

    val hasMovieQualityTag = path.contains("1080p") || path.contains("720p") ||
            path.contains("bluray") || path.contains("webrip") ||
            path.contains("x264") || path.contains("x265") || path.contains("hdrip")

    val inGeneralFolder = path.contains("/videos/") ||
            path.contains("/download/") ||
            path.contains("/downloads/")

    return inMovieFolder || hasMovieQualityTag || (inGeneralFolder && isLong)
}

fun isMovieOrShow(item: MediaItem): Boolean {
    return isMovie(item) || isTVSeries(item)
}

fun isMusicVideo(item: MediaItem): Boolean {
    val title = item.title.lowercase()
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    val hasValidDuration = item.durationMs in 60_000L..420_000L
    val hasUnknownDurationButArtist = item.durationMs == 0L && (!item.artist.isNullOrBlank() && item.artist != "<unknown>")

    if (!hasValidDuration && !hasUnknownDurationButArtist) return false

    val isHorizontal = (item.width >= item.height && item.width > 0) || item.aspectRatio >= 1.0f
    if (!isHorizontal) return false

    val hasArtist = !item.artist.isNullOrBlank() && item.artist != "<unknown>"
    val hasAlbumOrGenre = !item.album.isNullOrBlank()

    val musicPathKeywords = listOf("/music/", "/songs/", "/mv/", "/mvs/", "/audio/", "vevo", "soundtrack")
    val matchesMusicPath = musicPathKeywords.any { path.contains(it) }

    val musicTitleKeywords = listOf("official video", "audio", "lyrics", "ft.", "feat", "music video", "remix", "cover")
    val matchesMusicTitle = musicTitleKeywords.any { title.contains(it) }

    return hasArtist || hasAlbumOrGenre || matchesMusicPath || matchesMusicTitle
}

fun isDownloaded(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    return path.contains("/download") || path.contains("/chrome/")
}

fun isSocialMediaVideo(item: MediaItem): Boolean {
    val title = item.title.lowercase()
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    val socialNames = listOf(
        "whatsapp", "telegram", "instagram", "insta", "facebook", "fb", "tiktok", "snapchat", "snap", "pinterest", "twitter", "reddit"
    )
    val matchesTitle = socialNames.any { title.contains(it) }

    val targetFolders = listOf(
        "whatsapp/media",
        "pictures/whatsapp images",
        "telegram",
        "pictures/instagram",
        "download/instagram",
        "dcim/instagram",
        "pictures/facebook",
        "movies/tiktok",
        "pictures/tiktok",
        "dcim/tiktok",
        "pictures/snapchat",
        "dcim/snapchat",
        "pictures/pinterest",
        "pictures/twitter",
        "pictures/x",
        "pictures/reddit"
    )
    val matchesFolder = targetFolders.any { path.contains(it) }

    val socialPackages = listOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.twitter.android"
    )

    val matchesAndroidDir = socialPackages.any { pkg ->
        path.contains("/android/data/$pkg") || path.contains("/android/media/$pkg")
    }

    return matchesTitle || matchesFolder || matchesAndroidDir
}

fun formatFolderSize(totalBytes: Long): String {
    if (totalBytes <= 0) return "0 MB"
    val gb = totalBytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = totalBytes / (1024.0 * 1024.0)
        String.format(Locale.US, "%.0f MB", mb.coerceAtLeast(1.0))
    }
}
