package com.medianest.util

import android.content.Context
import com.medianest.data.db.AudioMetadataCache
import com.medianest.data.db.AudioMetadataCacheDao
import com.medianest.data.model.AudioTagInfo
import com.medianest.data.repository.NetworkRepository

object MetadataUtils {

    private val domainRegex = Regex(
        """(https?://|www\.|[a-zA-Z0-9-]+\.(com|net|org|co|in|ru|info|biz|site|xyz|mp3|download|online|club|top|store|link|me|cc|io|us|uk|de|fr|app|dev|tech)\b)""",
        RegexOption.IGNORE_CASE
    )

    fun hasDomainOrFalseInfo(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase().trim()
        if (lower == "<unknown>" || lower == "unknown" || lower == "unknown artist" || 
            lower == "unknown album" || lower == "unknown track" || lower == "null" ||
            lower == "n/a" || lower == "na" || lower == "none" || lower == "various artists" ||
            lower.startsWith("contributor") || lower.startsWith("guest artist") ||
            lower.startsWith("track ") || lower == "artist") return true
        return domainRegex.containsMatchIn(lower)
    }

    fun sanitizeArtist(artist: String?): String {
        return if (hasDomainOrFalseInfo(artist)) "Unknown Artist" else artist!!.trim()
    }

    fun sanitizeAlbum(album: String?): String {
        return if (hasDomainOrFalseInfo(album)) "Unknown Album" else album!!.trim()
    }

    fun sanitizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return "Unknown Track"
        if (domainRegex.containsMatchIn(title.lowercase())) {
            // Strip domain or return title without domain extension
            val cleaned = title.replace(domainRegex, "").trim()
            return if (cleaned.isNotBlank()) cleaned else "Unknown Track"
        }
        return title.trim()
    }

    fun isJunkTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val t = title.trim()
        if (t.isEmpty()) return true

        // Heuristic for random alphanumeric strings:
        // String (> 10 chars) with no spaces and low vowel count
        if (t.length > 10 && !t.contains(" ")) {
            val vowels = t.count { it.lowercaseChar() in "aeiou" }
            // Random strings usually have very few vowels compared to their length
            if (vowels <= t.length / 8) return true

            // Also check for excessive digit count
            val digits = t.count { it.isDigit() }
            if (digits > t.length / 2) return true
        }

        return false
    }

    fun cleanMovieOrOstTitle(title: String?): String {
        if (title.isNullOrBlank() || hasDomainOrFalseInfo(title)) return ""
        var clean = title.trim()
        
        // Remove file extensions
        clean = clean.replace(Regex("""\.(mp4|mkv|avi|mov|wmv|flv|webm|ts|m4v|mp3|flac|wav|aac|ogg|m4a)$""", RegexOption.IGNORE_CASE), "")
        
        // Remove resolution, release years, codecs, audio formats, source tags
        val junkRegex = Regex(
            """(?i)\b(1080p|720p|2160p|4k|2k|8k|480p|360p|uhd|fhd|hd|sd|bluray|blu-ray|bdrip|brrip|web-dl|webrip|web|dvd|dvdrip|hdtv|x264|x265|h264|h265|hevc|avc|10bit|8bit|aac|ac3|dts|dts-hd|truehd|ddp?5\.1|7\.1|atmos|repack|proper|remux|hdr10\+?|hdr|sdr|dolby vision|dv|esub|multi|dual audio|hindi|english|tamil|telugu|korean|japanese|yify|yts|rarbg|psa|galaxytv|tgx|eztv|\d{4})\b"""
        )
        clean = clean.replace(junkRegex, "")
        
        // Remove brackets, parentheses enclosing junk or leftover empty brackets
        clean = clean.replace(Regex("""\[[^\]]*\]|\([^\)]*\)"""), " ")
        
        // Replace dots, underscores, hyphens separating words with spaces
        clean = clean.replace(Regex("""[._\-\+]+"""), " ")
        
        // Collapse multiple spaces
        clean = clean.replace(Regex("""\s+"""), " ").trim()
        
        return clean
    }

    fun parseMultipleArtists(artistStr: String?): List<String> {
        val clean = sanitizeArtist(artistStr)
        if (clean == "Unknown Artist") return emptyList()
        
        // Split by common artist separators: comma, ampersand, feat., ft., featuring, with, x, slash, semicolon
        val rawParts = clean.split(
            Regex("""\s*(?:,|&|;|\bfeat\b\.?|\bft\b\.?|\bfeaturing\b|\bwith\b|\bx\b|/)\s*""", RegexOption.IGNORE_CASE)
        )
        
        return rawParts
            .map { it.trim() }
            .filter { it.isNotBlank() && !hasDomainOrFalseInfo(it) }
            .distinct()
    }

    suspend fun getOrFetchMetadata(
        context: Context,
        cacheDao: AudioMetadataCacheDao,
        networkRepository: NetworkRepository,
        mediaUriStr: String,
        rawTitle: String,
        rawArtist: String?,
        rawAlbum: String?,
        offlineMode: Boolean = false
    ): AudioTagInfo {
        val cleanTitle = sanitizeTitle(rawTitle)
        val cleanArtist = sanitizeArtist(rawArtist)
        val cleanAlbum = sanitizeAlbum(rawAlbum)

        // 1. Check local Room database cache
        try {
            val cached = cacheDao.getCache(mediaUriStr)
            if (cached != null) {
                return AudioTagInfo(
                    title = if (hasDomainOrFalseInfo(cached.title)) cleanTitle else cached.title ?: cleanTitle,
                    artist = if (hasDomainOrFalseInfo(cached.artist)) cleanArtist else cached.artist ?: cleanArtist,
                    album = if (hasDomainOrFalseInfo(cached.album)) cleanAlbum else cached.album ?: cleanAlbum,
                    coverArtUrl = cached.albumArtUri,
                    genre = cached.genre,
                    year = cached.year,
                    composer = cached.composer
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. If no cache and we have missing info (e.g. unknown artist or album or domain stripped), try fetching online
        if (!offlineMode) {
            val searchQuery = "$cleanTitle ${if (cleanArtist != "Unknown Artist") cleanArtist else ""}".trim()
            val fetchedInfo = networkRepository.fetchAudioMetadata(searchQuery, offlineMode = false)

            if (fetchedInfo != null) {
                val finalArtist = if (cleanArtist == "Unknown Artist") sanitizeArtist(fetchedInfo.artist) else cleanArtist
                val finalAlbum = if (cleanAlbum == "Unknown Album") sanitizeAlbum(fetchedInfo.album) else cleanAlbum

                val cacheEntity = AudioMetadataCache(
                    audioUri = mediaUriStr,
                    title = cleanTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    albumArtUri = fetchedInfo.coverArtUrl,
                    genre = fetchedInfo.genre,
                    year = fetchedInfo.year,
                    composer = fetchedInfo.composer,
                    fetchedAt = System.currentTimeMillis()
                )
                try {
                    cacheDao.saveCache(cacheEntity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return AudioTagInfo(
                    title = cleanTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    coverArtUrl = fetchedInfo.coverArtUrl,
                    genre = fetchedInfo.genre,
                    year = fetchedInfo.year,
                    composer = fetchedInfo.composer
                )
            }
        }

        return AudioTagInfo(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum
        )
    }
}
