package com.example.ui.library.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.SelectiveHiddenFolder
import com.example.data.model.MediaItem
import com.example.data.settings.SettingsManager
import com.example.ui.components.GlassSurface
import com.example.ui.components.translucentScrollBarGrid
import com.example.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun VideoFoldersGrid(
    videoFolderGroups: Map<String, List<MediaItem>>,
    activeFilterTab: String,
    settingsManager: SettingsManager,
    db: com.example.data.db.AppDatabase,
    roundedCornersEnabled: Boolean,
    cornerRadiusDp: Int,
    onFolderClick: (String) -> Unit,
    onFolderDelete: (String) -> Unit,
    onFolderInfo: (String) -> Unit,
    onCreateCategoryClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val appHiddenFolders by settingsManager.hiddenFolders.collectAsState(initial = emptySet())
    val selectiveHiddenFolders by db.selectiveHiddenFolderDao().getAllHiddenFolders().collectAsState(initial = emptyList())

    val selectiveVideoHidden = remember(selectiveHiddenFolders) {
        selectiveHiddenFolders.filter { it.mediaType == "VIDEO" && it.isHidden }
            .flatMap { listOf(it.folderPath, it.folderName) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    val allHiddenVideoFolders = remember(appHiddenFolders, selectiveVideoHidden) {
        appHiddenFolders + selectiveVideoHidden
    }

    fun checkFolderHidden(folderKey: String, items: List<MediaItem>?): Boolean {
        val folderName = folderKey.substringAfterLast('/')
        if (folderKey.startsWith(".") || folderName.startsWith(".")) return true
        if (folderKey in allHiddenVideoFolders || folderName in allHiddenVideoFolders) return true
        if (items.isNullOrEmpty()) return false
        return items.any { item ->
            item.title.startsWith(".") ||
                    (item.relativePath != null && (
                            allHiddenVideoFolders.any { hidden ->
                                hidden.equals(item.relativePath.trimEnd('/'), ignoreCase = true) ||
                                        item.relativePath.contains("/$hidden") ||
                                        item.relativePath.startsWith("$hidden/") ||
                                        hidden.equals(folderKey, ignoreCase = true)
                            } ||
                                    item.relativePath.contains("/.") ||
                                    item.relativePath.startsWith(".") ||
                                    item.relativePath.split("/").any { it.startsWith(".") }
                            ))
        }
    }

    val sortedFolderNames = remember(videoFolderGroups) {
        videoFolderGroups.keys.sortedWith(
            compareByDescending<String> { fn -> checkFolderHidden(fn, videoFolderGroups[fn]) }
                .thenBy { it.lowercase() }
        )
    }

    val visibleFolders = remember(sortedFolderNames, videoFolderGroups, activeFilterTab) {
        if (activeFilterTab == "HIDDEN") {
            sortedFolderNames.filter { fn -> checkFolderHidden(fn, videoFolderGroups[fn]) }
        } else {
            sortedFolderNames
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = Color(0xFFC0C5D0),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "ALL DIRECTORY FOLDERS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFFC0C5D0)
            )
        }

        val videoFolderGridState = rememberLazyGridState()
        LazyVerticalGrid(
            state = videoFolderGridState,
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier
                .fillMaxSize()
                .translucentScrollBarGrid(videoFolderGridState),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(visibleFolders, key = { "vfolder_$it" }) { folderName ->
                val folderItems = videoFolderGroups[folderName] ?: emptyList()
                val isHidden = checkFolderHidden(folderName, folderItems)
                val totalSizeBytes = remember(folderItems) { folderItems.sumOf { it.size } }
                val formattedSize = remember(totalSizeBytes) { formatFolderSize(totalSizeBytes) }
                val itemCountText = if (folderItems.size == 1) "1 Video" else "${folderItems.size} Videos"
                val cardShape = if (roundedCornersEnabled) RoundedCornerShape(cornerRadiusDp.dp.coerceAtLeast(16.dp)) else RoundedCornerShape(0.dp)

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .clickable { onFolderClick(folderName) },
                    shape = cardShape,
                    backgroundColor = if (isHidden) Color(0x22121520) else Color(0x28181C2B),
                    borderColor = Color(0x28FFFFFF)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassSurface(
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    backgroundColor = Color(0x35FFFFFF),
                                    borderColor = Color(0x4DFFFFFF)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                var showFolderMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showFolderMenu = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Folder options",
                                            tint = Color(0xFF9EA3B0),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showFolderMenu,
                                        onDismissRequest = { showFolderMenu = false },
                                        containerColor = if (LocalDarkTheme.current) Color(0xCC08090E) else Color(0xBFFFFFFF),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showFolderMenu = false
                                                onFolderDelete(folderName)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isHidden) "Unhide Folder" else "Hide Folder") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showFolderMenu = false
                                                scope.launch {
                                                    val current = settingsManager.hiddenFolders.first()
                                                    val folderPathKey = folderItems.firstOrNull()?.relativePath?.trim('/') ?: folderName
                                                    if (isHidden) {
                                                        settingsManager.setHiddenFolders(current - folderName - folderPathKey)
                                                        db.selectiveHiddenFolderDao().unhideFolder(folderName, "VIDEO")
                                                        db.selectiveHiddenFolderDao().unhideFolder(folderPathKey, "VIDEO")
                                                    } else {
                                                        settingsManager.setHiddenFolders(current + folderPathKey)
                                                        db.selectiveHiddenFolderDao().insertOrUpdate(
                                                            SelectiveHiddenFolder(
                                                                folderPath = folderPathKey,
                                                                folderName = folderName,
                                                                mediaType = "VIDEO",
                                                                isHidden = true
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Folder Info") },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            onClick = {
                                                showFolderMenu = false
                                                onFolderInfo(folderName)
                                            }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isHidden) "${folderName.substringAfterLast('/')} (Hidden)" else folderName.substringAfterLast('/'),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = itemCountText,
                                        fontSize = 12.sp,
                                        color = Color(0xFF9EA3B0)
                                    )
                                    Text(
                                        text = formattedSize,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF9EA3B0)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0..2) {
                                    if (i < folderItems.size) {
                                        val item = folderItems[i]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF181B26))
                                        ) {
                                            AsyncImage(
                                                model = item.uri,
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x0CFFFFFF))
                                                .drawWithContent {
                                                    drawContent()
                                                    val stroke = Stroke(
                                                        width = 1.dp.toPx(),
                                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                                    )
                                                    drawRoundRect(
                                                        color = Color(0x33FFFFFF),
                                                        style = stroke,
                                                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                                    )
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

//            item {
//                GlassSurface(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(168.dp)
//                        .clip(RoundedCornerShape(20.dp))
//                        .clickable { onCreateCategoryClick() },
//                    shape = RoundedCornerShape(20.dp),
//                    backgroundColor = Color(0x12FFFFFF),
//                    borderColor = Color.Transparent
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .drawWithContent {
//                                drawContent()
//                                val stroke = Stroke(
//                                    width = 1.5.dp.toPx(),
//                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
//                                )
//                                drawRoundRect(
//                                    color = Color(0x38FFFFFF),
//                                    style = stroke,
//                                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
//                                )
//                            },
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Column(
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.spacedBy(10.dp)
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .size(44.dp)
//                                    .clip(CircleShape)
//                                    .background(Color(0x2B3B82F6)),
//                                contentAlignment = Alignment.Center
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Default.Add,
//                                    contentDescription = "Create Folder",
//                                    tint = Color.White,
//                                    modifier = Modifier.size(24.dp)
//                                )
//                            }
//                            Text(
//                                text = "Create Folder",
//                                fontWeight = FontWeight.Bold,
//                                fontSize = 14.sp,
//                                color = Color.White
//                            )
//                        }
//                    }
//                }
//            }
        }
    }
}
