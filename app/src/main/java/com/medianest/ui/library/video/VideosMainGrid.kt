package com.medianest.ui.library.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.MediaGridItem
import com.medianest.ui.components.WideVideoCard
import com.medianest.ui.components.translucentScrollBarGrid
import com.medianest.ui.components.translucentScrollBarStaggeredGrid

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun VideosMainGrid(
    sortedDisplayList: List<MediaItem>,
    videoFolderGroups: Map<String, List<MediaItem>>,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    gridSizeLevel: Int,
    gridGapDp: Int,
    cornerRadiusDp: Int,
    roundedCornersEnabled: Boolean,
    selectedCategory: MediaCategory?,
    activeFilterTab: String,
    onVideoClick: (MediaItem) -> Unit,
    onVideoLongClick: (MediaItem) -> Unit,
    onInfoItem: (MediaItem) -> Unit,
    onVideoDelete: (MediaItem) -> Unit,
    onRemoveFromCategory: (MediaItem) -> Unit,
    onAddToCategory: ((MediaItem) -> Unit)? = null,
    onOpenFolder: (String, String?) -> Unit,
    onRename: (MediaItem) -> Unit,
    onMove: ((MediaItem) -> Unit)? = null,
    onCopy: ((MediaItem) -> Unit)? = null,
    selectedFolder: String? = null,
    targetVideoUri: String? = null,
    isFolderViewActive: Boolean = false,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    staggeredGridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
) {
    var highlightedVideoUri by remember { mutableStateOf<String?>(null) }
    // Only use Wide style for specific curated tabs
    val isWideStyle = activeFilterTab in listOf("MUSIC", "MOVIES", "SERIES", "EDITED")

    LaunchedEffect(targetVideoUri, sortedDisplayList) {
        if (!targetVideoUri.isNullOrBlank()) {
            val idx = sortedDisplayList.indexOfFirst { it.uri.toString() == targetVideoUri }
            if (idx >= 0) {
                highlightedVideoUri = targetVideoUri
                if (isWideStyle && activeFilterTab != "EDITED") {
                    gridState.animateScrollToItem(idx)
                } else {
                    staggeredGridState.animateScrollToItem(idx)
                }
                kotlinx.coroutines.delay(2500)
                highlightedVideoUri = null
            }
        }
    }

    if (isWideStyle) {
        if (activeFilterTab == "EDITED") {
            LazyVerticalStaggeredGrid(
                state = staggeredGridState,
                columns = StaggeredGridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarStaggeredGrid(staggeredGridState),
                contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                verticalItemSpacing = Dp(gridGapDp.toFloat())
            ) {
                items(sortedDisplayList, key = { it.id }) { item ->
                    WideVideoCard(
                        item = item,
                        isSelected = selectedUris.contains(item.uri.toString()),
                        isSelectionMode = isSelectionMode,
                        onClick = { onVideoClick(item) },
                        onLongClick = { onVideoLongClick(item) },
                        placeName = null,
                        onDelete = { onVideoDelete(item) },
                        onRemoveFromCategory = if (selectedCategory != null) { { onRemoveFromCategory(item) } } else null,
                        onAddToCategory = if (onAddToCategory != null) { { onAddToCategory(item) } } else null,
                        onRename = { onRename(item) },
                        onMove = if (onMove != null) { { onMove(item) } } else null,
                        onCopy = if (onCopy != null) { { onCopy(item) } } else null,
                        onShowInfo = { onInfoItem(item) },
                        useRealRatio = true
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .translucentScrollBarGrid(gridState),
                contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
                horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
                verticalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat()))
            ) {
                items(sortedDisplayList, key = { it.id }) { item ->
                    WideVideoCard(
                        item = item,
                        isSelected = selectedUris.contains(item.uri.toString()),
                        isSelectionMode = isSelectionMode,
                        onClick = { onVideoClick(item) },
                        onLongClick = { onVideoLongClick(item) },
                        placeName = null,
                        onDelete = { onVideoDelete(item) },
                        onRemoveFromCategory = if (selectedCategory != null) { { onRemoveFromCategory(item) } } else null,
                        onAddToCategory = if (onAddToCategory != null) { { onAddToCategory(item) } } else null,
                        onRename = { onRename(item) },
                        onMove = if (onMove != null) { { onMove(item) } } else null,
                        onCopy = if (onCopy != null) { { onCopy(item) } } else null,
                        onShowInfo = { onInfoItem(item) }
                    )
                }
            }
        }
    } else {
        val videoMinSize = when (gridSizeLevel) {
            0 -> 100.dp
            2 -> 180.dp
            3 -> 220.dp
            else -> 135.dp
        }
        LazyVerticalStaggeredGrid(
            state = staggeredGridState,
            columns = StaggeredGridCells.Adaptive(minSize = videoMinSize),
            modifier = Modifier
                .fillMaxSize()
                .translucentScrollBarStaggeredGrid(staggeredGridState),
            contentPadding = PaddingValues(Dp(gridGapDp.toFloat())),
            horizontalArrangement = Arrangement.spacedBy(Dp(gridGapDp.toFloat())),
            verticalItemSpacing = Dp(gridGapDp.toFloat())
        ) {
            items(sortedDisplayList, key = { it.id }) { item ->
                MediaGridItem(
                    item = item,
                    isSelected = selectedUris.contains(item.uri.toString()),
                    isSelectionMode = isSelectionMode,
                    cornerRadiusDp = cornerRadiusDp,
                    roundedCornersEnabled = roundedCornersEnabled,
                    isHighlighted = (item.uri.toString() == highlightedVideoUri),
                    onClick = { onVideoClick(item) },
                    onLongClick = { onVideoLongClick(item) },
                    onInfo = { onInfoItem(item) },
                    onDelete = { onVideoDelete(item) },
                    showRemoveOption = selectedCategory != null,
                    onRemoveFromCategory = { onRemoveFromCategory(item) },
                    onAddToCategory = if (onAddToCategory != null) { { onAddToCategory(item) } } else null,
                    onOpenFolder = { targetFolder, targetUri ->
                        val matchedKey = videoFolderGroups.keys.firstOrNull { key ->
                            key.equals(targetFolder, ignoreCase = true) ||
                                    key.lowercase().endsWith(targetFolder.lowercase()) ||
                                    targetFolder.lowercase().endsWith(key.lowercase()) ||
                                    key.substringAfterLast('/').equals(targetFolder.substringAfterLast('/'), ignoreCase = true)
                        } ?: targetFolder
                        onOpenFolder(matchedKey, targetUri)
                    },
                    onRename = { onRename(item) },
                    onMove = if (onMove != null) { { onMove(item) } } else null,
                    onCopy = if (onCopy != null) { { onCopy(item) } } else null,
                    showInGallery = (isFolderViewActive && selectedFolder != null)
                )
            }
        }
    }
}
