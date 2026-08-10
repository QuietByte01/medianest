package com.example.ui.library.image

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaCategory
import com.example.ui.components.GlassSurface

@Composable
fun ImageFilterRow(
    activeFilterTab: String,
    onFilterTabChange: (String) -> Unit,
    viewMode: Int,
    onViewModeChange: (Int) -> Unit,
    selectedFolder: String?,
    onSelectedFolderChange: (String?) -> Unit,
    selectedCategory: MediaCategory?,
    onSelectedCategoryChange: (MediaCategory?) -> Unit,
    sortField: String,
    onSortFieldChange: (String) -> Unit,
    isAscending: Boolean,
    onIsAscendingChange: (Boolean) -> Unit,
    onShowHiddenFiles: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf(
                FilterItem("All Photos", "ALL", Icons.Default.PhotoLibrary, 0),
                FilterItem("Folders & Albums", "FOLDERS", Icons.Default.FolderCopy, 1),
                FilterItem("Camera", "CAMERA", Icons.Default.PhotoCamera, 0),
                FilterItem("Favorites", "FAVORITES", Icons.Default.Favorite, 0),
                FilterItem("Notes & Studies", "NOTES", Icons.Default.Note, 0),
                FilterItem("Screenshots", "SCREENSHOTS", Icons.Default.Screenshot, 0),
                FilterItem("GIFs", "GIFS", Icons.Default.Animation, 0),
                FilterItem("Social Media", "SOCIAL", Icons.Default.Share, 0),
                FilterItem("PNG & SVG", "PNG_SVG", Icons.Default.HighQuality, 0),
                FilterItem("Edited", "EDITED", Icons.Default.Edit, 0),
                FilterItem("AI Generated", "AI_GENERATED", Icons.Default.AutoAwesome, 0),
                FilterItem("Anime", "ANIME", Icons.Default.Brush, 0),
                FilterItem("Wallpapers", "WALLPAPERS", Icons.Default.Wallpaper, 0),
                FilterItem("Hidden Folders", "HIDDEN", Icons.Default.FolderZip, 1),
                FilterItem("Trash", "TRASH", Icons.Default.Delete, 0)
            )

            filters.forEach { filter ->
                item {
                    val isSelected = activeFilterTab == filter.id && 
                                   (if (filter.targetViewMode != null) viewMode == filter.targetViewMode else true) && 
                                   selectedCategory == null
                    
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                onFilterTabChange(filter.id)
                                if (filter.targetViewMode != null) onViewModeChange(filter.targetViewMode)
                                onSelectedFolderChange(null)
                                onSelectedCategoryChange(null)
                                if (filter.id == "HIDDEN") {
                                    onShowHiddenFiles()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = filter.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFFC0C5D0),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = filter.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFC0C5D0)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(end = 16.dp, top = 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x221C1F2B))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                .clickable { showSortMenu = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "Sort",
                tint = Color(0xFF9EA3B0),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = sortField,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (isAscending) "Ascending" else "Descending",
                tint = Color(0xFF9EA3B0),
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onIsAscendingChange(!isAscending) }
            )

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                containerColor = Color(0xEE08090E),
                shape = RoundedCornerShape(16.dp)
            ) {
                listOf("Date", "Name", "Type", "Size").forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field, color = Color.White) },
                        onClick = {
                            onSortFieldChange(field)
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

private data class FilterItem(
    val label: String,
    val id: String,
    val icon: ImageVector,
    val targetViewMode: Int? = null
)
