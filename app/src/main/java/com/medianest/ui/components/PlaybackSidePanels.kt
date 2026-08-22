package com.medianest.ui.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.medianest.data.model.ArtistInfo
import com.medianest.data.model.MediaItem
import com.medianest.data.model.PopularAlbum
import com.medianest.data.model.LocalAlbumInfo
import com.medianest.data.model.SimilarArtist
import com.medianest.data.model.GlobalTrack
import com.medianest.data.model.LatestRelease
import com.medianest.data.model.PersonalArtistStats
import com.medianest.data.model.ArtistSocialLinks
import com.medianest.player.PlayerState
import com.medianest.util.rememberArtistImageUrl
import com.medianest.ui.videoplayer.getBreadcrumbParts
import com.medianest.ui.videoplayer.safeFormatDuration

/**
 * Side panel for music player showing the current queue.
 */
@Composable
fun GlossySidePanel(
    playerState: PlayerState,
    currentItem: MediaItem?,
    onSongClick: (Int) -> Unit,
    backgroundArt: Any? = null,
    showHidden: Boolean = false,
    hiddenFolders: Set<String> = emptySet()
) {
    val filteredQueue = remember(playerState.queue, showHidden, hiddenFolders) {
        if (showHidden) playerState.queue
        else playerState.queue.filter { item ->
            val folderName = item.bucketName ?: item.relativePath?.trim('/')?.substringAfterLast('/') ?: ""
            val isHidden = folderName.startsWith(".") || item.title.startsWith(".")
            val isExcluded = hiddenFolders.any { hidden ->
                hidden.equals(folderName, ignoreCase = true) ||
                (item.bucketId != null && hidden.equals(item.bucketId, ignoreCase = true)) ||
                (item.relativePath != null && item.relativePath.split("/").any { part -> part.isNotBlank() && part.equals(hidden, ignoreCase = true) })
            }
            !isHidden && !isExcluded
        }
    }

    GlassSurface(
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        backgroundColor = Color.Black.copy(alpha = 0.7f),
        borderColor = Color.White.copy(alpha = 0.12f),
        backgroundImage = backgroundArt,
        enableBlur = true,
        blurRadius = 30.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "UP NEXT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(filteredQueue) { _, item ->
                    val indexInOriginal = playerState.queue.indexOf(item)
                    val isPlaying = item.id == currentItem?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPlaying) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { if (indexInOriginal != -1) onSongClick(indexInOriginal) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.albumArtUri ?: item.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val state = painter.state
                                if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                                    GlassSurface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(14.dp),
                                        backgroundColor = Color.Transparent,
                                        borderColor = Color(0x22FFFFFF)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                color = if (isPlaying) Color.White else Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artist ?: "Unknown Artist",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Side panel for music player showing artist information.
 */
@Composable
fun ArtistInfoPanel(
    artistInfo: ArtistInfo,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1F24293A),
        borderColor = Color(0x2EFFFFFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Artist Image
            val rememberedArtistImage = com.medianest.util.rememberArtistImageUrl(artistInfo.name)
            
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(2.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(rememberedArtistImage ?: artistInfo.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = artistInfo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val state = painter.state
                    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                        val fallback = com.medianest.util.ArtistImageUtils.getFallbackArtistImageUrl(artistInfo.name)
                        AsyncImage(
                            model = fallback,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.1f)
                        )
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = artistInfo.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            // Genres
            if (artistInfo.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    artistInfo.genres.forEach { genre ->
                        Surface(
                            color = Color(0xFF6366F1).copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(0.5.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = genre,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA5B4FC),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }

            // Facts (Origin, Years Active)
            if (!artistInfo.origin.isNullOrBlank() || !artistInfo.yearsActive.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!artistInfo.origin.isNullOrBlank()) {
                        FactItem(icon = Icons.Default.LocationOn, text = artistInfo.origin!!)
                    }
                    if (!artistInfo.origin.isNullOrBlank() && !artistInfo.yearsActive.isNullOrBlank()) {
                        Text("  •  ", color = Color.White.copy(0.3f))
                    }
                    if (!artistInfo.yearsActive.isNullOrBlank()) {
                        FactItem(icon = Icons.Default.CalendarToday, text = artistInfo.yearsActive!!)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons (Share, Favorite)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Share Logic */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(0.2f))
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", fontSize = 12.sp)
                }
                Button(
                    onClick = { /* Favorite Logic */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63).copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Follow", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // About / Biography
            val bio = artistInfo.about
            val hasBio = !bio.isNullOrBlank() && 
                         !bio.contains("No detailed biography found", ignoreCase = true)
            
            if (hasBio) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About Artist", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF69F0AE))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bio!!,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Awards
            val filteredAwards = artistInfo.awards.filter { !it.equals("Certified Artist", ignoreCase = true) }
            if (filteredAwards.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Awards & Honors", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAwards) { award ->
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = award,
                                fontSize = 11.sp,
                                color = Color.White.copy(0.8f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Popular Albums
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Star, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Most Popular Albums", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(artistInfo.popularAlbums) { album ->
                    PopularAlbumItem(album)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Top Song Info (Repositioned above local albums)
            artistInfo.topSongTitle?.let { title ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Text("TOP GLOBAL SONG", fontSize = 10.sp, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold)
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Local Albums
            if (artistInfo.localAlbums.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("In Your Library", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artistInfo.localAlbums) { album ->
                        LocalAlbumItem(album, onAlbumClick)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Global Top Tracks
            if (artistInfo.topGlobalTracks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Popular Worldwide", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artistInfo.topGlobalTracks) { track ->
                        GlobalTrackItem(track)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Latest Release
            artistInfo.latestRelease?.let { release ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.NewReleases, null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Latest Release", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LatestReleaseItem(release)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Personal Stats
            if (artistInfo.personalStats.totalPlays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoGraph, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Your History", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCE93D8))
                }
                Spacer(modifier = Modifier.height(12.dp))
                PersonalStatsGrid(artistInfo.personalStats)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Social & Streaming Links
            val links = artistInfo.socialLinks
            if (links.spotify != null || links.youtube != null || links.instagram != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Link, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF80CBC4))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (links.spotify != null) SocialIcon(Icons.Default.MusicNote, Color(0xFF1DB954)) { /* Open Spotify */ }
                    if (links.youtube != null) SocialIcon(Icons.AutoMirrored.Filled.PlaylistPlay, Color(0xFFFF0000)) { /* Open YouTube */ }
                    if (links.instagram != null) SocialIcon(Icons.Default.CameraAlt, Color(0xFFE4405F)) { /* Open Instagram */ }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (artistInfo.similarArtists.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Groups, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Similar Artists", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCE93D8))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artistInfo.similarArtists) { artist ->
                        SimilarArtistItem(artist)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SocialIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FactItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = Color.White.copy(0.6f))
    }
}

@Composable
private fun ExternalLinkIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.08f))
            .clickable { /* Link Logic */ },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GlobalTrackItem(track: GlobalTrack) {
    Column(modifier = Modifier.width(110.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(track.artworkUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
    }
}

@Composable
private fun LatestReleaseItem(release: LatestRelease) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.06f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(release.artworkUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(release.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Spacer(modifier = Modifier.height(4.dp))
            Text("Released: ${release.releaseDate}", color = Color.White.copy(0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PersonalStatsGrid(stats: PersonalArtistStats) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatRow(label = "Total Plays", value = "${stats.totalPlays}")
        if (stats.topPlayedSong != null) {
            StatRow(label = "Your Favorite", value = stats.topPlayedSong)
        }
        if (stats.firstDiscovered > 0) {
            StatRow(label = "Discovered On", value = dateFormat.format(Date(stats.firstDiscovered * 1000)))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(0.5f))
        Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(max = 140.dp).basicMarquee())
    }
}

@Composable
private fun SimilarArtistItem(artist: SimilarArtist) {
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            placeholder = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Default.Person),
            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Default.Person)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PopularAlbumItem(album: PopularAlbum) {
    Column(modifier = Modifier.width(100.dp)) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                    Icon(Icons.Default.Album, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(36.dp))
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
    }
}

@Composable
private fun LocalAlbumItem(album: LocalAlbumInfo, onAlbumClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onAlbumClick(album.title) }
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                    Icon(Icons.Default.Album, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(36.dp))
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        Text(
            text = "${album.songCount} Songs",
            fontSize = 10.sp,
            color = Color.White.copy(0.5f),
            maxLines = 1
        )
    }
}

/**
 * Drawer-style side panel for video player showing the playlist queue.
 */
@Composable
fun SidebarQueueDrawer(
    playerState: PlayerState,
    onVideoClick: (MediaItem) -> Unit,
    onClose: () -> Unit,
    context: Context,
    showHidden: Boolean = false,
    hiddenFolders: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val filteredQueue = remember(playerState.queue, showHidden, hiddenFolders) {
        if (showHidden) playerState.queue
        else playerState.queue.filter { item ->
            val folderName = item.bucketName ?: item.relativePath?.trim('/')?.substringAfterLast('/') ?: ""
            val isHidden = folderName.startsWith(".") || item.title.startsWith(".")
            val isExcluded = hiddenFolders.any { hidden ->
                hidden.equals(folderName, ignoreCase = true) ||
                (item.bucketId != null && hidden.equals(item.bucketId, ignoreCase = true)) ||
                (item.relativePath != null && item.relativePath.split("/").any { part -> part.isNotBlank() && part.equals(hidden, ignoreCase = true) })
            }
            !isHidden && !isExcluded
        }
    }

    GlassSurface(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xF20E111A),
        borderColor = Color(0x33FFFFFF),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    val breadcrumbParts = remember(playerState.currentItem) {
                        getBreadcrumbParts(context, playerState.currentItem)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        breadcrumbParts.forEachIndexed { index, part ->
                            Text(
                                text = part,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (index < breadcrumbParts.lastIndex) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0x99FFFFFF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredQueue) { video ->
                    val isCurrent = video.uri == playerState.currentItem?.uri
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onVideoClick(video)
                            },
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = if (isCurrent) Color(0x33FFFFFF) else Color(0x1AFFFFFF),
                        borderColor = if (isCurrent) Color.White else Color(0x22FFFFFF)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFF581C87), Color(0xFF0F172A))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = video.albumArtUri ?: video.uri,
                                    contentDescription = video.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x99000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = safeFormatDuration(context, video),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = video.title,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
