package com.medianest.ui.components.mediainfo

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.PlaybackSpeedChip
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun ImageFilePropertiesContent(
    item: MediaItem,
    extracted: ComprehensiveMetadata,
    formattedSize: String,
    filePath: String,
    onDismiss: () -> Unit,
    onShowFileLocation: ((MediaItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var currentItemTitle by remember(item.id, item.title) { mutableStateOf(item.title) }
    var renameInputText by remember { mutableStateOf(currentItemTitle) }

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

    // Fast asynchronous extraction of deep FFmpeg / EXIF metadata for the image
    val ffmpegImageReport by produceState<com.medianest.util.MediaDiagnosticsReport?>(
        initialValue = com.medianest.util.MediaAnalyzer.getReportIfCached(filePath),
        key1 = filePath
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.medianest.util.MediaAnalyzer.analyze(filePath, item.type.name, context)
        }
    }

    val imgInfo = ffmpegImageReport?.imageInfo

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Image", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    label = { Text("Image Title") },
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
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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

        // Top Card with Image Thumbnail and Action Buttons
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
                            contentDescription = currentItemTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentItemTitle,
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
                    PlaybackSpeedChip(
                        label = "Share",
                        icon = Icons.Default.Share,
                        isSelected = false,
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, currentItemTitle)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Image"))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    PlaybackSpeedChip(
                        label = "Rename",
                        icon = Icons.Default.Edit,
                        isSelected = false,
                        onClick = {
                            renameInputText = currentItemTitle
                            showRenameDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    if (onShowFileLocation != null) {
                        PlaybackSpeedChip(
                            label = "Folder",
                            icon = Icons.Default.Folder,
                            isSelected = false,
                            onClick = {
                                onDismiss()
                                onShowFileLocation(item)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4 Stat Boxes (Matching Media Diagnostics)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(
                    icon = Icons.Default.Folder,
                    label = "FILE SIZE",
                    value = formattedSize.ifBlank { "Unknown" },
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.PlayArrow,
                    label = "RESOLUTION",
                    value = "$width × $height",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(
                    icon = Icons.Default.Info,
                    label = "COLOR DEPTH",
                    value = imgInfo?.colorDepth ?: "8-bit",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Image,
                    label = "FORMAT",
                    value = formatName,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Complete Unified Image Details (Default + FFmpeg Info Merged with no duplicates)
        InfoSectionCard(
            icon = Icons.Default.Info,
            title = "IMAGE DETAILS & TECHNICAL SPECIFICATIONS"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelValueBlock("Dimensions", "$width × $height pixels")
                LabelValueBlock("Megapixels & Aspect Ratio", "$megapixels • $aspectRatio")
                LabelValueBlock("Format / MIME Type", "$formatName ($mimeType)")
                LabelValueBlock("Color Depth", imgInfo?.colorDepth ?: "8-bit per channel")
                LabelValueBlock("Pixel Format & Alpha", "${imgInfo?.pixelFormat ?: "rgb24"} • ${if (imgInfo?.hasAlpha == true) "Alpha Transparent (RGBA)" else "Opaque (RGB)"}")
                LabelValueBlock("Color Profile / Space", imgInfo?.colorProfile ?: "sRGB (Standard Color Gamut)")
                LabelValueBlock("Orientation / Animation", "${imgInfo?.orientation ?: 0}° rotation • ${if (imgInfo?.isAnimated == true) "Animated Sequence" else "Static Image"}")
                LabelValueBlock("File Size", formattedSize)
                LabelValueBlock("File Directory", filePath.ifBlank { item.relativePath ?: "/storage/emulated/0/DCIM/" })
                LabelValueBlock("Date Added", dateFormatted)
                LabelValueBlock("Content URI", item.uri.toString())
            }
        }
    }
}
