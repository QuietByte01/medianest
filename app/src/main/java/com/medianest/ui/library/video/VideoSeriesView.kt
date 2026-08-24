package com.medianest.ui.library.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.WideVideoCard
import com.medianest.ui.components.translucentScrollBarGrid
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity

@Composable
fun VideoSeriesView(
    videosList: List<MediaItem>,
    sharedTitleWords: Set<String>,
    selectedSeriesName: String?,
    selectedSeasonName: String?,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit,
    onVideoClick: (MediaItem) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    cornerRadiusDp: Int,
    roundedCornersEnabled: Boolean,
    gridSizeLevel: Int,
    gridGapDp: Int,
    onInfoItem: (MediaItem) -> Unit,
    onVideoDelete: (MediaItem) -> Unit,
    onRename: (MediaItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 600

    val seriesVideos = remember(videosList, sharedTitleWords) { 
        videosList.filter { isTVSeries(it, sharedTitleWords) } 
    }
    val seriesFolderGroups = remember(seriesVideos) {
        seriesVideos.groupBy { extractSeriesName(it) }
    }

    if (selectedSeriesName == null) {
        // Initial state: Show all series in a list (matching master col width)
        val seriesGridState = rememberLazyGridState()
        val columns = GridCells.Fixed(1)

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TV SERIES", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Text("${seriesFolderGroups.size} Series Found", fontSize = 12.sp, color = Color(0xFF9EA3B0))
            }

            LazyVerticalGrid(
                state = seriesGridState,
                columns = columns,
                modifier = Modifier.fillMaxSize().translucentScrollBarGrid(seriesGridState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seriesFolderGroups.keys.toList(), key = { "series_$it" }) { sName ->
                    val sItems = seriesFolderGroups[sName] ?: emptyList()
                    val seasonsCount = remember(sItems) { sItems.map { extractSeasonName(it) }.toSet().size }
                    val coverItem = sItems.firstOrNull { it.albumArtUri != null } ?: sItems.firstOrNull()

                    SeriesCard(
                        name = sName,
                        seasonsCount = seasonsCount,
                        episodesCount = sItems.size,
                        coverItem = coverItem,
                        isSelected = false,
                        onClick = { onSeriesClick(sName) }
                    )
                }
            }
        }
    } else {
        // Master-Detail layout when series is selected
        val scrollState = rememberScrollState()
        val columnWidth = if (isTablet) Dp.Unspecified else (screenWidthDp * 0.85f).dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .then(if (!isTablet) Modifier.horizontalScroll(scrollState) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Column 1: SERIES LIST (Master)
            Column(
                modifier = Modifier
                    .then(if (isTablet) Modifier.weight(0.8f) else Modifier.width(columnWidth))
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SERIES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9EA3B0))
                }

                val masterGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = masterGridState,
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().translucentScrollBarGrid(masterGridState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(seriesFolderGroups.keys.toList(), key = { "master_$it" }) { sName ->
                        val sItems = seriesFolderGroups[sName] ?: emptyList()
                        val seasonsCount = remember(sItems) { sItems.map { extractSeasonName(it) }.toSet().size }
                        val coverItem = sItems.firstOrNull { it.albumArtUri != null } ?: sItems.firstOrNull()
                        val isSelected = selectedSeriesName == sName

                        SeriesCard(
                            name = sName,
                            seasonsCount = seasonsCount,
                            episodesCount = sItems.size,
                            coverItem = coverItem,
                            isSelected = isSelected,
                            compact = true,
                            onClick = { onSeriesClick(sName) }
                        )
                    }
                }
            }

            VerticalDivider(color = Color(0x2EFFFFFF), modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 12.dp))

            // Column 2: SEASONS
            Column(
                modifier = Modifier
                    .then(if (isTablet) Modifier.weight(0.8f) else Modifier.width(columnWidth))
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SEASONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9EA3B0))
                }

                val currentSeriesItems = seriesFolderGroups[selectedSeriesName] ?: emptyList()
                val seasonFolderGroups = remember(currentSeriesItems) {
                    currentSeriesItems.groupBy { extractSeasonName(it) }
                }

                val seasonGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = seasonGridState,
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().translucentScrollBarGrid(seasonGridState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(seasonFolderGroups.keys.sorted(), key = { "season_$it" }) { seasonName ->
                        val seasonItems = seasonFolderGroups[seasonName] ?: emptyList()
                        val coverItem = seasonItems.firstOrNull { it.albumArtUri != null } ?: seasonItems.firstOrNull()
                        val isSelected = selectedSeasonName == seasonName

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

            VerticalDivider(color = Color(0x2EFFFFFF), modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 12.dp))

            // Column 3: EPISODES
            Column(
                modifier = Modifier
                    .then(if (isTablet) Modifier.weight(1.4f) else Modifier.width(columnWidth))
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EPISODES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9EA3B0))
                }

                val currentSeasonItems = remember(seriesFolderGroups, selectedSeriesName, selectedSeasonName) {
                    if (selectedSeasonName != null) {
                        val seriesItems = seriesFolderGroups[selectedSeriesName] ?: emptyList()
                        seriesItems.filter { extractSeasonName(it) == selectedSeasonName }
                    } else {
                        emptyList()
                    }
                }

                val episodeGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = episodeGridState,
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().translucentScrollBarGrid(episodeGridState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentSeasonItems, key = { it.id }) { item ->
                        WideVideoCard(
                            item = item,
                            isSelected = selectedUris.contains(item.uri.toString()),
                            isSelectionMode = isSelectionMode,
                            onClick = { onVideoClick(item) },
                            onLongClick = { onVideoLongClick(item) },
                            placeName = null,
                            onDelete = { onVideoDelete(item) },
                            onRename = { onRename(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCard(
    name: String,
    seasonsCount: Int,
    episodesCount: Int,
    coverItem: MediaItem?,
    isSelected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (isSelected) Color(0x403B4252) else Color(0x28181C2B),
        borderColor = if (isSelected) Color(0x66FFFFFF) else Color(0x28FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(if (compact) 72.dp else 130.dp)
                    .height(if (compact) 44.dp else 76.dp)
                    .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.VideoThumbnailView(
                    item = coverItem,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackIcon = Icons.Default.Tv
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$seasonsCount Seasons • $episodesCount Episodes", fontSize = if (compact) 11.sp else 12.sp, color = Color(0xFF9EA3B0))
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (isSelected) Color(0x403B4252) else Color(0x28181C2B),
        borderColor = if (isSelected) Color(0x66FFFFFF) else Color(0x28FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                com.medianest.ui.components.VideoThumbnailView(
                    item = coverItem,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackIcon = Icons.Default.Folder
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("$episodesCount Episodes", fontSize = 11.sp, color = Color(0xFF9EA3B0))
            }
        }
    }
}
