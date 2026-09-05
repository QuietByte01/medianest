package com.medianest.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.BackdropGlassSurface
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.theme.LocalDarkTheme

@Composable
fun LibraryBatchActionBar(
    isSelectionMode: Boolean,
    selectedUris: Set<String>,
    currentTabItems: List<MediaItem>,
    isVideosTab: Boolean = false,
    isImagesTab: Boolean = false,
    onSelectAll: () -> Unit,
    onShowBatchInfo: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onAddToCategory: () -> Unit = {},
    onMoveToFilter: (() -> Unit)? = null,
    onStartSlideshow: (() -> Unit)? = null,
    onClearSelection: () -> Unit,
    context: Context
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    AnimatedVisibility(
        visible = isSelectionMode,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            BackdropGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x330F1015),
                borderColor = Color(0x38FFFFFF),
                enableBlur = true,
                blurRadius = 24.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedUris.size} selected",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = Color.White)
                        }

                        if (isImagesTab && onStartSlideshow != null) {
                            IconButton(onClick = onStartSlideshow) {
                                Icon(Icons.Default.Slideshow, contentDescription = "Start Slideshow", tint = Color(0xFF10B981))
                            }
                        }

                        IconButton(onClick = onShowBatchInfo) {
                            Icon(Icons.Default.Info, contentDescription = "Batch Info", tint = Color.White)
                        }

                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFFF5252))
                        }

                        if (!isTablet) {
                            var showSelectionMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showSelectionMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                                }
                                val isDark = LocalDarkTheme.current
                                GlassDropdownMenu(
                                    expanded = showSelectionMoreMenu,
                                    onDismissRequest = { showSelectionMoreMenu = false },
                                    modifier = Modifier.width(200.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Move Selected", color = if (isDark) Color.White else Color.Black) },
                                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                        onClick = { showSelectionMoreMenu = false; onMoveSelected() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy Selected", color = if (isDark) Color.White else Color.Black) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                        onClick = { showSelectionMoreMenu = false; onCopySelected() }
                                    )
                                    if (isImagesTab && onMoveToFilter != null) {
                                        DropdownMenuItem(
                                            text = { Text("Move to Filter...", color = if (isDark) Color.White else Color.Black) },
                                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                            onClick = { showSelectionMoreMenu = false; onMoveToFilter() }
                                        )
                                    }
                                    if (isVideosTab) {
                                        DropdownMenuItem(
                                            text = { Text("Add to Category", color = if (isDark) Color.White else Color.Black) },
                                            leadingIcon = { Icon(Icons.Default.LibraryAdd, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                            onClick = { showSelectionMoreMenu = false; onAddToCategory() }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Share Selected", color = if (isDark) Color.White else Color.Black) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showSelectionMoreMenu = false
                                            val urisToShare = selectedUris.map { Uri.parse(it) }
                                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                type = "*/*"
                                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                        }
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = onMoveSelected) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = Color.White)
                            }

                            IconButton(onClick = onCopySelected) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                            }

                            if (isImagesTab && onMoveToFilter != null) {
                                IconButton(onClick = onMoveToFilter) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Move to Filter", tint = Color.White)
                                }
                            }

                            if (isVideosTab) {
                                IconButton(onClick = onAddToCategory) {
                                    Icon(Icons.Default.LibraryAdd, contentDescription = "Add to Category", tint = Color.White)
                                }
                            }

                            IconButton(onClick = {
                                val urisToShare = selectedUris.map { Uri.parse(it) }
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                        }

                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
