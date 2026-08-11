package com.example.ui.library.image

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.ui.components.MediaGridItem
import com.example.ui.components.MediaLoadingAnimation
import com.example.ui.components.translucentScrollBarGrid
import com.example.ui.components.translucentScrollBarStaggeredGrid

@Composable
fun CollectionViews(
    selectedCategory: MediaCategory?,
    onSelectedCategoryChange: (MediaCategory?) -> Unit,
    imageCollections: List<MediaCategory>,
    categoryFolderMap: Map<Long, List<String>>,
    imagesList: List<MediaItem>,
    isLoading: Boolean,
    viewMode: Int,
    onViewModeChange: (Int) -> Unit,
    onIsFolderSelectionActiveChange: (Boolean) -> Unit,
    onEditCollectionRequest: (MediaCategory, Set<String>) -> Unit,
    onImageClick: (MediaItem, List<MediaItem>) -> Unit,
    onImageLongClick: (MediaItem) -> Unit,
    onContextSheetItemChange: (MediaItem) -> Unit,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    cornerRadiusDp: Int,
    roundedCornersEnabled: Boolean,
    gridGapDp: Int,
    imageMinSize: Dp
) {
    val cardShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            selectedCategory != null -> {
                val memberFolders = categoryFolderMap[selectedCategory.id] ?: emptyList()
                val collectionImages = remember(imagesList, memberFolders) {
                    imagesList.filter { (it.bucketName ?: "Pictures") in memberFolders }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Restored descriptive info (Back button is now in SortRow)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedCategory.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (memberFolders.isEmpty()) "No folders assigned"
                                else "Folders: ${memberFolders.joinToString(", ")} • ${collectionImages.size} items",
                                fontSize = 12.sp,
                                color = Color(0xFF9EA3B0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        IconButton(onClick = {
                            onEditCollectionRequest(selectedCategory, memberFolders.toSet())
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Edit Collection Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (collectionImages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No images in this collection",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap settings above to add folders to this collection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val collectionGridState = rememberLazyStaggeredGridState()
                        LazyVerticalStaggeredGrid(
                            state = collectionGridState,
                            columns = StaggeredGridCells.Adaptive(minSize = imageMinSize),
                            modifier = Modifier
                                .fillMaxSize()
                                .translucentScrollBarStaggeredGrid(collectionGridState),
                            contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                            horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                            verticalItemSpacing = Dp(gridGapDp.toFloat())
                        ) {
                            items(collectionImages, key = { it.id }) { item ->
                                MediaGridItem(
                                    item = item,
                                    isSelected = selectedUris.contains(item.uri.toString()),
                                    isSelectionMode = isSelectionMode,
                                    cornerRadiusDp = cornerRadiusDp,
                                    roundedCornersEnabled = roundedCornersEnabled,
                                    onClick = { onImageClick(item, collectionImages) },
                                    onLongClick = { onImageLongClick(item) },
                                    onMoreClick = { onContextSheetItemChange(item) }
                                )
                            }
                        }
                    }
                }
            }

            viewMode == 2 -> {
                if (isLoading && imageCollections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MediaLoadingAnimation(
                            mediaType = MediaType.IMAGE,
                            iconSize = 52.dp,
                            showLabel = true
                        )
                    }
                } else if (imageCollections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderCopy,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Collections Created",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select folders in the Folders view and tap 'Group into Collection' to create one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                onViewModeChange(1)
                                onIsFolderSelectionActiveChange(true)
                            }) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Folders to Group")
                            }
                        }
                    }
                } else {
                    val collectionsGridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = collectionsGridState,
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .translucentScrollBarGrid(collectionsGridState),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(imageCollections, key = { it.id }) { cat ->
                            val memberFolders = categoryFolderMap[cat.id] ?: emptyList()
                            val collectionImages = remember(imagesList, memberFolders) {
                                imagesList.filter { (it.bucketName ?: "Pictures") in memberFolders }
                            }
                            val coverImage = collectionImages.firstOrNull()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .clickable { onSelectedCategoryChange(cat) },
                                shape = cardShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (coverImage != null) {
                                            AsyncImage(
                                                model = coverImage.uri,
                                                contentDescription = cat.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Collections,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = cat.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${memberFolders.size} folders • ${collectionImages.size} items",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
