package com.medianest.ui.library.video

import android.net.Uri
import com.medianest.MediaNestApp
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.util.TrashManager
import java.io.File
import java.util.Locale

fun filterVideoList(videos: List<MediaItem>, filterTab: String, showHidden: Boolean = false): List<MediaItem> {
    val sharedWords = if (filterTab == "SERIES" || filterTab == "MOVIES") computeSharedTitleWords(videos) else emptySet()
    
    // Filter out excluded and hidden items from standard tabs
    val baseVideos = when (filterTab) {
        "EXCLUDED" -> videos.filter { com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
        "HIDDEN" -> videos.filter { com.medianest.util.FolderHiddenUtils.isItemHidden(it) && !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
        "FOLDERS" -> videos.filter { !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
        else -> videos.filter { item ->
            !com.medianest.util.FolderHiddenUtils.isItemExcluded(item) &&
            (showHidden || !com.medianest.util.FolderHiddenUtils.isItemHidden(item))
        }
    }

    return when (filterTab) {
        "ALL" -> baseVideos
        "CLIPS" -> baseVideos.filter { isClipsAndRecordings(it) }
        "SHORTS" -> baseVideos.filter { isShorts(it) }
        "SERIES" -> baseVideos.filter { isTVSeries(it, sharedWords) }
        "MUSIC" -> baseVideos.filter { isMusicVideo(it) }
        "MOVIES" -> baseVideos.filter { isMovie(it, sharedWords) }
        "DOWNLOADED" -> baseVideos.filter { isDownloaded(it) }
        "SOCIAL" -> baseVideos.filter { isSocialMediaVideo(it) }
        "EDITED" -> baseVideos.filter { isEditedVideo(it) }
        "EXCLUDED" -> baseVideos
        "HIDDEN" -> baseVideos
        else -> baseVideos
    }
}

private val wordRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

/**
 * Checks if [keyword] exists in [text] as an isolated whole word/token,
 * preventing false substring matches on random/garbage strings (e.g. "insta" or "snap" in "RUIDecdb251fb26insta142snap").
 */
fun containsWord(text: String, keyword: String): Boolean {
    if (text.isBlank() || keyword.isBlank()) return false
    val pattern = wordRegexCache.computeIfAbsent(keyword.lowercase()) { kw ->
        val escaped = Regex.escape(kw)
        Regex("(?i)(?:^|[^a-zA-Z0-9])$escaped(?:[^a-zA-Z0-9]|$)")
    }
    return pattern.containsMatchIn(text)
}

private val patternVideoEditor = Regex("(?i)\\bvideo\\s*editor\\b")
private val patternSeasonEpisode = Regex("(?i)s\\d{1,2}e\\d{1,2}")
private val patternNumberXNumber = Regex("(?i)\\d{1,2}x\\d{1,2}")
private val patternEpisodeExplicit = Regex("(?i)\\b(?:episode|ep|e)[\\s._-]*(\\d{1,3})\\b")
private val patternAnimeEpisode = Regex("(?i)(?:[_\\-]+\\s*|\\s+0)(\\d{1,4})(?:\\s*[(\\[]|\\s*$)|(?:\\s+)(?!19\\d{2}|20\\d{2})(\\d{3,4})(?:\\s*[(\\[]|\\s*$)")
private val patternSeasonRemoval = Regex("(?i)\\b(season\\s*(?:[1-9][0-9]?|100)|s(?:[1-9][0-9]?|100)|s0?[1-9]|s[1-9][0-9]|s100)\\b")
private val patternSpecialChars = Regex("[.,#!?\\-_+=()\\[\\]/{}&$@*]")
private val patternWhitespace = Regex("\\s+")
private val patternSeasonExtract = Regex("(?i)\\b(?:season|s)[\\s\\-_()\\[\\]]*((?:[1-9][0-9]?|100)|(?:0[1-9]|[1-9][0-9]|100))\\b")
private val patternDigitsOnly = Regex("\\d+")
private val patternAlphanumericWord = Regex("[^a-zA-Z0-9]+")
private val patternPathSegments = Regex("[/_\\s.\\-]+")
private val patternSeasonExplicit = Regex("(?i)\\bs\\d{1,3}\\b")
private val patternPureSeason = Regex("(?i)^(season\\s*(?:[1-9][0-9]?|100)|s(?:[1-9][0-9]?|100)|s0?[1-9]|s[1-9][0-9]|s100)$")

/**
 * Checks if a filename title looks like a random hex/hash/cache string
 * e.g., "RUIDecdb251fb26insta142snap", "7f8a9b0c1d2e3f4a5b6c7d8e9f", etc.
 */
fun isGarbageOrHashTitle(title: String): Boolean {
    val name = title.substringBeforeLast('.').trim()
    // If it is a single long continuous alphanumeric string with no spaces/dashes and mixed letters + digits
    if (name.length >= 16 && !name.contains(' ') && !name.contains('-') && !name.contains('_')) {
        val digits = name.count { it.isDigit() }
        val letters = name.count { it.isLetter() }
        if (digits >= 4 && letters >= 4) {
            return true
        }
    }
    // High hex/alphanumeric density or known random cache prefixes
    if (name.startsWith("RUI", ignoreCase = true) || name.startsWith("UUID", ignoreCase = true) || name.startsWith("CACHE", ignoreCase = true)) {
        if (name.length >= 14 && name.any { it.isDigit() }) return true
    }
    return false
}

fun isEditedVideo(item: MediaItem): Boolean {
    if (isGarbageOrHashTitle(item.title)) return false

    val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()
    val isWallpaper = listOf("wallpaper", "wallpapers", "wallhaven", "zedge", "backdrops", "live wallpaper").any { containsWord(path, it) }
    if (isWallpaper) return false

    if (isFromExcludedCategoryFolder(item)) return false

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

    val matchesKeyword = editKeywords.any { containsWord(path, it) }

    return path.contains("/dcim/video editor/") ||
            patternVideoEditor.containsMatchIn(path) ||
            matchesKeyword
}

fun isClipsAndRecordings(item: MediaItem): Boolean {
    val path = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    val title = item.title.lowercase()

    if (path.contains("dcim/camera/") || item.bucketName?.lowercase() == "camera") {
        return true
    }

    if (isGarbageOrHashTitle(item.title)) return false

    val excludedKeywords = listOf(
        "instagram", "snapchat", "tiktok", "facebook", "whatsapp", "telegram", "twitter", "reddit",
        "editor", "studio", "compress", "compressor", "gif", "capcut", "kinemaster", "vn", "inshot",
        "game", "gaming", "screenrecord", "recorder"
    )

    if (excludedKeywords.any { containsWord(path, it) }) {
        return false
    }

    val isInDcim = path.contains("dcim/")
    val isRecording = containsWord(path, "recording") || containsWord(path, "screenrecord")
    val isCameraVid = title.startsWith("vid_") || title.startsWith("vid-")

    return isInDcim || isRecording || isCameraVid
}

fun isShorts(item: MediaItem): Boolean {
    val isVerticalOrSquare = (item.height >= item.width && item.width > 0) || (item.aspectRatio in 0.01f..1.05f)
    val isUnder90s = item.durationMs in 1L..90_000L || item.durationMs == 0L

    val title = item.title.lowercase()
    val hasKeyword = if (isGarbageOrHashTitle(item.title)) false else {
        listOf("short", "shorts", "reel", "reels", "tiktok", "clip", "clips", "video clip").any { containsWord(title, it) }
    }

    return (isVerticalOrSquare && isUnder90s) || hasKeyword
}

private fun cleanSeriesName(name: String): String {
    // Remove season patterns ranging from 1 to 100 (e.g., "season 1", "season 100", "s01", "s100")
    val withoutSeason = name.replace(patternSeasonRemoval, "")

    // Remove specified special characters and symbols: . , # ! ? - _ + = ) ( [ ] / { } & $ @ *
    val sanitized = withoutSeason.replace(patternSpecialChars, " ")

    // Trim extra whitespace resulting from replacements
    return sanitized.replace(patternWhitespace, " ").trim()
}

fun extractSeriesName(item: MediaItem): String {
    val relPath = item.relativePath?.trim('/') ?: ""
    val bucket = item.bucketName ?: "Web Series"
    val parts = relPath.split('/').filter { it.isNotBlank() }

    for (i in parts.indices) {
        val p = parts[i]

        // Check if the directory name matches pure season patterns from 1 to 100
        val isSeasonDirOnly = patternPureSeason.matches(p.trim())

        if (isSeasonDirOnly) {
            // If the folder is only a season tag, return the parent directory if it exists
            if (i > 0) {
                return cleanSeriesName(parts[i - 1])
            }
        } else if (p.contains("season", ignoreCase = true) || patternSeasonExplicit.containsMatchIn(p)) {
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

    val matchResult = patternSeasonExtract.find(path)
    if (matchResult != null) {
        // Group 1 contains the actual matched number captured inside the pattern
        val numStr = matchResult.groupValues.getOrNull(1) ?: matchResult.value
        val num = patternDigitsOnly.find(numStr)?.value?.toIntOrNull() ?: 1

        // Format as a zero-padded two-digit string (e.g., Season 01, Season 10)
        return "Season %02d".format(num)
    }

    return "Season 01"
}

/**
 * Pre-computes distinct significant words that appear in 2 or more videos in O(N) time.
 */
fun computeSharedTitleWords(items: List<MediaItem>): Set<String> {
    if (items.size < 2) return emptySet()
    val wordCounts = HashMap<String, Int>(items.size * 2)
    for (item in items) {
        val words = extractSignificantWords(item.title)
        for (w in words) {
            wordCounts[w] = (wordCounts[w] ?: 0) + 1
        }
    }
    return wordCounts.filterValues { it >= 2 }.keys
}

fun isTVSeries(item: MediaItem, sharedWords: Set<String> = emptySet()): Boolean {
    // 1. Enforce duration constraints (at least 10 minutes)
    val isAtLeast10Min = item.durationMs >= 600_000L
    if (!isAtLeast10Min) return false

    val fullPath = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    val title = item.title.lowercase()
    
    // Segmented folder check
    val folderPath = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()
    val pathSegments = folderPath.split(patternPathSegments).filter { it.isNotBlank() }.toSet()
    val seriesFolders = setOf("series", "tv", "shows", "web-series", "webseries", "anime")
    val isSeriesFolder = pathSegments.any { it in seriesFolders }

    // 2. Exclusion keywords for non-series content
    val exclusionKeywords = listOf("recording", "screen_recording", "live", "test", "interview", "webinar", "zoom", "meeting", "tutorial", "presentation", "exam")
    if (exclusionKeywords.any { containsWord(title, it) || containsWord(fullPath, it) }) return false

    if (isGarbageOrHashTitle(item.title) && !isSeriesFolder && !fullPath.contains("season")) return false

    // Check if path or title contains explicit episode/season formatting
    val hasPattern = patternSeasonEpisode.containsMatchIn(fullPath) || 
                     patternNumberXNumber.containsMatchIn(fullPath) ||
                     patternEpisodeExplicit.containsMatchIn(fullPath) ||
                     patternAnimeEpisode.containsMatchIn(item.title)

    // Evaluate explicit television markers
    val matchesExplicitCriteria = hasPattern || isSeriesFolder || fullPath.contains("season")

    if (matchesExplicitCriteria) return true

    return false
}

fun isMovie(item: MediaItem, sharedWords: Set<String> = emptySet()): Boolean {
    // 1. Fundamental duration requirement (User specified: 20 min is right)
    val isAtLeast20Min = item.durationMs >= 1_200_000L
    if (!isAtLeast20Min) return false

    val title = item.title.lowercase()
    val fullPath = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
    
    // Segmented folder check
    val folderPath = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()
    val pathSegments = folderPath.split(patternPathSegments).filter { it.isNotBlank() }.toSet()
    val movieFolders = setOf("movies", "movie", "cinema", "films", "film")
    val inMovieFolder = pathSegments.any { it in movieFolders }

    // 2. Exclusion keywords for non-movie content
    val exclusionKeywords = listOf("recording", "screen_recording", "live", "test", "interview", "webinar", "zoom", "meeting", "tutorial", "presentation", "exam")
    if (exclusionKeywords.any { containsWord(title, it) || containsWord(fullPath, it) }) return false

    if (isGarbageOrHashTitle(item.title) && !inMovieFolder) return false

    // 3. Strong Movie Markers (Override series detection if these are present)
    val hasMovieQualityTag = fullPath.contains("1080p") || fullPath.contains("720p") ||
            fullPath.contains("2160p") || fullPath.contains("4k") ||
            fullPath.contains("bluray") || fullPath.contains("webrip") ||
            fullPath.contains("web-dl") || fullPath.contains("webdl") ||
            fullPath.contains("remux") || fullPath.contains("bdrip") ||
            fullPath.contains("x264") || fullPath.contains("x265") || fullPath.contains("hdrip") ||
            fullPath.contains("h264") || fullPath.contains("h265") || fullPath.contains("hevc")

    val isVeryLong = item.durationMs >= 2_400_000L // 40+ minutes

    // If it has strong markers, it's likely a movie even if it shares words (e.g. sequels)
    val hasStrongMarkers = inMovieFolder || hasMovieQualityTag

    // 4. Exclude other specific types
    if (isClipsAndRecordings(item) || isShorts(item) || isMusicVideo(item) || isSocialMediaVideo(item)) return false

    // 5. TV Series Check (Sequels/Collections are treated as Series/Collections if they share words)
    if (isTVSeries(item, sharedWords)) return false

    val inGeneralFolder = fullPath.contains("/videos/") ||
            fullPath.contains("/download/") ||
            fullPath.contains("/downloads/")

    return hasStrongMarkers || (inGeneralFolder && isVeryLong) || isVeryLong
}

/**
 * Helper function to tokenize a title and filter out allowed exceptions
 * like quality tags, generic words, and codecs.
 */
private fun extractSignificantWords(title: String): Set<String> {
    val normalized = title.lowercase()

    // Define terms to ignore during comparison
    val ignoredTerms = setOf(
        // Quality tags & codecs
        "1080p", "720p", "480p", "2160p", "4k", "bluray", "webrip",
        "web-dl", "webdl", "x264", "x265", "h264", "h265", "hevc", "hdrip", "hdr", "camrip", "dvdrip", "brrip",
        "mkv", "mp4", "avi", "aac", "ac3", "dts", "10bit",
        // Subtitle / audio terms
        "dual", "audio", "esub", "sub", "subs", "dub", "dubbed", "multi", "clean",
        // Common languages
        "hindi", "english", "tamil", "telugu", "korean", "japanese", "spanish", "french", "german", "chinese",
        // Common generic words
        "movie", "film", "video", "full", "download", "hd", "hq", "part", "vol", "volume", "episode", "season", "ep", "trailer", "teaser", "official", "uncut", "remastered", "extended", "complete",
        // Stop words
        "the", "and", "a", "an", "of", "in", "to", "for", "with", "on", "at", "by", "from", "is", "it", "or", "as"
    )

    // Split title into alphanumeric words, filter out short tokens (<3 chars), stop words, and numbers
    return normalized.split(patternAlphanumericWord)
        .filter { it.length >= 3 && it !in ignoredTerms && !it.all { char -> char.isDigit() } }
        .toSet()
}

fun isMovieOrShow(item: MediaItem): Boolean {
    return isMovie(item) || isTVSeries(item)
}

fun isMusicVideo(item: MediaItem): Boolean {
    if (isGarbageOrHashTitle(item.title)) return false

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
    val matchesMusicTitle = musicTitleKeywords.any { containsWord(title, it) }

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
    val matchesTitle = if (isGarbageOrHashTitle(item.title)) false else socialNames.any { containsWord(title, it) }

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
    if (isGarbageOrHashTitle(item.title)) return false

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
    
    val nameMatches = bucket.contains(catName) || relPath.contains(catName) || (catName.length >= 3 && containsWord(title, catName))
    if (nameMatches) return true
    
    return filterKeywords.isNotEmpty() && filterKeywords.any { kw ->
        containsWord(title, kw) || containsWord(relPath, kw) || containsWord(bucket, kw)
    }
}
