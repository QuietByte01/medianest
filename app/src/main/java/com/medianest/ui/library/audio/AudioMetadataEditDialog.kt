package com.medianest.ui.library.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.medianest.MediaNestApp
import com.medianest.data.db.AudioMetadataCache
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.NetworkRepository
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.GlassSurface
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
        containerColor = Color.Transparent
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            backgroundColor = Color(0xCC08090E),
            borderColor = Color(0x28FFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Edit Tag & Metadata", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!editAlbumArtUri.isNullOrBlank()) {
                        AsyncImage(
                            model = editAlbumArtUri,
                            contentDescription = "Preview Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    if (isFetching) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            com.medianest.ui.components.MediaLoadingAnimation(
                                mediaType = com.medianest.data.db.MediaType.AUDIO,
                                iconSize = 28.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    label = { Text("Artist") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editAlbum,
                    onValueChange = { editAlbum = it },
                    label = { Text("Album") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                        enabled = !isFetching,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Update")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
