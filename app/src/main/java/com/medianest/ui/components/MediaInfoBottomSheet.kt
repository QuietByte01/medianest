package com.medianest.ui.components

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.mediainfo.*
import com.medianest.util.ArtistImageUtils
import com.medianest.util.rememberArtistImageUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoBottomSheet(
    item: MediaItem?,
    onDismiss: () -> Unit,
    onShowFileLocation: ((MediaItem) -> Unit)? = null,
    onFetchInfo: ((MediaItem) -> Unit)? = null
) {
    if (item == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePath = remember(item.uri) { getFilePathFromUri(context, item.uri) }
    val fileObj = remember(filePath) { if (filePath.isNotBlank()) java.io.File(filePath) else null }

    val computedSize = remember(item.size, fileObj) {
        if (item.size > 0) item.size
        else fileObj?.takeIf { it.exists() }?.length() ?: queryFileSizeFromUri(context, item.uri)
    }

    val extracted by produceState(initialValue = ComprehensiveMetadata(), key1 = item.uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            extractComprehensiveMetadata(context, item)
        }
    }

    val formattedSize = remember(computedSize) { formatFileSize(computedSize) }

    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xCC08090E) else Color(0xBFFFFFFF)

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        skipPartiallyExpanded = true,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Unspecified,
        contentColor = if (isDark) Color.White else Color.Black,
        backgroundImage = item.albumArtUri ?: item.uri,
        enableBlur = true,
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
                    .padding(bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            imageVector = Icons.Default.ArrowBack,
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

            var showAudioDiagnosticsDialog by remember { mutableStateOf(false) }
            var audioDiagReport by remember { mutableStateOf<com.medianest.util.MediaDiagnosticsReport?>(null) }
            var isAudioAnalyzing by remember { mutableStateOf(false) }

            if (isTabletLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.38f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SolidGlossySurface(
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
                                backgroundColor = Color(0x3B272C3D),
                                showTopSheen = true
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

                            SolidGlossySurface(
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
                                backgroundColor = Color(0x2EFFFFFF),
                                showTopSheen = true
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

                        SolidGlossySurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        isAudioAnalyzing = true
                                        val report = com.medianest.util.MediaAnalyzer.analyze(filePath, item.type.name, context)
                                        audioDiagReport = report
                                        isAudioAnalyzing = false
                                        showAudioDiagnosticsDialog = true
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0x2EFFFFFF),
                            borderColor = Color(0x33FFFFFF),
                            showTopSheen = true
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isAudioAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing Audio Streams...", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Audio Stream Diagnostics", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (showAudioDiagnosticsDialog && audioDiagReport != null) {
                            MediaDiagnosticsDialog(
                                report = audioDiagReport!!,
                                item = item,
                                filePath = filePath,
                                currentItemTitle = item.title,
                                onDismissRequest = { showAudioDiagnosticsDialog = false },
                                onPlay = { showAudioDiagnosticsDialog = false; onDismiss() },
                                onRename = { },
                                context = context
                            )
                        }

                        SolidGlossySurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Sharing track details for '${item.title}'", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0x3BFFFFFF),
                            showTopSheen = true
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
                                            LabelValueBlock("TRACK NO.", "N/A")
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
                                            LabelValueBlock("CHANNELS", "N/A")
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
                        if (!com.medianest.util.MetadataUtils.hasDomainOrFalseInfo(bannerTitle)) {
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0x1F24293A),
                                borderColor = Color(0x26FFFFFF)
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
            val cleanArtistRaw = com.medianest.util.MetadataUtils.sanitizeArtist(item.artist)
            val multipleArtists = com.medianest.util.MetadataUtils.parseMultipleArtists(cleanArtistRaw)
            val artistName = if (multipleArtists.isNotEmpty()) multipleArtists.joinToString(", ") else cleanArtistRaw
            val rawComposer = extracted.composer
            val songwriterName = if (com.medianest.util.MetadataUtils.hasDomainOrFalseInfo(rawComposer)) artistName else rawComposer.trim()
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
                            val currentArtistImgUrl = com.medianest.util.ArtistImageUtils.getArtistImageUrl(currentArtist)
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

            // Media Stream Diagnostics Button (Phone Portrait)
            SolidGlossySurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isAudioAnalyzing = true
                            val report = com.medianest.util.MediaAnalyzer.analyze(filePath, item.type.name, context)
                            audioDiagReport = report
                            isAudioAnalyzing = false
                            showAudioDiagnosticsDialog = true
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0x2EFFFFFF),
                borderColor = Color(0x33FFFFFF),
                showTopSheen = true
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isAudioAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing Audio Streams...", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio Stream Diagnostics", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bottom Action Pill Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SolidGlossySurface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
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
                    backgroundColor = Color(0x3B272C3D),
                    showTopSheen = true
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show File Location", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }

                SolidGlossySurface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable {
                            if (onFetchInfo != null) {
                                onFetchInfo(item)
                            } else {
                                Toast.makeText(context, "Fetching metadata & album info for '${item.title}'...", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x2EFFFFFF),
                    showTopSheen = true
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Info", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
            }

            if (showAudioDiagnosticsDialog && audioDiagReport != null) {
                MediaDiagnosticsDialog(
                    report = audioDiagReport!!,
                    item = item,
                    filePath = filePath,
                    currentItemTitle = item.title,
                    onDismissRequest = { showAudioDiagnosticsDialog = false },
                    onPlay = { showAudioDiagnosticsDialog = false; onDismiss() },
                    onRename = { },
                    context = context
                )
            }
        }
    }
}
}
}
