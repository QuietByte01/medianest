package com.medianest.ui.library.video

import android.net.Uri
import com.medianest.MediaNestApp
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.util.TrashManager
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
    val isWallpaper = path.contains("wallpaper") || path.contains("wallpapers") || path.contains("wallhaven") || path.contains("zedge") || path.contains("backdrops") || path.contains("live wallpaper")
    if (isWallpaper) return false

    if (isFromExcludedCategoryFolder(item)) return false

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

private fun cleanSeriesName(name: String): String {
    // Remove season patterns ranging from 1 to 100 (e.g., "season 1", "season 100", "s01", "s100")
    val seasonRemovalPattern = Regex("(?i)\\b(season\\s*(?:[1-9][0-9]?|100)|s(?:[1-9][0-9]?|100)|s0?[1-9]|s[1-9][0-9]|s100)\\b")
    val withoutSeason = name.replace(seasonRemovalPattern, "")

    // Remove specified special characters and symbols: . , # ! ? - _ + = ) ( [ ] / { } & $ @ *
    val sanitized = withoutSeason.replace(Regex("[.,#!?\\-_+=()\\[\\]/{}&$@*]"), " ")

    // Trim extra whitespace resulting from replacements
    return sanitized.replace(Regex("\\s+"), " ").trim()
}

fun extractSeriesName(item: MediaItem): String {
    val relPath = item.relativePath?.trim('/') ?: ""
    val bucket = item.bucketName ?: "Web Series"
    val parts = relPath.split('/').filter { it.isNotBlank() }

    // Regex matching season numbers from 1 up to 100 (e.g., season 1, season 100, s1, s99, s01)
    val seasonPattern = Regex("(?i)^(season\\s*(?:[1-9][0-9]?|100)|s(?:[1-9][0-9]?|100)|s0?[1-9]|s[1-9][0-9]|s100)$")

    for (i in parts.indices) {
        val p = parts[i]

        // Check if the directory name matches pure season patterns from 1 to 100
        val isSeasonDirOnly = seasonPattern.matches(p.trim())

        if (isSeasonDirOnly) {
            // If the folder is only a season tag, return the parent directory if it exists
            if (i > 0) {
                return cleanSeriesName(parts[i - 1])
            }
        } else if (p.contains("season", ignoreCase = true) || Regex("(?i)\\bs\\d{1,3}\\b").containsMatchIn(p)) {
            // If it contains season info alongside other words, clean the current directory name
            return cleanSeriesName(p)
        }

        if (p.equals("Web Series", ignoreCase = true) || p.equals("Series", ignoreCase = true) || p.equals("TV", ignoreCase = true) || p.equals("TV Shows", ignoreCase = true)) {
            if (i + 1 < parts.size) {
                return cleanSeriesName(parts[i + 1])
            }
        }
    }

    // Fallback to cleaning the last part of the path or the bucket name
    val fallbackTarget = parts.lastOrNull() ?: bucket
    return cleanSeriesName(fallbackTarget)
}

fun extractSeasonName(item: MediaItem): String {
    val path = ((item.relativePath ?: "") + "/" + item.title).lowercase()

    // Comprehensive regex covering:
    // - "season", "s"
    // - Optional separators: spaces, hyphens, underscores, brackets, or no separator at all
    // - Numbers from 1 to 100 (supports leading zeros like 01 as well as single digits like 1)
    val seasonPattern = Regex("(?i)\\b(?:season|s)[\\s\\-_()\\[\\]]*((?:[1-9][0-9]?|100)|(?:0[1-9]|[1-9][0-9]|100))\\b")

    val matchResult = seasonPattern.find(path)
    if (matchResult != null) {
        // Group 1 contains the actual matched number captured inside the pattern
        val numStr = matchResult.groupValues.getOrNull(1) ?: matchResult.value
        val num = Regex("\\d+").find(numStr)?.value?.toIntOrNull() ?: 1

        // Format as a zero-padded two-digit string (e.g., Season 01, Season 10)
        return "Season %02d".format(num)
    }

    return "Season 01"
}

fun isTVSeries(item: MediaItem, allItems: List<MediaItem> = emptyList()): Boolean {
    // Construct a comprehensive lowercase string combining relative path, title, and URI for pattern matching
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    // Define regular expressions for standard TV show naming conventions (e.g., S01E02 or 1x02)
    val patternSeasonEpisode = Regex("(?i)s\\d{1,2}e\\d{1,2}")
    val patternNumberXNumber = Regex("(?i)\\d{1,2}x\\d{1,2}")

    // Check if path or title contains explicit episode/season formatting
    val hasPattern = patternSeasonEpisode.containsMatchIn(path) || patternNumberXNumber.containsMatchIn(path)

    // Check if the file resides in a designated directory for television content
    val isSeriesFolder = path.contains("/series/") || path.contains("/tv/") || path.contains("/shows/") || path.contains("/web series/")

    // Enforce duration constraints (at least 20 minutes, or 0L if duration is unknown/unprocessed)
    val isAtLeast20Min = item.durationMs >= 1_200_000L || item.durationMs == 0L

    // Evaluate explicit television markers combined with duration rules
    val matchesExplicitCriteria = isAtLeast20Min && (hasPattern || isSeriesFolder || path.contains("season") || path.contains("episode"))

    if (matchesExplicitCriteria) return true

    // Fallback: Check if multiple videos share significant repeated words, indicating a series batch
    if (allItems.isNotEmpty()) {
        val cleanedTitleWords = extractSignificantWords(item.title)

        // Count how many other media items share significant words with this item
        val matchingItemsCount = allItems.count { other ->
            if (other.uri == item.uri) return@count false
            val otherWords = extractSignificantWords(other.title)
            // If they share significant words, it's likely a series of episodes
            cleanedTitleWords.intersect(otherWords).isNotEmpty()
        }

        // If one or more other items share these terms, treat it as part of a TV series
        if (matchingItemsCount >= 3) {
            return true
        }
    }

    return false
}

fun isMovie(item: MediaItem, allItems: List<MediaItem> = emptyList()): Boolean {
    val title = item.title.lowercase()
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

    // 1. Exclusion keywords
    val exclusionKeywords = listOf("recording", "screen_recording", "live", "test", "interview", "webinar", "zoom", "meeting", "tutorial", "presentation")
    if (exclusionKeywords.any { title.contains(it) || path.contains(it) }) return false

    val isLong = item.durationMs >= 2_400_000L
    val isAtLeast20Min = item.durationMs >= 1_200_000L

    if (isLong && !isTVSeries(item)) return true
    if (isTVSeries(item) || isClipsAndRecordings(item) || isShorts(item)) return false
    if (!isAtLeast20Min) return false

    // 2. Check for repeated words across titles (Series detection)
    if (allItems.isNotEmpty()) {
        val cleanedTitleWords = extractSignificantWords(item.title)

        // Count how many other media items share significant words with this item
        val matchingItemsCount = allItems.count { other ->
            if (other.uri == item.uri) return@count false
            val otherWords = extractSignificantWords(other.title)
            // If they share significant words, it's likely a series/batch of episodes
            cleanedTitleWords.intersect(otherWords).isNotEmpty()
        }

        // If multiple videos share these words, treat them as a TV series and exclude
        if (matchingItemsCount >= 1) {
            return false
        }
    }

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

/**
 * Helper function to tokenize a title and filter out allowed exceptions
 * like quality tags and "dual audio".
 */
private fun extractSignificantWords(title: String): Set<String> {
    val normalized = title.lowercase()

    // Define terms to ignore during comparison
    val ignoredTerms = setOf(
        // Quality tags
        "1080p", "720p", "480p", "2160p", "4k", "bluray", "webrip",
        "web-dl", "x264", "x265", "hevc", "hdrip", "hdr", "camrip",
        // Specific exception mentioned
        "dual", "audio",
        // Common stop words or file artifacts that might create false positives
        "the", "and", "a", "an", "of", "in", "to", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
    )

    // Split title into words, remove punctuation, and filter out ignored terms
    return normalized.split(Regex("\\W+"))
        .filter { it.isNotBlank() && it !in ignoredTerms }
        .toSet()
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

fun isFromExcludedCategoryFolder(item: MediaItem): Boolean {
    val folderName = (item.bucketName ?: "").lowercase()
    val relPath = (item.relativePath ?: "").lowercase()
    val excluded = listOf("wallpaper", "movies", "songs","wallpapers", "movie", "song")
    return excluded.any { folderName.contains(it) || relPath.contains(it) }
}

fun isItemInCategory(
    item: MediaItem,
    category: MediaCategory,
    crossRefUris: Set<String>
): Boolean {
    val catName = category.name.trim().lowercase()
    
    // 1. Explicitly linked via DB
    val itemUri = item.uri.toString()
    val itemPath = item.uri.path ?: ""
    val isLinked = crossRefUris.contains(itemUri) || crossRefUris.contains(itemPath)
    if (isLinked) return true
    
    // 2. Exclude specific folders for automatic categorization
    if (isFromExcludedCategoryFolder(item)) return false

    // 3. Implicitly matched via keywords
    val filterKeywords = when (catName) {
        "workout" -> listOf("workout", "gym", "fitness", "exercise", "cardio", "lifting", "abs", "squat")
        "training videos" -> listOf("train", "tutorial", "learn", "course", "coaching", "drills", "practice")
        "birthday parties", "birthday" -> listOf("birthday", "bday", "party", "celebration", "cake")
        "travel & vlogs", "travel" -> listOf("travel", "vlog", "trip", "tour", "vacation", "journey", "holiday")
        "events & birthdays", "events" -> listOf("event", "festival", "party", "gala", "gathering", "celebration")
        else -> emptyList()
    }
    
    val title = item.title.lowercase()
    val relPath = (item.relativePath ?: "").lowercase()
    val bucket = (item.bucketName ?: "").lowercase()
    
    val nameMatches = bucket.contains(catName) || relPath.contains(catName) || (catName.length >= 3 && title.contains(catName))
    if (nameMatches) return true
    
    return filterKeywords.isNotEmpty() && filterKeywords.any { kw ->
        title.contains(kw) || relPath.contains(kw) || bucket.contains(kw)
    }
}
