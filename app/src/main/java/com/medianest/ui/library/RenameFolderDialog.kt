package com.medianest.ui.library

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.BackdropGlassSurface
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.theme.LocalDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
fun RenameFolderDialog(
    folderName: String,
    itemsInFolder: List<MediaItem>,
    defaultMediaType: MediaType = MediaType.VIDEO,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onRenameComplete: (String) -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val currentShortName = folderName.substringAfterLast('/')
    var newFolderName by remember { mutableStateOf(currentShortName) }
    var isRenaming by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
                ?: (dialogView.context as? Activity)?.window
            window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.setDimAmount(0.20f)
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    w.setBackgroundBlurRadius(60)
                }
            }
            onDispose {}
        }

        BackdropGlassSurface(
            shape = RoundedCornerShape(24.dp),
            enableBlur = true,
            blurRadius = 24.dp,
            tint = Color.Black.copy(alpha = 0.30f),
            baseColor = Color.Transparent,
            borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x18000000),
            borderWidth = 1.dp,
            backdropState = null,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Rename Folder",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Text(
                    text = "Enter a new name for '$currentShortName' (${itemsInFolder.size} items):",
                    fontSize = 13.sp,
                    color = Color(0xFFC0C5D0)
                )

                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New Folder Name", color = Color(0xFF9EA3B0)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0x88FFFFFF),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isRenaming) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = Color.White,
                        trackColor = Color(0x33FFFFFF)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isRenaming) {
                        Text("Cancel", color = Color(0xFF9EA3B0))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = newFolderName.isNotBlank() && newFolderName.trim() != currentShortName && !isRenaming,
                        onClick = {
                            val targetName = newFolderName.trim()
                            isRenaming = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val baseFolder = when (defaultMediaType) {
                                        MediaType.AUDIO -> "Music"
                                        MediaType.IMAGE -> "Pictures"
                                        MediaType.VIDEO -> "Movies"
                                    }

                                    // Find source folders on disk
                                    val srcDirs = mutableSetOf<File>()
                                    itemsInFolder.forEach { item ->
                                        if (item.uri.scheme == "file") {
                                            val p = item.uri.path
                                            if (p != null) {
                                                val parent = File(p).parentFile
                                                if (parent != null && parent.exists()) srcDirs.add(parent)
                                            }
                                        }
                                        val rel = item.relativePath?.trim('/')
                                        if (rel != null) {
                                            val root = Environment.getExternalStorageDirectory()
                                            val d = File(root, rel)
                                            if (d.exists()) srcDirs.add(d)
                                        }
                                    }

                                    val newPathsToScan = mutableListOf<String>()

                                    // 1. Rename directories on disk directly if possible
                                    srcDirs.forEach { srcDir ->
                                        try {
                                            if (srcDir.exists() && srcDir.isDirectory) {
                                                val destDir = File(srcDir.parentFile, targetName)
                                                if (srcDir.name.equals(currentShortName, ignoreCase = true) || srcDir.name.equals(folderName, ignoreCase = true)) {
                                                    val renamed = srcDir.renameTo(destDir)
                                                    if (renamed) {
                                                        newPathsToScan.add(destDir.absolutePath)
                                                    } else {
                                                        // Move files manually and remove old dir
                                                        destDir.mkdirs()
                                                        srcDir.listFiles()?.forEach { file ->
                                                            val df = File(destDir, file.name)
                                                            file.copyTo(df, overwrite = true)
                                                            file.delete()
                                                            newPathsToScan.add(df.absolutePath)
                                                        }
                                                        srcDir.deleteRecursively()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    // 2. Also update MediaStore entries if content scheme
                                    itemsInFolder.forEach { item ->
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                                                val values = ContentValues().apply {
                                                    val subPath = item.relativePath?.trim('/') ?: baseFolder
                                                    val newRelPath = if (subPath.contains(currentShortName)) {
                                                        subPath.replace(currentShortName, targetName)
                                                    } else {
                                                        "$baseFolder/$targetName"
                                                    }
                                                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$newRelPath/")
                                                }
                                                context.contentResolver.update(item.uri, values, null, null)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    // 3. Clear hidden media binary caches
                                    MediaStoreRepository.clearHiddenMediaCache(context)

                                    // 4. Trigger media scanner on new paths
                                    if (newPathsToScan.isNotEmpty()) {
                                        MediaScannerConnection.scanFile(
                                            context,
                                            newPathsToScan.toTypedArray(),
                                            null,
                                            null
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Rename", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
