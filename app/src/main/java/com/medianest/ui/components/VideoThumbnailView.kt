package com.medianest.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import coil.size.Precision
import com.medianest.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Robust video thumbnail renderer that automatically skips initial black/blank frames
 * by seeking into the video duration (15% by default) and falling back to MediaMetadataRetriever
 * with multi-offset luminance inspection if Coil fails or returns an empty frame.
 */
@Composable
fun VideoThumbnailView(
    uri: Any?,
    durationMs: Long = 0L,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = Icons.Default.Movie
) {
    if (uri == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fallbackBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    val videoSeekMicros = remember(durationMs, uri) {
        when {
            durationMs > 3_000L -> (durationMs * 150L).coerceAtLeast(1_500_000L) // 15% into video
            durationMs > 1_000L -> (durationMs * 500L) // 50% into short clip
            else -> 1_500_000L // 1.5 seconds if duration is 0/unknown
        }
    }

    val imageRequest = remember(uri, durationMs, videoSeekMicros) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(true)
            .precision(Precision.INEXACT)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMicros(videoSeekMicros)
            .build()
    }

    Box(modifier = modifier) {
        if (fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    val targetUri = when (uri) {
                        is Uri -> uri
                        is String -> Uri.parse(uri)
                        else -> null
                    }
                    if (targetUri != null && fallbackBitmap == null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            val extracted = extractNonBlackVideoThumbnail(context, targetUri, durationMs)
                            withContext(Dispatchers.Main) {
                                fallbackBitmap = extracted
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun VideoThumbnailView(
    item: MediaItem?,
    modifier: Modifier = Modifier,
    contentDescription: String? = item?.title,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = Icons.Default.Movie
) {
    val uri = item?.albumArtUri ?: item?.uri
    VideoThumbnailView(
        uri = uri,
        durationMs = item?.durationMs ?: 0L,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        fallbackIcon = fallbackIcon
    )
}

/**
 * Extracts a non-black frame using MediaMetadataRetriever by testing multiple candidate timestamps.
 */
fun extractNonBlackVideoThumbnail(
    context: Context,
    uri: Uri,
    hintDurationMs: Long = 0L
): Bitmap? {
    var retriever: MediaMetadataRetriever? = null
    return try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: hintDurationMs

        val candidateFactors = listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.10f, 0.05f)
        val candidateMicros = candidateFactors.map { factor ->
            if (durationMs > 1_000L) (durationMs * 1000L * factor).toLong() else 1_500_000L
        }.distinct()

        var chosenFrame: Bitmap? = null
        for (seekMicros in candidateMicros) {
            val frame = retriever.getFrameAtTime(seekMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null && !isBitmapMostlyBlack(frame)) {
                chosenFrame = frame
                break
            }
            if (frame != null && chosenFrame == null) {
                chosenFrame = frame
            }
        }

        chosenFrame
    } catch (_: Exception) {
        null
    } finally {
        try {
            retriever?.release()
        } catch (_: Exception) {}
    }
}

/**
 * Checks whether a frame is mostly black (e.g. intro black screen / dark transition).
 */
private fun isBitmapMostlyBlack(bitmap: Bitmap): Boolean {
    return try {
        val readableBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }
        if (readableBitmap.width < 4 || readableBitmap.height < 4) return true
        val stepX = (readableBitmap.width / 5).coerceAtLeast(1)
        val stepY = (readableBitmap.height / 5).coerceAtLeast(1)
        var darkCount = 0
        var total = 0
        var x = 0
        while (x < readableBitmap.width) {
            var y = 0
            while (y < readableBitmap.height) {
                val pixel = readableBitmap.getPixel(x, y)
                val luma = (0.299 * AndroidColor.red(pixel) +
                        0.587 * AndroidColor.green(pixel) +
                        0.114 * AndroidColor.blue(pixel))
                if (luma < 15.0) darkCount++
                total++
                y += stepY
            }
            x += stepX
        }
        total > 0 && (darkCount.toFloat() / total) > 0.88f
    } catch (_: Exception) {
        false
    }
}
