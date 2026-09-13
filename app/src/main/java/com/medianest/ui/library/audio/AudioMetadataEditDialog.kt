package com.medianest.ui.library.audio

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.medianest.MediaNestApp
import com.medianest.data.db.AudioMetadataCache
import com.medianest.data.model.AudioTagInfo
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.NetworkRepository
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.AdaptiveBottomSheet
import com.medianest.ui.components.SolidGlossySurface
import com.medianest.util.AudioTagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class FetchOption {
    ALL,
    INFO_ONLY,
    ART_ONLY
}

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
    var showFetchOptionsSheet by remember { mutableStateOf(false) }
    var fetchedPreviewInfo by remember { mutableStateOf<AudioTagInfo?>(null) }
    var pendingFetchOption by remember { mutableStateOf<FetchOption?>(null) }

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

    // Function to execute online metadata fetch
    fun executeFetch(option: FetchOption) {
        scope.launch {
            isFetching = true
            val info = networkRepository.fetchAudioMetadata(
                editTitle.ifBlank { item.title },
                offlineMode
            )
            isFetching = false
            if (info != null) {
                pendingFetchOption = option
                fetchedPreviewInfo = info
            } else {
                Toast.makeText(context, "No online tags found for this query", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to apply fetched metadata
    fun applyFetchedMetadata(info: AudioTagInfo, applyInfo: Boolean, applyArt: Boolean) {
        if (applyInfo) {
            editTitle = info.title
            editArtist = info.artist
            editAlbum = info.album
            if (!info.genre.isNullOrBlank()) editGenre = info.genre
            if (!info.year.isNullOrBlank()) editYear = info.year
            if (!info.composer.isNullOrBlank()) editComposer = info.composer
            if (!info.albumArtist.isNullOrBlank()) editAlbumArtist = info.albumArtist
            if (!info.trackNumber.isNullOrBlank()) editTrackNumber = info.trackNumber
        }
        if (applyArt && !info.coverArtUrl.isNullOrBlank()) {
            editAlbumArtUri = info.coverArtUrl
        }
        val message = when {
            applyInfo && applyArt -> "Applied online tags & album art!"
            applyInfo -> "Applied online tags (artwork untouched)!"
            applyArt -> "Applied online album art (tags untouched)!"
            else -> "No changes applied"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                Column {
                    Text(
                        text = "Edit Tag & Metadata",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Lossless Tag & Artwork Editor",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

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
                            .background(Color.Black.copy(alpha = 0.6f)),
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

            // Album Art & Fetch Action Buttons Row
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

            Spacer(modifier = Modifier.height(20.dp))

            // Online Fetch Tags Button (Positioned at bottom above Save)
            OutlinedButton(
                onClick = { showFetchOptionsSheet = true },
                enabled = !isFetching && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x66818CF8)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF818CF8))
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF818CF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetching Online Metadata...", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetch Tags & Cover Art Online", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save / Update Action Button (Physically writes tags & updates library)
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

                            // 1. Lossless physical tag writing to file on disk using FFmpeg
                            val tagData = AudioTagWriter.TagData(
                                title = finalTitle,
                                artist = finalArtist,
                                album = finalAlbum,
                                albumArtist = finalAlbumArtist,
                                genre = finalGenre,
                                year = finalYear,
                                composer = finalComposer,
                                trackNumber = finalTrackNumber,
                                lyrics = finalLyrics,
                                albumArtUri = finalAlbumArt
                            )

                            val physicalWriteSuccess = withContext(Dispatchers.IO) {
                                AudioTagWriter.writeAudioTags(context, item.uri, tagData)
                            }

                            // 2. Save to Room database cache
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

                            if (physicalWriteSuccess) {
                                Toast.makeText(context, "Tags written to song file & library updated!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Tags saved to library!", Toast.LENGTH_SHORT).show()
                            }

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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Writing tags to audio file...",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save & Apply Tags",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Fetch Options Selector Dialog
    if (showFetchOptionsSheet) {
        Dialog(onDismissRequest = { showFetchOptionsSheet = false }) {
            com.medianest.ui.components.AmbientGlassSurface(
                modifier = Modifier
                    .fillMaxWidth(0.92f),
                shape = RoundedCornerShape(24.dp),
                backgroundImage = editAlbumArtUri ?: item.albumArtUri ?: item.uri,
                borderWidth = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fetch Online Metadata",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Search iTunes & MusicBrainz for tags and artwork",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Option 1: Both Info & Art
                    FetchChoiceItem(
                        icon = Icons.Default.Public,
                        title = "Fetch Info & Artwork (All)",
                        subtitle = "Retrieve track details, album info, and high-res cover art",
                        accentColor = Color(0xFF38BDF8),
                        onClick = {
                            showFetchOptionsSheet = false
                            executeFetch(FetchOption.ALL)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Info Only
                    FetchChoiceItem(
                        icon = Icons.Default.Description,
                        title = "Fetch Info Only",
                        subtitle = "Update title, artist, album, genre, year (keep existing art)",
                        accentColor = Color(0xFF34D399),
                        onClick = {
                            showFetchOptionsSheet = false
                            executeFetch(FetchOption.INFO_ONLY)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Art Only
                    FetchChoiceItem(
                        icon = Icons.Default.Image,
                        title = "Fetch Album Art Only",
                        subtitle = "Find high-resolution cover art (keep existing text tags)",
                        accentColor = Color(0xFFF472B6),
                        onClick = {
                            showFetchOptionsSheet = false
                            executeFetch(FetchOption.ART_ONLY)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showFetchOptionsSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Fetched Preview & Granular Selection Modal
    if (fetchedPreviewInfo != null) {
        val info = fetchedPreviewInfo!!
        var applyInfoChecked by remember { mutableStateOf(pendingFetchOption != FetchOption.ART_ONLY) }
        var applyArtChecked by remember { mutableStateOf(pendingFetchOption != FetchOption.INFO_ONLY && !info.coverArtUrl.isNullOrBlank()) }

        Dialog(onDismissRequest = { fetchedPreviewInfo = null }) {
            com.medianest.ui.components.AmbientGlassSurface(
                modifier = Modifier
                    .fillMaxWidth(0.92f),
                shape = RoundedCornerShape(24.dp),
                backgroundImage = editAlbumArtUri ?: item.albumArtUri ?: item.uri,
                borderWidth = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Online Tags Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preview Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x22FFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!info.coverArtUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = info.coverArtUrl,
                                contentDescription = "Cover Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = info.artist,
                                fontSize = 13.sp,
                                color = Color(0xFF38BDF8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = info.album,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!info.year.isNullOrBlank() || !info.genre.isNullOrBlank()) {
                                Text(
                                    text = listOfNotNull(info.genre, info.year).joinToString(" • "),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selection Checkbox 1: Apply Text Information
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { applyInfoChecked = !applyInfoChecked }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = applyInfoChecked,
                            onCheckedChange = { applyInfoChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF38BDF8),
                                checkmarkColor = Color.Black
                            )
                        )
                        Column {
                            Text(
                                text = "Apply Text Metadata",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Title, Artist, Album, Genre, Year, Composer, Track #",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Selection Checkbox 2: Apply Album Artwork
                    if (!info.coverArtUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { applyArtChecked = !applyArtChecked }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = applyArtChecked,
                                onCheckedChange = { applyArtChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF38BDF8),
                                    checkmarkColor = Color.Black
                                )
                            )
                            Column {
                                Text(
                                    text = "Apply Album Artwork",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "High-resolution front album cover",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { fetchedPreviewInfo = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Text("Cancel", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                applyFetchedMetadata(info, applyInfoChecked, applyArtChecked)
                                fetchedPreviewInfo = null
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF38BDF8),
                                contentColor = Color.Black
                            ),
                            enabled = applyInfoChecked || applyArtChecked
                        ) {
                            Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FetchChoiceItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0x18FFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
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
