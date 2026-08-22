package com.medianest.ui.library.video

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.mediainfo.getFilePathFromUri
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.FolderHiddenUtils
import com.medianest.util.formatBytesReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MoveFolderDialog(
    folderName: String,
    folderGroups: Map<String, List<MediaItem>>,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var targetName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Move Folder: $folderName") },
        text = {
            Column {
                Text("Enter target folder name to move all items from '$folderName':")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = targetName,
                    onValueChange = { targetName = it },
                    label = { Text("Destination Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = targetName.isNotBlank(),
                onClick = {
                    val target = targetName.trim()
                    onDismiss()
                    if (target.isNotBlank()) {
                        val itemsToMove = folderGroups[folderName] ?: emptyList()
                        scope.launch(Dispatchers.IO) {
                            val root = Environment.getExternalStorageDirectory()
                            val destDir = File(root, "Movies/$target")
                            destDir.mkdirs()
                            itemsToMove.forEach { item ->
                                try {
                                    val file = File(item.uri.path ?: "")
                                    if (file.exists()) {
                                        file.copyTo(File(destDir, file.name), overwrite = true)
                                        file.delete()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteFolderDialog(
    folderName: String,
    itemsToDelete: List<MediaItem>,
    context: Context,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Delete Folder: $folderName") },
        text = {
            Text("Are you sure you want to delete this folder and all ${itemsToDelete.size} videos inside? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    onDismiss()
                    scope.launch(Dispatchers.IO) {
                        itemsToDelete.forEach { item ->
                            try {
                                FolderHiddenUtils.deleteMediaUri(context, item.uri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FolderInfoDialog(
    srcFolder: String,
    videoFolderGroups: Map<String, List<MediaItem>>,
    videosList: List<MediaItem>,
    context: Context,
    onDismiss: () -> Unit
) {
    val items = videoFolderGroups[srcFolder]
        ?: videoFolderGroups.entries.firstOrNull { it.key.lowercase().endsWith(srcFolder.lowercase()) || it.key.lowercase().contains(srcFolder.lowercase()) || srcFolder.lowercase().contains(it.key.lowercase()) }?.value
        ?: videosList.filter {
            it.bucketName.equals(srcFolder, ignoreCase = true) ||
                    it.bucketName.equals(srcFolder.substringAfterLast('/'), ignoreCase = true) ||
                    (it.relativePath != null && (it.relativePath.contains(srcFolder) || srcFolder.contains(it.relativePath.trim('/'))))
        }
    val totalSize = items.sumOf { it.size }
    val folderPath = items.firstOrNull()?.let {
        val p = getFilePathFromUri(context, it.uri)
        if (p.contains('/')) p.substringBeforeLast('/') else it.relativePath ?: srcFolder
    } ?: srcFolder

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Folder Info", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Folder Name: ${srcFolder.substringAfterLast('/')}", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Folder Path: $folderPath", fontSize = 13.sp, color = Color(0xFF9EA3B0))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total Videos: ${items.size}", color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Total Size: ${formatBytesReport(totalSize)}", color = Color.White)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}