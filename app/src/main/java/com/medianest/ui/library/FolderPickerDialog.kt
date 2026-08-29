package com.medianest.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.medianest.ui.theme.LocalDarkTheme

@Composable
fun FolderPickerDialog(
    title: String,
    actionButtonText: String = "Select",
    availableFolders: List<String>,
    folderItemCounts: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    val isDark = LocalDarkTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val distinctFolders = remember(availableFolders) {
        availableFolders.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
    }

    val filteredFolders = remember(distinctFolders, searchQuery) {
        if (searchQuery.isBlank()) distinctFolders
        else distinctFolders.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val canConfirm = (isCreatingNew && newFolderName.trim().isNotBlank()) || (!isCreatingNew && selectedFolder != null)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xF012131A) else Color(0xF0FFFFFF),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x18000000))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                        Text(
                            text = "Choose destination folder",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search folders...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Create New Folder Toggle / Field
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isCreatingNew = !isCreatingNew },
                    color = if (isCreatingNew) (if (isDark) Color(0x336366F1) else Color(0x226366F1)) else (if (isDark) Color(0x18FFFFFF) else Color(0x0A000000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isCreatingNew) Icons.Default.FolderOpen else Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isCreatingNew) "New Folder Details" else "+ Create New Folder",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6366F1)
                            )
                        }

                        AnimatedVisibility(visible = isCreatingNew) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    placeholder = { Text("Enter folder name...", fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Folder List
                Text(
                    text = "EXISTING FOLDERS (${filteredFolders.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF8E95A5) else Color(0xFF64748B),
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching folders found" else "No folders found",
                                    fontSize = 13.sp,
                                    color = if (isDark) Color(0xFF8E95A5) else Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        items(filteredFolders, key = { it }) { folderName ->
                            val isSelected = !isCreatingNew && selectedFolder == folderName
                            val count = folderItemCounts[folderName]

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) (if (isDark) Color(0x336366F1) else Color(0x226366F1))
                                        else if (isDark) Color(0x10FFFFFF) else Color(0x06000000)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF6366F1) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        isCreatingNew = false
                                        selectedFolder = folderName
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF6366F1) else (if (isDark) Color(0x22FFFFFF) else Color(0x14000000))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else (if (isDark) Color.White else Color(0xFF1E293B)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folderName.substringAfterLast('/'),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isDark) Color.White else Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (folderName.contains('/')) {
                                        Text(
                                            text = folderName,
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFF8E95A5) else Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (count != null) {
                                        Text(
                                            text = "$count item(s)",
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFF8E95A5) else Color(0xFF64748B)
                                        )
                                    }
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        isCreatingNew = false
                                        selectedFolder = folderName
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF6366F1)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = canConfirm,
                        onClick = {
                            val target = if (isCreatingNew) newFolderName.trim() else selectedFolder?.trim().orEmpty()
                            if (target.isNotBlank()) {
                                onFolderSelected(target)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text(actionButtonText, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
