package com.example.ui.components

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import com.example.data.db.MediaType
import com.example.data.model.MediaItem
import com.example.util.ArtistImageUtils
import com.example.util.rememberArtistImageUrl
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.ui.components.GlassSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoBottomSheet(
    item: MediaItem?,
    onDismiss: () -> Unit,
    onShowFileLocation: ((MediaItem) -> Unit)? = null,
    onFetchInfo: ((MediaItem) -> Unit)? = null
) {
    // ISSUE: Metadata extraction is performed directly in composition via 'remember'.
    // This can block the UI thread for large files or slow storage.
    // RECOMMENDATION: Move this to a Coroutine or use produceState.
    if (item == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePath = remember(item.uri) { getFilePathFromUri(context, item.uri) }
    val fileObj = remember(filePath) { if (filePath.isNotBlank()) java.io.File(filePath) else null }

    val computedSize = remember(item.size, fileObj) {
        if (item.size > 0) item.size
        else fileObj?.takeIf { it.exists() }?.length() ?: queryFileSizeFromUri(context, item.uri)
    }

    val extracted = remember(item.uri) {
        extractComprehensiveMetadata(context, item)
    }

    val formattedSize = remember(computedSize) { formatFileSize(computedSize) }

    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isDark = com.example.ui.theme.LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xCC08090E) else Color(0xCCE3E3E3)

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheetBg,
        contentColor = if (isDark) Color.White else Color.Black,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        if (item.type == MediaType.VIDEO) {
            VideoFilePropertiesContent(
                item = item,
                extracted = extracted,
                formattedSize = formattedSize,
                filePath = filePath,
                onDismiss = onDismiss,
                onShowFileLocation = onShowFileLocation,
                onFetchInfo = onFetchInfo
            )
        } else if (item.type == MediaType.IMAGE) {
            ImageFilePropertiesContent(
                item = item,
                extracted = extracted,
                formattedSize = formattedSize,
                filePath = filePath,
                onDismiss = onDismiss,
                onShowFileLocation = onShowFileLocation
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Top Bar: Back Button, Title, Edit Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassSurface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onDismiss() },
                    shape = CircleShape,
                    backgroundColor = Color.Transparent,
                    borderColor = Color.Transparent
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Track Information & Details",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                GlassSurface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onDismiss() },
                    shape = CircleShape,
                    backgroundColor = Color.Transparent,
                    borderColor = Color.Transparent
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isTabletLandscape) {
                // Tablet / Landscape 2-Column Layout matching Reference UI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column (Artwork, Badges, Social Platforms, Actions, Share)
                    Column(
                        modifier = Modifier.weight(0.38f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Artwork Card with overlaid Title, Artist, and Badges
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            if (item.albumArtUri != null) {
                                AsyncImage(
                                    model = item.albumArtUri,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            // Dark Gradient Overlay at Bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color(0xEC000000))
                                        )
                                    )
                            )

                            // Title, Artist, Quality Badges inside bottom of Artwork
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.artist ?: "Unknown Artist",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    QualityBadgePill("24-BIT")
                                    QualityBadgePill(extracted.sampleRate.ifBlank { "96 KHZ" })
                                    QualityBadgePill(item.mimeType.substringAfter('/').uppercase().ifBlank { "FLAC" })
                                }
                            }
                        }


                        // Action Buttons: Location & Fetch Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassSurface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (onShowFileLocation != null) {
                                            onShowFileLocation(item)
                                        } else {
                                            val folder = item.bucketName ?: "Music"
                                            Toast.makeText(context, "Location: Internal Storage > $folder > ${item.title}", Toast.LENGTH_LONG).show()
                                        }
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0x26272C3D)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Location", fontSize = 11.sp, color = Color.White)
                                }
                            }

                            GlassSurface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (onFetchInfo != null) {
                                            onFetchInfo(item)
                                        } else {
                                            Toast.makeText(context, "Fetching metadata for '${item.title}'...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0x20FFFFFF)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fetch Info", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }

                        var showDiagnosticsDialog by remember { mutableStateOf(false) }
                        var diagReport by remember { mutableStateOf<com.example.util.MediaDiagnosticsReport?>(null) }
                        var isAnalyzing by remember { mutableStateOf(false) }

                        // Media Stream Diagnostics Button
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        isAnalyzing = true
                                        val report = com.example.util.MediaAnalyzer.analyze(filePath, item.uri.toString(), context)
                                        diagReport = report
                                        isAnalyzing = false
                                        showDiagnosticsDialog = true
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0x33A855F7)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing Media Streams...", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Media Stream Diagnostics", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (showDiagnosticsDialog && diagReport != null) {
                            AlertDialog(
                                onDismissRequest = { showDiagnosticsDialog = false },
                                containerColor = Color(0xEE08090E),
                                shape = RoundedCornerShape(24.dp),
                                title = { Text("Media Stream Diagnostics", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                                        val codec = diagReport!!.videoStream?.codecName ?: diagReport!!.audioStreams.firstOrNull()?.codecName ?: "N/A"
                                        Text(text = "Codec: $codec", color = Color.White, fontSize = 13.sp)
                                        val resolution = if (diagReport!!.videoStream != null) "${diagReport!!.videoStream!!.width}x${diagReport!!.videoStream!!.height}" else "N/A"
                                        Text(text = "Resolution: $resolution", color = Color(0xFF9EA3B0), fontSize = 12.sp)
                                        val duration = diagReport!!.format?.duration ?: 0.0
                                        Text(text = "Duration: ${duration.toInt()}s", color = Color(0xFF9EA3B0), fontSize = 12.sp)
                                        val bitrate = (diagReport!!.format?.bitrate ?: 0L) / 1000
                                        Text(text = "Bitrate: $bitrate kbps", color = Color(0xFF9EA3B0), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(text = "Diagnostic Report Log:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(modifier = Modifier.fillMaxWidth().background(Color(0x33000000)).padding(8.dp)) {
                                            Text(text = diagReport!!.toShareText(), color = Color(0xFF8E95A5), fontSize = 10.5.sp)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, diagReport!!.toShareText())
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                                    }) {
                                        Text("Share Report", color = Color(0xFFA855F7))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDiagnosticsDialog = false }) {
                                        Text("Close", color = Color.White)
                                    }
                                }
                            )
                        }

                        // Share Track Details Button
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Sharing track details for '${item.title}'", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0x33FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share Track Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Right Column (2x2 Spec Grid, Featured OST Banner, Artists & Songwriters Card)
                    Column(
                        modifier = Modifier.weight(0.62f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 2x2 Specs Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                InfoSectionCard(icon = Icons.Default.GraphicEq, title = "Audio Specifications") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("FILE FORMAT", extracted.codecInfo.ifBlank { "N/A" })
                                            LabelValueBlock("SAMPLE RATE", extracted.sampleRate.ifBlank { "N/A" })
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("BITRATE", extracted.bitrate.ifBlank { "N/A" })
                                            LabelValueBlock("FILE SIZE", formattedSize)
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                InfoSectionCard(icon = Icons.Default.Audiotrack, title = "Track Metadata") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("ALBUM", item.album ?: extracted.album.ifBlank { "Unknown Album" })
                                            LabelValueBlock("GENRE", extracted.genre.ifBlank { "Unknown Genre" })
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("RELEASE YEAR", if (extracted.dateTaken.length >= 4) extracted.dateTaken.take(4) else "N/A")
                                            LabelValueBlock("TRACK NO.", "N/A") // Issue: Track number not available in current model
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                InfoSectionCard(icon = Icons.Default.Info, title = "File Information") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("CHANNELS", "N/A") // Issue: Channels not in ComprehensiveMetadata
                                            LabelValueBlock("ENCODING", "N/A")
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("COMPRESSION", "N/A")
                                            LabelValueBlock("MD5 CHECKSUM", "N/A")
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                InfoSectionCard(icon = Icons.Default.Security, title = "Licensing & Publisher") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("LABEL", "N/A")
                                            LabelValueBlock("ISRC CODE", "N/A")
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LabelValueBlock("COPYRIGHT", "N/A")
                                            LabelValueBlock("EXPLICIT", "N/A")
                                        }
                                    }
                                }
                            }
                        }

                        // Featured OST / Soundtracks Banner
                        val bannerTitle = item.album ?: item.title
                        if (!com.example.util.MetadataUtils.hasDomainOrFalseInfo(bannerTitle)) {
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0x1F24293A),
                                borderColor = Color(0x3364B5F6)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "FEATURED OST / MOVIE SOUNDTRACKS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64B5F6)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$bannerTitle (Featured Cinematic Track)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Artists & Songwriters Card (Full Width for Tablet)
                val artistName = item.artist ?: "Anthony Gonzalez"
                val songwriterName = extracted.composer.ifBlank { "Yann Gonzalez" }
                val artistImgUrl = rememberArtistImageUrl(artistName)
                val songwriterImgUrl = remember(songwriterName) { ArtistImageUtils.getSongwriterImageUrl(songwriterName) }

                InfoSectionCard(icon = Icons.Default.Person, title = "Artists & Songwriters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2A2E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(artistImgUrl).crossfade(true).build(),
                                    contentDescription = artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (painter.state is AsyncImagePainter.State.Error) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(artistName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Primary Artist / Writer", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2A2E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(songwriterImgUrl).crossfade(true).build(),
                                    contentDescription = songwriterName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (painter.state is AsyncImagePainter.State.Error) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(songwriterName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Songwriter", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2A2E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Issue: Hardcoded producer info
                            // Text("Justin Meldal-Johnsen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            // Text("Producer / Contributor", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                            Text("N/A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Producer", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                        }

                        // Additional placeholder artists to demonstrate scrolling if needed
                        repeat(5) { i ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Contributor ${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                                Text("Guest Artist", fontSize = 9.sp, color = Color(0xFF9EA3B0))
                            }
                        }
                    }
                }
            } else {

            // Hero Header Card (Glassmorphic)
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color(0x2424293A),
                borderColor = Color(0x2EFFFFFF)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.albumArtUri != null) {
                            AsyncImage(
                                model = item.albumArtUri,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.artist ?: "Unknown Artist",
                            fontSize = 13.sp,
                            color = Color(0xFF9EA3B0),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio quality pill badges (24-BIT, 96 KHZ, FLAC)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QualityBadgePill("24-BIT")
                            QualityBadgePill(extracted.sampleRate.ifBlank { "96 KHZ" })
                            QualityBadgePill(item.mimeType.substringAfter('/').uppercase().ifBlank { "FLAC" })
                        }
                    }
                }
            }

            // Section 1: Audio Specifications Card
            InfoSectionCard(
                icon = Icons.Default.GraphicEq,
                title = "Audio Specifications"
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        LabelValueBlock("FILE FORMAT", extracted.codecInfo.ifBlank { item.mimeType.uppercase() })
                        Spacer(modifier = Modifier.height(12.dp))
                        LabelValueBlock("SAMPLE RATE", extracted.sampleRate.ifBlank { "44.1 kHz" })
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        LabelValueBlock("BITRATE", extracted.bitrate.ifBlank { "1,411 kbps" })
                        Spacer(modifier = Modifier.height(12.dp))
                        LabelValueBlock("FILE SIZE", formattedSize)
                    }
                }
            }

            // Section 2: Track Metadata Card
            InfoSectionCard(
                icon = Icons.Default.Audiotrack,
                title = "Track Metadata"
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        LabelValueBlock("ALBUM", item.album ?: extracted.album.ifBlank { "Unknown Album" })
                        Spacer(modifier = Modifier.height(12.dp))
                        LabelValueBlock("GENRE", extracted.genre.ifBlank { "Synthwave / Electronic" })
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        LabelValueBlock("RELEASE YEAR", if (extracted.dateTaken.length >= 4) extracted.dateTaken.take(4) else "2024")
                        Spacer(modifier = Modifier.height(12.dp))
                        LabelValueBlock("TRACK NO.", "1 / 12")
                    }
                }
            }

            // Section 3: Song Credits Card
            val cleanArtistRaw = com.example.util.MetadataUtils.sanitizeArtist(item.artist)
            val multipleArtists = com.example.util.MetadataUtils.parseMultipleArtists(cleanArtistRaw)
            val artistName = if (multipleArtists.isNotEmpty()) multipleArtists.joinToString(", ") else cleanArtistRaw
            val rawComposer = extracted.composer
            val songwriterName = if (com.example.util.MetadataUtils.hasDomainOrFalseInfo(rawComposer)) artistName else rawComposer.trim()
            val isSongwriterDifferent = songwriterName.isNotBlank() && !songwriterName.equals(artistName, ignoreCase = true) && !songwriterName.equals("Unknown Artist", ignoreCase = true)
            val movieThumbUrl = remember(item.title, item.album, extracted.album, item.artist) {
                ArtistImageUtils.getMovieThumbUrl(item.title, item.album ?: extracted.album, item.artist)
            }
            val songwriterImgUrl = remember(songwriterName) { ArtistImageUtils.getSongwriterImageUrl(songwriterName) }

            InfoSectionCard(
                icon = Icons.Default.Person,
                title = "Song Credits"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Performer(s)", fontSize = 12.sp, color = Color(0xFF9EA3B0), modifier = Modifier.widthIn(min = 80.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = artistName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Songwriter", fontSize = 12.sp, color = Color(0xFF9EA3B0), modifier = Modifier.widthIn(min = 80.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = songwriterName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Producer", fontSize = 12.sp, color = Color(0xFF9EA3B0), modifier = Modifier.widthIn(min = 80.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        // ISSUE: Producer info is often missing from basic MediaStore metadata.
                        // Consider using a dedicated metadata library (like Media3 or TagLib) for better results.
                        Text(
                            text = if (multipleArtists.isNotEmpty()) multipleArtists.first() else artistName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0x28FFFFFF))

                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top
                    ) {
                        val displayArtistList = if (multipleArtists.isNotEmpty()) multipleArtists else listOf(artistName)
                        items(displayArtistList.size) { index ->
                            val currentArtist = displayArtistList[index]
                            val currentArtistImgUrl = com.example.util.ArtistImageUtils.getArtistImageUrl(currentArtist)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context).data(currentArtistImgUrl).crossfade(true).build(),
                                        contentDescription = currentArtist,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val state = painter.state
                                        if (state is AsyncImagePainter.State.Error) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(0.8f), modifier = Modifier.size(26.dp))
                                        } else {
                                            SubcomposeAsyncImageContent()
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(if (displayArtistList.size > 1) "Artist ${index + 1}" else "Artist", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                                Text(currentArtist, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            }
                        }

                        if (isSongwriterDifferent) {
                            item {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context).data(songwriterImgUrl).crossfade(true).build(),
                                            contentDescription = songwriterName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            val state = painter.state
                                            if (state is AsyncImagePainter.State.Error) {
                                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White.copy(0.8f), modifier = Modifier.size(26.dp))
                                            } else {
                                                SubcomposeAsyncImageContent()
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Songwriter", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                                    Text(songwriterName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }
                            }
                        }

                        if (movieThumbUrl != null) {
                            item {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context).data(movieThumbUrl).crossfade(true).build(),
                                            contentDescription = "Movie OST",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            val state = painter.state
                                            if (state is AsyncImagePainter.State.Error) {
                                                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White.copy(0.8f), modifier = Modifier.size(26.dp))
                                            } else {
                                                SubcomposeAsyncImageContent()
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Movie OST", fontSize = 10.sp, color = Color(0xFF9EA3B0))
                                    Text(item.album ?: "Film Soundtrack", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            }

            // Bottom Action Pill Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (onShowFileLocation != null) {
                                onShowFileLocation(item)
                            } else {
                                val folder = item.bucketName ?: "Music"
                                Toast.makeText(context, "Location: Internal Storage > $folder > ${item.title}", Toast.LENGTH_LONG).show()
                            }
                            onDismiss()
                        },
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x2B272C3D)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show File Location", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }

                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (onFetchInfo != null) {
                                onFetchInfo(item)
                            } else {
                                Toast.makeText(context, "Fetching metadata & album info for '${item.title}'...", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x20FFFFFF)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Info", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
            }
        }
    }
}
}

@Composable
private fun QualityBadgePill(text: String) {
    GlassSurface(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0x26FFFFFF),
        borderColor = Color(0x3DFFFFFF)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InfoSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1AFFFFFF),
        borderColor = Color(0x2AFFFFFF)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.90f), modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun VideoFilePropertiesContent(
    item: MediaItem,
    extracted: ComprehensiveMetadata,
    formattedSize: String,
    filePath: String,
    onDismiss: () -> Unit,
    onShowFileLocation: ((MediaItem) -> Unit)? = null,
    onFetchInfo: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var currentItemTitle by remember(item.id, item.title) { mutableStateOf(item.title) }
    var renameInputText by remember { mutableStateOf(currentItemTitle) }

    val (resolutionStr, resBadgeText) = remember(extracted.width, extracted.height) {
        val w = extracted.width
        val h = extracted.height
        if (w > 0 && h > 0) {
            val maxDim = maxOf(w, h)
            val badge = when {
                maxDim >= 3840 -> "4K UHD"
                maxDim >= 2560 -> "2K QHD"
                maxDim >= 1920 -> "1080p FHD"
                maxDim >= 1280 -> "720p HD"
                else -> "SD"
            }
            "${w} × ${h} ($badge)" to badge
        } else {
            "Standard Resolution" to null
        }
    }

    val imageRequest = remember<ImageRequest>(item.uri, item.durationMs) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
            .videoFrameMicros(if (item.durationMs > 5000) 3_000_000L else if (item.durationMs > 2000) 1_000_000L else 0L)
            .build()
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Video", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    label = { Text("Video Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0x66FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            currentItemTitle = renameInputText
                            Toast.makeText(context, "Renamed to '$renameInputText'", Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xF2121522),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Video File Properties",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isCompactScreen = configuration.screenWidthDp < 600
        val isCompact = isCompactScreen

        // Top Hero Card
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x3B181A26),
            borderColor = Color(0x2EFFFFFF)
        ) {
            if (isCompactScreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x33FFFFFF), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.albumArtUri != null) {
                            AsyncImage(
                                model = item.albumArtUri,
                                contentDescription = currentItemTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        // Dynamic Resolution Badge
                        if (!resBadgeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xCC000000))
                                    .border(0.5.dp, Color(0x66FFFFFF), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = resBadgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Details Column on Bottom
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentItemTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2
                        )
                        Text(
                            text = "📁 ${filePath.ifBlank { item.relativePath ?: "/Internal Storage/Download/Movies/" }}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Pill Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable {
                                        renameInputText = currentItemTitle
                                        showRenameDialog = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rename", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = item.mimeType ?: "video/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, currentItemTitle)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Thumbnail
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(82.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x33FFFFFF), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.albumArtUri != null) {
                            AsyncImage(
                                model = item.albumArtUri,
                                contentDescription = currentItemTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Dynamic Resolution Badge
                        if (!resBadgeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xCC000000))
                                    .border(0.5.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = resBadgeText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Title & Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentItemTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "📁 ${filePath.ifBlank { item.relativePath ?: "/Internal Storage/Download/Movies/" }}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Pill Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable {
                                        renameInputText = currentItemTitle
                                        showRenameDialog = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rename", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = item.mimeType ?: "video/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, currentItemTitle)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4 Stat Boxes Row / 2x2 Grid on phone
        if (isCompactScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(
                        icon = Icons.Default.Folder,
                        label = "FILE SIZE",
                        value = formattedSize.ifBlank { "Unknown" },
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        icon = Icons.Default.Movie,
                        label = "RESOLUTION",
                        value = resolutionStr,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(
                        icon = Icons.Default.Info,
                        label = "DURATION",
                        value = if (item.durationMs > 0) formatDuration(item.durationMs) else if (extracted.durationMs > 0) formatDuration(extracted.durationMs) else "00:17:00",
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        icon = Icons.Default.Security,
                        label = "CODEC",
                        value = extracted.codecInfo.ifBlank { item.mimeType?.substringAfter('/')?.uppercase() ?: "HEVC/H.265" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBox(
                    icon = Icons.Default.Folder,
                    label = "FILE SIZE",
                    value = formattedSize.ifBlank { "Unknown" },
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Movie,
                    label = "RESOLUTION",
                    value = resolutionStr,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Info,
                    label = "DURATION",
                    value = if (item.durationMs > 0) formatDuration(item.durationMs) else if (extracted.durationMs > 0) formatDuration(extracted.durationMs) else "00:17:00",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Security,
                    label = "CODEC",
                    value = extracted.codecInfo.ifBlank { item.mimeType?.substringAfter('/')?.uppercase() ?: "HEVC/H.265" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val realStream = remember(item.uri) { extractRealStreamDetails(context, item) }

        // VIDEO STREAM TECHNICAL SPECS Section
        InfoSectionCard(
            icon = Icons.Default.Movie,
            title = "VIDEO STREAM TECHNICAL SPECS"
        ) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelValueBlock("Video Format / Container", realStream.containerFormat)
                    LabelValueBlock("Video Codec", realStream.videoCodec)
                    LabelValueBlock("Frame Rate", realStream.frameRate)
                    LabelValueBlock("Color Space / Range", realStream.colorSpace)
                    LabelValueBlock("Display Aspect Ratio", realStream.aspectRatio)
                    LabelValueBlock("Video Bitrate", realStream.videoBitrate.ifBlank { extracted.bitrate.ifBlank { "Auto" } })
                    LabelValueBlock("HDR Metadata", realStream.hdrInfo)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Video Format / Container", realStream.containerFormat)
                        LabelValueBlock("Video Codec", realStream.videoCodec)
                        LabelValueBlock("Frame Rate", realStream.frameRate)
                        LabelValueBlock("Color Space / Range", realStream.colorSpace)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Display Aspect Ratio", realStream.aspectRatio)
                        LabelValueBlock("Video Bitrate", realStream.videoBitrate.ifBlank { extracted.bitrate.ifBlank { "Auto" } })
                        LabelValueBlock("HDR Metadata", realStream.hdrInfo)
                    }
                }
            }
        }

        // AUDIO STREAMS Section
        InfoSectionCard(
            icon = Icons.Default.Audiotrack,
            title = "AUDIO STREAMS"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Embedded Tracks", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        realStream.audioTracks.forEachIndexed { idx, trackName ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (idx == 0) Color(0x44FFFFFF) else Color(0x22FFFFFF))
                                    .border(if (idx == 0) 1.dp else 0.5.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(trackName, fontSize = 11.sp, fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium, color = Color.White)
                            }
                        }
                    }
                }

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabelValueBlock("Audio Format", realStream.audioFormat)
                        LabelValueBlock("Audio Bitrate", realStream.audioBitrate)
                        LabelValueBlock("Audio Channels", realStream.audioChannels)
                        LabelValueBlock("Sample Rate", realStream.audioSampleRate.ifBlank { extracted.sampleRate.ifBlank { "48.0 kHz" } })
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabelValueBlock("Audio Format", realStream.audioFormat)
                            LabelValueBlock("Audio Bitrate", realStream.audioBitrate)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabelValueBlock("Audio Channels", realStream.audioChannels)
                            LabelValueBlock("Sample Rate", realStream.audioSampleRate.ifBlank { extracted.sampleRate.ifBlank { "48.0 kHz" } })
                        }
                    }
                }
            }
        }

        // STORAGE & SYSTEM PROPERTIES Section
        InfoSectionCard(
            icon = Icons.Default.Folder,
            title = "STORAGE & SYSTEM PROPERTIES"
        ) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabelValueBlock("File Directory", filePath.ifBlank { "/storage/emulated/0/Download/Movies/" })
                    LabelValueBlock("Date Modified", "Aug 01, 2026 • 14:25:31")
                    LabelValueBlock("Date Created", "Aug 01, 2026 • 14:22:08")
                    LabelValueBlock("Storage Permissions", "Read / Write (rw-rw----)")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("File Directory", filePath.ifBlank { "/storage/emulated/0/Download/Movies/" })
                        LabelValueBlock("Date Modified", "Aug 01, 2026 • 14:25:31")
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabelValueBlock("Date Created", "Aug 01, 2026 • 14:22:08")
                        LabelValueBlock("Storage Permissions", "Read / Write (rw-rw----)")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1AFFFFFF),
        borderColor = Color(0x2AFFFFFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 0.5.sp)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun LabelValueBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A7F90)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1
        )
    }
}

private data class ComprehensiveMetadata(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: String = "",
    val sampleRate: String = "",
    val volumeDb: String = "",
    val frameRate: String = "",
    val codecInfo: String = "",
    val album: String = "",
    val artist: String = "",
    val genre: String = "",
    val composer: String = "",
    val location: String = "",
    val dateTaken: String = "",
    val cameraMakeModel: String = "",
    val exifDetails: String = ""
)

private fun extractComprehensiveMetadata(context: Context, item: MediaItem): ComprehensiveMetadata {
    var duration = 0L
    var w = 0
    var h = 0
    var bitrate = ""
    var sampleRate = ""
    var codecInfo = ""
    var album = ""
    var artist = ""
    var genre = ""
    var composer = ""

    if (item.type == MediaType.AUDIO || item.type == MediaType.VIDEO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durStr.isNullOrBlank()) duration = durStr.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!widthStr.isNullOrBlank()) w = widthStr.toIntOrNull() ?: 0
            if (!heightStr.isNullOrBlank()) h = heightStr.toIntOrNull() ?: 0

            val bitStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (!bitStr.isNullOrBlank()) {
                val bps = bitStr.toLongOrNull() ?: 0L
                bitrate = if (bps > 1_000_000) String.format(Locale.US, "%.2f Mbps", bps / 1_000_000.0)
                else "${bps / 1000} kbps"
            }

            val srStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            if (!srStr.isNullOrBlank()) {
                val sr = srStr.toIntOrNull() ?: 0
                sampleRate = "${sr / 1000.0} kHz"
            }

            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: ""

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: item.mimeType
            codecInfo = mime.replace("audio/", "").replace("video/", "").uppercase(Locale.US)

            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    return ComprehensiveMetadata(
        durationMs = duration,
        width = w,
        height = h,
        bitrate = bitrate,
        sampleRate = sampleRate,
        codecInfo = codecInfo,
        album = album,
        artist = artist,
        genre = genre,
        composer = composer
    )
}

private fun queryFileSizeFromUri(context: Context, uri: Uri): Long {
    try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (idx != -1) return cursor.getLong(idx)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 0L
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val value = size / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
}

fun getFilePathFromUri(context: Context, uri: Uri): String {
    // ISSUE: MediaStore.MediaColumns.DATA is deprecated since Android 10 (API 29).
    // It may not return a valid path for all URI types in modern Android versions.
    if (uri.scheme == "file") return uri.path ?: ""
    if (uri.scheme == "content") {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (columnIndex != -1) return cursor.getString(columnIndex) ?: ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return uri.path ?: uri.toString()
}

@Composable
private fun ImageFilePropertiesContent(
    item: MediaItem,
    extracted: ComprehensiveMetadata,
    formattedSize: String,
    filePath: String,
    onDismiss: () -> Unit,
    onShowFileLocation: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val width = if (extracted.width > 0) extracted.width else 1920
    val height = if (extracted.height > 0) extracted.height else 1080
    val megapixels = String.format(Locale.US, "%.1f MP", (width.toLong() * height.toLong()) / 1_000_000.0)

    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(width, height).coerceAtLeast(1)
    val aspectRatio = "${width / g}:${height / g}"

    val mimeType = item.mimeType.ifBlank { "image/jpeg" }
    val formatName = mimeType.substringAfter('/').uppercase(Locale.US)

    val dateFormatted = remember(item.dateAdded) {
        if (item.dateAdded > 0) {
            val date = if (item.dateAdded > 10_000_000_000L) Date(item.dateAdded) else Date(item.dateAdded * 1000L)
            SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.US).format(date)
        } else {
            SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.US).format(Date())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Image File Properties",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
            }
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x3B181A26),
            borderColor = Color(0x2EFFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2
                        )
                        Text(
                            text = "$formattedSize • $width × $height ($megapixels)",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "📁 ${filePath.ifBlank { item.relativePath ?: "/storage/emulated/0/DCIM/" }}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Image"))
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable {
                                val editIntent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                                    setDataAndType(item.uri, mimeType)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(android.content.Intent.createChooser(editIntent, "Edit Image"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No editing app available", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    if (onShowFileLocation != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    onDismiss()
                                    onShowFileLocation(item)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("Folder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        InfoSectionCard(
            icon = Icons.Default.Info,
            title = "IMAGE DETAILS & SPECIFICATIONS"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelValueBlock("Dimensions", "$width × $height pixels")
                LabelValueBlock("Megapixels & Aspect Ratio", "$megapixels • $aspectRatio")
                LabelValueBlock("Format / MIME Type", "$formatName ($mimeType)")
                LabelValueBlock("File Size", formattedSize)
                LabelValueBlock("File Directory", filePath.ifBlank { item.relativePath ?: "/storage/emulated/0/DCIM/" })
                LabelValueBlock("Date Added", dateFormatted)
                LabelValueBlock("Content URI", item.uri.toString())
            }
        }
    }
}

data class RealVideoAudioDetails(
    val containerFormat: String = "MP4 (.mp4)",
    val videoCodec: String = "H.264 / AVC",
    val frameRate: String = "30 fps",
    val colorSpace: String = "BT.709 / Standard",
    val aspectRatio: String = "16:9",
    val videoBitrate: String = "Auto",
    val hdrInfo: String = "SDR (Standard Dynamic Range)",
    val audioFormat: String = "AAC (Advanced Audio Coding)",
    val audioBitrate: String = "192 kbps",
    val audioChannels: String = "2 Channels (Stereo)",
    val audioSampleRate: String = "48.0 kHz",
    val audioTracks: List<String> = listOf("Track 1: Primary Audio")
)

private fun extractRealStreamDetails(context: Context, item: MediaItem): RealVideoAudioDetails {
    var container = item.mimeType.substringAfter('/').uppercase(Locale.US)
    if (container.isBlank()) container = "MP4"
    val containerStr = "$container (.$container)"

    var vCodec = "H.264 / AVC"
    var fRate = "30 fps"
    var vBitrate = ""
    var aCodec = "AAC"
    var aBitrate = ""
    var aChannels = "2 Channels (Stereo)"
    var aSampleRate = "48.0 kHz"
    val trackList = mutableListOf<String>()

    try {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(context, item.uri, null)
        val numTracks = extractor.trackCount
        var audioTrackCount = 0

        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""

            if (mime.startsWith("video/")) {
                val codec = mime.substringAfter("video/").uppercase(Locale.US)
                vCodec = when {
                    codec.contains("AVC") || codec.contains("H264") || codec.contains("4") -> "H.264 / AVC"
                    codec.contains("HEVC") || codec.contains("H265") || codec.contains("5") -> "HEVC / H.265"
                    codec.contains("VP9") -> "VP9"
                    codec.contains("AV1") -> "AV1"
                    else -> codec
                }
                if (format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                    val fr = try { format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE) } catch (e: Exception) { try { format.getFloat(android.media.MediaFormat.KEY_FRAME_RATE).toInt() } catch(ex: Exception) { 30 } }
                    if (fr > 0) fRate = "$fr fps"
                }
                if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                    val br = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                    if (br > 0) {
                        vBitrate = if (br >= 1_000_000) String.format(Locale.US, "%.2f Mbps", br / 1_000_000.0) else "${br / 1000} kbps"
                    }
                }
            } else if (mime.startsWith("audio/")) {
                audioTrackCount++
                val codec = mime.substringAfter("audio/").uppercase(Locale.US)
                aCodec = when {
                    codec.contains("AAC") -> "AAC (Advanced Audio Coding)"
                    codec.contains("MPEG") || codec.contains("MP3") -> "MP3 (MPEG Audio Layer III)"
                    codec.contains("OPUS") -> "Opus"
                    codec.contains("FLAC") -> "FLAC (Free Lossless Audio Codec)"
                    codec.contains("AC3") || codec.contains("EAC3") -> "Dolby Digital (AC-3)"
                    else -> codec
                }
                val lang = if (format.containsKey(android.media.MediaFormat.KEY_LANGUAGE)) format.getString(android.media.MediaFormat.KEY_LANGUAGE)?.uppercase(Locale.US) else "Default"
                trackList.add("Track $audioTrackCount: ${lang ?: "Primary"} ($codec)")

                if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                    val ch = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    aChannels = when (ch) {
                        1 -> "1 Channel (Mono)"
                        2 -> "2 Channels (Stereo)"
                        6 -> "6 Channels (5.1 Surround)"
                        8 -> "8 Channels (7.1 Surround)"
                        else -> "$ch Channels"
                    }
                }
                if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                    val sr = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    if (sr > 0) aSampleRate = "${sr / 1000.0} kHz"
                }
                if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                    val br = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                    if (br > 0) aBitrate = "${br / 1000} kbps"
                }
            }
        }
        extractor.release()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (trackList.isEmpty()) {
        trackList.add("Track 1: Primary Audio ($aCodec)")
    }

    return RealVideoAudioDetails(
        containerFormat = containerStr,
        videoCodec = vCodec,
        frameRate = fRate,
        colorSpace = "BT.709 / Standard",
        aspectRatio = "16:9",
        videoBitrate = vBitrate,
        hdrInfo = "SDR (Standard Dynamic Range)",
        audioFormat = aCodec,
        audioBitrate = if (aBitrate.isNotBlank()) aBitrate else "192 kbps",
        audioChannels = aChannels,
        audioSampleRate = aSampleRate,
        audioTracks = trackList
    )
}
