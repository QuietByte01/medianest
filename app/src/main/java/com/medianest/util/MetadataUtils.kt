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
        if (lower == "<unknown>" || lower == "unknown" || lower == "unknown artist" || lower == "unknown album" || lower == "unknown track" || lower == "null") return true
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
