package com.medianest.ui.videoplayer.studio

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.util.Locale
import java.util.UUID

enum class StudioTool {
    TRIM, CROP, FILTERS, ADJUST, TEXT, AUDIO
}

enum class CropPreset(val label: String) {
    ORIGINAL("Original"),
    P_16_9("16:9"),
    P_9_16("9:16"),
    P_1_1("1:1"),
    P_4_3("4:3"),
    P_4_5("4:5"),
    P_21_9("21:9")
}

enum class VideoStudioFilter(val label: String, val previewColor: Color) {
    ORIGINAL("Original", Color(0xFF333333)),
    CINEMA("Cinema 35mm", Color(0xFF1B4965)),
    VIVID("Vivid", Color(0xFFE63946)),
    NOIR("Noir B&W", Color(0xFF555555)),
    VINTAGE("Vintage", Color(0xFFC4A482)),
    WARM("Warm Sun", Color(0xFFFFA500)),
    COOL("Cool Cyan", Color(0xFF00BFFF)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF007F)),
    DREAMY("Dreamy", Color(0xFFB388FF))
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
