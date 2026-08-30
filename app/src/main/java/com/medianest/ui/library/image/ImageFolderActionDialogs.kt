package com.medianest.ui.library.image

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
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
import com.medianest.ui.library.FolderPickerDialog
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.FolderHiddenUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MoveImageFolderDialog(
    folderName: String,
    folderGroups: Map<String, List<MediaItem>>,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    com.medianest.ui.library.MoveFolderDialog(
        folderName = folderName,
        folderGroups = folderGroups,
        mediaType = com.medianest.data.db.MediaType.IMAGE,
        scope = scope,
        onDismiss = onDismiss
    )
}

@Composable
fun DeleteImageFolderDialog(
    folderName: String,
    itemsToDelete: List<MediaItem>,
    context: Context,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    com.medianest.ui.components.DeleteConfirmationDialog(
        title = "Delete Folder",
        message = "Are you sure you want to delete '${folderName.substringAfterLast('/')}' and all ${itemsToDelete.size} items inside? This action cannot be undone.",
        onDismiss = onDismiss,
        onConfirm = {
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
    )
}

@Composable
fun ImageFolderInfoDialog(
    srcFolder: String,
    folderGroups: Map<String, List<MediaItem>>,
    context: Context,
    onDismiss: () -> Unit
) {
    val items = folderGroups[srcFolder] ?: emptyList()
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
                Text("Total Items: ${items.size}", color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Total Size: ${Formatter.formatFileSize(context, totalSize)}", color = Color.White)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}