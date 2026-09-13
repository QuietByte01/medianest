package com.medianest.ui.library

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import java.util.Locale
import android.net.Uri
import com.medianest.ui.components.SolidGlossySurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.components.backdropReceiver
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
    onCreateCategory: (String, String, String?) -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    var newCategoryName by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf(if (isVideosTab) "Category" else "queue_music") }
    val isDark = LocalDarkTheme.current
    val shape = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                w.setDimAmount(0.40f)
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply {
                        blurBehindRadius = 80
                    }
                }
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val cardModifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = false
                ) {}
                .clip(shape)
                .then(
                    if (backdropState != null) {
                        Modifier.backdropReceiver(
                            state = backdropState,
                            blurRadius = 24.dp,
                            tint = if (isDark) Color(0x6608090E) else Color(0x80FFFFFF),
                            baseColor = Color.Transparent,
                            showTopBorder = false
                        )
                    } else Modifier
                )

            GlassSurface(
                shape = shape,
                enableBlur = true,
                backdropState = backdropState,
                backgroundColor = if (backdropState != null) Color.Transparent else if (isDark) Color(0xCC08090E) else Color(0xBFFFFFFF),
                borderColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000),
                modifier = cardModifier
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Icon Container
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x22FFFFFF), CircleShape)
                            .border(1.dp, Color(0x33FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVideosTab) Icons.Default.FolderSpecial else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Dialog Title
                    Text(
                        text = if (isVideosTab) "Create Video Category" else "New Playlist",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    // Text Field
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text(if (isVideosTab) "Category Name" else "Playlist Name", fontSize = 13.sp) },
                        placeholder = {
                            Text(
                                text = if (isVideosTab) "e.g. Movies, Series, Clips" else "e.g. Chill Vibes, Workout Mix",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.30f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.60f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedContainerColor = Color(0x18000000),
                            unfocusedContainerColor = Color(0x18000000)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Choose Icon Section (Shown ONLY for Video Categories, NOT for Playlists)
                    if (isVideosTab) {
                        Text(
                            text = "Choose Icon",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(CategoryIconUtils.AVAILABLE_ICONS) { (iconKey, vector) ->
                                val isSelected = selectedIconName == iconKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedIconName = iconKey },
                                    label = { Text(iconKey) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = vector,
                                            contentDescription = iconKey,
                                            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0x33FFFFFF),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0x1AFFFFFF),
                                        labelColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.White.copy(alpha = 0.25f),
                                        selectedBorderColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Clean Proportioned Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Create Button
                        val isCreateEnabled = newCategoryName.isNotBlank()
                        val btnBg = if (isCreateEnabled) Color(0xFF6366F1) else Color(0x336366F1)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(btnBg)
                                .border(1.dp, if (isCreateEnabled) Color(0x88818CF8) else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                .clickable(enabled = isCreateEnabled) {
                                    if (isCreateEnabled) {
                                        val typeStr = if (isVideosTab) "VIDEO" else "AUDIO"
                                        onCreateCategory(newCategoryName, typeStr, selectedIconName)
                                        onDismiss()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCreateEnabled) Color.White else Color.White.copy(alpha = 0.40f)
                            )
                        }
                    }
                }
            }
        }
    }
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val maxDialogWidth = if (isTablet) 520.dp else 340.dp

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

    val blurState = rememberBackdropBlurState()

    // Full screen dim backdrop with blur and black tint
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .backdropReceiver(blurState, blurRadius = 24.dp)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = maxDialogWidth)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(enabled = false) {}, // Intercept click inside card
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color.Black.copy(alpha = 0.72f), // Black tint with blur
            borderColor = Color(0x3334D399),
            enableBlur = true,
            blurRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with Title and Red Close X Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "SELECTION INFO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Selected", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("${selectedItems.size} file(s)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Size", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(formattedSize, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    val typesCount = selectedItems.groupBy { it.type }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")
                    if (typesCount.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Media Types", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(typesCount, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
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

