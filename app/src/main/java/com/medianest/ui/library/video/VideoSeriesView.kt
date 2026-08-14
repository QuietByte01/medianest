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

@Composable
fun VideoSeriesView(
    videosList: List<MediaItem>,
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
    val seriesVideos = remember(videosList) { videosList.filter { isTVSeries(it) } }
    val seriesFolderGroups = remember(seriesVideos) {
        seriesVideos.groupBy { extractSeriesName(it) }
    }

    // Three-column layout matching the target screenshot design
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Column 1: ALL SERIES
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ALL SERIES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9EA3B0))
            }

            val seriesGridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = seriesGridState,
                columns = GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarGrid(seriesGridState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seriesFolderGroups.keys.toList(), key = { "series_$it" }) { sName ->
                    val sItems = seriesFolderGroups[sName] ?: emptyList()
                    val seasonsCount = remember(sItems) { sItems.map { extractSeasonName(it) }.toSet().size }
                    val coverUri = sItems.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: sItems.firstOrNull()?.uri
                    val isSelected = selectedSeriesName == sName

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSeriesClick(sName) },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isSelected) Color(0x403B4252) else Color(0x28181C2B),
                        borderColor = if (isSelected) Color(0x66FFFFFF) else Color(0x28FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverUri != null) {
                                    AsyncImage(
                                        model = coverUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$seasonsCount Seasons • ${sItems.size} Episodes", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(
            color = Color(0x2EFFFFFF),
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(vertical = 12.dp)
        )

        // Column 2: SEASONS
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SEASONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9EA3B0))
            }

            val currentSeriesItems = remember(seriesFolderGroups, selectedSeriesName) {
                if (selectedSeriesName != null) seriesFolderGroups[selectedSeriesName] ?: emptyList() else emptyList()
            }
            val seasonFolderGroups = remember(currentSeriesItems) {
                currentSeriesItems.groupBy { extractSeasonName(it) }
            }

            val seasonGridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = seasonGridState,
                columns = GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarGrid(seasonGridState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seasonFolderGroups.keys.sorted(), key = { "season_$it" }) { seasonName ->
                    val seasonItems = seasonFolderGroups[seasonName] ?: emptyList()
                    val coverUri = seasonItems.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: seasonItems.firstOrNull()?.uri
                    val isSelected = selectedSeasonName == seasonName

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSeasonClick(seasonName) },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isSelected) Color(0x403B4252) else Color(0x28181C2B),
                        borderColor = if (isSelected) Color(0x66FFFFFF) else Color(0x28FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverUri != null) {
                                    AsyncImage(
                                        model = coverUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(seasonName, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Color.White)
                                Text("${seasonItems.size} Episodes", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(
            color = Color(0x2EFFFFFF),
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(vertical = 12.dp)
        )

        // Column 3: EPISODES
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("EPISODES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9EA3B0))
            }

            val currentSeasonItems = remember(seriesFolderGroups, selectedSeriesName, selectedSeasonName) {
                if (selectedSeriesName != null && selectedSeasonName != null) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarGrid(episodeGridState),
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
