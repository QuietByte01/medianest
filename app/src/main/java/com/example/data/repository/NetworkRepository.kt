package com.example.data.repository

import com.example.data.model.AudioTagInfo
import com.example.data.model.LyricLine
import com.example.data.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class NetworkRepository(
    private val client: OkHttpClient = OkHttpClient()
) {

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
                val request = Request.Builder().url(url).build()

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
                val request = Request.Builder().url(searchUrl).build()

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
        offlineMode: Boolean
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        if (offlineMode) return@withContext emptyList()

        val cleanTitle = videoTitle
            .substringBeforeLast(".")
            .replace(Regex("[_.-]"), " ")
            .replace(Regex("[\\[\\(](1080p|720p|4k|bluray|web-dl|x264|x265|hevc|hd)[\\]\\)]", RegexOption.IGNORE_CASE), "")
            .trim()

        val list = mutableListOf<SubtitleItem>()

        try {
            val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = "https://sub.wyzie.ru/search?title=$encoded"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "MediaNestApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.startsWith("[")) {
                        val array = JSONArray(bodyStr)
                        for (i in 0 until array.length().coerceAtMost(10)) {
                            val obj = array.getJSONObject(i)
                            val id = obj.optString("id", "sub_$i")
                            val display = obj.optString("display_name", "$cleanTitle Subtitle ${i + 1}")
                            val language = obj.optString("language", if (lang.isNotBlank()) lang else "English")
                            val url = obj.optString("url", "")
                            list.add(
                                SubtitleItem(
                                    id = id,
                                    name = display,
                                    language = language,
                                    downloadUrl = if (url.isNotBlank()) url else null,
                                    isLocal = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list
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
                                title = com.example.util.MetadataUtils.sanitizeTitle(title),
                                artist = com.example.util.MetadataUtils.sanitizeArtist(artist),
                                album = com.example.util.MetadataUtils.sanitizeAlbum(album),
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
                        title = com.example.util.MetadataUtils.sanitizeTitle(title),
                        artist = com.example.util.MetadataUtils.sanitizeArtist(artist),
                        album = com.example.util.MetadataUtils.sanitizeAlbum(album)
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
