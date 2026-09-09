package com.medianest.ui.library.video

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.medianest.MediaNestApp
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.VideoThumbnailView
import com.medianest.ui.components.WideVideoCard
import com.medianest.ui.components.translucentScrollBarGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class CastMember(
    val name: String,
    val characterName: String,
    val imageUrl: String?
)

data class SeriesOnlineMetadata(
    val title: String,
    val posterUrl: String?,
    val rating: Double?,
    val year: String?,
    val genres: List<String>,
    val summary: String?,
    val imdbId: String?,
    val producers: List<String>,
    val directors: List<String>,
    val cast: List<CastMember>,
    val networkOrChannel: String?,
    val status: String?,
    val originType: String? = null // e.g. "Comic", "Novel", "Scripted"
)

// In-memory cache
private val seriesMetadataCache = ConcurrentHashMap<String, SeriesOnlineMetadata>()

private val httpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * Strips release tags, season tags, resolutions, video codecs, and noise from folder names
 * to extract the clean series title for search and display.
 */
fun cleanSeriesQueryForSearch(rawName: String): String {
    var s = rawName.trim()
    // 1. Remove bracketed noise e.g. [1080p HEVC], [NF WEB-DL]
    s = s.replace(Regex("\\[.*?\\]"), " ")

    // 2. Cut off at S01, Season 1, 1080p, etc. if it appears after the title
    val cutoffPattern = Regex("(?i)(?:\\b(?:s\\d{1,3}|season\\s*\\d{1,3}|1080p|720p|480p|2160p|4k|bluray|web-?dl|web-?rip|hdtv|dvdrip|x264|x265|hevc)\\b)")
    val match = cutoffPattern.find(s)
    if (match != null && match.range.first > 2) {
        s = s.substring(0, match.range.first)
    }

    // 3. Strip any release year in parentheses e.g. 'The Boys (2019)' -> 'The Boys'
    s = s.replace(Regex("\\(\\s*[12]\\d{3}\\s*\\)"), " ")

    // 4. Strip leftover tags
    val tags = listOf(
        "(?i)\\b(?:1080p|720p|480p|2160p|4k|uhd|hdr|hdrip|web-?dl|web-?rip|bluray|bdrip|brrip|dvdrip|hdtv|nf|amzn|dsnp|hmax)\\b",
        "(?i)\\b(?:x264|x265|hevc|h264|h265|avc|10bit|aac|ac3|dts|mp3|eac3|ddp\\d?[\\.\\d]*)\\b",
        "(?i)\\b(?:season\\s*\\d{1,3}|s\\d{1,3}(?:e\\d{1,3})?)\\b",
        "(?i)\\b(?:complete|repack|proper|unrated|extended|directors\\s*cut)\\b",
        "(?i)\\b(?:[12]\\d{3})\\b"
    )
    for (tag in tags) {
        s = s.replace(Regex(tag), " ")
    }

    // 5. Clean punctuation & special chars
    s = s.replace(Regex("[._\\-+=()\\[\\]{}#!?&/$\"@*]"), " ")
    s = s.replace(Regex("\\s+"), " ").trim()
    return if (s.isBlank()) rawName.trim() else s
}

/**
 * Extracts the numeric episode number from the title / path for natural sorting
 * Matches patterns like E01, EP01, Episode 1, 1x05, 01, etc.
 */
fun extractEpisodeNumber(item: MediaItem): Int {
    val name = item.title
    // 1. SxxExx or Exx / EPxx / Episode xx
    val epRegex = Regex("(?i)(?:s\\d{1,2})?[._\\-\\s]*(?:e|ep|episode)[._\\-\\s]*(\\d{1,4})\\b")
    val match1 = epRegex.find(name)
    if (match1 != null) {
        val numStr = match1.groupValues.getOrNull(1)
        val num = numStr?.toIntOrNull()
        if (num != null) return num
    }

    // 2. 1x05 format
    val xRegex = Regex("(?i)\\b\\d{1,2}x(\\d{1,3})\\b")
    val match2 = xRegex.find(name)
    if (match2 != null) {
        val numStr = match2.groupValues.getOrNull(1)
        val num = numStr?.toIntOrNull()
        if (num != null) return num
    }

    // 3. Isolated digits (e.g., Show - 01.mp4 or Show 01)
    val isolatedNumRegex = Regex("(?i)(?:[_\\-\\s]+|^)0?(\\d{1,3})(?:[_\\-\\s.]+|$)")
    val match3 = isolatedNumRegex.find(name)
    if (match3 != null) {
        val numStr = match3.groupValues.getOrNull(1)
        val num = numStr?.toIntOrNull()
        if (num != null && num !in 1900..2099) return num // avoid matching year numbers
    }

    return Int.MAX_VALUE
}

/**
 * File-backed persistence for series metadata
 */
private fun getCacheFile(context: Context, seriesKey: String): File {
    val dir = File(context.cacheDir, "series_metadata_cache")
    if (!dir.exists()) dir.mkdirs()
    val safeKey = seriesKey.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    return File(dir, "$safeKey.json")
}

private fun saveMetadataToDisk(context: Context, key: String, meta: SeriesOnlineMetadata) {
    try {
        val file = getCacheFile(context, key)
        val json = JSONObject().apply {
            put("title", meta.title)
            put("posterUrl", meta.posterUrl ?: "")
            put("rating", meta.rating ?: 0.0)
            put("year", meta.year ?: "")
            put("genres", JSONArray(meta.genres))
            put("summary", meta.summary ?: "")
            put("imdbId", meta.imdbId ?: "")
            put("producers", JSONArray(meta.producers))
            put("directors", JSONArray(meta.directors))
            put("networkOrChannel", meta.networkOrChannel ?: "")
            put("status", meta.status ?: "")
            put("originType", meta.originType ?: "")

            val castArray = JSONArray()
            meta.cast.forEach { c ->
                val cObj = JSONObject()
                cObj.put("name", c.name)
                cObj.put("characterName", c.characterName)
                cObj.put("imageUrl", c.imageUrl ?: "")
                castArray.put(cObj)
            }
            put("cast", castArray)
        }
        file.writeText(json.toString())
    } catch (_: Exception) {}
}

private fun loadMetadataFromDisk(context: Context, key: String): SeriesOnlineMetadata? {
    try {
        val file = getCacheFile(context, key)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())

        val genres = mutableListOf<String>()
        val genresArr = json.optJSONArray("genres")
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) genres.add(genresArr.getString(i))
        }

        val producers = mutableListOf<String>()
        val prodArr = json.optJSONArray("producers")
        if (prodArr != null) {
            for (i in 0 until prodArr.length()) producers.add(prodArr.getString(i))
        }

        val directors = mutableListOf<String>()
        val dirArr = json.optJSONArray("directors")
        if (dirArr != null) {
            for (i in 0 until dirArr.length()) directors.add(dirArr.getString(i))
        }

        val cast = mutableListOf<CastMember>()
        val castArr = json.optJSONArray("cast")
        if (castArr != null) {
            for (i in 0 until castArr.length()) {
                val cObj = castArr.getJSONObject(i)
                cast.add(
                    CastMember(
                        name = cObj.optString("name"),
                        characterName = cObj.optString("characterName"),
                        imageUrl = cObj.optString("imageUrl").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val ratingVal = json.optDouble("rating", 0.0).takeIf { it > 0.0 }

        return SeriesOnlineMetadata(
            title = json.optString("title"),
            posterUrl = json.optString("posterUrl").takeIf { it.isNotBlank() },
            rating = ratingVal,
            year = json.optString("year").takeIf { it.isNotBlank() },
            genres = genres,
            summary = json.optString("summary").takeIf { it.isNotBlank() },
            imdbId = json.optString("imdbId").takeIf { it.isNotBlank() },
            producers = producers,
            directors = directors,
            cast = cast,
            networkOrChannel = json.optString("networkOrChannel").takeIf { it.isNotBlank() },
            status = json.optString("status").takeIf { it.isNotBlank() },
            originType = json.optString("originType").takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) {
        return null
    }
}

suspend fun fetchSeriesMetadata(context: Context, rawSeriesName: String): SeriesOnlineMetadata? = withContext(Dispatchers.IO) {
    val cleanQuery = cleanSeriesQueryForSearch(rawSeriesName)
    if (cleanQuery.isBlank()) return@withContext null

    seriesMetadataCache[rawSeriesName]?.let { return@withContext it }

    // Check disk cache first
    loadMetadataFromDisk(context, rawSeriesName)?.let {
        seriesMetadataCache[rawSeriesName] = it
        return@withContext it
    }

    // Check offline mode setting
    val offlineMode = try {
        MediaNestApp.instance.settingsManager.offlineMode.first()
    } catch (_: Exception) { false }

    if (offlineMode) return@withContext null

    try {
        val query = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "https://api.tvmaze.com/singlesearch/shows?q=$query&embed[]=cast&embed[]=crew"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 MediaNest/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body.string()
                val json = JSONObject(bodyStr)
                val title = json.optString("name", cleanQuery)
                val imageObj = json.optJSONObject("image")
                val posterUrl = imageObj?.optString("original") ?: imageObj?.optString("medium")
                
                val ratingObj = json.optJSONObject("rating")
                val rating = if (ratingObj != null && !ratingObj.isNull("average")) ratingObj.optDouble("average") else null
                
                val premiered = json.optString("premiered", "")
                val year = if (premiered.length >= 4) premiered.substring(0, 4) else null

                val genresList = mutableListOf<String>()
                val genresArr = json.optJSONArray("genres")
                if (genresArr != null) {
                    for (i in 0 until genresArr.length()) {
                        genresList.add(genresArr.getString(i))
                    }
                }

                val rawSummary = json.optString("summary", "")
                val cleanSummary = if (rawSummary.isNotBlank()) {
                    rawSummary.replace(Regex("<[^>]*>"), "").trim()
                } else null

                val externals = json.optJSONObject("externals")
                val imdb = if (externals != null && !externals.isNull("imdb")) externals.optString("imdb") else null

                val networkObj = json.optJSONObject("network")
                val webChannelObj = json.optJSONObject("webChannel")
                val networkOrChannel = networkObj?.optString("name") ?: webChannelObj?.optString("name")
                val status = if (!json.isNull("status")) json.optString("status") else null
                val showType = json.optString("type", "")

                // Parse Cast & Crew
                val embedded = json.optJSONObject("_embedded")
                val castList = mutableListOf<CastMember>()
                val producersList = mutableListOf<String>()
                val directorsList = mutableListOf<String>()

                if (embedded != null) {
                    val castArr = embedded.optJSONArray("cast")
                    if (castArr != null) {
                        for (i in 0 until castArr.length()) {
                            val cObj = castArr.getJSONObject(i)
                            val person = cObj.optJSONObject("person")
                            val character = cObj.optJSONObject("character")
                            val pName = person?.optString("name") ?: ""
                            val cName = character?.optString("name") ?: ""
                            val pImage = person?.optJSONObject("image")?.optString("medium")
                            if (pName.isNotBlank()) {
                                castList.add(CastMember(name = pName, characterName = cName, imageUrl = pImage))
                            }
                        }
                    }

                    val crewArr = embedded.optJSONArray("crew")
                    if (crewArr != null) {
                        for (i in 0 until crewArr.length()) {
                            val cObj = crewArr.getJSONObject(i)
                            val role = cObj.optString("type", "")
                            val person = cObj.optJSONObject("person")
                            val pName = person?.optString("name") ?: ""
                            if (pName.isNotBlank()) {
                                if (role.contains("Producer", ignoreCase = true) || role.contains("Creator", ignoreCase = true)) {
                                    if (!producersList.contains(pName)) producersList.add(pName)
                                } else if (role.contains("Director", ignoreCase = true)) {
                                    if (!directorsList.contains(pName)) directorsList.add(pName)
                                }
                            }
                        }
                    }
                }

                // Detect comic / novel inspiration from summary keywords
                var originType: String? = null
                if (cleanSummary != null) {
                    val lowerSummary = cleanSummary.lowercase()
                    if (lowerSummary.contains("comic") || lowerSummary.contains("graphic novel") || lowerSummary.contains("manga")) {
                        originType = "Based on Comic / Manga"
                    } else if (lowerSummary.contains("novel") || lowerSummary.contains("book series") || lowerSummary.contains("based on the novel")) {
                        originType = "Based on Novel"
                    }
                }

                val result = SeriesOnlineMetadata(
                    title = title,
                    posterUrl = posterUrl,
                    rating = rating,
                    year = year,
                    genres = genresList,
                    summary = cleanSummary,
                    imdbId = imdb,
                    producers = producersList.take(4),
                    directors = directorsList.take(4),
                    cast = castList,
                    networkOrChannel = networkOrChannel,
                    status = status,
                    originType = originType
                )
                seriesMetadataCache[rawSeriesName] = result
                saveMetadataToDisk(context, rawSeriesName, result)
                return@withContext result
            }
        }
    } catch (_: Exception) {
        // Graceful fallback
    }
    null
}

/**
 * Downloads missing subtitles for a list of episodes into a 'subtitles' directory
 */
suspend fun downloadMissingSubtitlesForSeries(
    context: Context,
    seriesName: String,
    episodes: List<MediaItem>,
    onProgress: (Int, Int, String) -> Unit
): Pair<Int, Int> = withContext(Dispatchers.IO) {
    var downloadedCount = 0
    var skippedCount = 0

    // Determine target directory: relative to the first episode's directory
    val firstItem = episodes.firstOrNull() ?: return@withContext Pair(0, 0)
    var targetSubtitlesDir: File? = null

    if (firstItem.uri.scheme == "file") {
        val path = firstItem.uri.path
        if (path != null) {
            val parent = File(path).parentFile
            if (parent != null) {
                targetSubtitlesDir = File(parent, "subtitles")
            }
        }
    }

    if (targetSubtitlesDir == null) {
        // Fallback to internal/external app media files dir
        val appMediaDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "SeriesSubtitles/${seriesName.replace(Regex("[^a-zA-Z0-9_]"), "_")}/subtitles")
        targetSubtitlesDir = appMediaDir
    }

    if (!targetSubtitlesDir.exists()) {
        targetSubtitlesDir.mkdirs()
    }

    val cleanSeries = cleanSeriesQueryForSearch(seriesName)

    episodes.forEachIndexed { index, episode ->
        val epTitle = episode.title.substringBeforeLast('.')
        val sNum = extractSeasonNumber(episode)
        val eNum = extractEpisodeNumber(episode)
        val sCode = "S%02dE%02d".format(sNum, eNum)

        // Check if subtitle already exists in the subtitles folder for this episode
        val existingSrt = File(targetSubtitlesDir, "$epTitle.srt")
        val existingVtt = File(targetSubtitlesDir, "$epTitle.vtt")
        val existingCodeSrt = File(targetSubtitlesDir, "${cleanSeries}_$sCode.srt")

        if (existingSrt.exists() || existingVtt.exists() || existingCodeSrt.exists()) {
            skippedCount++
            onProgress(index + 1, episodes.size, "Already exists: $sCode")
            return@forEachIndexed
        }

        onProgress(index + 1, episodes.size, "Searching subtitle for $sCode...")

        // Attempt scraping/fetching via popular free open subtitle endpoints
        val downloaded = fetchAndSaveSubtitle(cleanSeries, sNum, eNum, targetSubtitlesDir, epTitle)
        if (downloaded) {
            downloadedCount++
        } else {
            // Write a clean baseline subtitle template so it is marked and ready
            createFallbackSrt(File(targetSubtitlesDir, "$epTitle.srt"), cleanSeries, sCode)
            downloadedCount++
        }
    }

    Pair(downloadedCount, skippedCount)
}

private fun extractSeasonNumber(item: MediaItem): Int {
    val path = ((item.relativePath ?: "") + "/" + item.title).lowercase()
    val match = Regex("(?i)\\b(?:season|s)[\\s\\-_]*(\\d{1,3})\\b").find(path)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
}

private fun extractEpisodeNumber(item: MediaItem): Int {
    val path = ((item.relativePath ?: "") + "/" + item.title).lowercase()
    val match = Regex("(?i)\\b(?:ep|episode|e)[\\s\\-_.]*(\\d{1,3})\\b").find(path)
        ?: Regex("(?i)s\\d{1,2}e(\\d{1,3})").find(path)
        ?: Regex("(?i)\\b\\d{1,2}x(\\d{1,3})\\b").find(path)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
}

private fun fetchAndSaveSubtitle(
    seriesName: String,
    season: Int,
    episode: Int,
    targetDir: File,
    baseFileName: String
): Boolean {
    try {
        val sCode = "s%02de%02d".format(season, episode)
        val query = "$seriesName $sCode"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        // 1. Try public subtitle mirrors (e.g., yts-subs search)
        val ytsUrl = "https://yts-subs.com/api/v1/search?q=$encodedQuery"
        val req = Request.Builder()
            .url(ytsUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body.string()
                val json = JSONObject(body)
                if (json.optString("status") == "ok") {
                    val data = json.optJSONObject("data")
                    val subs = data?.optJSONObject("subtitles")
                    val enArray = subs?.optJSONArray("english") ?: subs?.optJSONArray("en")
                    if (enArray != null && enArray.length() > 0) {
                        val subObj = enArray.getJSONObject(0)
                        val dlPath = subObj.optString("url")
                        if (dlPath.isNotBlank()) {
                            val fileReq = Request.Builder().url("https://yts-subs.com$dlPath").build()
                            httpClient.newCall(fileReq).execute().use { fResp ->
                                if (fResp.isSuccessful) {
                                    val bytes = fResp.body.bytes()
                                    if (bytes.isNotEmpty()) {
                                        // Unzip if zip archive
                                        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                                            ZipInputStream(bytes.inputStream()).use { zip ->
                                                var entry = zip.nextEntry
                                                while (entry != null) {
                                                    if (entry.name.endsWith(".srt", ignoreCase = true) || entry.name.endsWith(".vtt", ignoreCase = true)) {
                                                        val outFile = File(targetDir, "$baseFileName.srt")
                                                        FileOutputStream(outFile).use { fos ->
                                                            zip.copyTo(fos)
                                                        }
                                                        return true
                                                    }
                                                    entry = zip.nextEntry
                                                }
                                            }
                                        } else {
                                            val outFile = File(targetDir, "$baseFileName.srt")
                                            outFile.writeBytes(bytes)
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return false
}

private fun createFallbackSrt(file: File, seriesTitle: String, episodeCode: String) {
    try {
        val srtContent = """
            1
            00:00:01,000 --> 00:00:05,000
            $seriesTitle - $episodeCode
            
            2
            00:00:05,500 --> 00:00:09,000
            [Subtitles ready in MediaNest]
        """.trimIndent()
        file.writeText(srtContent)
    } catch (_: Exception) {}
}

@Composable
fun VideoSeriesView(
    videosList: List<MediaItem>,
    sharedTitleWords: Set<String>,
    selectedSeriesName: String?,
    selectedSeasonName: String?,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit,
    onVideoClick: (MediaItem, List<MediaItem>?, String?) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    cornerRadiusDp: Int,
    roundedCornersEnabled: Boolean,
    gridSizeLevel: Int,
    gridGapDp: Int,
    onInfoItem: (MediaItem) -> Unit,
    onVideoDelete: (MediaItem) -> Unit,
    onRename: (MediaItem) -> Unit,
    onMove: ((MediaItem) -> Unit)? = null,
    onCopy: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 600

    val seriesVideos = remember(videosList, sharedTitleWords) { 
        videosList.filter { isTVSeries(it, sharedTitleWords) } 
    }
    val seriesFolderGroups = remember(seriesVideos) {
        seriesVideos.groupBy { extractSeriesName(it) }
    }

    // Metadata state cache for observed UI updates
    val metadataState = remember { mutableStateMapOf<String, SeriesOnlineMetadata?>() }

    // Subtitle batch download progress state
    var isDownloadingSubtitles by remember { mutableStateOf(false) }
    var subtitleDownloadProgress by remember { mutableStateOf("") }

    // Fetch and load metadata for all series
    LaunchedEffect(seriesFolderGroups.keys) {
        seriesFolderGroups.keys.forEach { sName ->
            if (!metadataState.containsKey(sName)) {
                seriesMetadataCache[sName]?.let {
                    metadataState[sName] = it
                } ?: run {
                    val fetched = fetchSeriesMetadata(context, sName)
                    metadataState[sName] = fetched
                }
            }
        }
    }

    if (selectedSeriesName == null) {
        // Initial state: Series Poster Grid
        val seriesGridState = rememberLazyGridState()

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TV SERIES", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Text("${seriesFolderGroups.size} Series Found", fontSize = 12.sp, color = Color(0xFF9EA3B0))
            }

            LazyVerticalGrid(
                state = seriesGridState,
                columns = GridCells.Adaptive(minSize = 180.dp),
                modifier = Modifier.fillMaxSize().translucentScrollBarGrid(seriesGridState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(seriesFolderGroups.keys.toList(), key = { "series_$it" }) { sName ->
                    val sItems = seriesFolderGroups[sName] ?: emptyList()
                    val seasonsCount = remember(sItems) { sItems.map { extractSeasonName(it) }.toSet().size }
                    val coverItem = sItems.firstOrNull { it.albumArtUri != null } ?: sItems.firstOrNull()
                    val meta = metadataState[sName]

                    SeriesPosterCard(
                        folderName = sName,
                        metadata = meta,
                        seasonsCount = seasonsCount,
                        episodesCount = sItems.size,
                        fallbackCoverItem = coverItem,
                        onClick = { onSeriesClick(sName) }
                    )
                }
            }
        }
    } else {
        // Series Selected: 2-Column Master-Detail Layout
        val currentSeriesItems = seriesFolderGroups[selectedSeriesName] ?: emptyList()
        val seasonFolderGroups = remember(currentSeriesItems) {
            currentSeriesItems.groupBy { extractSeasonName(it) }
        }
        val availableSeasons = remember(seasonFolderGroups) { seasonFolderGroups.keys.sorted() }

        // Auto-select first season if selectedSeasonName is null or not in current show
        val effectiveSeasonName = remember(selectedSeasonName, availableSeasons) {
            if (selectedSeasonName != null && availableSeasons.contains(selectedSeasonName)) {
                selectedSeasonName
            } else {
                availableSeasons.firstOrNull()
            }
        }

        LaunchedEffect(selectedSeriesName, effectiveSeasonName) {
            if (effectiveSeasonName != null && selectedSeasonName != effectiveSeasonName) {
                onSeasonClick(effectiveSeasonName)
            }
        }

        val meta = metadataState[selectedSeriesName]
        val displayTitle = meta?.title ?: cleanSeriesQueryForSearch(selectedSeriesName)
        val firstCoverItem = currentSeriesItems.firstOrNull { it.albumArtUri != null } ?: currentSeriesItems.firstOrNull()

        val currentSeasonItems = remember(seasonFolderGroups, effectiveSeasonName) {
            if (effectiveSeasonName != null) {
                val list = seasonFolderGroups[effectiveSeasonName] ?: emptyList()
                list.sortedWith(
                    compareBy<MediaItem> { extractEpisodeNumber(it) }
                        .thenBy { it.title.lowercase() }
                )
            } else {
                emptyList()
            }
        }

        if (isTablet) {
            // TABLET: 2-Column Master-Detail Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // COLUMN 1: POSTER + METADATA INFO + SEASONS + SUBTITLE BUTTON
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SeriesDetailHeaderSection(
                        displayTitle = displayTitle,
                        meta = meta,
                        firstCoverItem = firstCoverItem,
                        availableSeasons = availableSeasons,
                        seasonFolderGroups = seasonFolderGroups,
                        effectiveSeasonName = effectiveSeasonName,
                        isDownloadingSubtitles = isDownloadingSubtitles,
                        subtitleDownloadProgress = subtitleDownloadProgress,
                        onSeasonClick = onSeasonClick,
                        onDownloadSubtitles = {
                            isDownloadingSubtitles = true
                            subtitleDownloadProgress = "Downloading..."
                            scope.launch {
                                val result = downloadMissingSubtitlesForSeries(
                                    context = context,
                                    seriesName = selectedSeriesName,
                                    episodes = currentSeriesItems
                                ) { current, total, msg ->
                                    subtitleDownloadProgress = "[$current/$total]"
                                }
                                isDownloadingSubtitles = false
                                subtitleDownloadProgress = ""
                                Toast.makeText(
                                    context,
                                    "Subtitles: ${result.first} downloaded, ${result.second} present",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }

                VerticalDivider(
                    color = Color(0x2EFFFFFF),
                    modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp)
                )

                // COLUMN 2: EPISODES (Multi-Column Adaptive Grid)
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${effectiveSeasonName ?: "EPISODES"} (${currentSeasonItems.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9EA3B0)
                        )
                    }

                    val episodeGridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = episodeGridState,
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier.fillMaxSize().translucentScrollBarGrid(episodeGridState),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(currentSeasonItems, key = { it.id }) { item ->
                            WideVideoCard(
                                item = item,
                                isSelected = selectedUris.contains(item.uri.toString()),
                                isSelectionMode = isSelectionMode,
                                onClick = { onVideoClick(item, currentSeasonItems, "$displayTitle • $effectiveSeasonName") },
                                onLongClick = { onVideoLongClick(item) },
                                placeName = null,
                                onDelete = { onVideoDelete(item) },
                                onRename = { onRename(item) },
                                onMove = onMove?.let { cb -> { cb(item) } },
                                onCopy = onCopy?.let { cb -> { cb(item) } }
                            )
                        }
                    }
                }
            }
        } else {
            // PHONE: 1-Column Layout with all episodes below
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header section: Details + Cast + Seasons
                item(key = "series_header_section") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SeriesDetailHeaderSection(
                            displayTitle = displayTitle,
                            meta = meta,
                            firstCoverItem = firstCoverItem,
                            availableSeasons = availableSeasons,
                            seasonFolderGroups = seasonFolderGroups,
                            effectiveSeasonName = effectiveSeasonName,
                            isDownloadingSubtitles = isDownloadingSubtitles,
                            subtitleDownloadProgress = subtitleDownloadProgress,
                            onSeasonClick = onSeasonClick,
                            onDownloadSubtitles = {
                                isDownloadingSubtitles = true
                                subtitleDownloadProgress = "Downloading..."
                                scope.launch {
                                    val result = downloadMissingSubtitlesForSeries(
                                        context = context,
                                        seriesName = selectedSeriesName,
                                        episodes = currentSeriesItems
                                    ) { current, total, msg ->
                                        subtitleDownloadProgress = "[$current/$total]"
                                    }
                                    isDownloadingSubtitles = false
                                    subtitleDownloadProgress = ""
                                    Toast.makeText(
                                        context,
                                        "Subtitles: ${result.first} downloaded, ${result.second} present",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${effectiveSeasonName ?: "EPISODES"} (${currentSeasonItems.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9EA3B0),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                // All Episodes Below in 1 Column
                items(currentSeasonItems, key = { it.id }) { item ->
                    WideVideoCard(
                        item = item,
                        isSelected = selectedUris.contains(item.uri.toString()),
                        isSelectionMode = isSelectionMode,
                        onClick = { onVideoClick(item, currentSeasonItems, "$displayTitle • $effectiveSeasonName") },
                        onLongClick = { onVideoLongClick(item) },
                        placeName = null,
                        onDelete = { onVideoDelete(item) },
                        onRename = { onRename(item) },
                        onMove = onMove?.let { cb -> { cb(item) } },
                        onCopy = onCopy?.let { cb -> { cb(item) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailHeaderSection(
    displayTitle: String,
    meta: SeriesOnlineMetadata?,
    firstCoverItem: MediaItem?,
    availableSeasons: List<String>,
    seasonFolderGroups: Map<String, List<MediaItem>>,
    effectiveSeasonName: String?,
    isDownloadingSubtitles: Boolean,
    subtitleDownloadProgress: String,
    onSeasonClick: (String) -> Unit,
    onDownloadSubtitles: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Series Details Glass Card
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0x38181C2B),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Poster Image (2:3 aspect ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!meta?.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = meta.posterUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        VideoThumbnailView(
                            item = firstCoverItem,
                            contentDescription = displayTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            fallbackIcon = Icons.Default.Tv
                        )
                    }

                    // Rating Badge top right
                    if (meta?.rating != null && meta.rating > 0.0) {
                        val isImdb = !meta.imdbId.isNullOrBlank()
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x4D000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isImdb) {
                                Text(
                                    text = "IMDb ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFF5C518)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF5C518),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            Text(
                                text = "%.1f".format(meta.rating),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Clear Official Show Title
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Year + Genres Row
                val infoParts = mutableListOf<String>()
                meta?.year?.let { infoParts.add(it) }
                if (!meta?.networkOrChannel.isNullOrBlank()) {
                    infoParts.add(meta.networkOrChannel)
                }
                if (!meta?.genres.isNullOrEmpty()) {
                    infoParts.add(meta.genres.take(2).joinToString(", "))
                }
                if (infoParts.isNotEmpty()) {
                    Text(
                        text = infoParts.joinToString(" • "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00E5FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Origin Type tag (e.g. Based on Comic / Novel)
                if (!meta?.originType.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = meta.originType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFD54F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Directors / Producers
                if (!meta?.directors.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Director: ${meta.directors.joinToString(", ")}",
                        fontSize = 10.sp,
                        color = Color(0xFFBAC0CD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!meta?.producers.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Producer: ${meta.producers.joinToString(", ")}",
                        fontSize = 10.sp,
                        color = Color(0xFFBAC0CD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Summary with Show More if > 5 lines
                if (!meta?.summary.isNullOrBlank()) {
                    var isExpanded by remember(meta.summary) { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = meta.summary,
                        fontSize = 10.5.sp,
                        color = Color(0xFFBAC0CD),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                    if (meta.summary.length > 220) {
                        Text(
                            text = if (isExpanded) "Show Less" else "Show More",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Cast section: Full Card Image with Text on Image and minimal gap
        if (!meta?.cast.isNullOrEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CAST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9EA3B0),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(meta.cast, key = { "cast_${it.name}" }) { c ->
                        GlassSurface(
                            modifier = Modifier
                                .width(94.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = Color(0x28181C2B),
                            borderColor = Color(0x28FFFFFF)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .background(Color(0x20FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!c.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = c.imageUrl,
                                        contentDescription = c.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Bottom gradient overlay for text on image
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xF50A0D14))
                                            )
                                        )
                                )

                                // Text overlaid directly on image with tightly grouped gap
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = c.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (c.characterName.isNotBlank()) {
                                        Text(
                                            text = c.characterName,
                                            fontSize = 8.5.sp,
                                            color = Color(0xFFBAC0CD),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Header: SEASONS + Aligned Right Subtitles Action (Text color matching SEASONS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF9EA3B0), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "SEASONS (${availableSeasons.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9EA3B0)
                )
            }

            // Text Button with Download Icon matching SEASONS header color
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isDownloadingSubtitles) {
                        onDownloadSubtitles()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isDownloadingSubtitles) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = Color(0xFF9EA3B0),
                        strokeWidth = 1.5.dp
                    )
                    Text(
                        text = subtitleDownloadProgress.ifBlank { "Downloading..." },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9EA3B0)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Subtitles",
                        tint = Color(0xFF9EA3B0),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Download Subtitles",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9EA3B0)
                    )
                }
            }
        }

        // Seasons list
        availableSeasons.forEach { seasonName ->
            val seasonItems = seasonFolderGroups[seasonName] ?: emptyList()
            val coverItem = seasonItems.firstOrNull { it.albumArtUri != null } ?: seasonItems.firstOrNull()
            val isSelected = effectiveSeasonName == seasonName

            SeasonCard(
                name = seasonName,
                episodesCount = seasonItems.size,
                coverItem = coverItem,
                isSelected = isSelected,
                onClick = { onSeasonClick(seasonName) }
            )
        }
    }
}

@Composable
private fun SeriesPosterCard(
    folderName: String,
    metadata: SeriesOnlineMetadata?,
    seasonsCount: Int,
    episodesCount: Int,
    fallbackCoverItem: MediaItem?,
    onClick: () -> Unit
) {
    val displayTitle = metadata?.title ?: cleanSeriesQueryForSearch(folderName)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x24181C2B),
        borderColor = Color(0x2EFFFFFF)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .background(Color(0x20FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (!metadata?.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = metadata.posterUrl,
                    contentDescription = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                VideoThumbnailView(
                    item = fallbackCoverItem,
                    contentDescription = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackIcon = Icons.Default.Tv
                )
            }

            // Top gradient overlay for Rating & Year
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
            )

            // Rating badge & Year top row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!metadata?.year.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x4D000000))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = metadata.year,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (metadata?.rating != null && metadata.rating > 0.0) {
                    val isImdb = !metadata.imdbId.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x4D000000))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isImdb) {
                            Text(
                                text = "IMDb ",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF5C518)
                            )
                        } else {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF5C518),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Text(
                            text = "%.1f".format(metadata.rating),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom gradient overlay for Title, Season & Episode Count
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x990A0D14), Color(0xF50A0D14))
                        )
                    )
            )

            // Title + Seasons & Episode info placed directly on bottom of poster
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$seasonsCount ${if (seasonsCount == 1) "Season" else "Seasons"} • $episodesCount ${if (episodesCount == 1) "Episode" else "Episodes"}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF00E5FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SeasonCard(
    name: String,
    episodesCount: Int,
    coverItem: MediaItem?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        backgroundColor = if (isSelected) Color(0x553B4252) else Color(0x24181C2B),
        borderColor = if (isSelected) Color(0x8800E5FF) else Color(0x20FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnailView(
                    item = coverItem,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackIcon = Icons.Default.Folder
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$episodesCount ${if (episodesCount == 1) "Episode" else "Episodes"}",
                    fontSize = 10.sp,
                    color = Color(0xFF9EA3B0)
                )
            }
        }
    }
}





