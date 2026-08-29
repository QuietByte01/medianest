package com.medianest.ui.library

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.theme.LocalDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RenameFolderDialog(
    folderName: String,
    itemsInFolder: List<MediaItem>,
    defaultMediaType: MediaType = MediaType.VIDEO,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onRenameComplete: (String) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val currentShortName = folderName.substringAfterLast('/')
    var newFolderName by remember { mutableStateOf(currentShortName) }
    var isRenaming by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Rename Folder",
                color = if (isDark) Color.White else Color(0xFF1E293B),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter a new name for '$currentShortName' (${itemsInFolder.size} items):",
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFFC0C5D0) else Color(0xFF475569)
                )
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New Folder Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isRenaming) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = Color(0xFF6366F1)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = newFolderName.isNotBlank() && newFolderName.trim() != currentShortName && !isRenaming,
                onClick = {
                    val targetName = newFolderName.trim()
                    isRenaming = true
                    scope.launch(Dispatchers.IO) {
                        val baseFolder = when (defaultMediaType) {
                            MediaType.AUDIO -> "Music"
                            MediaType.IMAGE -> "Pictures"
                            MediaType.VIDEO -> "Movies"
                        }

                        var successCount = 0
                        itemsInFolder.forEach { item ->
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                                    val values = ContentValues().apply {
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseFolder/$targetName/")
                                    }
                                    val updated = context.contentResolver.update(item.uri, values, null, null)
                                    if (updated > 0) successCount++
                                } else {
                                    val root = Environment.getExternalStorageDirectory()
                                    val destDir = File(root, "$baseFolder/$targetName")
                                    destDir.mkdirs()
                                    val srcFile = File(item.uri.path ?: "")
                                    if (srcFile.exists()) {
                                        val destFile = File(destDir, srcFile.name)
                                        srcFile.copyTo(destFile, overwrite = true)
                                        srcFile.delete()
                                        successCount++
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        withContext(Dispatchers.Main) {
                            isRenaming = false
                            onDismiss()
                            Toast.makeText(
                                context,
                                "Renamed folder to '$targetName'",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRenameComplete(targetName)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("Rename", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRenaming) {
                Text("Cancel", color = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B))
            }
        }
    )
}
