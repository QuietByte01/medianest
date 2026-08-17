package com.medianest.ui.library.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.WideVideoCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChronologicalCategoryVideoGrid(
    category: MediaCategory,
    videos: List<MediaItem>,
    onVideoClick: (MediaItem) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onDelete: (MediaItem) -> Unit,
    onRemoveFromCategory: (MediaItem) -> Unit,
    onShowInfo: ((MediaItem) -> Unit)? = null,
    gridState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    // Grouping by date
    val groupedByDate = remember(videos) {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        videos.sortedByDescending { it.dateAdded }.groupBy { item ->
            if (item.dateAdded > 0) {
                val millis = if (item.dateAdded > 10_000_000_000L) item.dateAdded else item.dateAdded * 1000L
                dateFormat.format(Date(millis)).uppercase(Locale.US)
            } else {
                "RECENTLY ADDED"
            }
        }
    }

    LazyColumn(
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var isFirstGroup = true
        groupedByDate.forEach { (dateHeader, itemsInGroup) ->
            val isFirst = isFirstGroup
            isFirstGroup = false

            item(key = "header_$dateHeader") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📅", fontSize = 13.sp)
                        Text(
                            text = if (isFirst) "$dateHeader " else dateHeader,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color(0xFFC0C5D0),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0x28FFFFFF), thickness = 1.dp)
                }
            }

            item(key = "grid_$dateHeader") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(itemsInGroup, key = { it.id }) { item ->
                        val isSelected = selectedUris.contains(item.uri.toString())
                        val context = androidx.compose.ui.platform.LocalContext.current

                        // Asynchronously fetch place name from embedded GPS metadata with Room caching
                        var locationResult by remember(item.id) { mutableStateOf<com.medianest.util.VideoLocationResult?>(null) }
                        val db = remember { com.medianest.MediaNestApp.instance.database }

                        LaunchedEffect(item.uri) {
                            locationResult = com.medianest.util.LocationUtils.getVideoLocationResult(
                                context = context,
                                uri = item.uri,
                                fallbackCategory = item.bucketName ?: category.name,
                                locationCacheDao = db.locationCacheDao()
                            )
                        }

                        WideVideoCard(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = { onVideoClick(item) },
                            onLongClick = { onVideoLongClick(item) },
                            modifier = Modifier.width(280.dp),
                            placeName = locationResult?.placeName,
                            isLocationFallback = locationResult?.isLocationFallback ?: true,
                            onDelete = { onDelete(item) },
                            onRemoveFromCategory = { onRemoveFromCategory(item) },
                            onShowInfo = onShowInfo?.let { cb -> { cb(item) } }
                        )
                    }
                }
            }
        }
    }
}
