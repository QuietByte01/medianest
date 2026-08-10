package com.example.ui.library

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
import com.example.data.model.MediaItem
import com.example.ui.components.GlassSurface

@Composable
fun LibraryBatchActionBar(
    isSelectionMode: Boolean,
    selectedUris: Set<String>,
    currentTabItems: List<MediaItem>,
    isVideosTab: Boolean,
    onSelectAll: () -> Unit,
    onShowBatchInfo: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onAddToCategory: () -> Unit,
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
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x331C1F2B),
                borderColor = Color(0x38FFFFFF)
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
                                DropdownMenu(
                                    expanded = showSelectionMoreMenu,
                                    onDismissRequest = { showSelectionMoreMenu = false },
                                    containerColor = Color(0xEF12151F),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Move Selected") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = Color.White) },
                                        onClick = { showSelectionMoreMenu = false; onMoveSelected() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy Selected") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White) },
                                        onClick = { showSelectionMoreMenu = false; onCopySelected() }
                                    )
                                    if (isVideosTab) {
                                        DropdownMenuItem(
                                            text = { Text("Add to Category") },
                                            leadingIcon = { Icon(Icons.Default.LibraryAdd, contentDescription = null, tint = Color.White) },
                                            onClick = { showSelectionMoreMenu = false; onAddToCategory() }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Share Selected") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
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
