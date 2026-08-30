package com.medianest.ui.library

import android.content.Context
import java.util.Locale
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.theme.LocalDarkTheme
import com.medianest.util.CategoryIconUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CreateCategoryDialog(
    isVideosTab: Boolean,
    onDismiss: () -> Unit,
    onCreateCategory: (String, String, String?) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Category") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(if (isVideosTab) "Create Video Category" else "Create Audio Playlist")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Choose Icon",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    val iconsToDisplay = if (isVideosTab) CategoryIconUtils.AVAILABLE_ICONS else CategoryIconUtils.AUDIO_ICONS
                    items(iconsToDisplay) { (iconKey, vector) ->
                        val isSelected = selectedIconName == iconKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedIconName = iconKey },
                            label = { Text(iconKey) },
                            leadingIcon = {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = iconKey,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newCategoryName.isNotBlank()) {
                    val typeStr = if (isVideosTab) "VIDEO" else "AUDIO"
                    onCreateCategory(newCategoryName, typeStr, selectedIconName)
                    onDismiss()
                }
            }) {
                Text("Create")
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
fun DeleteSelectedDialog(
    selectedUris: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    context: Context
) {
    com.medianest.ui.components.DeleteConfirmationDialog(
        title = "Delete Selected Items",
        message = "Are you sure you want to delete ${selectedUris.size} selected item(s)? This will permanently remove the files from your device storage.",
        onDismiss = onDismiss,
        onConfirm = {
            selectedUris.forEach { uriStr ->
                try {
                    com.medianest.util.FolderHiddenUtils.deleteMediaUri(context, Uri.parse(uriStr))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onConfirm()
            onDismiss()
        }
    )
}

@Composable
fun BatchInfoDialog(
    selectedUris: Set<String>,
    currentTabItems: List<MediaItem>,
    onDismiss: () -> Unit
) {
    val selectedItems = remember(selectedUris, currentTabItems) {
        currentTabItems.filter { selectedUris.contains(it.uri.toString()) }
    }
    val totalSizeBytes = remember(selectedItems) { selectedItems.sumOf { it.size } }
    val formattedSize = remember(totalSizeBytes) {
        when {
            totalSizeBytes >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", totalSizeBytes.toDouble() / (1024 * 1024 * 1024))
            totalSizeBytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", totalSizeBytes.toDouble() / (1024 * 1024))
            else -> String.format(Locale.getDefault(), "%d KB", totalSizeBytes / 1024)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Selection Info", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total Selected: ${selectedItems.size} file(s)", color = Color.White)
                Text("Total Size: $formattedSize", color = Color(0xFF6366F1), fontWeight = FontWeight.SemiBold)
                val typesCount = selectedItems.groupBy { it.type }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")
                if (typesCount.isNotBlank()) {
                    Text("Media Types: $typesCount", color = Color(0xFF9EA3B0), fontSize = 13.sp)
                }
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

@Composable
fun BatchMoveDialog(
    selectedUris: Set<String>,
    currentTabItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    coroutineScope: CoroutineScope
) {
    val availableFolders = remember(currentTabItems) {
        currentTabItems.mapNotNull { it.relativePath?.trim('/')?.takeIf { p -> p.isNotBlank() } ?: it.bucketName }.distinct()
    }
    val folderItemCounts = remember(currentTabItems) {
        currentTabItems.groupingBy { it.relativePath?.trim('/')?.takeIf { p -> p.isNotBlank() } ?: (it.bucketName ?: "Media") }.eachCount()
    }

    FolderPickerDialog(
        title = "Move ${selectedUris.size} File(s)",
        actionButtonText = "Move Here",
        availableFolders = availableFolders,
        folderItemCounts = folderItemCounts,
        onDismiss = onDismiss,
        onFolderSelected = { targetFolder ->
            onDismiss()
            val dest = targetFolder.trim()
            if (dest.isNotBlank()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val selectedItems = currentTabItems.filter { selectedUris.contains(it.uri.toString()) }
                    val root = android.os.Environment.getExternalStorageDirectory()
                    val destDir = File(root, "MediaNest/$dest")
                    destDir.mkdirs()
                    selectedItems.forEach { item ->
                        try {
                            val srcFile = File(item.uri.path ?: "")
                            if (srcFile.exists()) {
                                val destFile = File(destDir, srcFile.name)
                                srcFile.renameTo(destFile)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    launch(Dispatchers.Main) { onComplete() }
                }
            }
        }
    )
}

@Composable
fun BatchCopyDialog(
    selectedUris: Set<String>,
    currentTabItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    coroutineScope: CoroutineScope
) {
    val availableFolders = remember(currentTabItems) {
        currentTabItems.mapNotNull { it.relativePath?.trim('/')?.takeIf { p -> p.isNotBlank() } ?: it.bucketName }.distinct()
    }
    val folderItemCounts = remember(currentTabItems) {
        currentTabItems.groupingBy { it.relativePath?.trim('/')?.takeIf { p -> p.isNotBlank() } ?: (it.bucketName ?: "Media") }.eachCount()
    }

    FolderPickerDialog(
        title = "Copy ${selectedUris.size} File(s)",
        actionButtonText = "Copy Here",
        availableFolders = availableFolders,
        folderItemCounts = folderItemCounts,
        onDismiss = onDismiss,
        onFolderSelected = { targetFolder ->
            onDismiss()
            val dest = targetFolder.trim()
            if (dest.isNotBlank()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val selectedItems = currentTabItems.filter { selectedUris.contains(it.uri.toString()) }
                    val root = android.os.Environment.getExternalStorageDirectory()
                    val destDir = File(root, "MediaNest/$dest")
                    destDir.mkdirs()
                    selectedItems.forEach { item ->
                        try {
                            val srcFile = File(item.uri.path ?: "")
                            if (srcFile.exists()) {
                                val destFile = File(destDir, srcFile.name)
                                srcFile.copyTo(destFile, overwrite = true)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    launch(Dispatchers.Main) { onComplete() }
                }
            }
        }
    )
}

