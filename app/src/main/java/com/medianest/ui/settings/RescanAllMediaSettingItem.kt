package com.medianest.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.db.MediaType
import com.medianest.data.repository.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable Setting Component for triggering a full storage rescan.
 * Displays a responsive horizontal progress bar and currently scanning directory path:
 * - On Tablets (>= 600dp): Displayed on the right side of the row.
 * - On Phones (< 600dp): Displayed directly below the title and action button.
 */
@Composable
fun RescanAllMediaSettingItem(
    mediaStoreRepository: MediaStoreRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRescanning by remember { mutableStateOf(false) }
    var currentScanPath by remember { mutableStateOf("") }
    var scannedItemsCount by remember { mutableIntStateOf(0) }
    var scanProgress by remember { mutableFloatStateOf(0f) }

    val isTabletScreen = LocalConfiguration.current.screenWidthDp >= 600

    fun startRescan() {
        if (isRescanning) return
        isRescanning = true
        currentScanPath = "Starting media scan..."
        scannedItemsCount = 0
        scanProgress = 0.05f

        scope.launch(Dispatchers.IO) {
            try {
                MediaStoreRepository.clearHiddenMediaCache(context)
                val images = mediaStoreRepository.scanHiddenMedia(MediaType.IMAGE, emptySet(), forceRescan = true) { path, count ->
                    currentScanPath = path
                    scannedItemsCount = count
                    scanProgress = 0.15f + 0.25f * (count.coerceAtMost(100) / 100f)
                }
                val videos = mediaStoreRepository.scanHiddenMedia(MediaType.VIDEO, emptySet(), forceRescan = true) { path, count ->
                    currentScanPath = path
                    scannedItemsCount = images.size + count
                    scanProgress = 0.45f + 0.25f * (count.coerceAtMost(100) / 100f)
                }
                val audio = mediaStoreRepository.scanHiddenMedia(MediaType.AUDIO, emptySet(), forceRescan = true) { path, count ->
                    currentScanPath = path
                    scannedItemsCount = images.size + videos.size + count
                    scanProgress = 0.75f + 0.20f * (count.coerceAtMost(100) / 100f)
                }
                scanProgress = 1.0f
                withContext(Dispatchers.Main) {
                    val total = images.size + videos.size + audio.size
                    Toast.makeText(context, "Rescan completed! Discovered $total media files.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRescanning = false
                scanProgress = 0f
                currentScanPath = ""
            }
        }
    }

    if (isTabletScreen) {
        // Tablet: Horizontal progress bar and current path text on the right side
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Rescan All Media",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Deep scan device storage to detect newly added, moved, hidden, or excluded photos, videos, and music",
                    fontSize = 12.sp,
                    color = Color(0xFF8E95A5),
                    lineHeight = 16.sp
                )
            }

            if (isRescanning) {
                Column(
                    modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scanning storage...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "$scannedItemsCount items",
                            fontSize = 11.sp,
                            color = Color(0xFF34D399)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { scanProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF34D399),
                        trackColor = Color(0x33FFFFFF)
                    )
                    if (currentScanPath.isNotBlank()) {
                        Text(
                            text = currentScanPath,
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Button(
                    onClick = { startRescan() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3334D399)),
                    border = BorderStroke(1.dp, Color(0x6634D399))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan All", fontSize = 13.sp, color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        // Phone: Stacked layout with progress bar and currently scanning path text below
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Rescan All Media",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Deep scan device storage to detect newly added, moved, hidden, or excluded photos, videos, and music",
                        fontSize = 12.sp,
                        color = Color(0xFF8E95A5),
                        lineHeight = 16.sp
                    )
                }

                Button(
                    enabled = !isRescanning,
                    onClick = { startRescan() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRescanning) Color(0x2234D399) else Color(0x3334D399),
                        disabledContainerColor = Color(0x2234D399)
                    ),
                    border = BorderStroke(1.dp, Color(0x6634D399))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRescanning) "Scanning..." else "Rescan All", fontSize = 13.sp, color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold)
                }
            }

            if (isRescanning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scanning device storage...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "$scannedItemsCount items found",
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { scanProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF34D399),
                        trackColor = Color(0x33FFFFFF)
                    )
                    if (currentScanPath.isNotBlank()) {
                        Text(
                            text = "Scanning: $currentScanPath",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
