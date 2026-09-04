package com.medianest.ui.library

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Reusable dialog for moving or copying a single media file to an existing or new folder.
 */
@Composable
fun MoveOrCopyFileDialog(
    item: MediaItem,
    allItems: List<MediaItem>,
    isCopy: Boolean,
    onDismiss: () -> Unit,
    backdropState: com.medianest.ui.components.BackdropBlurState? = com.medianest.ui.components.LocalBackdropState.current
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val typeLabel = remember(item.type) {
        item.type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }

    val baseDir = remember(item.type) {
        when (item.type) {
            MediaType.AUDIO -> "Music"
            MediaType.VIDEO -> "Movies"
            MediaType.IMAGE -> "Pictures"
        }
    }

    val availableFolders = remember(allItems) {
        allItems.mapNotNull { it.relativePath?.trim('/')?.takeIf { p -> p.isNotBlank() } ?: it.bucketName }.distinct()
    }
    val folderItemCounts = remember(allItems, baseDir) {
        allItems.groupingBy { it.relativePath?.trim('/')?.takeIf { p -> p.isNotBlank() } ?: (it.bucketName ?: baseDir) }.eachCount()
    }

    val actionName = if (isCopy) "Copy" else "Move"

    FolderPickerDialog(
        title = "$actionName $typeLabel File",
        actionButtonText = "$actionName Here",
        availableFolders = availableFolders,
        folderItemCounts = folderItemCounts,
        backdropState = backdropState,
        onDismiss = onDismiss,
        onFolderSelected = { targetFolder ->
            val dest = targetFolder.trim()
            onDismiss()
            if (dest.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        if (!isCopy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                            val values = ContentValues().apply {
                                when (item.type) {
                                    MediaType.AUDIO -> put(MediaStore.Audio.Media.RELATIVE_PATH, "$baseDir/$dest/")
                                    MediaType.VIDEO -> put(MediaStore.Video.Media.RELATIVE_PATH, "$baseDir/$dest/")
                                    MediaType.IMAGE -> put(MediaStore.Images.Media.RELATIVE_PATH, "$baseDir/$dest/")
                                }
                            }
                            context.contentResolver.update(item.uri, values, null, null)
                        } else {
                            val root = Environment.getExternalStorageDirectory()
                            val destDir = File(root, "$baseDir/$dest")
                            destDir.mkdirs()
                            val srcFile = File(item.uri.path ?: "")
                            if (srcFile.exists()) {
                                val destFile = File(destDir, srcFile.name)
                                if (isCopy) {
                                    srcFile.copyTo(destFile, overwrite = true)
                                } else {
                                    srcFile.copyTo(destFile, overwrite = true)
                                    srcFile.delete()
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "${if (isCopy) "Copied" else "Moved"} to $dest", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to $actionName file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )
}

/**
 * Reusable dialog for moving all media items from one folder into another folder.
 */
@Composable
fun MoveFolderDialog(
    folderName: String,
    folderGroups: Map<String, List<MediaItem>>,
    mediaType: MediaType,
    scope: CoroutineScope = rememberCoroutineScope(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val baseDir = remember(mediaType) {
        when (mediaType) {
            MediaType.AUDIO -> "Music"
            MediaType.VIDEO -> "Movies"
            MediaType.IMAGE -> "Pictures"
        }
    }

    val availableFolders = remember(folderGroups, folderName) {
        folderGroups.keys.filter { it != folderName }.distinct()
    }
    val folderItemCounts = remember(folderGroups) {
        folderGroups.mapValues { it.value.size }
    }

    FolderPickerDialog(
        title = "Move Folder: ${folderName.substringAfterLast('/')}",
        actionButtonText = "Move Here",
        availableFolders = availableFolders,
        folderItemCounts = folderItemCounts,
        onDismiss = onDismiss,
        onFolderSelected = { targetFolder ->
            val target = targetFolder.trim()
            onDismiss()
            if (target.isNotBlank()) {
                val itemsToMove = folderGroups[folderName] ?: emptyList()
                scope.launch(Dispatchers.IO) {
                    try {
                        val root = Environment.getExternalStorageDirectory()
                        val destDir = File(root, "$baseDir/$target")
                        destDir.mkdirs()

                        itemsToMove.forEach { item ->
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                                    val values = ContentValues().apply {
                                        when (mediaType) {
                                            MediaType.AUDIO -> put(MediaStore.Audio.Media.RELATIVE_PATH, "$baseDir/$target/")
                                            MediaType.VIDEO -> put(MediaStore.Video.Media.RELATIVE_PATH, "$baseDir/$target/")
                                            MediaType.IMAGE -> put(MediaStore.Images.Media.RELATIVE_PATH, "$baseDir/$target/")
                                        }
                                    }
                                    context.contentResolver.update(item.uri, values, null, null)
                                } else {
                                    val file = File(item.uri.path ?: "")
                                    if (file.exists()) {
                                        file.copyTo(File(destDir, file.name), overwrite = true)
                                        file.delete()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Moved ${itemsToMove.size} items to $target", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )
}
