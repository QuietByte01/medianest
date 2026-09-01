package com.medianest.ui.components

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import com.medianest.data.model.MediaItem
import java.io.File

@Composable
fun RenameFileDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onRenameSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    var newTitle by remember(item) { mutableStateOf(item.title) }
    var isRenaming by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(24.dp),
            backgroundColor = Color(0xEF12151E),
            borderColor = Color(0x38FFFFFF),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename File",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Rename File",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("File Name", color = Color(0xFF9EA3B0)) },
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
                        onClick = {
                            val trimmed = newTitle.trim()
                            if (trimmed.isNotBlank() && trimmed != item.title) {
                                isRenaming = true
                                val success = performRename(context, item, trimmed)
                                isRenaming = false
                                if (success) {
                                    Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                                    onRenameSuccess(trimmed)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to rename file", Toast.LENGTH_SHORT).show()
                                }
                            } else if (trimmed == item.title) {
                                onDismiss()
                            }
                        },
                        enabled = !isRenaming && newTitle.trim().isNotBlank(),
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

private fun performRename(context: Context, item: MediaItem, newTitle: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newTitle)
            put(MediaStore.MediaColumns.TITLE, newTitle)
        }
        val rows = resolver.update(item.uri, values, null, null)
        if (rows > 0) {
            true
        } else {
            val path = item.uri.path
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists()) {
                    val ext = file.extension
                    val parent = file.parentFile
                    if (parent != null) {
                        val newFile = File(parent, if (ext.isNotEmpty()) "$newTitle.$ext" else newTitle)
                        file.renameTo(newFile)
                    } else false
                } else false
            } else false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
