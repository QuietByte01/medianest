package com.medianest.player.fx

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import com.medianest.ui.components.media.MediaEffect

/**
 * A Media3 RgbMatrix effect that applies mathematically accurate GPU color grading presets
 * with zero CPU overhead and seamless real-time dynamic switching during playback.
 */
@UnstableApi
class ColorGradingGlEffect(private val effectMode: MediaEffect) : RgbMatrix {

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        return when (effectMode) {
            MediaEffect.OFF, MediaEffect.NORMAL, MediaEffect.ORIGINAL -> {
                floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f
                )
            }
            MediaEffect.TRUE_COLOR -> {
                // Warm White-Point Shift
                floatArrayOf(
                    1.05f, 0f, 0f, 0f,
                    0f, 0.98f, 0f, 0f,
                    0f, 0f, 0.90f, 0f,
                    0f, 0f, 0f, 1f
                )
            }
            MediaEffect.CINEMA -> {
                // Teal & Orange Filmic Grade
                floatArrayOf(
                    1.15f, 0.0f, 0.0f, 0f,
                    0.0f, 1.02f, 0.0f, 0f,
                    -0.05f, 0.08f, 1.18f, 0f,
                    0.03f, 0.01f, 0.05f, 1f
                )
            }
            MediaEffect.BW, MediaEffect.NOIR -> {
                // Rec. 709 Luminance weights (0.2126, 0.7152, 0.0722)
                floatArrayOf(
                    0.2126f, 0.2126f, 0.2126f, 0f,
                    0.7152f, 0.7152f, 0.7152f, 0f,
                    0.0722f, 0.0722f, 0.0722f, 0f,
                    0f, 0f, 0f, 1f
                )
            }
            MediaEffect.VIVID -> {
                // High Saturation / Contrast Pop
                floatArrayOf(
                    1.25f, -0.10f, -0.10f, 0f,
                    -0.10f, 1.25f, -0.10f, 0f,
                    -0.10f, -0.10f, 1.25f, 0f,
                    0.02f, 0.02f, 0.02f, 1f
                )
            }
            MediaEffect.NIGHT_VISION -> {
                // Blue light reduction / night shift
                floatArrayOf(
                    1.0f, 0f, 0f, 0f,
                    0f, 0.90f, 0f, 0f,
                    0f, 0f, 0.60f, 0f,
                    0f, 0f, 0f, 1f
                )
            }
            MediaEffect.VINTAGE, MediaEffect.VINTAGE_CRT -> {
                // 35mm Warm Filmic Vintage
                floatArrayOf(
                    1.05f, 0.05f, 0.05f, 0f,
                    0.05f, 0.95f, 0.05f, 0f,
                    0.05f, 0.05f, 0.80f, 0f,
                    0.04f, 0.02f, -0.02f, 1f
                )
            }
            MediaEffect.SHARPEN, MediaEffect.HIGH_CONTRAST -> {
                // Dynamic Contrast Boost
                floatArrayOf(
                    1.25f, 0f, 0f, 0f,
                    0f, 1.25f, 0f, 0f,
                    0f, 0f, 1.25f, 0f,
                    -0.125f, -0.125f, -0.125f, 1f
                )
            }
            MediaEffect.BALANCED -> {
                // Balanced Natural Tone Equalization
                floatArrayOf(
                    1.08f, -0.04f, -0.04f, 0f,
                    -0.04f, 1.08f, -0.04f, 0f,
                    -0.04f, -0.04f, 1.08f, 0f,
                    0.01f, 0.01f, 0.01f, 1f
                )
            }
            MediaEffect.NATURAL -> {
                // Natural Saturation Baseline (Balanced sRGB profile)
                floatArrayOf(
                    0.88f, 0.06f, 0.06f, 0f,
                    0.06f, 0.88f, 0.06f, 0f,
                    0.06f, 0.06f, 0.88f, 0f,
                    0f, 0f, 0f, 1f
                )
            }
            MediaEffect.WARM -> {
                // Warm Golden Hour
                floatArrayOf(
                    1.15f, 0f, 0f, 0f,
                    0f, 1.05f, 0f, 0f,
                    0f, 0f, 0.85f, 0f,
                    0.02f, 0.01f, 0f, 1f
                )
            }
            MediaEffect.COOL -> {
                // Cool Cyan Breeze
                floatArrayOf(
                    0.85f, 0f, 0f, 0f,
                    0f, 1.05f, 0f, 0f,
                    0f, 0f, 1.20f, 0f,
                    0f, 0.01f, 0.02f, 1f
                )
            }
            MediaEffect.CYBERPUNK -> {
                // Neon Pink & Cyan Split Toning
                floatArrayOf(
                    1.30f, 0.0f, 0.20f, 0f,
                    0.0f, 0.90f, 0.10f, 0f,
                    0.20f, 0.20f, 1.30f, 0f,
                    0.05f, 0.0f, 0.05f, 1f
                )
            }
            MediaEffect.DREAMY -> {
                // Soft Lavender Dream
                floatArrayOf(
                    1.10f, 0.02f, 0.05f, 0f,
                    0.02f, 0.98f, 0.02f, 0f,
                    0.05f, 0.02f, 1.15f, 0f,
                    0.03f, 0.01f, 0.04f, 1f
                )
            }
            MediaEffect.SEPIA -> {
                // Classic Sepia
                floatArrayOf(
                    0.393f, 0.349f, 0.272f, 0f,
                    0.769f, 0.686f, 0.534f, 0f,
                    0.189f, 0.168f, 0.131f, 0f,
                    0f, 0f, 0f, 1f
                )
            }
        }
    }
}
