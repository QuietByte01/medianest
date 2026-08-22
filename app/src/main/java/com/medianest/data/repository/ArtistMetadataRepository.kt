package com.medianest.data.repository

import android.content.Context
import com.medianest.MediaNestApp
import com.medianest.data.db.ArtistMetadata
import com.medianest.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ArtistMetadataRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val db = MediaNestApp.instance.database
    private val dao = db.artistMetadataDao()
    private val settings = MediaNestApp.instance.settingsManager
    private val userAgent = "MediaNestApp/1.0 (https://github.com/medianest; admin@medianest.com)"

    suspend fun getArtistInfo(artistName: String): ArtistInfo = withContext(Dispatchers.IO) {
        val cleanName = cleanArtistName(artistName)
        val offlineMode = settings.offlineMode.first()
        val cached = dao.getArtistMetadata(cleanName)

        if (cached != null) {
            // Cache fresh for 7 days
            if (offlineMode || System.currentTimeMillis() - cached.lastUpdated < 7 * 24 * 60 * 60 * 1000) {
                return@withContext mapToArtistInfo(cached)
            }
        }

        if (offlineMode) {
            return@withContext cached?.let { mapToArtistInfo(it) } ?: ArtistInfo(
                name = artistName,
                imageUrl = null,
                awards = emptyList(),
                popularAlbums = emptyList(),
                localAlbums = emptyList(),
                about = "Information not available offline.",
                isPlaceholder = true
            )
        }

        // Fetch from network using cleaned name
        val fetched = fetchFromNetwork(cleanName)
        if (fetched != null) {
            dao.saveArtistMetadata(mapToEntity(fetched))
            return@withContext fetched
        }

        return@withContext cached?.let { mapToArtistInfo(it) } ?: ArtistInfo(
            name = artistName,
            imageUrl = null,
            awards = emptyList(),
            popularAlbums = emptyList(),
            localAlbums = emptyList(),
            about = "Failed to fetch artist details.",
            isPlaceholder = true
        )
    }

    private fun cleanArtistName(name: String): String {
        return name.split(Regex("(?i)\\s+(feat\\.?|ft\\.?|featuring|with|&)\\s+")).first()
            .replace(Regex("[\\[(].*?[\\])]"), "")
            .replace(Regex("- (Remastered|Radio Edit|Single|Live).*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun mapToArtistInfo(entity: ArtistMetadata): ArtistInfo {
        return ArtistInfo(
            name = entity.artistName,
            imageUrl = entity.imageUrl,
            awards = entity.awards?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            popularAlbums = parsePopularAlbums(entity.popularAlbumsJson),
            localAlbums = emptyList(),
            genres = entity.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            similarArtists = parseSimilarArtists(entity.similarArtistsJson),
            topGlobalTracks = parseGlobalTracks(entity.topGlobalTracksJson),
            latestRelease = parseLatestRelease(entity.latestReleaseJson),
            socialLinks = parseSocialLinks(entity.socialLinksJson),
            yearsActive = entity.yearsActive,
            origin = entity.origin,
            about = entity.about,
            topSongTitle = null
        )
    }

    private fun mapToEntity(info: ArtistInfo): ArtistMetadata {
        return ArtistMetadata(
            artistName = info.name,
            about = info.about,
            awards = info.awards.joinToString("|"),
            imageUrl = info.imageUrl,
            popularAlbumsJson = serializePopularAlbums(info.popularAlbums),
            genres = info.genres.joinToString(","),
            similarArtistsJson = serializeSimilarArtists(info.similarArtists),
            topGlobalTracksJson = serializeGlobalTracks(info.topGlobalTracks),
            latestReleaseJson = serializeLatestRelease(info.latestRelease),
            socialLinksJson = serializeSocialLinks(info.socialLinks),
            yearsActive = info.yearsActive,
            origin = info.origin,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun parseSimilarArtists(json: String?): List<SimilarArtist> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<SimilarArtist>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SimilarArtist(obj.getString("name"), obj.optString("imageUrl")))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeSimilarArtists(artists: List<SimilarArtist>): String {
        val array = JSONArray()
        artists.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("imageUrl", it.imageUrl)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseGlobalTracks(json: String?): List<GlobalTrack> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<GlobalTrack>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(GlobalTrack(obj.getString("title"), obj.optString("artworkUrl")))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeGlobalTracks(tracks: List<GlobalTrack>): String {
        val array = JSONArray()
        tracks.forEach {
            val obj = JSONObject()
            obj.put("title", it.title)
            obj.put("artworkUrl", it.artworkUrl)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseLatestRelease(json: String?): LatestRelease? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            LatestRelease(obj.getString("title"), obj.getString("releaseDate"), obj.optString("artworkUrl"))
        } catch (_: Exception) { null }
    }

    private fun serializeLatestRelease(release: LatestRelease?): String? {
        if (release == null) return null
        return JSONObject().apply {
            put("title", release.title)
            put("releaseDate", release.releaseDate)
            put("artworkUrl", release.artworkUrl)
        }.toString()
    }

    private fun parseSocialLinks(json: String?): ArtistSocialLinks {
        if (json.isNullOrBlank()) return ArtistSocialLinks()
        return try {
            val obj = JSONObject(json)
            ArtistSocialLinks(
                spotify = obj.optString("spotify").takeIf { it.isNotBlank() },
                youtube = obj.optString("youtube").takeIf { it.isNotBlank() },
                instagram = obj.optString("instagram").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) { ArtistSocialLinks() }
    }

    private fun serializeSocialLinks(links: ArtistSocialLinks): String {
        return JSONObject().apply {
            put("spotify", links.spotify)
            put("youtube", links.youtube)
            put("instagram", links.instagram)
        }.toString()
    }

    private fun parsePopularAlbums(json: String?): List<PopularAlbum> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<PopularAlbum>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(PopularAlbum(obj.getString("title"), obj.optString("artworkUrl")))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializePopularAlbums(albums: List<PopularAlbum>): String {
        val array = JSONArray()
        albums.forEach {
            val obj = JSONObject()
            obj.put("title", it.title)
            obj.put("artworkUrl", it.artworkUrl)
            array.put(obj)
        }
        return array.toString()
    }

    private suspend fun fetchFromNetwork(artistName: String): ArtistInfo? {
        try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            
            // 1. Deezer: Image, Fans, Similar, Top Tracks
            val deezerUrl = "https://api.deezer.com/search/artist?q=$encodedName"
            val response = client.newCall(Request.Builder().url(deezerUrl).header("User-Agent", userAgent).build()).execute()
            
            var imageUrl: String? = null
            val awards = mutableListOf<String>()
            val similarArtists = mutableListOf<SimilarArtist>()
            val topTracks = mutableListOf<GlobalTrack>()
            val genres = mutableListOf<String>()
            
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val artist = data.getJSONObject(0)
                    val artistId = artist.optString("id")
                    imageUrl = artist.optString("picture_xl", artist.optString("picture_big"))
                    val fans = artist.optInt("nb_fan", 0)
                    val albums = artist.optInt("nb_album", 0)
                    if (fans > 1000000) awards.add("Global Icon: ${fans/1000000}M+ Fans")
                    else if (fans > 50000) awards.add("Rising Star: ${fans/1000}K+ Fans")
                    
                    if (albums > 20) awards.add("Music Legend: $albums Albums")
                    else if (albums > 5) awards.add("Prolific: $albums Albums")
                    
                    if (artistId.isNotBlank()) {
                        fetchSimilarFromDeezer(artistId, similarArtists)
                        fetchTopTracksFromDeezer(artistId, topTracks)
                    }
                }
            }

            // 2. Wikipedia: Proper search then extract (Bio, Origin, Years Active)
            val wikiInfo = fetchWikipediaData(artistName)

            // 3. iTunes: Popular Albums, Latest Release, Genres
            val iTunesUrl = "https://itunes.apple.com/search?term=$encodedName&entity=album&limit=10"
            val popularAlbums = mutableListOf<PopularAlbum>()
            var latestRelease: LatestRelease? = null
            
            client.newCall(Request.Builder().url(iTunesUrl).header("User-Agent", userAgent).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            val title = item.optString("collectionName")
                            val art = item.optString("artworkUrl100").replace("100x100", "600x600")
                            val date = item.optString("releaseDate")
                            val genre = item.optString("primaryGenreName")
                            if (genre.isNotBlank() && !genres.contains(genre)) genres.add(genre)
                            
                            if (i < 5) popularAlbums.add(PopularAlbum(title, art))
                            
                            // Check for latest release
                            val currentRelease = latestRelease
                            if (currentRelease == null || (date.isNotBlank() && date > currentRelease.releaseDate)) {
                                latestRelease = LatestRelease(title, date.take(10), art)
                            }
                        }
                    }
                }
            }

            if (imageUrl == null && wikiInfo == null && popularAlbums.isEmpty()) return null

            return ArtistInfo(
                name = artistName,
                imageUrl = imageUrl,
                awards = awards,
                popularAlbums = popularAlbums,
                localAlbums = emptyList(),
                genres = genres,
                similarArtists = similarArtists,
                topGlobalTracks = topTracks,
                latestRelease = latestRelease,
                yearsActive = wikiInfo?.yearsActive,
                origin = wikiInfo?.origin,
                about = wikiInfo?.about,
                topSongTitle = topTracks.firstOrNull()?.title
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun fetchSimilarFromDeezer(artistId: String, outList: MutableList<SimilarArtist>) {
        try {
            val url = "https://api.deezer.com/artist/$artistId/related"
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length().coerceAtMost(6)) {
                        val a = data.getJSONObject(i)
                        outList.add(SimilarArtist(a.getString("name"), a.optString("picture_medium")))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun fetchTopTracksFromDeezer(artistId: String, outList: MutableList<GlobalTrack>) {
        try {
            val url = "https://api.deezer.com/artist/$artistId/top?limit=5"
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val t = data.getJSONObject(i)
                        val album = t.optJSONObject("album")
                        outList.add(GlobalTrack(t.getString("title"), album?.optString("cover_medium")))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private data class WikiData(val about: String?, val yearsActive: String?, val origin: String?)

    private fun fetchWikipediaData(artistName: String): WikiData? {
        val variations = listOf(artistName, "$artistName (musician)", "$artistName (band)")
        for (query in variations) {
            try {
                val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${URLEncoder.encode(query, "UTF-8")}&format=json&utf8=1"
                val response = client.newCall(Request.Builder().url(searchUrl).header("User-Agent", userAgent).build()).execute()
                if (response.isSuccessful) {
                    val searchJson = JSONObject(response.body?.string() ?: "")
                    val results = searchJson.optJSONObject("query")?.optJSONArray("search")
                    if (results != null && results.length() > 0) {
                        for (i in 0 until results.length().coerceAtMost(3)) {
                            val result = results.getJSONObject(i)
                            val title = result.optString("title")
                            val snippet = result.optString("snippet").lowercase()
                            val isMusic = snippet.contains("music") || snippet.contains("singer") || snippet.contains("band") || snippet.contains("artist")
                            if (isMusic || i == 0) {
                                return fetchWikipediaDetails(title)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun fetchWikipediaDetails(title: String): WikiData? {
        try {
            val url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts|revisions&exintro&explaintext&rvprop=content&rvsection=0&titles=${URLEncoder.encode(title, "UTF-8")}&format=json&redirects=1"
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val pages = json.optJSONObject("query")?.optJSONObject("pages")
                pages?.keys()?.forEach { key ->
                    if (key != "-1") {
                        val page = pages.optJSONObject(key)
                        val extract = page?.optString("extract")
                        val content = page?.optJSONArray("revisions")?.optJSONObject(0)?.optString("*") ?: ""
                        
                        val origin = cleanWikiText(findWikiFact(content, "origin"))
                        val yearsActive = cleanWikiText(findWikiFact(content, "years_active"))
                        
                        return WikiData(cleanWikiText(extract), yearsActive, origin)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun cleanWikiText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        
        // 1. Strip HTML comments <!-- ... -->
        val noComments = text.replace(Regex("<!--[\\s\\S]*?-->"), "")
        
        // 2. Decode HTML entities (&amp;, &eacute;, etc)
        return try {
            android.text.Html.fromHtml(noComments, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (_: Exception) {
            noComments.trim()
        }
    }

    private fun findWikiFact(content: String, key: String): String? {
        val regex = Regex("$key\\s*=\\s*(.*?)\\n", RegexOption.IGNORE_CASE)
        val match = regex.find(content)
        return match?.groupValues?.get(1)?.replace(Regex("\\[\\[|\\]\\]|\\{\\{|\\}\\}"), "")?.trim()
    }
}
