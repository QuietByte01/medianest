package com.example.ui.library.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.data.db.MediaCategory
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.formatDuration
import com.example.util.CategoryIconUtils
import com.example.util.formatBytesReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChronologicalCategoryVideoGrid(
    category: MediaCategory,
    videos: List<MediaItem>,
    onVideoClick: (MediaItem) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onDelete: (MediaItem) -> Unit,
    onRemoveFromCategory: (MediaItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) videos
        else videos.filter { it.title.contains(searchQuery, ignoreCase = true) || (it.relativePath ?: "").contains(searchQuery, ignoreCase = true) }
    }

    val groupedByDate = remember(filteredVideos) {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        filteredVideos.sortedByDescending { it.dateAdded }.groupBy { item ->
            if (item.dateAdded > 0) {
                dateFormat.format(Date(item.dateAdded * 1000L)).uppercase(Locale.US)
            } else {
                "OCTOBER 14, 2025"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Icon(
                    imageVector = CategoryIconUtils.getCategoryIcon(category.iconName),
                    contentDescription = null,
                    tint = Color(0xFFA855F7),
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x331C1F2B)
                ) {
                    Text(
                        text = "${videos.size} Videos",
                        fontSize = 12.sp,
                        color = Color(0xFF9EA3B0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search logs...", fontSize = 12.sp, color = Color(0xFF8E95A5)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8E95A5), modifier = Modifier.size(16.dp)) },
                singleLine = true,
                modifier = Modifier
                    .width(200.dp)
                    .height(42.dp),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0x66A855F7),
                    unfocusedBorderColor = Color(0x28FFFFFF),
                    focusedContainerColor = Color(0x221C1F2B),
                    unfocusedContainerColor = Color(0x221C1F2B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        LazyColumn(
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
                                text = if (isFirst) "$dateHeader  (SORTED CHRONOLOGICALLY)" else dateHeader,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
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
                            val locationStr = item.bucketName ?: (item.relativePath?.trim('/')?.substringAfterLast('/') ?: "Paris, France")
                            val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
                            val timeStr = if (item.dateAdded > 0) timeFormat.format(Date(item.dateAdded * 1000L)) else "06:30 AM"

                            GlassSurface(
                                modifier = Modifier
                                    .width(280.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onVideoClick(item) },
                                shape = RoundedCornerShape(18.dp),
                                backgroundColor = Color(0x221C1F2B),
                                borderColor = Color(0x28FFFFFF)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    ) {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xDD0F1015),
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .align(Alignment.TopStart)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("📍", fontSize = 10.sp)
                                                Text(locationStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xDD0F1015),
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = formatDuration(item.durationMs),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🕒 $timeStr",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF9EA3B0)
                                        )
                                        Text(
                                            text = if (item.size > 0) formatBytesReport(item.size) else "410 MB",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF9EA3B0),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}