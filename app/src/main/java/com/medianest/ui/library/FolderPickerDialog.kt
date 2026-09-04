package com.medianest.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.medianest.ui.components.BackdropGlassSurface
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.theme.LocalDarkTheme

@Composable
fun FolderPickerDialog(
    title: String,
    actionButtonText: String = "Select",
    availableFolders: List<String>,
    folderItemCounts: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
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

        BackdropGlassSurface(
            shape = RoundedCornerShape(24.dp),
            enableBlur = true,
            blurRadius = 24.dp,
            tint = Color.Black.copy(alpha = 0.30f),
            baseColor = Color.Transparent,
            borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x18000000),
            borderWidth = 1.dp,
            backdropState = backdropState,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* consume */ })
                }
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

                // Option Tabs: Choose Existing vs Create New
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x1AFFFFFF) else Color(0x0D000000))
                        .padding(4.dp)
                ) {
                    Surface(
                        onClick = { isCreatingNew = false },
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isCreatingNew) Color(0xFF6366F1) else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Existing Folder",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        onClick = { isCreatingNew = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCreatingNew) Color(0xFF6366F1) else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "New Folder",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isCreatingNew) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("Folder Name", color = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B)) },
                            placeholder = { Text("e.g. My Favorites", color = if (isDark) Color(0x55FFFFFF) else Color(0x55000000)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isDark) Color.White else Color(0xFF1E293B),
                                unfocusedTextColor = if (isDark) Color.White else Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x33000000)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search folders...", color = if (isDark) Color(0x55FFFFFF) else Color(0x55000000)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) Color.White else Color(0xFF1E293B),
                            unfocusedTextColor = if (isDark) Color.White else Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x33000000)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )

                    // Folder list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredFolders) { folder ->
                            val isSelected = selectedFolder == folder
                            val count = folderItemCounts[folder] ?: 0

                            Surface(
                                onClick = { selectedFolder = folder },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0x446366F1) else if (isDark) Color(0x11FFFFFF) else Color(0x0A000000),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF818CF8) else Color(0xFF6366F1),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = folder.substringAfterLast('/'),
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isDark) Color.White else Color(0xFF1E293B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (folder.contains('/')) {
                                                Text(
                                                    text = folder,
                                                    fontSize = 10.5.sp,
                                                    color = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (count > 0) {
                                        Text(
                                            text = "$count items",
                                            fontSize = 11.5.sp,
                                            color = if (isDark) Color(0xFF9EA3B0) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Buttons
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
                        onClick = {
                            val target = if (isCreatingNew) newFolderName.trim() else (selectedFolder ?: "")
                            if (target.isNotBlank()) {
                                onFolderSelected(target)
                            }
                        },
                        enabled = canConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0x336366F1),
                            disabledContentColor = Color(0x66FFFFFF)
                        )
                    ) {
                        Text(actionButtonText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
