package com.example.util

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object ArtistImageUtils {

    private val cache = ConcurrentHashMap<String, String>()

    private val ARTIST_PORTRAITS = listOf(
        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80", // Singer microphone
        "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80", // Concert stage
        "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400&q=80", // Vocalist live
        "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=400&q=80", // DJ producer
        "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=400&q=80", // Guitarist portrait
        "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=400&q=80", // Music band
        "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=400&q=80", // Neon performer
        "https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?w=400&q=80", // Piano performer
        "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=400&q=80", // Nightclub DJ
        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&q=80", // Electronic music producer
        "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=400&q=80", // Male singer portrait
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&q=80", // Female singer portrait
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&q=80", // Composer studio portrait
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&q=80", // Music producer studio
        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=400&q=80"  // Cyberpunk synthwave producer
    )

    fun getFallbackArtistImageUrl(artistName: String?): String {
        if (artistName.isNullOrBlank() || artistName.equals("Unknown Artist", ignoreCase = true) || artistName.equals("Unknown", ignoreCase = true)) {
            return ARTIST_PORTRAITS[0]
        }
        val clean = artistName.trim().lowercase()
        val index = Math.abs(clean.hashCode()) % ARTIST_PORTRAITS.size
        return ARTIST_PORTRAITS[index]
    }

    fun getArtistImageUrl(artistName: String?): String {
        if (artistName.isNullOrBlank() || artistName.equals("Unknown Artist", ignoreCase = true) || artistName.equals("Unknown", ignoreCase = true)) {
            return ARTIST_PORTRAITS[0]
        }
        val cleanKey = artistName.trim().lowercase()
        return cache[cleanKey] ?: getFallbackArtistImageUrl(artistName)
    }

    suspend fun fetchArtistImageUrl(artistName: String?, offlineMode: Boolean = false): String = withContext(Dispatchers.IO) {
        if (artistName.isNullOrBlank() || artistName.equals("Unknown Artist", ignoreCase = true) || artistName.equals("Unknown", ignoreCase = true)) {
            return@withContext ARTIST_PORTRAITS[0]
        }
        val cleanKey = artistName.trim().lowercase()
        cache[cleanKey]?.let { return@withContext it }

        if (offlineMode) {
            return@withContext getFallbackArtistImageUrl(artistName)
        }

        try {
            // 1. Query Deezer API for artist portrait
            val encodedName = URLEncoder.encode(artistName.trim(), "UTF-8")
            val deezerUrl = "https://api.deezer.com/search/artist?q=$encodedName"
            val connection = (URL(deezerUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val match = Regex(""""picture_big":"([^"]+)"""").find(responseText)
                    ?: Regex(""""picture_xl":"([^"]+)"""").find(responseText)
                    ?: Regex(""""picture_medium":"([^"]+)"""").find(responseText)
                if (match != null) {
                    val imageUrl = match.groupValues[1].replace("\\/", "/")
                    if (imageUrl.isNotBlank() && !imageUrl.contains("/artist//")) {
                        cache[cleanKey] = imageUrl
                        return@withContext imageUrl
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore & try iTunes fallback
        }

        try {
            // 2. Query iTunes API for artist / album artwork
            val encodedName = URLEncoder.encode(artistName.trim(), "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encodedName&entity=album&limit=1"
            val connection = (URL(itunesUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val match = Regex(""""artworkUrl100":"([^"]+)"""").find(responseText)
                if (match != null) {
                    val imageUrl = match.groupValues[1].replace("100x100bb", "600x600bb")
                    cache[cleanKey] = imageUrl
                    return@withContext imageUrl
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        val fallback = getFallbackArtistImageUrl(artistName)
        cache[cleanKey] = fallback
        return@withContext fallback
    }

    fun getSongwriterImageUrl(composerName: String?): String {
        if (composerName.isNullOrBlank() || composerName.equals("Unknown", ignoreCase = true)) {
            return "https://images.unsplash.com/photo-1455390582262-044cdead277a?w=400&q=80"
        }
        val clean = composerName.trim().lowercase()
        val index = Math.abs(clean.hashCode() + 777) % ARTIST_PORTRAITS.size
        return ARTIST_PORTRAITS[index]
    }

    fun getMovieThumbUrl(title: String?, album: String?, artist: String?): String? {
        val combined = "${title ?: ""} ${album ?: ""} ${artist ?: ""}".lowercase()
        val isMovieOrOst = combined.contains("ost") || 
                           combined.contains("soundtrack") || 
                           combined.contains("movie") || 
                           combined.contains("film") || 
                           combined.contains("motion picture") || 
                           combined.contains("cyberpunk") || 
                           combined.contains("interstellar") || 
                           combined.contains("inception") || 
                           combined.contains("dune") || 
                           combined.contains("batman") || 
                           combined.contains("score") ||
                           combined.contains("theme")
        if (!isMovieOrOst) return null

        return when {
            combined.contains("cyberpunk") -> "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=400&q=80"
            combined.contains("interstellar") || combined.contains("space") -> "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&q=80"
            combined.contains("dune") -> "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=400&q=80"
            combined.contains("batman") || combined.contains("dark") -> "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=400&q=80"
            else -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=400&q=80"
        }
    }
}

@Composable
fun rememberArtistImageUrl(artistName: String?): String {
    val settingsManager = remember { com.example.MediaNestApp.instance.settingsManager }
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)
    
    val clean = artistName?.trim() ?: "Unknown Artist"
    var imageUrl by remember(clean) { mutableStateOf(ArtistImageUtils.getArtistImageUrl(clean)) }

    LaunchedEffect(clean, offlineMode) {
        val fetched = ArtistImageUtils.fetchArtistImageUrl(clean, offlineMode)
        imageUrl = fetched
    }

    return imageUrl
}
