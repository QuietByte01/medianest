package com.medianest.ui.components.media

import androidx.compose.ui.graphics.Color

/**
 * Unified Aspect Ratio enum for both Video Player and Media Studio.
 * Includes all modes for fitting, cropping, fixed proportions, and stretching.
 */
enum class MediaAspectRatio(val label: String, val ratio: Float? = null) {
    FIT("Fit"),
    CROP("Crop"), // Smart auto-fill
    P_16_9("16:9", 16f / 9f),
    P_16_10("16:10", 16f / 10f),
    P_9_16("9:16", 9f / 16f),
    P_4_3("4:3", 4f / 3f),
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

/**
 * Unified Media Effect/Filter enum.
 * Consolidates Studio visual filters and Player post-processing effects.
 */
enum class MediaEffect(val label: String, val previewColor: Color = Color(0xFF333333)) {
    // Basic / Reset
    NORMAL("Normal"),
    TRUE_COLOR("Natural Balance"),
    
    // Artistic / Studio
    ORIGINAL("Original"),
    CINEMA("Cinema 35mm", Color(0xFF1B4965)),
    VIVID("Vivid Punch", Color(0xFFE63946)),
    NOIR("Noir B&W", Color(0xFF555555)),
    VINTAGE("Vintage", Color(0xFFC4A482)),
    WARM("Warm Sun", Color(0xFFFFA500)),
    COOL("Cool Cyan", Color(0xFF00BFFF)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF007F)),
    DREAMY("Dreamy", Color(0xFFB388FF)),
    
    // Technical / Enhancement
    BALANCED("Balanced"),
    SHARPEN("Sharpen"),
    HIGH_CONTRAST("High Contrast"),
    SEPIA("Sepia Film"),
    BW("Black & White"),
    NIGHT_VISION("Night Vision"),
    VINTAGE_CRT("Vintage CRT");

    companion object {
        fun fromString(value: String): MediaEffect {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}
