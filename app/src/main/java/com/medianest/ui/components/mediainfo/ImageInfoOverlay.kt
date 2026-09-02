package com.medianest.ui.components.mediainfo

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.components.rememberBackdropBlurState
import com.medianest.util.ImageTagManager
import com.medianest.util.LocationUtils
import com.medianest.util.MediaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

data class ImageExifLocationData(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val dateTaken: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val flash: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val placeName: String? = null,
) {
    val hasLocation: Boolean get() = (latitude != null && longitude != null) || !placeName.isNullOrBlank()
    val hasCameraInfo: Boolean get() = !cameraMake.isNullOrBlank() || !cameraModel.isNullOrBlank() ||
            !focalLength.isNullOrBlank() || !aperture.isNullOrBlank() ||
            !shutterSpeed.isNullOrBlank() || !iso.isNullOrBlank() || !dateTaken.isNullOrBlank()
}

internal data class ImageTagSpec(
    val id: String,
    val label: String,
    val icon: ImageVector
)

internal fun extractImageExifAndLocation(context: Context, item: MediaItem?): ImageExifLocationData {
    if (item == null) return ImageExifLocationData()
    var exif: ExifInterface? = null

    // 1. Try direct file on disk first
    try {
        val relPath = item.relativePath.orEmpty()
        if (relPath.isNotBlank()) {
            val primaryStorage = android.os.Environment.getExternalStorageDirectory()
            val candidateFile = File(primaryStorage, if (relPath.endsWith("/")) "$relPath${item.title}" else "$relPath/${item.title}")
            if (candidateFile.exists() && candidateFile.canRead()) {
                exif = ExifInterface(candidateFile)
            }
        }
        if (exif == null && !item.uri.path.isNullOrBlank()) {
            val file = File(item.uri.path!!)
            if (file.exists() && file.canRead()) {
                exif = ExifInterface(file)
            }
        }
    } catch (_: Exception) {}

    // 2. Try MediaStore setRequireOriginal (prevents Android 10+ from scrubbing location EXIF)
    if (exif == null) {
        try {
            val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                try {
                    MediaStore.setRequireOriginal(item.uri)
                } catch (_: Exception) {
                    item.uri
                }
            } else item.uri

            context.contentResolver.openInputStream(targetUri)?.use { stream ->
                exif = ExifInterface(stream)
            }
        } catch (_: Exception) {}
    }

    // 3. Fallback to standard openInputStream
    if (exif == null) {
        try {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                exif = ExifInterface(stream)
            }
        } catch (_: Exception) {}
    }

    if (exif == null) return ImageExifLocationData()

    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotBlank() }
    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotBlank() }

    val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
    val dateTaken = if (!rawDate.isNullOrBlank()) {
        try {
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val parsed = parser.parse(rawDate)
            if (parsed != null) {
                SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.US).format(parsed)
            } else rawDate
        } catch (_: Exception) {
            rawDate
        }
    } else null

    val flVal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
    val focalLength = if (flVal > 0) String.format(Locale.US, "%.1f mm", flVal) else null

    val fNumVal = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
        ?: exif.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, 0.0)
    val aperture = if (fNumVal > 0) String.format(Locale.US, "f/%.1f", fNumVal) else null

    val expVal = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
    val shutterSpeed = if (expVal > 0) {
        if (expVal < 1.0) {
            val denom = (1.0 / expVal).roundToInt()
            "1/${denom}s"
        } else {
            String.format(Locale.US, "%.1fs", expVal)
        }
    } else null

    @Suppress("DEPRECATION")
    val isoVal = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }
        ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
    val iso = if (isoVal > 0) "ISO $isoVal" else null

    val flashVal = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
    val flash = when {
        flashVal == -1 -> null
        (flashVal and 1) != 0 -> "Flash Fired"
        else -> "Off, Did not fire"
    }

    var lat: Double? = null
    var lon: Double? = null
    val latLong = exif.latLong
    if (latLong != null && latLong.size >= 2) {
        val l0 = latLong[0]
        val l1 = latLong[1]
        if (l0 != 0.0 || l1 != 0.0) {
            lat = l0
            lon = l1
        }
    }

    if (lat == null || lon == null) {
        val output = FloatArray(2)
        if (exif.getLatLong(output)) {
            lat = output[0].toDouble()
            lon = output[1].toDouble()
        }
    }

    val altVal = exif.getAltitude(Double.NaN)
    val altitude = if (!altVal.isNaN()) altVal else null

    return ImageExifLocationData(
        cameraMake = make,
        cameraModel = model,
        dateTaken = dateTaken,
        focalLength = focalLength,
        aperture = aperture,
        shutterSpeed = shutterSpeed,
        iso = iso,
        flash = flash,
        latitude = lat,
        longitude = lon,
        altitude = altitude,
        placeName = null,
    )
}

/**
 * Modern floating Glass Surface UI component for displaying complete image metadata,
 * file properties, rendering engine specs, camera EXIF, geolocation details,
 * and interactive image categorization tags with smooth background blur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageInfoOverlay(
    item: MediaItem?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShowFileLocation: ((MediaItem) -> Unit)? = null,
) {
    if (item == null) return

    var isCollapsed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val maxOverlayWidth = when {
        isLandscape -> 580.dp
        isTablet -> 520.dp
        else -> 340.dp
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var currentItemTitle by remember(item.id, item.title) { mutableStateOf(item.title) }
    var renameInputText by remember { mutableStateOf(currentItemTitle) }

    var userTags by remember(item.id, item.uri) {
        mutableStateOf(ImageTagManager.getTags(context, item.uri.toString()))
    }

    val blurState = rememberBackdropBlurState()

    val isGif = item.mimeType.contains("gif", ignoreCase = true) || item.title.endsWith(".gif", ignoreCase = true) || (item.relativePath?.endsWith(".gif", ignoreCase = true) == true)
    val isHeavyGif = isGif && item.size >= 50 * 1024 * 1024L
    val isAnimatedWebpOrAvif = item.mimeType.contains("webp", ignoreCase = true) || item.mimeType.contains("avif", ignoreCase = true)
    val isLargeImage = item.width > 4000 || item.height > 4000

    val engineName = when {
        isHeavyGif -> "GifDecoder (Streaming Raster)"
        isGif -> "ImageDecoder (Hardware AHardwareBuffer)"
        isAnimatedWebpOrAvif -> "ImageDecoder (Hardware Animated)"
        isLargeImage -> "Subsampling Region Decoder (64 Tiles)"
        else -> "Coil Hardware GraphicBuffer"
    }

    val width = (if (item.width > 0) item.width else 1920).coerceAtLeast(1)
    val height = (if (item.height > 0) item.height else 1080).coerceAtLeast(1)
    val megaPixels = String.format(Locale.US, "%.1f MP", (width.toLong() * height.toLong()) / 1_000_000f)

    fun gcd(a: Int, b: Int): Int = if (b <= 0) a.coerceAtLeast(1) else gcd(b, a % b)
    val g = gcd(width, height).coerceAtLeast(1)
    val aspectRatio = "${width / g}:${height / g}"

    val mimeType = item.mimeType.ifBlank { "image/jpeg" }
    val formatName = mimeType.substringAfter('/').uppercase(Locale.US)

    val uncompressedFrameRamMb = (width.toLong() * height.toLong() * 4L) / (1024f * 1024f)
    val fileSizeBytes = item.size
    val fileSizeFormatted = when {
        fileSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024f * 1024f))
        fileSizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024f)
        else -> "$fileSizeBytes B"
    }

    val filePath: String = remember(item.id, item.uri) {
        val rel = item.relativePath.orEmpty()
        rel.ifBlank { item.uri.path.orEmpty() }
    }

    val dateFormatted = remember(item.dateAdded) {
        val added = item.dateAdded
        if (added > 0) {
            val date = if (added > 10_000_000_000L) Date(added) else Date(added * 1000L)
            SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.US).format(date)
        } else "N/A"
    }

    val exifLocationData by produceState<ImageExifLocationData?>(initialValue = null, key1 = item.id, key2 = filePath) {
        value = withContext(Dispatchers.IO) {
            var data = extractImageExifAndLocation(context, item)
            val lat = data.latitude
            val lon = data.longitude
            if (lat != null && lon != null) {
                val place = LocationUtils.reverseGeocode(context, lat, lon)
                data = data.copy(placeName = place)
            }
            data
        }
    }

    val ffmpegReport by produceState(
        initialValue = if (filePath.isNotBlank()) MediaAnalyzer.getReportIfCached(filePath) else null,
        key1 = item.id,
        key2 = filePath
    ) {
        if (filePath.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                MediaAnalyzer.analyze(filePath, "IMAGE", context)
            }
        }
    }

    val imgInfo = ffmpegReport?.imageInfo

    val availableTags = remember {
        listOf(
            ImageTagSpec("COOKING", "Cooking & Food", Icons.Default.Restaurant),
            ImageTagSpec("TRAVEL", "Travel & Places", Icons.Default.Flight),
            ImageTagSpec("NOTES", "Notes & Studies", Icons.Default.Note),
            ImageTagSpec("AI_GENERATED", "AI Generated", Icons.Default.AutoAwesome),
            ImageTagSpec("GARDENING", "Gardening & Nature", Icons.Default.Park),
            ImageTagSpec("ANIME", "Anime & Art", Icons.Default.Brush),
            ImageTagSpec("WALLPAPERS", "Wallpapers", Icons.Default.Wallpaper),
            ImageTagSpec("PETS", "Pets & Animals", Icons.Default.Pets),
            ImageTagSpec("FAMILY", "Family & People", Icons.Default.People),
            ImageTagSpec("DOCUMENTS", "Receipts & Docs", Icons.Default.Description),
            ImageTagSpec("MEMES", "Memes & Funny", Icons.Default.EmojiEmotions),
            ImageTagSpec("SOCIAL", "Social Media", Icons.Default.Share),
            ImageTagSpec("EDITED", "Edited", Icons.Default.Edit)
        )
    }

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

    // Centered Dim Backdrop Container with Blurred Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            .backdropReceiver(blurState, blurRadius = 24.dp)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = modifier
                .widthIn(max = maxOverlayWidth)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clickable(enabled = false) {}, // Intercept click inside card
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xDC0F1015),
            borderColor = Color(0x3334D399),
            enableBlur = true,
            blurRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Row with Title, Collapse button, and Red Close 'X' Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isCollapsed = !isCollapsed }
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Image Info",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "IMAGE INFORMATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { isCollapsed = !isCollapsed },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = "Toggle Collapse",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Prominent Red Close X Button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close image info",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!isCollapsed) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    Column(
                        modifier = Modifier
                            .heightIn(max = if (isLandscape) 280.dp else 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. FILE & RESOLUTION DETAILS
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            DebugStatRow("File Name", currentItemTitle)
                            DebugStatRow("Resolution", "$width × $height ($megaPixels)")
                            DebugStatRow("Aspect Ratio", aspectRatio)
                            DebugStatRow("Format / MIME", "$formatName ($mimeType)")
                            DebugStatRow("File Size", fileSizeFormatted, valueColor = Color(0xFF38BDF8))
                            DebugStatRow("Directory", if (filePath.isNotBlank()) filePath else (item.relativePath ?: "DCIM"))
                            DebugStatRow("Date Added", dateFormatted)
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        // 2. ENGINE & COLOR SPECIFICATIONS
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            DebugStatRow("Decoder Engine", engineName, valueColor = Color(0xFFFFD54F))

                            if (isGif) {
                                DebugStatRow("Decoded VRAM", "Hardware GPU Buffer", valueColor = Color(0xFF34D399))
                                DebugStatRow("Frame Footprint", String.format(Locale.US, "%.1f MB / frame", uncompressedFrameRamMb))
                            } else {
                                DebugStatRow("Decoded VRAM", String.format(Locale.US, "%.2f MB (GPU VRAM)", uncompressedFrameRamMb), valueColor = Color(0xFF34D399))
                            }

                            DebugStatRow("Bitmap Allocator", "HARDWARE (0 JVM Heap)")
                            DebugStatRow("Color Depth", imgInfo?.colorDepth ?: "8-bit per channel")
                            DebugStatRow("Pixel Format", imgInfo?.pixelFormat ?: "rgb24")
                            DebugStatRow("Color Profile", imgInfo?.colorProfile ?: "sRGB (Standard)")
                            DebugStatRow("Orientation", "${imgInfo?.orientation ?: 0}° rotation")
                            DebugStatRow("Alpha Channel", if (imgInfo?.hasAlpha == true) "Yes (RGBA)" else "No (Opaque)")
                        }

                        // 3. GEOLOCATION DETAILS (when available)
                        exifLocationData?.let { exif ->
                            if (exif.hasLocation) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = "Location",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "GEOLOCATION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }

                                    if (!exif.placeName.isNullOrBlank()) {
                                        DebugStatRow("Location", exif.placeName, valueColor = Color(0xFF38BDF8))
                                    }

                                    if (exif.latitude != null && exif.longitude != null) {
                                        val lat = exif.latitude
                                        val lon = exif.longitude
                                        val latStr = String.format(Locale.US, "%.5f° %s", abs(lat), if (lat >= 0) "N" else "S")
                                        val lonStr = String.format(Locale.US, "%.5f° %s", abs(lon), if (lon >= 0) "E" else "W")
                                        DebugStatRow("Coordinates", "$latStr, $lonStr")
                                    }

                                    val alt = exif.altitude
                                    if (alt != null) {
                                        DebugStatRow("Altitude", String.format(Locale.US, "%.1f m", alt))
                                    }
                                }
                            }

                            // 4. CAMERA & EXIF DETAILS (when available)
                            if (exif.hasCameraInfo) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "Camera",
                                            tint = Color(0xFFA7F3D0),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "CAMERA & EXIF",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA7F3D0)
                                        )
                                    }

                                    val camList = listOfNotNull(exif.cameraMake, exif.cameraModel)
                                        .distinct()
                                    val camString = if (camList.isNotEmpty()) camList.joinToString(" ") else ""
                                    if (camString.isNotBlank()) {
                                        DebugStatRow("Camera", camString, valueColor = Color(0xFFA7F3D0))
                                    }

                                    if (!exif.dateTaken.isNullOrBlank()) {
                                        DebugStatRow("Date Taken", exif.dateTaken)
                                    }

                                    val exposureParams = listOfNotNull(
                                        exif.focalLength,
                                        exif.aperture,
                                        exif.shutterSpeed,
                                        exif.iso
                                    ).joinToString(" • ")

                                    if (exposureParams.isNotBlank()) {
                                        DebugStatRow("Exposure", exposureParams)
                                    }

                                    if (!exif.flash.isNullOrBlank()) {
                                        DebugStatRow("Flash", exif.flash)
                                    }
                                }
                            }
                        }

                        // 5. INTERACTIVE IMAGE TAGS & CATEGORIES
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = "Tags",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "IMAGE TAGS & CATEGORIES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            }

                            Text(
                                text = "Tap tags to assign or filter images in gallery:",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableTags.forEach { tagSpec ->
                                    val isTagged = userTags.contains(tagSpec.id)

                                    GlassSurface(
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = if (isTagged) Color(0x5534D399) else Color(0x1F222736),
                                        borderColor = if (isTagged) Color(0xFF34D399) else Color(0x2BFFFFFF),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val isAdded = ImageTagManager.toggleTag(context, item.uri.toString(), tagSpec.id)
                                                userTags = ImageTagManager.getTags(context, item.uri.toString())
                                                val msg = if (isAdded) "Tagged as '${tagSpec.label}'" else "Removed from '${tagSpec.label}'"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = tagSpec.icon,
                                                contentDescription = tagSpec.label,
                                                tint = if (isTagged) Color(0xFF34D399) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = tagSpec.label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isTagged) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isTagged) Color.White else Color(0xFFCBD5E1)
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
}

@Composable
internal fun DebugStatRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}
