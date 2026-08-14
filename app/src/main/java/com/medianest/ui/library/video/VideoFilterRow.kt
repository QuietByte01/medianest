package com.medianest.ui.library.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.MediaCategory
import com.medianest.ui.components.GlassSurface

@Composable
fun VideoFilterRow(
    isFolderViewActive: Boolean,
    selectedCategory: MediaCategory?,
    activeFilterTab: String,
    musicCount: Int,
    moviesCount: Int,
    seriesCount: Int,
    clipsCount: Int,
    shortsCount: Int,
    socialCount: Int,
    editedCount: Int,
    downloadedCount: Int,
    trashedCount: Int,
    onFilterSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterTabItem(
                label = "All Videos",
                icon = Icons.Default.GridView,
                isSelected = !isFolderViewActive && selectedCategory == null && activeFilterTab == "ALL",
                onClick = { onFilterSelect("ALL") }
            )
        }

        item {
            FilterTabItem(
                label = "Folders",
                icon = Icons.Default.Folder,
                isSelected = isFolderViewActive,
                onClick = { onFilterSelect("FOLDERS") }
            )
        }

        if (musicCount > 0 || activeFilterTab == "MUSIC") {
            item {
                FilterTabItem(
                    label = "Songs",
                    icon = Icons.Default.MusicNote,
                    isSelected = activeFilterTab == "MUSIC",
                    onClick = { onFilterSelect("MUSIC") }
                )
            }
        }

        if (moviesCount > 0 || activeFilterTab == "MOVIES") {
            item {
                FilterTabItem(
                    label = "Movies",
                    icon = Icons.Default.Movie,
                    isSelected = activeFilterTab == "MOVIES",
                    onClick = { onFilterSelect("MOVIES") }
                )
            }
        }

        if (seriesCount > 0 || activeFilterTab == "SERIES") {
            item {
                FilterTabItem(
                    label = "Web Series",
                    icon = Icons.Default.Tv,
                    isSelected = activeFilterTab == "SERIES",
                    onClick = { onFilterSelect("SERIES") }
                )
            }
        }

        if (clipsCount > 0 || activeFilterTab == "CLIPS") {
            item {
                FilterTabItem(
                    label = "Clips & Recordings",
                    icon = Icons.Default.Videocam,
                    isSelected = activeFilterTab == "CLIPS",
                    onClick = { onFilterSelect("CLIPS") }
                )
            }
        }

        if (shortsCount > 0 || activeFilterTab == "SHORTS") {
            item {
                FilterTabItem(
                    label = "Shorts",
                    icon = Icons.Default.FlashOn,
                    isSelected = activeFilterTab == "SHORTS",
                    onClick = { onFilterSelect("SHORTS") }
                )
            }
        }

        if (socialCount > 0 || activeFilterTab == "SOCIAL") {
            item {
                FilterTabItem(
                    label = "Social Media",
                    icon = Icons.Default.Share,
                    isSelected = activeFilterTab == "SOCIAL",
                    onClick = { onFilterSelect("SOCIAL") }
                )
            }
        }

        if (editedCount > 0 || activeFilterTab == "EDITED") {
            item {
                FilterTabItem(
                    label = "Edited",
                    icon = Icons.Default.ContentCut,
                    isSelected = activeFilterTab == "EDITED",
                    onClick = { onFilterSelect("EDITED") }
                )
            }
        }

        if (downloadedCount > 0 || activeFilterTab == "DOWNLOADED") {
            item {
                FilterTabItem(
                    label = "Downloaded",
                    icon = Icons.Default.Download,
                    isSelected = activeFilterTab == "DOWNLOADED",
                    onClick = { onFilterSelect("DOWNLOADED") }
                )
            }
        }

        if (trashedCount > 0) {
            item {
                FilterTabItem(
                    label = "Trash ($trashedCount)",
                    icon = Icons.Default.Delete,
                    isSelected = activeFilterTab == "TRASH",
                    onClick = { onFilterSelect("TRASH") }
                )
            }
        }

        item {
            FilterTabItem(
                label = "Category",
                icon = Icons.Default.Category,
                isSelected = !isFolderViewActive && activeFilterTab == "CATEGORIES",
                onClick = { onFilterSelect("CATEGORIES") }
            )
        }
    }
}

@Composable
private fun FilterTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
        borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color(0xFF9EA3B0),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF9EA3B0)
            )
        }
    }
}
