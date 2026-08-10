package com.example.ui.library.image

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaItem
import com.example.ui.theme.LocalDarkTheme
import com.example.util.FolderHiddenUtils

@Composable
fun FolderBatchActionBar(
    visible: Boolean,
    selectedFolderNames: Set<String>,
    folderGroups: Map<String, List<MediaItem>>,
    context: Context,
    onSelectAllToggle: () -> Unit,
    onShowBatchInfo: () -> Unit,
    onGroupClick: () -> Unit,
    onClearSelection: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedFolderNames.size} folder(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSelectAllToggle) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    IconButton(onClick = onShowBatchInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Folder Info", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        Icon(Icons.Default.Share, contentDescription = "Share Folders", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        Icon(Icons.Default.Delete, contentDescription = "Delete Folders", tint = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        enabled = selectedFolderNames.isNotEmpty(),
                        onClick = onGroupClick
                    ) {
                        Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Group")
                    }

                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Folder Selection", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
    val selectedItems = remember(selectedFolderNames, folderGroups) {
        selectedFolderNames.flatMap { folderGroups[it] ?: emptyList() }
    }
    val totalSizeBytes = remember(selectedItems) { selectedItems.sumOf { it.size } }
    val formattedSize = remember(totalSizeBytes) {
        when {
            totalSizeBytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", totalSizeBytes.toDouble() / (1024 * 1024 * 1024))
            totalSizeBytes >= 1024 * 1024 -> String.format("%.1f MB", totalSizeBytes.toDouble() / (1024 * 1024))
            else -> String.format("%d KB", totalSizeBytes / 1024)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Folder Selection Info", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selected Folders: ${selectedFolderNames.size}", color = Color.White)
                Text("Total Files: ${selectedItems.size}", color = Color.White)
                Text("Total Size: $formattedSize", color = Color(0xFF6366F1), fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("OK", color = Color.White)
            }
        }
    )
}