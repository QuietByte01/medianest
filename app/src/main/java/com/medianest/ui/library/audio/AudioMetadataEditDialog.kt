package com.medianest.ui.library.audio

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.SolidGlossySurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMetadataEditDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onUpdated: ((MediaItem) -> Unit)? = null
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
    var editAlbumArtist by remember(item.uri) { mutableStateOf(item.albumArtist ?: "") }
    var editGenre by remember(item.uri) { mutableStateOf(item.genre ?: "") }
    var editYear by remember(item.uri) { mutableStateOf(item.year ?: "") }
    var editComposer by remember(item.uri) { mutableStateOf(item.composer ?: "") }
    var editTrackNumber by remember(item.uri) { mutableStateOf(item.trackNumber ?: "") }
    var editLyricsPlain by remember(item.uri) { mutableStateOf("") }
    var editAlbumArtUri by remember(item.uri) { mutableStateOf(item.albumArtUri?.toString()) }

    var isFetching by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Load any existing cached values on opening
    LaunchedEffect(item.uri) {
        val cached = withContext(Dispatchers.IO) {
            db.metadataCacheDao().getCache(item.uri.toString())
        }
        if (cached != null) {
            if (!cached.title.isNullOrBlank()) editTitle = cached.title
            if (!cached.artist.isNullOrBlank()) editArtist = cached.artist
            if (!cached.album.isNullOrBlank()) editAlbum = cached.album
            if (!cached.albumArtUri.isNullOrBlank()) editAlbumArtUri = cached.albumArtUri
            if (!cached.albumArtist.isNullOrBlank()) editAlbumArtist = cached.albumArtist
            if (!cached.genre.isNullOrBlank()) editGenre = cached.genre
            if (!cached.year.isNullOrBlank()) editYear = cached.year
            if (!cached.composer.isNullOrBlank()) editComposer = cached.composer
            if (!cached.trackNumber.isNullOrBlank()) editTrackNumber = cached.trackNumber
            if (!cached.lyricsPlain.isNullOrBlank()) editLyricsPlain = cached.lyricsPlain
        }
    }

    // Image Picker Launcher to choose album art from Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val artDir = File(context.filesDir, "album_art")
                    if (!artDir.exists()) artDir.mkdirs()
                    val artFile = File(artDir, "art_${item.uri.toString().hashCode()}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        artFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val permUri = Uri.fromFile(artFile).toString()
                    withContext(Dispatchers.Main) {
                        editAlbumArtUri = permUri
                        Toast.makeText(context, "Album art selected from gallery", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image from gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        backgroundImage = editAlbumArtUri ?: item.albumArtUri ?: item.uri,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Tag & Metadata",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Album Artwork Hero Container
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x33FFFFFF))
                    .border(1.5.dp, Color(0x38FFFFFF), RoundedCornerShape(20.dp))
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (!editAlbumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = editAlbumArtUri,
                        contentDescription = "Album Art Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap to choose",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Overlay Loading Spinner when fetching online
                if (isFetching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.medianest.ui.components.MediaLoadingAnimation(
                            mediaType = com.medianest.data.db.MediaType.AUDIO,
                            iconSize = 32.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Album Art Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Button
                SolidGlossySurface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { galleryLauncher.launch("image/*") },
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0x4D38BDF8),
                    borderColor = Color(0x6638BDF8),
                    showTopSheen = true
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Gallery Art",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Online Fetch Button
                SolidGlossySurface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isFetching) {
                            scope.launch {
                                isFetching = true
                                val info = networkRepository.fetchAudioMetadata(
                                    editTitle.ifBlank { item.title },
                                    offlineMode
                                )
                                if (info != null) {
                                    editTitle = info.title
                                    editArtist = info.artist
                                    editAlbum = info.album
                                    if (!info.coverArtUrl.isNullOrBlank()) {
                                        editAlbumArtUri = info.coverArtUrl
                                    }
                                    if (!info.genre.isNullOrBlank()) {
                                        editGenre = info.genre
                                    }
                                    if (!info.year.isNullOrBlank()) {
                                        editYear = info.year
                                    }
                                    if (!info.composer.isNullOrBlank()) {
                                        editComposer = info.composer
                                    }
                                    if (!info.albumArtist.isNullOrBlank()) {
                                        editAlbumArtist = info.albumArtist
                                    }
                                    if (!info.trackNumber.isNullOrBlank()) {
                                        editTrackNumber = info.trackNumber
                                    }
                                    Toast.makeText(context, "Tags fetched successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No online tags found for this query", Toast.LENGTH_SHORT).show()
                                }
                                isFetching = false
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0x221C1F2B),
                    borderColor = Color(0x28FFFFFF),
                    showTopSheen = true
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Fetch Tags",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Remove Art Button (if art is present)
                if (!editAlbumArtUri.isNullOrBlank()) {
                    SolidGlossySurface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                editAlbumArtUri = null
                                Toast.makeText(context, "Album art cleared", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = Color(0x33EF4444),
                        borderColor = Color(0x44EF4444),
                        showTopSheen = true
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Remove",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Metadata Input Fields
            TagInputField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = "Track Title",
                icon = Icons.Default.MusicNote
            )

            Spacer(modifier = Modifier.height(10.dp))

            TagInputField(
                value = editArtist,
                onValueChange = { editArtist = it },
                label = "Artist",
                icon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(10.dp))

            TagInputField(
                value = editAlbum,
                onValueChange = { editAlbum = it },
                label = "Album",
                icon = Icons.Default.Album
            )

            Spacer(modifier = Modifier.height(10.dp))

            TagInputField(
                value = editAlbumArtist,
                onValueChange = { editAlbumArtist = it },
                label = "Album Artist",
                icon = Icons.Default.Group
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TagInputField(
                        value = editGenre,
                        onValueChange = { editGenre = it },
                        label = "Genre",
                        icon = Icons.Default.Category
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TagInputField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = "Year",
                        icon = Icons.Default.CalendarToday
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TagInputField(
                        value = editComposer,
                        onValueChange = { editComposer = it },
                        label = "Composer",
                        icon = Icons.Default.EditNote
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TagInputField(
                        value = editTrackNumber,
                        onValueChange = { editTrackNumber = it },
                        label = "Track No.",
                        icon = Icons.Default.FormatListNumbered
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lyrics / Notes Multiline Field
            OutlinedTextField(
                value = editLyricsPlain,
                onValueChange = { editLyricsPlain = it },
                label = { Text("Plain Lyrics / Track Notes", fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 150.dp),
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0x88FFFFFF),
                    unfocusedBorderColor = Color(0x28FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF64B5F6),
                    unfocusedLabelColor = Color(0xFF9EA3B0),
                    focusedContainerColor = Color(0x18000000),
                    unfocusedContainerColor = Color(0x18000000)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save / Update Action Button
            SolidGlossySurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(enabled = !isSaving) {
                        scope.launch {
                            isSaving = true
                            val finalTitle = editTitle.ifBlank { item.title }
                            val finalArtist = editArtist.takeIf { it.isNotBlank() }
                            val finalAlbum = editAlbum.takeIf { it.isNotBlank() }
                            val finalAlbumArt = editAlbumArtUri?.takeIf { it.isNotBlank() }
                            val finalGenre = editGenre.takeIf { it.isNotBlank() }
                            val finalYear = editYear.takeIf { it.isNotBlank() }
                            val finalComposer = editComposer.takeIf { it.isNotBlank() }
                            val finalAlbumArtist = editAlbumArtist.takeIf { it.isNotBlank() }
                            val finalTrackNumber = editTrackNumber.takeIf { it.isNotBlank() }
                            val finalLyrics = editLyricsPlain.takeIf { it.isNotBlank() }

                            val cacheEntity = AudioMetadataCache(
                                audioUri = item.uri.toString(),
                                title = finalTitle,
                                artist = finalArtist,
                                album = finalAlbum,
                                albumArtUri = finalAlbumArt,
                                genre = finalGenre,
                                year = finalYear,
                                composer = finalComposer,
                                albumArtist = finalAlbumArtist,
                                trackNumber = finalTrackNumber,
                                lyricsPlain = finalLyrics,
                                fetchedAt = System.currentTimeMillis()
                            )

                            withContext(Dispatchers.IO) {
                                db.metadataCacheDao().saveCache(cacheEntity)
                            }

                            val updatedItem = item.copy(
                                title = finalTitle,
                                artist = finalArtist,
                                album = finalAlbum,
                                albumArtUri = finalAlbumArt?.let { Uri.parse(it) },
                                genre = finalGenre,
                                year = finalYear,
                                composer = finalComposer,
                                albumArtist = finalAlbumArtist,
                                trackNumber = finalTrackNumber
                            )

                            // Immediately notify ExoPlayerManager so currently playing audio updates
                            ExoPlayerManager.getInstance(context).updateItemMetadata(updatedItem)

                            // Call external callback if provided
                            onUpdated?.invoke(updatedItem)

                            Toast.makeText(context, "Metadata updated successfully!", Toast.LENGTH_SHORT).show()
                            isSaving = false
                            onDismiss()
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0x4D38BDF8),
                borderColor = Color(0x6638BDF8),
                showTopSheen = true
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
//                        Icon(
//                            imageVector = Icons.Default.Check,
//                            contentDescription = null,
//                            modifier = Modifier.size(18.dp),
//                            tint = Color.White
//                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update Metadata",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0x88FFFFFF),
            unfocusedBorderColor = Color(0x28FFFFFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFF64B5F6),
            unfocusedLabelColor = Color(0xFF9EA3B0),
            focusedContainerColor = Color(0x18000000),
            unfocusedContainerColor = Color(0x18000000)
        )
    )
}

