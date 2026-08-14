package com.medianest.ui.library.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun CreateCollectionDialog(
    selectedFolderNames: Set<String>,
    initialCollectionName: String,
    imagesList: List<MediaItem>,
    onCreateCollection: (String, List<String>, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newCollectionName by remember { mutableStateOf(initialCollectionName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Group Folders into Collection") },
        text = {
            Column {
                Text(
                    text = "Group ${selectedFolderNames.size} folder(s): ${selectedFolderNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = newCollectionName.isNotBlank(),
                onClick = {
                    val coverImg = imagesList.firstOrNull { (it.bucketName ?: "Pictures") in selectedFolderNames }?.uri?.toString()
                    onCreateCollection(newCollectionName.trim(), selectedFolderNames.toList(), coverImg)
                    onDismiss()
                }
            ) {
                Text("Create Collection")
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
fun EditCollectionDialog(
    selectedCategory: MediaCategory,
    initialFolders: Set<String>,
    folderGroups: Map<String, List<MediaItem>>,
    imagesList: List<MediaItem>,
    onUpdateCollection: (Long, String, List<String>, String?) -> Unit,
    onUngroupRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    var editCollectionName by remember { mutableStateOf(selectedCategory.name) }
    var editSelectedFolders by remember { mutableStateOf(initialFolders) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Edit Collection Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = editCollectionName,
                    onValueChange = { editCollectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Assign Folders to Collection:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                folderGroups.keys.forEach { folderName ->
                    val isChecked = editSelectedFolders.contains(folderName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editSelectedFolders = if (isChecked) {
                                    editSelectedFolders - folderName
                                } else {
                                    editSelectedFolders + folderName
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                editSelectedFolders = if (checked == true) {
                                    editSelectedFolders + folderName
                                } else {
                                    editSelectedFolders - folderName
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = folderName,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${folderGroups[folderName]?.size ?: 0} items",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onUngroupRequest) {
                    Text("Ungroup Collection", color = MaterialTheme.colorScheme.error)
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = editCollectionName.isNotBlank(),
                        onClick = {
                            val coverImg = imagesList.firstOrNull { (it.bucketName ?: "Pictures") in editSelectedFolders }?.uri?.toString()
                            onUpdateCollection(
                                selectedCategory.id,
                                editCollectionName.trim(),
                                editSelectedFolders.toList(),
                                coverImg
                            )
                            onDismiss()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    )
}

@Composable
fun UngroupConfirmDialog(
    categoryName: String,
    onConfirmUngroup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Ungroup Collection?") },
        text = {
            Text("Are you sure you want to ungroup '$categoryName'? This only removes the collection grouping. Your original folders and images on your device will NOT be deleted.")
        },
        confirmButton = {
            TextButton(onClick = onConfirmUngroup) {
                Text("Ungroup", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}