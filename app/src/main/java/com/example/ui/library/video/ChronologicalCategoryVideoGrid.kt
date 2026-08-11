package com.example.ui.library.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaCategory
import com.example.data.model.MediaItem
import com.example.ui.components.WideVideoCard
import com.example.util.LocationUtils
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
        else videos.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.relativePath ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    val groupedByDate = remember(filteredVideos) {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        filteredVideos.sortedByDescending { it.dateAdded }.groupBy { item ->
            if (item.dateAdded > 0) {
                dateFormat.format(Date(item.dateAdded * 1000L)).uppercase(Locale.US)
            } else {
                "RECENTLY ADDED"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Back Header (#NotRequired)
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp, vertical = 8.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(12.dp)
//        ) {
//            IconButton(onClick = onBack) {
//                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
//            }
//
//            OutlinedTextField(
//                value = searchQuery,
//                onValueChange = { searchQuery = it },
//                placeholder = { Text("Search in ${category.name}...", fontSize = 14.sp) },
//                modifier = Modifier
//                    .weight(1f)
//                    .height(52.dp),
//                shape = RoundedCornerShape(26.dp),
//                colors = OutlinedTextFieldDefaults.colors(
//                    focusedBorderColor = Color(0x66FFFFFF),
//                    unfocusedBorderColor = Color(0x22FFFFFF),
//                    focusedContainerColor = Color(0x11FFFFFF),
//                    unfocusedContainerColor = Color(0x08FFFFFF),
//                    focusedTextColor = Color.White,
//                    unfocusedTextColor = Color.White
//                ),
//                singleLine = true,
//                trailingIcon = {
//                    if (searchQuery.isNotEmpty()) {
//                        IconButton(onClick = { searchQuery = "" }) {
//                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
//                        }
//                    }
//                }
//            )
//        }

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
                            var placeName by remember(item.id) { mutableStateOf<String?>(null) }
                            val db = remember { com.example.MediaNestApp.instance.database }

                            LaunchedEffect(item.uri) {
                                placeName = LocationUtils.getPlaceNameFromVideo(
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
                                placeName = placeName,
                                onDelete = { onDelete(item) },
                                onRemoveFromCategory = { onRemoveFromCategory(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
