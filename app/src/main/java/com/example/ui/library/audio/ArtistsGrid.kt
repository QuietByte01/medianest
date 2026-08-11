package com.example.ui.library.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.translucentScrollBarGrid
import com.example.util.rememberArtistImageUrl

@Composable
fun ArtistsGrid(
    songs: List<MediaItem>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onSongLongClick: (MediaItem) -> Unit,
    gridSizeLevel: Int = 1,
    initialSelectedArtist: String? = null,
    onAddToPlaylist: (MediaItem) -> Unit = {},
    sortField: String = "Name",
    isAscending: Boolean = true,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
) {
    val context = LocalContext.current
    var selectedArtist by remember(initialSelectedArtist) { mutableStateOf(initialSelectedArtist) }

    BackHandler(enabled = selectedArtist != null) {
        selectedArtist = null
    }

    val artistsMap = remember(songs) {
        songs.groupBy { it.artist ?: "Unknown Artist" }
    }

    val sortedArtistNames = remember(artistsMap, sortField, isAscending) {
        val keys = artistsMap.keys.toList()
        val comp = when (sortField) {
            "Name" -> compareBy<String> { it.lowercase() }
            "Date Added" -> compareBy<String> { name ->
                artistsMap[name]?.maxByOrNull { it.dateAdded }?.dateAdded ?: 0L
            }
            else -> compareBy<String> { it.lowercase() }
        }
        if (isAscending) keys.sortedWith(comp) else keys.sortedWith(comp).reversed()
    }

    val topArtistEntry = remember(artistsMap) {
        artistsMap.maxByOrNull { it.value.size }
    }

    if (selectedArtist != null) {
        val artistName = selectedArtist!!
        val artistSongs = artistsMap[artistName] ?: emptyList()
        val artistAlbums = remember(artistSongs) { artistSongs.mapNotNull { it.album }.distinct() }
        var artistSubTab by remember { mutableIntStateOf(0) } // 0 = Songs, 1 = Albums
        val artistImgUrl = rememberArtistImageUrl(artistName)
        val coverUri = artistSongs.firstOrNull { it.albumArtUri != null }?.albumArtUri

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Row removed as it is now in parent SortRow

            // Centered Artist Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(artistImgUrl).crossfade(true).build(),
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = painter.state
                        if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                            // Secondary fallback to album art if network artist image fails
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(coverUri).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val coverState = painter.state
                                if (coverState is AsyncImagePainter.State.Loading || coverState is AsyncImagePainter.State.Error || coverUri == null) {
                                    GlassSurface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(16.dp),
                                        backgroundColor = Color.Transparent,
                                        borderColor = Color(0x22FFFFFF)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(52.dp))
                                        }
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = artistName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${artistSongs.size} Songs · ${artistAlbums.size.coerceAtLeast(1)} Albums",
                    fontSize = 12.5.sp,
                    color = Color(0xFF9EA3B0),
                    textAlign = TextAlign.Center
                )
            }

            // Two Switcher Tabs: Albums & Songs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (artistSubTab == 0) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (artistSubTab == 0) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { artistSubTab = 0 }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Songs (${artistSongs.size})",
                            fontSize = 13.sp,
                            fontWeight = if (artistSubTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (artistSubTab == 0) Color.White else Color(0xFF9EA3B0)
                        )
                    }
                }

                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (artistSubTab == 1) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (artistSubTab == 1) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { artistSubTab = 1 }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Albums (${artistAlbums.size.coerceAtLeast(1)})",
                            fontSize = 13.sp,
                            fontWeight = if (artistSubTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (artistSubTab == 1) Color.White else Color(0xFF9EA3B0)
                        )
                    }
                }
            }

            // Tab Content
            if (artistSubTab == 0) {
                SongsList(artistSongs, selectedUris, isSelectionMode, onSongClick, onSongLongClick, onAddToPlaylist = onAddToPlaylist)
            } else {
                AlbumsGrid(artistSongs, selectedUris, isSelectionMode, onSongClick, onSongLongClick, gridSizeLevel = gridSizeLevel, onAddToPlaylist = onAddToPlaylist)
            }
        }
    } else {

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Featured Artist Header Banner
            if (topArtistEntry != null) {
                val topName = topArtistEntry!!.key
                val topSongs = topArtistEntry!!.value
                val topCover = topSongs.firstOrNull()?.albumArtUri
                val albumCount = remember(topSongs) { topSongs.mapNotNull { it.album }.distinct().size.coerceAtLeast(1) }

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { selectedArtist = topName },
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x2EFFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val topArtistImgUrl = rememberArtistImageUrl(topName)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(topArtistImgUrl).crossfade(true).build(),
                                contentDescription = topName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val state = painter.state
                                if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context).data(topCover).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val coverState = painter.state
                                        if (coverState is AsyncImagePainter.State.Loading || coverState is AsyncImagePainter.State.Error || topCover == null) {
                                            GlassSurface(
                                                modifier = Modifier.fillMaxSize(),
                                                shape = CircleShape,
                                                backgroundColor = Color.Transparent,
                                                borderColor = Color(0x22FFFFFF)
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        } else {
                                            SubcomposeAsyncImageContent()
                                        }
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("MOST PLAYED ARTIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E95A5), letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(topName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$albumCount Albums • ${topSongs.size} Tracks in Library", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                        }

                        val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
                        if (isTablet) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassSurface(
                                    modifier = Modifier.clickable {
                                        if (topSongs.isNotEmpty()) onSongClick(topSongs.first())
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = Color(0x33FFFFFF),
                                    borderColor = Color(0x3DFFFFFF)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play All",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Play All",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                GlassSurface(
                                    modifier = Modifier.clickable {
                                        if (topSongs.isNotEmpty()) onSongClick(topSongs.shuffled().first())
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = Color(0x22FFFFFF),
                                    borderColor = Color(0x28FFFFFF)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Shuffle",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            GlassSurface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        if (topSongs.isNotEmpty()) onSongClick(topSongs.first())
                                    },
                                shape = CircleShape,
                                backgroundColor = Color(0x3DFFFFFF),
                                borderColor = Color(0x4DFFFFFF)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val baseColumns = if (screenWidthDp >= 840) 5 else if (screenWidthDp >= 600) 4 else 2
            val gridColumns = when (gridSizeLevel) {
                0 -> (baseColumns * 1.5f).toInt()
                1 -> baseColumns
                2 -> (baseColumns * 0.75f).toInt().coerceAtLeast(1)
                3 -> (baseColumns * 0.5f).toInt().coerceAtLeast(1)
                else -> baseColumns
            }
            
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarGrid(gridState),
                contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(sortedArtistNames, key = { it }) { artistName ->
                    val artistSongs = artistsMap[artistName] ?: emptyList()
                    val coverItem = artistSongs.firstOrNull()
                    val albumCount = remember(artistSongs) { artistSongs.mapNotNull { it.album }.distinct().size.coerceAtLeast(1) }

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedArtist = artistName },
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x221C1F2B),
                        borderColor = Color(0x2EFFFFFF)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val artistImgUrl = rememberArtistImageUrl(artistName)
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(artistImgUrl).crossfade(true).build(),
                                    contentDescription = artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val state = painter.state
                                    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                                        val albumArtModel = coverItem?.albumArtUri
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context).data(albumArtModel).crossfade(true).build(),
                                            contentDescription = artistName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            val coverState = painter.state
                                            if (coverState is AsyncImagePainter.State.Loading || coverState is AsyncImagePainter.State.Error || albumArtModel == null) {
                                                GlassSurface(
                                                    modifier = Modifier.fillMaxSize(),
                                                    shape = CircleShape,
                                                    backgroundColor = Color.Transparent,
                                                    borderColor = Color(0x22FFFFFF)
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = artistName,
                                                            tint = Color.White.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                SubcomposeAsyncImageContent()
                                            }
                                        }
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = artistName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$albumCount Albums • ${artistSongs.size} Songs",
                                fontSize = 11.sp,
                                color = Color(0xFF9EA3B0),
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
