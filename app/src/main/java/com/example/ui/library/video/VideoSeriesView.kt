package com.example.ui.library.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaGridItem

@Composable
fun VideoSeriesView(
    videosList: List<MediaItem>,
    selectedSeriesName: String?,
    selectedSeasonName: String?,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit,
    onBackFromSeason: () -> Unit,
    onBackFromSeries: () -> Unit,
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

    if (selectedSeriesName == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Web Series (${seriesFolderGroups.size} Series)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(seriesFolderGroups.keys.toList(), key = { "series_$it" }) { sName ->
                    val sItems = seriesFolderGroups[sName] ?: emptyList()
                    val seasonsCount = remember(sItems) { sItems.map { extractSeasonName(it) }.toSet().size }
                    val coverUri = sItems.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: sItems.firstOrNull()?.uri

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSeriesClick(sName) },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x28181C2B),
                        borderColor = Color(0x28FFFFFF)
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
    } else if (selectedSeasonName == null) {
        val currentSeriesItems = remember(seriesFolderGroups, selectedSeriesName) {
            seriesFolderGroups[selectedSeriesName] ?: emptyList()
        }
        val seasonFolderGroups = remember(currentSeriesItems) {
            currentSeriesItems.groupBy { extractSeasonName(it) }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = { onBackFromSeries() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(selectedSeriesName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text("${seasonFolderGroups.size} Seasons • ${currentSeriesItems.size} Episodes", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(seasonFolderGroups.keys.sorted(), key = { "season_$it" }) { seasonName ->
                    val seasonItems = seasonFolderGroups[seasonName] ?: emptyList()
                    val coverUri = seasonItems.firstOrNull { it.albumArtUri != null }?.albumArtUri ?: seasonItems.firstOrNull()?.uri

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSeasonClick(seasonName) },
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x28181C2B),
                        borderColor = Color(0x28FFFFFF)
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
    } else {
        val currentSeasonItems = remember(seriesFolderGroups, selectedSeriesName, selectedSeasonName) {
            val seriesItems = seriesFolderGroups[selectedSeriesName] ?: emptyList()
            seriesItems.filter { extractSeasonName(it) == selectedSeasonName }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = { onBackFromSeason() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text("$selectedSeriesName > $selectedSeasonName", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${currentSeasonItems.size} Episodes", fontSize = 12.sp, color = Color(0xFF9EA3B0))
                }
            }

            val videoMinSize = when (gridSizeLevel) {
                0 -> 100.dp
                2 -> 180.dp
                3 -> 220.dp
                else -> 135.dp
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = videoMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                verticalItemSpacing = Dp(gridGapDp.toFloat())
            ) {
                items(currentSeasonItems, key = { it.id }) { item ->
                    MediaGridItem(
                        item = item,
                        isSelected = selectedUris.contains(item.uri.toString()),
                        isSelectionMode = isSelectionMode,
                        cornerRadiusDp = cornerRadiusDp,
                        roundedCornersEnabled = roundedCornersEnabled,
                        onClick = { onVideoClick(item) },
                        onLongClick = { onVideoLongClick(item) },
                        onInfo = { onInfoItem(item) },
                        onDelete = { onVideoDelete(item) },
                        onRename = { onRename(item) }
                    )
                }
            }
        }
    }
}
