package com.medianest.ui.components.media

import androidx.compose.ui.graphics.Color

enum class MediaAspectRatio(val label: String, val ratio: Float? = null) {
    FIT("Fit"),
    CROP("Crop"),
    P_16_9("16:9", 16f / 9f),
    P_16_10("16:10", 16f / 10f),
    P_9_16("9:16", 9f / 16f),
    P_4_3("4:3", 4f / 3f),
    P_3_4("3:4", 3f / 4f),
    P_1_1("1:1", 1f / 1f),
    P_4_5("4:5", 4f / 5f),
    P_21_9("21:9", 21f / 9f),
    ORIGINAL("Original"),
    STRETCH("Stretch");

    companion object {
        fun fromString(value: String): MediaAspectRatio {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) } ?: FIT
        }
    }
}

enum class MediaEffect(val label: String, val previewColor: Color = Color(0xFF333333)) {
    OFF("Off"),
    NORMAL("Off"),
    TRUE_COLOR("True Color", Color(0xFFE2B053)),
    BALANCED("Balanced", Color(0xFF4CAF50)),
    BRIGHT("Bright", Color(0xFFFFD700)),
    NATURAL("Natural", Color(0xFF64B5F6)),
    ORIGINAL("Original"),
    CINEMA("Cinema 35mm", Color(0xFF1B4965)),
    VIVID("Vivid Punch", Color(0xFFE63946)),
    NOIR("Noir B&W", Color(0xFF555555)),
    VINTAGE("Vintage", Color(0xFFC4A482)),
    WARM("Warm Sun", Color(0xFFFFA500)),
    COOL("Cool Cyan", Color(0xFF00BFFF)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF007F)),
    DREAMY("Dreamy", Color(0xFFB388FF)),
    SHARPEN("Sharpen"),
    HIGH_CONTRAST("High Contrast"),
    SEPIA("Sepia Film"),
    BW("B&W"),
    NIGHT_VISION("Night Vision"),
    VINTAGE_CRT("Vintage CRT");

    companion object {
        fun fromString(value: String): MediaEffect {
            return entries.find { 
                it.name.equals(value, ignoreCase = true) || 
                it.label.equals(value, ignoreCase = true)
            } ?: OFF
        }
    }
}
