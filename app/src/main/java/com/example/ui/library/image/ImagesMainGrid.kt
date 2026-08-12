package com.example.ui.library.image

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaCategory
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaGridItem
import com.example.ui.components.MediaLoadingAnimation
import com.example.ui.components.translucentScrollBarStaggeredGrid

@Composable
fun ImagesMainGrid(
    images: List<MediaItem>,
    selectedFolder: String?,
    onSelectedFolderChange: (String?) -> Unit,
    viewMode: Int,
    onViewModeChange: (Int) -> Unit,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    cornerRadiusDp: Int,
    roundedCornersEnabled: Boolean,
    gridGapDp: Int,
    imageMinSize: Dp,
    onImageClick: (MediaItem, List<MediaItem>) -> Unit,
    onImageLongClick: (MediaItem) -> Unit,
    onInfoItemChange: (MediaItem?) -> Unit,
    onImageToDeleteChange: (MediaItem?) -> Unit,
    onContextSheetItemChange: (MediaItem?) -> Unit,
    onRemoveFromCategory: (MediaItem) -> Unit,
    selectedCategory: MediaCategory?,
    isLoading: Boolean,
    activeFilterTab: String,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (viewMode == 1 && selectedFolder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSelectedFolderChange(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to folders")
                }
                Text(
                    text = selectedFolder.substringAfterLast('/'),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${images.size} items",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading && images.isEmpty()) {
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
        } else if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x28FFFFFF)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = when (activeFilterTab) {
                                "FAVORITES" -> Icons.Default.Favorite
                                "TRASH" -> Icons.Default.Delete
                                "CAMERA" -> Icons.Default.PhotoCamera
                                else -> Icons.Default.PhotoLibrary
                            },
                            contentDescription = null,
                            tint = Color(0xFFC0C5D0),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (activeFilterTab) {
                                "FAVORITES" -> "No Favorite Photos"
                                "TRASH" -> "Trash is Empty"
                                "CAMERA" -> "No Camera Photos"
                                else -> "No Images Found"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (activeFilterTab) {
                                "FAVORITES" -> "Photos you mark as favorite will appear here"
                                "TRASH" -> "Deleted photos in trash will appear here"
                                "CAMERA" -> "Photos taken with your camera will appear here"
                                else -> "Photos added to your device will appear here"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF9EA3B0)
                        )
                    }
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Adaptive(minSize = imageMinSize),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarStaggeredGrid(gridState),
                contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                verticalItemSpacing = Dp(gridGapDp.toFloat())
            ) {
                items(images, key = { it.id }) { item ->
                    MediaGridItem(
                        item = item,
                        isSelected = selectedUris.contains(item.uri.toString()),
                        isSelectionMode = isSelectionMode,
                        cornerRadiusDp = cornerRadiusDp,
                        roundedCornersEnabled = roundedCornersEnabled,
                        onClick = { onImageClick(item, images) },
                        onLongClick = { onImageLongClick(item) },
                        onInfo = { onInfoItemChange(item) },
                        onDelete = { onImageToDeleteChange(item) },
                        showRemoveOption = selectedCategory != null,
                        onRemoveFromCategory = { onRemoveFromCategory(item) },
                        onOpenFolder = { folderName ->
                            onViewModeChange(1)
                            onSelectedFolderChange(folderName)
                        },
                        onMoreClick = { onContextSheetItemChange(item) },
                        showInGallery = (viewMode == 1 && selectedFolder != null)
                    )
                }
            }
        }
    }
}
