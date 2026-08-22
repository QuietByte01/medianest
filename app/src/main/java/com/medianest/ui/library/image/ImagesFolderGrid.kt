package com.medianest.ui.library.image

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.util.Locale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.medianest.data.db.MediaType
import com.medianest.data.db.SelectiveHiddenFolder
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MediaLoadingAnimation
import com.medianest.ui.components.translucentScrollBarStaggeredGrid
import com.medianest.ui.theme.LocalDarkTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagesFolderGrid(
    visibleFolders: List<String>,
    folderGroups: Map<String, List<MediaItem>>,
    isFolderSelectionActive: Boolean,
    onIsFolderSelectionActiveChange: (Boolean) -> Unit,
    selectedFolderNames: Set<String>,
    onSelectedFolderNamesChange: (Set<String>) -> Unit,
    isLoading: Boolean,
    isScanningHidden: Boolean = false,
    activeFilterTab: String,
    onSelectedFolderChange: (String?) -> Unit,
    onFolderDeleteRequest: (String) -> Unit,
    onFolderInfoRequest: (String) -> Unit,
    onToggleFolderHidden: (String, List<MediaItem>, Boolean) -> Unit,
    isFolderHidden: (String, List<MediaItem>?) -> Boolean,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFolderSelectionActive) "${selectedFolderNames.size} Folders Selected" 
                       else if (activeFilterTab == "HIDDEN") "Hidden Folders (${visibleFolders.size})" 
                       else if (activeFilterTab == "EXCLUDED") "Excluded Folders (${visibleFolders.size})"
                       else "All Folders (${folderGroups.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFC0C5D0)
            )

            Row {
                if (isFolderSelectionActive) {
                    TextButton(onClick = {
                        onSelectedFolderNamesChange(
                            if (selectedFolderNames.size == folderGroups.size) emptySet() else folderGroups.keys.toSet()
                        )
                    }) {
                        Text(if (selectedFolderNames.size == folderGroups.size) "Deselect All" else "Select All", color = Color(0xFFC0C5D0))
                    }
                    TextButton(onClick = {
                        onIsFolderSelectionActiveChange(false)
                        onSelectedFolderNamesChange(emptySet())
                    }) {
                        Text("Done", color = Color(0xFFC0C5D0))
                    }
                } else {
                    TextButton(onClick = { onIsFolderSelectionActiveChange(true) }) {
                        Icon(Icons.Default.Checklist, contentDescription = null, tint = Color(0xFFC0C5D0), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Select Folders", color = Color(0xFFC0C5D0))
                    }
                }
            }
        }

        if ((isLoading && visibleFolders.isEmpty()) || (isScanningHidden && activeFilterTab == "HIDDEN" && visibleFolders.isEmpty())) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MediaLoadingAnimation(
                    mediaType = MediaType.IMAGE,
                    iconSize = 52.dp,
                    showLabel = isScanningHidden && activeFilterTab == "HIDDEN",
                    customMessage = if (isScanningHidden && activeFilterTab == "HIDDEN") "Scanning hidden folders..." else null
                )
            }
        } else if (visibleFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x28FFFFFF)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFC0C5D0), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (activeFilterTab == "EXCLUDED") "No Excluded Folders" else "No Hidden Folders",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeFilterTab == "EXCLUDED") "Folders manually hidden will be listed here" else "Folders marked hidden will be listed here",
                            fontSize = 12.sp,
                            color = Color(0xFF9EA3B0)
                        )
                    }
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .translucentScrollBarStaggeredGrid(gridState),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                items(visibleFolders, key = { it }) { folderName ->
                    val folderItems = folderGroups[folderName] ?: emptyList()
                    val isChecked = selectedFolderNames.contains(folderName)
                    val isHidden = isFolderHidden(folderName, folderItems)

                    val totalSizeBytes = remember(folderItems) { folderItems.sumOf { it.size } }
                    val formattedSize = remember(totalSizeBytes) {
                        val mb = totalSizeBytes / (1024.0 * 1024.0)
                        if (mb >= 1024) String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0) else String.format(Locale.getDefault(), "%.0f MB", mb)
                    }
                    val samplePath = remember(folderItems) {
                        folderItems.firstOrNull()?.relativePath?.trimEnd('/')?.let { "/$it" } ?: "/DCIM/$folderName"
                    }
                    val coverRatio = remember(folderItems) {
                        folderItems.firstOrNull()?.aspectRatio?.coerceIn(0.75f, 1.8f) ?: 1.2f
                    }

                    GlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isChecked) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isChecked) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isFolderSelectionActive) {
                                        onSelectedFolderNamesChange(if (isChecked) selectedFolderNames - folderName else selectedFolderNames + folderName)
                                    } else {
                                        onSelectedFolderChange(folderName)
                                    }
                                },
                                onLongClick = {
                                    if (!isFolderSelectionActive) {
                                        onIsFolderSelectionActiveChange(true)
                                        onSelectedFolderNamesChange(setOf(folderName))
                                    } else {
                                        onSelectedFolderNamesChange(if (isChecked) selectedFolderNames - folderName else selectedFolderNames + folderName)
                                    }
                                }
                            )
                    ) {
                        Box {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(coverRatio)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (LocalDarkTheme.current) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (folderItems.size >= 4) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                AsyncImage(
                                                    model = folderItems[0].uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                                )
                                                Spacer(modifier = Modifier.width(1.dp))
                                                AsyncImage(
                                                    model = folderItems[1].uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                AsyncImage(
                                                    model = folderItems[2].uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                                )
                                                Spacer(modifier = Modifier.width(1.dp))
                                                AsyncImage(
                                                    model = folderItems[3].uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                                )
                                            }
                                        }
                                    } else if (folderItems.isNotEmpty()) {
                                        AsyncImage(
                                            model = folderItems[0].uri,
                                            contentDescription = folderName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFF9EA3B0),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoLibrary,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%,d", folderItems.size),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    if (isHidden) {
                                        Surface(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VisibilityOff,
                                                contentDescription = "Hidden Folder",
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.padding(2.dp).size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isHidden) "${folderName.substringAfterLast('/')} (Hidden)" else folderName.substringAfterLast('/'),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = samplePath,
                                        fontSize = 11.sp,
                                        color = Color(0xFF9EA3B0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formattedSize,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF9EA3B0)
                                    )
                                }
                            }

                            if (isFolderSelectionActive) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onSelectedFolderNamesChange(if (checked == true) selectedFolderNames + folderName else selectedFolderNames - folderName)
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            } else {
                                var showFolderMenu by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                    IconButton(
                                        onClick = { showFolderMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Folder options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    GlassDropdownMenu(
                                        expanded = showFolderMenu,
                                        onDismissRequest = { showFolderMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Folder Info", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                                            onClick = {
                                                showFolderMenu = false
                                                onFolderInfoRequest(folderName)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isHidden) "Include Folder" else "Exclude Folder", color = Color.White) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            },
                                            onClick = {
                                                showFolderMenu = false
                                                onToggleFolderHidden(folderName, folderItems, isHidden)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showFolderMenu = false
                                                onFolderDeleteRequest(folderName)
                                            }
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
