package com.medianest.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color(0x33818CF8),
    backgroundColor: Color = Color(0xFF1E293B),
    backgroundImage: Any? = null,
    containerColor: Color = Color(0xFF1E293B),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
    ) {
        content()
    }
}

@Composable
fun AmbientGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundImage: Any? = null,
    containerColor: Color = Color(0xFF1E293B),
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        backgroundImage = backgroundImage,
        containerColor = containerColor,
        content = content
    )
}

@Composable
fun AppFormatChip(
    text: String = "",
    format: String = text,
    label: String = text,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF1E293B))
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label.ifBlank { format.ifBlank { text } },
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PaletteTagChip(
    text: String = "",
    label: String = text,
    formatKey: String = text,
    isSelected: Boolean = false,
    paletteColor: Color = Color(0xFF6366F1),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AppFormatChip(
        text = text,
        label = label,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val secs = (ms / 1000) % 60
    val mins = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

fun formatTimeShort(ms: Long): String = formatDuration(ms)

fun setDataSourceSafe(retriever: MediaMetadataRetriever, context: Context, uri: Uri) {
    try {
        retriever.setDataSource(context, uri)
    } catch (_: Exception) {}
}

open class VideoFrameDecoder {
    open fun release() {}
}
