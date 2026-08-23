package com.medianest.ui.library.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.MediaCategory
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.CategoryIconUtils

@Composable
fun VideoCategoryRow(
    allVideoCategories: List<MediaCategory>,
    selectedCategory: MediaCategory?,
    isFolderViewActive: Boolean,
    allCrossRefs: List<CategoryMediaCrossRef>,
    videosList: List<MediaItem>,
    onCategorySelect: (MediaCategory?) -> Unit,
    onCreateCategoryClick: () -> Unit,
    onCategoryInfoClick: (MediaCategory) -> Unit,
    onCategoryDeleteClick: (MediaCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(allVideoCategories, key = { it.id }) { cat ->
            var showCatMenu by remember { mutableStateOf(false) }
            val isSelected = !isFolderViewActive && selectedCategory?.id == cat.id
            val count = remember(allCrossRefs, cat, videosList) {
                val crossRefUris = allCrossRefs.filter { it.categoryId == cat.id }.map { it.mediaUri }.toSet()
                videosList.count { item ->
                    isItemInCategory(item, cat, crossRefUris)
                }
            }

            val firstVideoUri = remember(allCrossRefs, cat, videosList) {
                val crossRefUris = allCrossRefs.filter { it.categoryId == cat.id }.map { it.mediaUri }.toSet()
                videosList.firstOrNull { isItemInCategory(it, cat, crossRefUris) }?.let { it.albumArtUri ?: it.uri }
            }

            Box {
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = if (isSelected) Color(0x44C0C0C0) else Color(0x221C1F2B),
                    borderColor = if (isSelected) Color(0x88C0C0C0) else Color(0x28FFFFFF),
                    modifier = Modifier
                        .height(61.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            onCategorySelect(if (isSelected) null else cat)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Small square box container for Icon with max 16.dp rounded border
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0x66FFFFFF) else Color(0x1FFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (cat.iconName) {
                                    "school" -> Icons.Default.School
                                    "cake" -> Icons.Default.Cake
                                    "flight" -> Icons.Default.Flight
                                    "fitness" -> Icons.Default.FitnessCenter
                                    else -> CategoryIconUtils.getCategoryIcon(cat.iconName)
                                },
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFFC0C5D0),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .widthIn(min = 40.dp, max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$count Videos",
                                fontSize = 10.sp,
                                color = Color(0xFF9EA3B0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (cat.id != -999L) {
                            IconButton(
                                onClick = { showCatMenu = true },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Category Options",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }

                GlassDropdownMenu(
                    expanded = showCatMenu,
                    onDismissRequest = { showCatMenu = false },
                    backgroundImage = firstVideoUri
                ) {
                    DropdownMenuItem(
                        text = { Text("Category Info") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showCatMenu = false
                            onCategoryInfoClick(cat)
                        }
                    )
                    
                    val protectedNames = setOf(
                        "training videos", "birthday parties", "travel & vlogs", 
                        "workout", "all categorized videos", "all categories", "favorites"
                    )
                    val isProtected = protectedNames.contains(cat.name.lowercase().trim()) || cat.id == -999L

                    if (!isProtected) {
                        DropdownMenuItem(
                            text = { Text("Delete Category", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showCatMenu = false
                                onCategoryDeleteClick(cat)
                            }
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x221C1F2B),
                borderColor = Color(0x28FFFFFF),
                modifier = Modifier
                    .height(61.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCreateCategoryClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Small square box container for Add Icon with max 16.dp rounded border
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(
                                width = 1.dp,
                                color = Color(0x1FFFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "New Category",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}