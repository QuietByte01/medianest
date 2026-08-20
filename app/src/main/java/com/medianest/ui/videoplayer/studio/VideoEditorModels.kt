package com.medianest.ui.videoplayer.studio

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.util.Locale
import java.util.UUID

enum class StudioTool {
    TRIM, CROP, FILTERS, ADJUST, TEXT, AUDIO
}

enum class StudioBlurMode(val label: String) {
    NONE("No Blur"),
    GAUSSIAN("Gaussian"),
    RADIAL_FOCUS("Tilt-Shift / Focus"),
    PIXELATE("Mosaic / Pixelate"),
    MOTION_BLUR("Motion Blur")
}

enum class StudioFontFamily(val label: String) {
    SANS_SERIF("Modern"),
    SERIF("Classic Serif"),
    MONOSPACE("Code Mono"),
    CURSIVE("Handwritten")
}

enum class TextBackgroundStyle(val label: String) {
    TRANSLUCENT("Glass"),
    SOLID_BLACK("Solid Dark"),
    NEON_PILL("Neon Badge"),
    NONE("No Background")
}

data class StudioClipItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val isVideo: Boolean,
    val durationMs: Long,
    var thumbnail: Bitmap? = null
)

class StudioTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontFamilyStyle: StudioFontFamily = StudioFontFamily.SANS_SERIF,
    val color: Color = Color.White,
    val backgroundStyle: TextBackgroundStyle = TextBackgroundStyle.TRANSLUCENT,
    val fontSizeSp: Float = 20f,
    var offsetX: Float = 50f,
    var offsetY: Float = 100f
)

class StudioEmojiSticker(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    var offsetX: Float = 100f,
    var offsetY: Float = 150f
)

class StudioImageSticker(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val isGif: Boolean = false,
    var offsetX: Float = 100f,
    var offsetY: Float = 150f,
    var widthDp: Float = 120f
)

fun formatTimeShort(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
