package com.medianest.ui.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
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
import coil.request.videoFrameMicros
import androidx.compose.ui.graphics.asImageBitmap
import android.widget.Toast
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.ArtistInfo
import com.medianest.data.model.MediaItem
import com.medianest.data.model.PopularAlbum
import com.medianest.data.model.LocalAlbumInfo
import com.medianest.data.model.SimilarArtist
import com.medianest.data.model.GlobalTrack
import kotlinx.coroutines.launch
import com.medianest.data.model.LatestRelease
import com.medianest.data.model.PersonalArtistStats
import com.medianest.data.model.ArtistSocialLinks
import com.medianest.player.PlayerState
import com.medianest.player.ExoPlayerManager
import com.medianest.util.rememberArtistImageUrl
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
    hiddenFolders: Set<String> = emptySet(),
    backdropState: BackdropBlurState? = LocalBackdropState.current
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
        backgroundColor = Color.Black.copy(alpha = 0.72f),
        borderColor = Color.White.copy(alpha = 0.15f),
        enableBlur = true,
        blurRadius = 30.dp,
        backdropState = backdropState,
        backgroundImage = backgroundArt ?: (currentItem?.albumArtUri ?: currentItem?.uri),
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
    onPopularAlbumClick: (String) -> Unit,
    onLocalAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    browsingAlbumName: String? = null,
    allAudioItems: List<MediaItem> = emptyList(),
    onBackToArtist: () -> Unit = {},
    playerManager: ExoPlayerManager? = null,
    isLoading: Boolean = false,
    useCardShape: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.medianest.MediaNestApp.instance.database }

    val allCrossRefs by db.categoryDao().getAllCrossRefs().collectAsState(initial = emptyList())
    val artistCategories by db.categoryDao().getCategoriesByType("ARTIST").collectAsState(initial = emptyList())

    val followedCategory = remember(artistCategories) {
        artistCategories.find { it.name.equals("Followed Artists", ignoreCase = true) }
    }

    val isArtistFollowed = remember(allCrossRefs, followedCategory, artistInfo.name) {
        if (followedCategory == null) false
        else allCrossRefs.any { it.categoryId == followedCategory.id && it.mediaUri.equals(artistInfo.name, ignoreCase = true) }
    }

    val toggleFollowLambda = {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dao = db.categoryDao()
            val cats = dao.getCategoriesByTypeSync("ARTIST")
            var cat = cats.find { it.name.equals("Followed Artists", ignoreCase = true) }
            if (cat == null) {
                val newId = dao.insertCategory(MediaCategory(name = "Followed Artists", type = "ARTIST"))
                cat = MediaCategory(id = newId, name = "Followed Artists", type = "ARTIST")
            }

            if (isArtistFollowed) {
                dao.removeMediaFromCategory(cat.id, artistInfo.name)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Unfollowed ${artistInfo.name}", Toast.LENGTH_SHORT).show()
                }
            } else {
                dao.insertCategoryCrossRef(CategoryMediaCrossRef(categoryId = cat.id, mediaUri = artistInfo.name))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Following ${artistInfo.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Composable
    fun PanelContent() {
        Box(modifier = Modifier.fillMaxSize()) {
            if (browsingAlbumName != null) {
                AlbumTracklistView(
                    albumName = browsingAlbumName,
                    allAudioItems = allAudioItems,
                    onBack = onBackToArtist,
                    playerManager = playerManager,
                    artistName = artistInfo.name
                )
            } else {
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

                    // Action Buttons (Share, Follow)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out ${artistInfo.name} on MediaNest")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out ${artistInfo.name} on MediaNest!\n${artistInfo.about?.take(150) ?: ""}")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Artist"))
                            },
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
                            onClick = { toggleFollowLambda() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isArtistFollowed) Color(0xFFE91E63) else Color(0xFFE91E63).copy(alpha = 0.25f)
                            ),
                            border = if (isArtistFollowed) null else BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isArtistFollowed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArtistFollowed) "Following" else "Follow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // About / Biography
                    val bio = artistInfo.about
                    val hasBio = !bio.isNullOrBlank() && 
                                !bio.contains("No detailed biography found", ignoreCase = true)
                    
                    if (hasBio) {
                        var isBioExpanded by remember(artistInfo.name) { mutableStateOf(false) }
                        var canExpandBio by remember(artistInfo.name) { mutableStateOf(false) }

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
                            maxLines = if (isBioExpanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayoutResult ->
                                if (!isBioExpanded && (textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount >= 5)) {
                                    canExpandBio = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        )
                        if (canExpandBio) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isBioExpanded) "Show less" else "Show more",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF69F0AE),
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .clickable { isBioExpanded = !isBioExpanded }
                                    .padding(vertical = 4.dp)
                            )
                        }
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
                            PopularAlbumItem(album, onPopularAlbumClick)
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
                                fontWeight = FontWeight.Bold
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
                                LocalAlbumItem(album, onLocalAlbumClick)
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
                                GlobalTrackItem(
                                    track = track,
                                    artistName = artistInfo.name,
                                    allAudioItems = allAudioItems,
                                    playerManager = playerManager,
                                    currentItem = playerManager?.playerState?.collectAsState()?.value?.currentItem
                                )
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
                        LatestReleaseItem(
                            release = release,
                            artistName = artistInfo.name,
                            allAudioItems = allAudioItems,
                            playerManager = playerManager,
                            currentItem = playerManager?.playerState?.collectAsState()?.value?.currentItem
                        )
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
                                SimilarArtistItem(artist, onArtistClick)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center
                ) {
                    SolidGlossySurface(
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xCC1A1C24),
                        borderColor = Color(0x33FFFFFF),
                        showTopSheen = true
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center),
                            color = Color(0xFF64B5F6),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }
    }

    if (useCardShape) {
        GlassSurface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            enableBlur = true,
            backgroundColor = Color(0x1F24293A),
            borderColor = Color(0x2EFFFFFF)
        ) {
            PanelContent()
        }
    } else {
        Box(modifier = modifier) {
            PanelContent()
        }
    }
}

@Composable
private fun AlbumTracklistView(
    albumName: String,
    allAudioItems: List<MediaItem>,
    onBack: () -> Unit,
    playerManager: ExoPlayerManager? = null,
    artistName: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artistMetadataRepo = remember { com.medianest.MediaNestApp.instance.artistMetadataRepository }
    var isLoadingOnlineTracks by remember(albumName, artistName) { mutableStateOf(true) }
    var onlineTracks by remember(albumName, artistName) { mutableStateOf<List<com.medianest.data.model.AlbumTrack>>(emptyList()) }

    LaunchedEffect(albumName, artistName) {
        isLoadingOnlineTracks = true
        val tracks = artistMetadataRepo.getAlbumTracks(artistName ?: "", albumName)
        onlineTracks = tracks
        isLoadingOnlineTracks = false
    }

    val localAlbumSongs = remember(albumName, allAudioItems, artistName) {
        val cleanTarget = normalizeName(albumName)
        val cleanArtist = artistName?.let { normalizeName(it) }
        
        allAudioItems.filter { 
            val itemAlbum = normalizeName(it.album ?: "")
            val itemArtist = normalizeName(it.artist ?: "")
            
            val albumMatch = itemAlbum.isNotEmpty() && (itemAlbum == cleanTarget || itemAlbum.contains(cleanTarget) || cleanTarget.contains(itemAlbum))
            
            val artistMatch = cleanArtist == null || itemArtist == cleanArtist ||
                (itemArtist.isNotEmpty() && (itemArtist.contains(cleanArtist) || cleanArtist.contains(itemArtist)))
            
            albumMatch && artistMatch
        }
    }

    val playerState = playerManager?.playerState?.collectAsState()?.value
    val currentItem = playerState?.currentItem

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().alpha(0.9f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!artistName.isNullOrBlank()) {
                    Text(
                        text = artistName,
                        fontSize = 12.sp,
                        color = Color(0xFFA5B4FC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoadingOnlineTracks) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Color(0xFF64B5F6),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        } else if (onlineTracks.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(onlineTracks) { idx, track ->
                    val cleanTrackTitle = normalizeName(track.title)
                    val cleanArtistName = normalizeName(track.artistName ?: artistName ?: "")

                    // Match ONLY against localAlbumSongs (local songs in this specific album)
                    val matchedLocal = remember(localAlbumSongs, cleanTrackTitle) {
                        if (cleanTrackTitle.isBlank()) null
                        else {
                            localAlbumSongs.firstOrNull { localItem ->
                                val localTitle = normalizeName(localItem.title)
                                localTitle == cleanTrackTitle ||
                                    (cleanTrackTitle.length >= 4 && (localTitle.contains(cleanTrackTitle) || cleanTrackTitle.contains(localTitle)))
                            }
                        }
                    }

                    val isPlayingThis = currentItem != null && (
                        (matchedLocal != null && currentItem.uri == matchedLocal.uri) ||
                        (
                            normalizeName(currentItem.title) == cleanTrackTitle &&
                            cleanArtistName.isNotBlank() &&
                            normalizeName(currentItem.artist ?: "").contains(cleanArtistName)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPlayingThis) Color.White.copy(alpha = 0.16f)
                                else if (matchedLocal != null) Color.White.copy(alpha = 0.05f)
                                else Color.Transparent
                            )
                            .clickable {
                                if (matchedLocal != null) {
                                    playerManager?.playMediaList(listOf(matchedLocal), 0)
                                } else {
                                    val previewUrl = track.previewUrl
                                    val targetArtist = track.artistName ?: artistName ?: "Unknown Artist"
                                    if (!previewUrl.isNullOrBlank()) {
                                        val onlineItem = MediaItem(
                                            id = -(System.currentTimeMillis() + idx),
                                            title = track.title,
                                            artist = targetArtist,
                                            album = albumName,
                                            uri = android.net.Uri.parse(previewUrl),
                                            albumArtUri = null,
                                            durationMs = track.durationMs,
                                            type = com.medianest.data.db.MediaType.AUDIO,
                                            mimeType = "audio/mp4"
                                        )
                                        playerManager?.playMediaList(listOf(onlineItem), 0)
                                    } else {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val (streamUrl, artUrl) = artistMetadataRepo.resolveTrackStream(targetArtist, track.title)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                if (!streamUrl.isNullOrBlank()) {
                                                    val onlineItem = MediaItem(
                                                        id = -(System.currentTimeMillis() + idx),
                                                        title = track.title,
                                                        artist = targetArtist,
                                                        album = albumName,
                                                        uri = android.net.Uri.parse(streamUrl),
                                                        albumArtUri = artUrl?.let { android.net.Uri.parse(it) },
                                                        durationMs = track.durationMs,
                                                        type = com.medianest.data.db.MediaType.AUDIO,
                                                        mimeType = "audio/mp4"
                                                    )
                                                    playerManager?.playMediaList(listOf(onlineItem), 0)
                                                } else {
                                                    Toast.makeText(context, "No stream available for ${track.title}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${track.trackNumber}",
                            fontSize = 14.sp,
                            color = if (isPlayingThis) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                color = if (matchedLocal != null) Color.White else Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.durationMs > 0) {
                                Text(
                                    text = formatDuration(track.durationMs),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }
                        if (isPlayingThis) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Playing",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (matchedLocal != null) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "In Library",
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = "Online Stream Available",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else if (localAlbumSongs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(localAlbumSongs) { idx, track ->
                    val isPlayingThis = currentItem != null && track.uri == currentItem.uri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPlayingThis) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                playerManager?.playMediaList(localAlbumSongs, idx)
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            fontSize = 14.sp,
                            color = if (isPlayingThis) Color.White else Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.durationMs > 0) {
                                Text(
                                    text = formatDuration(track.durationMs),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }
                        if (isPlayingThis) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Playing",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No tracks found for this album.",
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun normalizeName(name: String): String {
    return name.trim().lowercase()
        .replace(Regex("[\\[(].*?[\\])]"), "") // Remove anything in brackets or parentheses
        .replace(Regex("[^a-z0-9\\s]"), "")     // Remove special characters
        .replace(Regex("\\s+"), " ")           // Normalize whitespace
        .trim()
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
private fun GlobalTrackItem(
    track: GlobalTrack,
    artistName: String,
    allAudioItems: List<MediaItem>,
    playerManager: ExoPlayerManager?,
    currentItem: MediaItem?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleanTrackTitle = remember(track.title) { normalizeName(track.title) }
    val cleanArtistName = remember(track.artistName, artistName) { normalizeName(track.artistName ?: artistName) }

    val matchedLocal = remember(allAudioItems, cleanTrackTitle, cleanArtistName) {
        if (cleanTrackTitle.isBlank() || cleanArtistName.isBlank()) null
        else {
            allAudioItems.firstOrNull { localItem ->
                val localTitle = normalizeName(localItem.title)
                val localArtist = normalizeName(localItem.artist ?: "")

                val isTitleMatch = localTitle == cleanTrackTitle ||
                    (cleanTrackTitle.length >= 4 && (localTitle.contains(cleanTrackTitle) || cleanTrackTitle.contains(localTitle)))

                val isArtistMatch = localArtist.isNotBlank() &&
                    (localArtist.contains(cleanArtistName) || cleanArtistName.contains(localArtist))

                isTitleMatch && isArtistMatch
            }
        }
    }

    val isPlayingThis = currentItem != null && (
        (matchedLocal != null && currentItem.uri == matchedLocal.uri) ||
        (
            normalizeName(currentItem.title) == cleanTrackTitle &&
            cleanArtistName.isNotBlank() &&
            normalizeName(currentItem.artist ?: "").contains(cleanArtistName)
        )
    )

    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable {
                if (matchedLocal != null) {
                    playerManager?.playMediaList(listOf(matchedLocal), 0)
                } else {
                    val previewUrl = track.previewUrl
                    val targetArtist = track.artistName ?: artistName
                    if (!previewUrl.isNullOrBlank()) {
                        val onlineItem = MediaItem(
                            id = -(System.currentTimeMillis()),
                            title = track.title,
                            artist = targetArtist,
                            album = "Top Track",
                            uri = android.net.Uri.parse(previewUrl),
                            albumArtUri = track.artworkUrl?.let { android.net.Uri.parse(it) },
                            durationMs = 0L,
                            type = com.medianest.data.db.MediaType.AUDIO,
                            mimeType = "audio/mp4"
                        )
                        playerManager?.playMediaList(listOf(onlineItem), 0)
                    } else {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val repo = com.medianest.MediaNestApp.instance.artistMetadataRepository
                            val (streamUrl, artUrl) = repo.resolveTrackStream(targetArtist, track.title)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (!streamUrl.isNullOrBlank()) {
                                    val onlineItem = MediaItem(
                                        id = -(System.currentTimeMillis()),
                                        title = track.title,
                                        artist = targetArtist,
                                        album = "Top Track",
                                        uri = android.net.Uri.parse(streamUrl),
                                        albumArtUri = (artUrl ?: track.artworkUrl)?.let { android.net.Uri.parse(it) },
                                        durationMs = 0L,
                                        type = com.medianest.data.db.MediaType.AUDIO,
                                        mimeType = "audio/mp4"
                                    )
                                    playerManager?.playMediaList(listOf(onlineItem), 0)
                                } else {
                                    Toast.makeText(context, "No stream available for ${track.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Play / Status Icon Badge Overlay
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlayingThis) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Playing",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(16.dp)
                    )
                } else if (matchedLocal != null) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "In Library",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Online Stream Available",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LatestReleaseItem(
    release: LatestRelease,
    artistName: String,
    allAudioItems: List<MediaItem>,
    playerManager: ExoPlayerManager?,
    currentItem: MediaItem?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cleanTrackTitle = remember(release.title) { normalizeName(release.title) }
    val cleanArtistName = remember(artistName) { normalizeName(artistName) }

    val matchedLocal = remember(allAudioItems, cleanTrackTitle, cleanArtistName) {
        if (cleanTrackTitle.isBlank() || cleanArtistName.isBlank()) null
        else {
            allAudioItems.firstOrNull { localItem ->
                val localTitle = normalizeName(localItem.title)
                val localArtist = normalizeName(localItem.artist ?: "")

                val isTitleMatch = localTitle == cleanTrackTitle ||
                    (cleanTrackTitle.length >= 4 && (localTitle.contains(cleanTrackTitle) || cleanTrackTitle.contains(localTitle)))

                val isArtistMatch = localArtist.isNotBlank() &&
                    (localArtist.contains(cleanArtistName) || cleanArtistName.contains(localArtist))

                isTitleMatch && isArtistMatch
            }
        }
    }

    val isPlayingThis = currentItem != null && (
        (matchedLocal != null && currentItem.uri == matchedLocal.uri) ||
        (
            normalizeName(currentItem.title) == cleanTrackTitle &&
            cleanArtistName.isNotBlank() &&
            normalizeName(currentItem.artist ?: "").contains(cleanArtistName)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPlayingThis) Color.White.copy(alpha = 0.16f)
                else if (matchedLocal != null) Color.White.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable {
                if (matchedLocal != null) {
                    playerManager?.playMediaList(listOf(matchedLocal), 0)
                } else {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val repo = com.medianest.MediaNestApp.instance.artistMetadataRepository
                        val (streamUrl, artUrl) = repo.resolveTrackStream(artistName, release.title)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (!streamUrl.isNullOrBlank()) {
                                val onlineItem = MediaItem(
                                    id = -(System.currentTimeMillis()),
                                    title = release.title,
                                    artist = artistName,
                                    album = "Latest Release",
                                    uri = android.net.Uri.parse(streamUrl),
                                    albumArtUri = (artUrl ?: release.artworkUrl)?.let { android.net.Uri.parse(it) },
                                    durationMs = 0L,
                                    type = com.medianest.data.db.MediaType.AUDIO,
                                    mimeType = "audio/mp4"
                                )
                                playerManager?.playMediaList(listOf(onlineItem), 0)
                            } else {
                                Toast.makeText(context, "No stream available for ${release.title}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(release.artworkUrl)
                .crossfade(true)
                .build(),
            contentDescription = release.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Released: ${release.releaseDate}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isPlayingThis) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Playing",
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(22.dp)
            )
        } else if (matchedLocal != null) {
            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = "In Library",
                tint = Color(0xFF81C784),
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "Online Stream Available",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
            )
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
        Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(max = 140.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SimilarArtistItem(artist: SimilarArtist, onClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick(artist.name) },
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
private fun PopularAlbumItem(album: PopularAlbum, onClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick(album.title) }
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
            overflow = TextOverflow.Ellipsis
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
            overflow = TextOverflow.Ellipsis
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
    backdropState: BackdropBlurState? = LocalBackdropState.current,
    modifier: Modifier = Modifier
) {
    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xE60D111A) else Color(0xF2FFFFFF)
    val baseBg = if (isDark) Color(0x6608090E) else Color(0x66FFFFFF)
    val shape = RoundedCornerShape(16.dp)

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

    BackdropGlassSurface(
        shape = shape,
        blurRadius = 24.dp,
        tint = Color(0x660A0C10),
        baseColor = Color.Transparent,
        borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x28000000),
        borderWidth = 0.5.dp,
        backdropState = backdropState,
        backgroundImage = playerState.currentItem?.albumArtUri ?: playerState.currentItem?.uri,
        modifier = modifier.clickable(enabled = false) {}
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val contextTitle = playerState.queueTitle
                    val contextIcon = remember(contextTitle) {
                        when {
                            contextTitle == null -> Icons.Default.Folder
                            contextTitle.equals("All Videos", ignoreCase = true) -> Icons.Default.GridView
                            contextTitle.contains("Songs", ignoreCase = true) || contextTitle.contains("Music", ignoreCase = true) -> Icons.Default.MusicNote
                            contextTitle.contains("Movies", ignoreCase = true) -> Icons.Default.Movie
                            contextTitle.contains("Web Series", ignoreCase = true) || contextTitle.contains("Series", ignoreCase = true) || contextTitle.contains("Season", ignoreCase = true) || contextTitle.contains("•") -> Icons.Default.Tv
                            contextTitle.contains("Clips", ignoreCase = true) || contextTitle.contains("Recordings", ignoreCase = true) -> Icons.Default.Videocam
                            contextTitle.contains("Shorts", ignoreCase = true) -> Icons.Default.FlashOn
                            contextTitle.contains("Social", ignoreCase = true) -> Icons.Default.Share
                            contextTitle.contains("Edited", ignoreCase = true) -> Icons.Default.ContentCut
                            contextTitle.contains("Download", ignoreCase = true) -> Icons.Default.Download
                            contextTitle.contains("Hidden", ignoreCase = true) -> Icons.Default.FolderZip
                            contextTitle.contains("Excluded", ignoreCase = true) -> Icons.Default.VisibilityOff
                            contextTitle.contains("Travel", ignoreCase = true) -> Icons.Default.Flight
                            contextTitle.contains("Birthday", ignoreCase = true) -> Icons.Default.Cake
                            contextTitle.contains("Training", ignoreCase = true) -> Icons.Default.School
                            contextTitle.contains("Workout", ignoreCase = true) -> Icons.Default.FitnessCenter
                            else -> Icons.Default.Folder
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(contextIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    if (!contextTitle.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = contextTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val currentIndex = filteredQueue.indexOfFirst { it.uri == playerState.currentItem?.uri }
                            val positionText = if (currentIndex >= 0) "${currentIndex + 1} of ${filteredQueue.size} videos" else "${filteredQueue.size} videos"
                            Text(
                                text = positionText,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    } else {
                        val folderName = playerState.currentItem?.let { item ->
                            item.bucketName ?: item.relativePath?.trim('/')?.substringAfterLast('/') ?: "Single Video"
                        } ?: "Single Video"
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = folderName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredQueue, key = { it.uri.toString() }) { video ->
                    val isCurrent = video.uri == playerState.currentItem?.uri
                    SidebarQueueVideoCard(
                        video = video,
                        isCurrent = isCurrent,
                        context = context,
                        onClick = { onVideoClick(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarQueueVideoCard(
    video: MediaItem,
    isCurrent: Boolean,
    context: Context,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isSurfaceMode = LocalIsSurfaceViewMode.current
    val cardBg = if (isSurfaceMode) {
        if (isCurrent) Color(0xCC1A1E29) else Color(0x9910141E)
    } else {
        if (isCurrent) Color(0x33FFFFFF) else Color(0x1AFFFFFF)
    }
    val cardBorder = if (isCurrent) {
        if (isSurfaceMode) Color(0x8038BDF8) else Color.White
    } else {
        if (isSurfaceMode) Color(0x33FFFFFF) else Color(0x22FFFFFF)
    }

    var fallbackBitmap by remember(video.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    val videoSeekMicros = remember(video.durationMs) {
        if (video.durationMs > 1_000) {
            (video.durationMs * 1000L * 0.15f).toLong()
        } else {
            0L
        }
    }

    val imageRequest = remember<ImageRequest>(video.uri, video.durationMs, context) {
        ImageRequest.Builder(context)
            .data(video.uri)
            .diskCacheKey("queue_${video.uri}_${video.size}_${video.dateAdded}")
            .memoryCacheKey("queue_${video.uri}_${video.size}_${video.dateAdded}")
            .crossfade(true)
            .precision(coil.size.Precision.INEXACT)
            .size(360, 200)
            .decoderFactory(com.medianest.util.SemaphoreVideoFrameDecoder.Factory())
            .videoFrameMicros(videoSeekMicros)
            .build()
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        enableBlur = !isSurfaceMode,
        backgroundColor = cardBg,
        borderColor = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF581C87), Color(0xFF0F172A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (fallbackBitmap != null) {
                    Image(
                        bitmap = fallbackBitmap!!.asImageBitmap(),
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = {
                            coroutineScope.launch {
                                val bmp = com.medianest.util.ThumbnailManager.getThumbnail(context, video.uri)
                                if (bmp != null) {
                                    fallbackBitmap = bmp
                                }
                            }
                        }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.40f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        RoundedPlayIcon(
                            modifier = Modifier.size(10.5.dp),
                            tint = Color.White
                        )
                        Text(
                            text = safeFormatDuration(context, video),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
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
