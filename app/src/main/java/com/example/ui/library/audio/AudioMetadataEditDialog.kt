package com.example.ui.library.audio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MediaNestApp
import com.example.data.db.AudioMetadataCache
import com.example.data.model.MediaItem
import com.example.data.repository.NetworkRepository
import com.example.ui.components.AdaptiveBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMetadataEditDialog(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = MediaNestApp.instance.database
    val networkRepository = remember { NetworkRepository() }
    val settingsManager = MediaNestApp.instance.settingsManager
    val offlineMode by settingsManager.offlineMode.collectAsState(initial = false)

    var editTitle by remember(item.uri) { mutableStateOf(item.title) }
    var editArtist by remember(item.uri) { mutableStateOf(item.artist ?: "") }
    var editAlbum by remember(item.uri) { mutableStateOf(item.album ?: "") }
    var editAlbumArtUri by remember(item.uri) { mutableStateOf(item.albumArtUri?.toString()) }
    var isFetching by remember { mutableStateOf(false) }

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(text = "Edit Tag & Metadata", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = editArtist,
                onValueChange = { editArtist = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = editAlbum,
                onValueChange = { editAlbum = it },
                label = { Text("Album") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isFetching = true
                            val info = networkRepository.fetchAudioMetadata(editTitle.ifBlank { item.title }, offlineMode)
                            if (info != null) {
                                editTitle = info.title
                                editArtist = info.artist
                                editAlbum = info.album
                                editAlbumArtUri = info.coverArtUrl
                            }
                            isFetching = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isFetching
                ) {
                    Text("Fetch Tags")
                }

                Button(
                    onClick = {
                        scope.launch {
                            val cache = AudioMetadataCache(
                                audioUri = item.uri.toString(),
                                title = editTitle.ifBlank { item.title },
                                artist = editArtist,
                                album = editAlbum,
                                albumArtUri = editAlbumArtUri
                            )
                            db.metadataCacheDao().saveCache(cache)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Update")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
