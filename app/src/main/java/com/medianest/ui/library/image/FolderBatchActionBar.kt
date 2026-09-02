package com.medianest.ui.library.image

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.util.FolderHiddenUtils
import java.util.Locale

@Composable
fun FolderBatchActionBar(
    visible: Boolean,
    selectedFolderNames: Set<String>,
    folderGroups: Map<String, List<MediaItem>>,
    context: Context,
    onSelectAllToggle: () -> Unit,
    onShowBatchInfo: () -> Unit,
    onClearSelection: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
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
                        text = "${selectedFolderNames.size} folders selected",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSelectAllToggle) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = Color.White)
                        }

                        IconButton(onClick = onShowBatchInfo) {
                            Icon(Icons.Default.Info, contentDescription = "Folder Info", tint = Color.White)
                        }

                        IconButton(onClick = {
                            val selectedItems = selectedFolderNames.flatMap { folderGroups[it] ?: emptyList() }
                            val urisToShare = selectedItems.map { it.uri }
                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "*/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Folders"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Folders", tint = Color.White)
                        }

                        IconButton(onClick = {
                            val itemsToDelete = selectedFolderNames.flatMap { folderGroups[it] ?: emptyList() }
                            itemsToDelete.forEach { item ->
                                try {
                                    FolderHiddenUtils.deleteMediaUri(context, item.uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            onClearSelection()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Folders", tint = Color(0xFFFF5252))
                        }

                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderBatchInfoModal(
    selectedFolderNames: Set<String>,
    folderGroups: Map<String, List<MediaItem>>,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val maxDialogWidth = if (isTablet) 520.dp else 340.dp

    val selectedItems = remember(selectedFolderNames, folderGroups) {
        selectedFolderNames.flatMap { folderGroups[it] ?: emptyList() }
    }
    val totalSizeBytes = remember(selectedItems) { selectedItems.sumOf { it.size } }
    val formattedSize = remember(totalSizeBytes) {
        when {
            totalSizeBytes >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", totalSizeBytes.toDouble() / (1024 * 1024 * 1024))
            totalSizeBytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", totalSizeBytes.toDouble() / (1024 * 1024))
            else -> String.format(Locale.getDefault(), "%d KB", totalSizeBytes / 1024)
        }
    }

    val blurState = rememberBackdropBlurState()

    // Full screen dim backdrop with blur and black tint
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .backdropReceiver(blurState, blurRadius = 24.dp)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = maxDialogWidth)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(enabled = false) {}, // Intercept click inside card
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color.Black.copy(alpha = 0.72f), // Black tint with blur
            borderColor = Color(0x3334D399),
            enableBlur = true,
            blurRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with Title and Red Close X Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "FOLDER SELECTION INFO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Selected Folders", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("${selectedFolderNames.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Files", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("${selectedItems.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Size", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(formattedSize, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
