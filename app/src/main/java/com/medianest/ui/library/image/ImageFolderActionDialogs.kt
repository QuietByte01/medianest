package com.medianest.ui.library.image

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    onDismiss: () -> Unit,
    backdropState: com.medianest.ui.components.BackdropBlurState? = com.medianest.ui.components.LocalBackdropState.current
) {
    val items = folderGroups[srcFolder] ?: emptyList()
    val totalSize = items.sumOf { it.size }
    val folderPath = items.firstOrNull()?.let {
        val p = getFilePathFromUri(context, it.uri)
        if (p.contains('/')) p.substringBeforeLast('/') else it.relativePath ?: srcFolder
    } ?: srcFolder
    val isDark = LocalDarkTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        BackHandler(onBack = onDismiss)

        com.medianest.ui.components.BackdropGlassSurface(
            shape = RoundedCornerShape(24.dp),
            enableBlur = true,
            blurRadius = 24.dp,
            tint = Color.Black.copy(alpha = 0.30f),
            baseColor = Color.Transparent,
            borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x18000000),
            borderWidth = 1.dp,
            backdropState = backdropState,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* consume */ })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text("Folder Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Text("Folder Name: ${srcFolder.substringAfterLast('/')}", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Folder Path: $folderPath", fontSize = 13.sp, color = Color(0xFF9EA3B0))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total Items: ${items.size}", color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Total Size: ${Formatter.formatFileSize(context, totalSize)}", color = Color.White)
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}