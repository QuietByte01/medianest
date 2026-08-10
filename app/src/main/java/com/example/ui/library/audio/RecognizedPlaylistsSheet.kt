package com.example.ui.library.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.components.AdaptiveBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizedPlaylistsSheet(
    audioList: List<MediaItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var discoveredFiles by remember { mutableStateOf<List<DiscoveredPlaylist>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }

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
                            results.add(DiscoveredPlaylist(name = name.substringBeforeLast('.'), path = path, file = File(path)))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            discoveredFiles = results
            isScanning = false
        }
    }

    AdaptiveBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Recognized Playlists (${discoveredFiles.size})",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (isScanning) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
                            Text(text = pl.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                            Button(onClick = { onDismiss() }) {
                                Text("Import", fontSize = 11.sp)
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
