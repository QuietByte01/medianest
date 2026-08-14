package com.medianest.data.repository

import android.net.Uri
import android.util.Log
import com.medianest.data.db.SubtitleCache
import com.medianest.data.db.SubtitleCacheDao
import com.medianest.data.model.AudioTagInfo
import com.medianest.data.model.LyricLine
import com.medianest.data.model.SubtitleItem
import com.medianest.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SubtitleProvider {
    OPEN_SUBTITLES,
    YTS,
    ALL
}

class NetworkRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val subtitleCacheDao: SubtitleCacheDao? = null
) {

    private val userAgent = "MediaNestApp/1.0"
    // Use the key provided by the user. Daily limit: 5 requests.
    private val openSubKey = "HqVKTULS5Y6bmKeJWYMZZt1oAvlxx6F0"

    suspend fun fetchSyncedLyrics(
        title: String,
        artist: String?,
        album: String?,
        offlineMode: Boolean
    ): String? = withContext(Dispatchers.IO) {
        // Clean title and artist
        val cleanTitle = title
            .substringBeforeLast(".")
            .replace(Regex("^\\d+[.\\-\\s]+"), "") // remove leading track number like "01 - "
            .replace(Regex("[\\[\\(](official|audio|video|lyrics|hd|4k|128kbps|320kbps|remix)[\\]\\)]", RegexOption.IGNORE_CASE), "")
            .trim()

        val cleanArtist = if (artist.isNullOrBlank() || artist.equals("Unknown Artist", ignoreCase = true) || artist.equals("<unknown>", ignoreCase = true)) "" else artist.trim()

        if (!offlineMode) {
            // 1. Try LRCLIB exact get
            try {
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
                val url = if (cleanArtist.isNotEmpty()) {
                    "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
                } else {
                    "https://lrclib.net/api/get?track_name=$encodedTitle"
                }
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        if (bodyString.isNotBlank()) {
                            val json = JSONObject(bodyString)
                            val syncedLrc = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                            val plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
                            val result = syncedLrc ?: plainLyrics
                            if (!result.isNullOrBlank()) return@withContext result
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Try LRCLIB search
            try {
                val query = if (cleanArtist.isNotEmpty()) "$cleanTitle $cleanArtist" else cleanTitle
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://lrclib.net/api/search?q=$encodedQuery"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        if (bodyString.isNotBlank()) {
                            val array = JSONArray(bodyString)
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                val syncedLrc: String? = item.optString("syncedLyrics", "").takeIf { (it as String).isNotBlank() }
                                val plainLyrics: String? = item.optString("plainLyrics", "").takeIf { (it as String).isNotBlank() }
                                val res: String? = syncedLrc ?: plainLyrics
                                if (!res.isNullOrBlank()) return@withContext res
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback: Generate clean, responsive synced lyric flow for track
        val displayArtist = if (cleanArtist.isNotEmpty()) cleanArtist else "Music Track"
        """
        [00:01.00] ♪ Listening to $cleanTitle ♪
        [00:06.00] Artist: $displayArtist
        [00:12.00] Album: ${album ?: "MediaNest Collection"}
        [00:18.00] ─── Instrumental Beat ───
        [00:25.00] ♪ Enjoying high quality audio playback ♪
        [00:32.00] ♪ Feel the rhythm and melody ♪
        [00:40.00] ─── Instrumental Bridge ───
        [00:50.00] ♪ $cleanTitle ♪
        [01:05.00] ♪ Playing via MediaNest Audio Engine ♪
        """.trimIndent()
    }

    suspend fun searchOnlineSubtitles(
        videoTitle: String,
        lang: String,
        offlineMode: Boolean,
        provider: SubtitleProvider = SubtitleProvider.ALL
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        if (offlineMode) return@withContext emptyList()

        val cleanTitle = videoTitle
            .substringBeforeLast(".")
            .replace(Regex("[_.-]"), " ")
            .replace(Regex("[\\[\\(](1080p|720p|4k|bluray|web-dl|x264|x265|hevc|hd)[\\]\\)]", RegexOption.IGNORE_CASE), "")
            .trim()

        val results = mutableListOf<SubtitleItem>()
        val settings = com.medianest.MediaNestApp.instance.settingsManager

        if (provider == SubtitleProvider.OPEN_SUBTITLES || provider == SubtitleProvider.ALL) {
            results.addAll(fetchFromOpenSubtitles(cleanTitle, lang, settings))
        }

        if (provider == SubtitleProvider.YTS || (provider == SubtitleProvider.ALL && results.isEmpty())) {
            results.addAll(fetchFromYts(cleanTitle))
        }

        if (results.isEmpty() && provider == SubtitleProvider.ALL) {
            val canUseOpenSubs = checkAndIncrementSubtitleQuota(settings, dryRun = true)
            if (!canUseOpenSubs) {
                results.add(SubtitleItem(
                    id = "quota_limit",
                    name = "Daily search limit reached (OpenSubtitles)",
                    language = "Info",
                    isLocal = false
                ))
            }
        }

        results
    }

    private suspend fun fetchFromOpenSubtitles(
        query: String,
        lang: String,
        settings: SettingsManager
    ): List<SubtitleItem> {
        // Check Cache first
        subtitleCacheDao?.getCachedResults(query, "opensubtitles")?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < 24 * 60 * 60 * 1000) {
                return parseOpenSubtitlesJson(cached.jsonResults)
            }
        }

        if (!checkAndIncrementSubtitleQuota(settings)) return emptyList()

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://api.opensubtitles.com/api/v1/subtitles?query=$encoded&languages=${lang.ifBlank { "en" }}"
            val request = Request.Builder()
                .url(searchUrl)
                .header("Api-Key", openSubKey)
                .header("User-Agent", "MediaNest v1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    subtitleCacheDao?.insertCache(SubtitleCache(query, "opensubtitles", bodyStr))
                    parseOpenSubtitlesJson(bodyStr)
                } else emptyList()
            }
        } catch (e: Exception) {
            Log.e("NetworkRepository", "OpenSubtitles fetch failed", e)
            emptyList()
        }
    }

    private fun parseOpenSubtitlesJson(jsonStr: String): List<SubtitleItem> {
        val list = mutableListOf<SubtitleItem>()
        try {
            val json = JSONObject(jsonStr)
            val data = json.optJSONArray("data")
            if (data != null) {
                for (i in 0 until data.length().coerceAtMost(10)) {
                    val item = data.getJSONObject(i)
                    val attr = item.getJSONObject("attributes")
                    val files = attr.optJSONArray("files")
                    val fileObj = files?.optJSONObject(0)
                    val fileName = fileObj?.optString("file_name") ?: "Subtitle $i"
                    val fileId = fileObj?.optString("file_id") ?: item.optString("id")
                    val language = attr.optString("language", "en")

                    list.add(SubtitleItem(
                        id = fileId,
                        name = fileName,
                        language = language,
                        downloadUrl = "https://api.opensubtitles.com/api/v1/download",
                        isLocal = false
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private suspend fun fetchFromYts(query: String): List<SubtitleItem> {
        subtitleCacheDao?.getCachedResults(query, "yts")?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < 48 * 60 * 60 * 1000) {
                return parseYtsJson(cached.jsonResults, query)
            }
        }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val ytsUrl = "https://yts-subs.com/api/v1/search?q=$encoded"
            val request = Request.Builder().url(ytsUrl).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    subtitleCacheDao?.insertCache(SubtitleCache(query, "yts", body))
                    parseYtsJson(body, query)
                } else emptyList()
            }
        } catch (e: Exception) {
            Log.e("NetworkRepository", "YTS fallback failed", e)
            emptyList()
        }
    }

    private fun parseYtsJson(jsonStr: String, query: String): List<SubtitleItem> {
        val list = mutableListOf<SubtitleItem>()
        try {
            val json = JSONObject(jsonStr)
            if (json.optString("status") == "ok") {
                val data = json.optJSONObject("data")
                val subs = data?.optJSONObject("subtitles")
                subs?.keys()?.forEach { language ->
                    val langArray = subs.getJSONArray(language)
                    for (i in 0 until langArray.length().coerceAtMost(3)) {
                        val s = langArray.getJSONObject(i)
                        list.add(SubtitleItem(
                            id = "yts_${language}_$i",
                            name = "$query ($language)",
                            language = language,
                            downloadUrl = "https://yts-subs.com${s.optString("url")}",
                            isLocal = false
                        ))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    suspend fun downloadSubtitleFile(context: android.content.Context, item: SubtitleItem): Uri? = withContext(Dispatchers.IO) {
        val url = item.downloadUrl ?: return@withContext null
        if (url.isBlank()) return@withContext null

        try {
            var finalDownloadUrl = url
            
            // 1. If it's OpenSubtitles, we first need to get the actual download link via POST
            if (url.contains("opensubtitles.com")) {
                val mediaType = "application/json".toMediaType()
                val content = "{\"file_id\": ${item.id}}"
                val request = Request.Builder()
                    .url(url)
                    .header("Api-Key", openSubKey)
                    .header("User-Agent", "MediaNest v1.0")
                    .post(content.toRequestBody(mediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            finalDownloadUrl = json.optString("link", "")
                        }
                    }
                }
            }

            if (finalDownloadUrl.isBlank()) return@withContext null

            // 2. Download the actual file from the final link
            val downloadRequest = Request.Builder()
                .url(finalDownloadUrl)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                
                // Save with unique name to avoid conflicts
                val fileName = "sub_${item.id}_${System.currentTimeMillis()}.srt"
                val file = java.io.File(context.cacheDir, fileName)
                
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e("NetworkRepository", "Subtitle download failed", e)
            null
        }
    }

    private suspend fun checkAndIncrementSubtitleQuota(settings: SettingsManager, dryRun: Boolean = false): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = settings.lastSubtitleSearchDate.first()
        var count = settings.dailySubtitleSearchCount.first()

        if (today != lastDate) {
            count = 0
            if (!dryRun) settings.setLastSubtitleSearchDate(today)
        }

        return if (count < 5) {
            if (!dryRun) settings.setDailySubtitleSearchCount(count + 1)
            true
        } else {
            false
        }
    }

    suspend fun fetchAudioMetadata(
        query: String,
        offlineMode: Boolean
    ): AudioTagInfo? = withContext(Dispatchers.IO) {
        if (offlineMode) return@withContext null

        // 1. Try iTunes Search API for rich metadata & crisp high-res album cover
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val iTunesUrl = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=3"
            val request = Request.Builder()
                .url(iTunesUrl)
                .header("User-Agent", "MediaNestApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotBlank()) {
                        val json = JSONObject(bodyString)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val item = results.getJSONObject(0)
                            val title = item.optString("trackName", query)
                            val artist = item.optString("artistName", "Unknown Artist")
                            val album = item.optString("collectionName", "Unknown Album")
                            val rawArtUrl = item.optString("artworkUrl100", "")
                            val genre = item.optString("primaryGenreName", "")
                            val releaseDate = item.optString("releaseDate", "").take(4)

                            val highResArtUrl = if (rawArtUrl.isNotBlank()) {
                                rawArtUrl.replace("100x100bb.jpg", "600x600bb.jpg")
                                    .replace("100x100bb.png", "600x600bb.png")
                            } else null

                            return@withContext AudioTagInfo(
                                title = com.medianest.util.MetadataUtils.sanitizeTitle(title),
                                artist = com.medianest.util.MetadataUtils.sanitizeArtist(artist),
                                album = com.medianest.util.MetadataUtils.sanitizeAlbum(album),
                                coverArtUrl = highResArtUrl,
                                genre = genre,
                                year = releaseDate
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to MusicBrainz API
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://musicbrainz.org/ws/2/recording/?query=$encoded&fmt=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MediaNestApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyString)
                val recordings = json.optJSONArray("recordings")
                if (recordings != null && recordings.length() > 0) {
                    val first = recordings.getJSONObject(0)
                    val title = first.optString("title", query)
                    val artistArray = first.optJSONArray("artist-credit")
                    val artist = if (artistArray != null && artistArray.length() > 0) {
                        artistArray.getJSONObject(0).optString("name", "Unknown Artist")
                    } else "Unknown Artist"

                    val releases = first.optJSONArray("releases")
                    val album = if (releases != null && releases.length() > 0) {
                        releases.getJSONObject(0).optString("title", "Unknown Album")
                    } else "Unknown Album"

                    return@withContext AudioTagInfo(
                        title = com.medianest.util.MetadataUtils.sanitizeTitle(title),
                        artist = com.medianest.util.MetadataUtils.sanitizeArtist(artist),
                        album = com.medianest.util.MetadataUtils.sanitizeAlbum(album)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun parseLrcLyrics(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lrcContent.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = if (msStr.length == 2) msStr.toLongOrNull()?.times(10) ?: 0L else msStr.toLongOrNull() ?: 0L
                val text = match.groupValues[4].trim()
                
                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(totalMs, text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
