package com.medianest.ui.audioplayer

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medianest.data.db.AppDatabase
import com.medianest.data.db.CategoryMediaCrossRef
import com.medianest.data.db.MediaCategory
import com.medianest.data.model.MediaItem
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.AmbientGlassSurface
import com.medianest.ui.components.GlassSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AddAlbumToPlaylistDialog(
    albumSongs: List<MediaItem>,
    audioPlaylists: List<MediaCategory>,
    db: AppDatabase,
    playerManager: ExoPlayerManager,
    context: Context,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    val albumArtUri = remember(albumSongs) { albumSongs.firstOrNull()?.let { it.albumArtUri ?: it.uri } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AmbientGlassSurface(
            shape = RoundedCornerShape(24.dp),
            backgroundImage = albumArtUri,
            borderWidth = 0.5.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.88f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top header: "< Add to"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add to",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x1AFFFFFF),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        // Row 1: Create playlist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCreatingPlaylist = !isCreatingPlaylist }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Create playlist",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        if (isCreatingPlaylist) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newPlaylistNameInput,
                                    onValueChange = { newPlaylistNameInput = it },
                                    placeholder = { Text("Playlist name", color = Color.White.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    onClick = {
                                        if (newPlaylistNameInput.isNotBlank()) {
                                            scope.launch {
                                                val name = newPlaylistNameInput.trim()
                                                val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                                                var targetCat = categories.find { it.name.equals(name, ignoreCase = true) }
                                                if (targetCat == null) {
                                                    val newId = db.categoryDao().insertCategory(
                                                        MediaCategory(name = name, type = "AUDIO")
                                                    )
                                                    targetCat = MediaCategory(id = newId, name = name, type = "AUDIO")
                                                }
                                                val refs = albumSongs.map {
                                                    CategoryMediaCrossRef(categoryId = targetCat!!.id, mediaUri = it.uri.toString())
                                                }
                                                db.categoryDao().insertCategoryCrossRefs(refs)
                                                Toast.makeText(context, "Added ${albumSongs.size} tracks to \"$name\"", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        )

                        // Row 2: Queue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerManager.addToQueue(albumSongs)
                                    Toast.makeText(context, "Added ${albumSongs.size} tracks to queue", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Queue",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        )

                        // Row 3: Favourite tracks
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val categories = db.categoryDao().getCategoriesByType("AUDIO").first()
                                        var favCat = categories.find { it.name.equals("Favorites", ignoreCase = true) }
                                        if (favCat == null) {
                                            val newId = db.categoryDao().insertCategory(
                                                MediaCategory(name = "Favorites", type = "AUDIO")
                                            )
                                            favCat = MediaCategory(id = newId, name = "Favorites", type = "AUDIO")
                                        }
                                        val refs = albumSongs.map {
                                            CategoryMediaCrossRef(categoryId = favCat!!.id, mediaUri = it.uri.toString())
                                        }
                                        db.categoryDao().insertCategoryCrossRefs(refs)
                                        Toast.makeText(context, "Added ${albumSongs.size} tracks to Favourite tracks", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Favourite tracks",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        val userPlaylists = audioPlaylists.filter { !it.name.equals("Favorites", ignoreCase = true) }
                        if (userPlaylists.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                            ) {
                                items(userPlaylists.size) { idx ->
                                    val playlist = userPlaylists[idx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val refs = albumSongs.map {
                                                        CategoryMediaCrossRef(categoryId = playlist.id, mediaUri = it.uri.toString())
                                                    }
                                                    db.categoryDao().insertCategoryCrossRefs(refs)
                                                    Toast.makeText(context, "Added ${albumSongs.size} tracks to \"${playlist.name}\"", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = playlist.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                    if (idx < userPlaylists.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            color = Color.White.copy(alpha = 0.12f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}