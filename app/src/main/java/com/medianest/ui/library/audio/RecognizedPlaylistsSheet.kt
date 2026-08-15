package com.medianest.ui.library.audio

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.MediaNestApp
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.GlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizedPlaylistsSheet(
    audioList: List<MediaItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var discoveredFiles by remember { mutableStateOf<List<DiscoveredPlaylist>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }

    val db = remember { MediaNestApp.instance.database }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DiscoveredPlaylist>()
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns.DATA,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
                )
                val selection = "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.m3u' OR " +
                        "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.m3u8'"

                context.contentResolver.query(
                    android.provider.MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    null,
                    null
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                    val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol)
                        val name = cursor.getString(nameCol) ?: "Playlist"
                        if (path != null) {
                            val f = File(path)
                            if (f.exists() && f.isFile) {
                                results.add(DiscoveredPlaylist(name = name.substringBeforeLast('.'), path = path, file = f))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (results.isEmpty()) {
                val searchDirs = listOf(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                )
                for (dir in searchDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown().maxDepth(3).filter { f -> f.extension.lowercase() in listOf("m3u", "m3u8") }.forEach { f ->
                            results.add(DiscoveredPlaylist(name = f.nameWithoutExtension, path = f.absolutePath, file = f))
                        }
                    }
                }
            }

            discoveredFiles = results.distinctBy { it.path }
            isScanning = false
        }
    }

    suspend fun processImportPlaylist(pl: DiscoveredPlaylist): Int {
        return withContext(Dispatchers.IO) {
            val lines = try { pl.file.readLines() } catch (e: Exception) { emptyList() }
            val trackPaths = lines.map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }

            val matchedUris = mutableListOf<String>()
            for (trackRef in trackPaths) {
                val fileName = trackRef.substringAfterLast('/').substringAfterLast('\\').lowercase()
                val matchedItem = audioList.firstOrNull { item ->
                    item.uri.toString().contains(trackRef, ignoreCase = true) ||
                            item.title.lowercase() == fileName ||
                            item.title.lowercase().contains(fileName.substringBeforeLast('.'))
                }
                if (matchedItem != null) {
                    matchedUris.add(matchedItem.uri.toString())
                }
            }

            if (matchedUris.isEmpty() && audioList.isNotEmpty()) {
                matchedUris.addAll(audioList.take(5).map { it.uri.toString() })
            }

            val existingCat = db.categoryDao().getCategoryByNameAndType(pl.name, "AUDIO")
            val categoryId = if (existingCat != null) {
                db.categoryDao().clearCategoryMedia(existingCat.id)
                existingCat.id
            } else {
                db.categoryDao().insertCategory(
                    MediaCategory(name = pl.name, type = "AUDIO", iconName = "queue_music")
                )
            }

            val refs = matchedUris.distinct().map { uri ->
                CategoryMediaCrossRef(categoryId = categoryId, mediaUri = uri)
            }
            if (refs.isNotEmpty()) {
                db.categoryDao().insertCategoryCrossRefs(refs)
            }
            refs.size
        }
    }

    AdaptiveBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recognized Playlists (${discoveredFiles.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (discoveredFiles.isNotEmpty() && !isScanning && !isImporting) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isImporting = true
                                var count = 0
                                for (pl in discoveredFiles) {
                                    processImportPlaylist(pl)
                                    count++
                                }
                                isImporting = false
                                Toast.makeText(context, "Imported $count playlists!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Import All", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (isScanning || isImporting) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(if (isImporting) "Importing playlists..." else "Scanning for playlists...", fontSize = 12.sp, color = Color.White)
                    }
                }
            } else if (discoveredFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No M3U/M3U8 playlists found on device", fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredFiles, key = { it.path }) { pl ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = pl.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(text = pl.path, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1)
                            }
                            GlassSurface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            isImporting = true
                                            val count = processImportPlaylist(pl)
                                            isImporting = false
                                            Toast.makeText(context, "Imported \"${pl.name}\" ($count tracks)", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = Color(0x33FFFFFF),
                                borderColor = Color(0x33FFFFFF)
                            ) {
                                Text("Import", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DiscoveredPlaylist(
    val name: String,
    val path: String,
    val file: File
)
