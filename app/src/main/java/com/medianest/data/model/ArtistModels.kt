package com.medianest.data.model

import android.net.Uri

data class PopularAlbum(
    val title: String,
    val artworkUrl: String?
)

data class LocalAlbumInfo(
    val title: String,
    val artworkUri: Uri?,
    val songCount: Int
)

data class GlobalTrack(
    val title: String,
    val artworkUrl: String?
)

data class LatestRelease(
    val title: String,
    val releaseDate: String,
    val artworkUrl: String?
)

data class ArtistSocialLinks(
    val spotify: String? = null,
    val youtube: String? = null,
    val instagram: String? = null
)

data class PersonalArtistStats(
    val totalPlays: Int = 0,
    val topPlayedSong: String? = null,
    val firstDiscovered: Long = 0L,
    val inPlaylists: List<String> = emptyList()
)

data class SimilarArtist(
    val name: String,
    val imageUrl: String?
)

data class ArtistInfo(
    val name: String,
    val imageUrl: String?,
    val awards: List<String>,
    val popularAlbums: List<PopularAlbum>,
    val localAlbums: List<LocalAlbumInfo>,
    val genres: List<String> = emptyList(),
    val similarArtists: List<SimilarArtist> = emptyList(),
    val topGlobalTracks: List<GlobalTrack> = emptyList(),
    val latestRelease: LatestRelease? = null,
    val socialLinks: ArtistSocialLinks = ArtistSocialLinks(),
    val personalStats: PersonalArtistStats = PersonalArtistStats(),
    val yearsActive: String? = null,
    val origin: String? = null,
    val about: String? = null,
    val topSongTitle: String? = null
)

object MockArtistDataSource {
    fun getArtistInfo(artistName: String?): ArtistInfo {
        val name = artistName ?: "Unknown Artist"
        val seed = name.hashCode().toString()
        
        return ArtistInfo(
            name = name,
            imageUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=$seed",
            awards = listOf("Grammy Winner 2024", "Top Artist of the Year", "Platinum Record"),
            popularAlbums = listOf(
                PopularAlbum("The Masterpiece", "https://picsum.photos/seed/${seed}1/200"),
                PopularAlbum("Electric Dreams", "https://picsum.photos/seed/${seed}2/200"),
                PopularAlbum("Midnight Soul", "https://picsum.photos/seed/${seed}3/200"),
                PopularAlbum("Skyline Beats - Deluxe Edition Extended", "https://picsum.photos/seed/${seed}4/200")
            ),
            localAlbums = listOf(
                LocalAlbumInfo("Local Hits Vol 1", null, 12),
                LocalAlbumInfo("Studio Sessions", null, 8),
                LocalAlbumInfo("Live in London", null, 15)
            ),
            topSongTitle = "The Greatest Hit Ever Made"
        )
    }
}
