package com.example.ui.components

import android.util.Log
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

enum class PictureMode(
    val key: String,
    val displayName: String,
    val subtitle: String,
    val description: String
) {
    BALANCED(
        key = "BALANCED",
        displayName = "Balanced Natural+",
        subtitle = "Optimal balance (Between Natural & Vivid)",
        description = "Bridges Natural and Vivid modes. Enhances lifelike color depth, vibrant skin tones, and dynamic contrast without oversaturating."
    ),
    NATURAL(
        key = "NATURAL",
        displayName = "Natural (Standard)",
        subtitle = "Original sRGB profile",
        description = "Standard flat color profile matching the source media with zero color matrix modification."
    ),
    VIVID(
        key = "VIVID",
        displayName = "Vivid Punch",
        subtitle = "High saturation & rich pop",
        description = "Boosts color saturation and edge contrast for punchy, high-impact visuals."
    ),
    CINEMATIC(
        key = "CINEMATIC",
        displayName = "Cinematic Warm",
        subtitle = "Warm filmic aesthetic",
        description = "Imparts a soft amber warmth, filmic shadow contrast, and cinematic highlight tones."
    ),
    CUSTOM(
        key = "CUSTOM",
        displayName = "Custom Profile",
        subtitle = "Manual slider control",
        description = "Fine-tune Saturation, Contrast, and Color Warmth to your exact preference."
    );

    companion object {
        fun fromKey(key: String): PictureMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: BALANCED
    }
}

object PictureModeUtils {
    private const val TAG = "PictureModeUtils"

    /**
     * Constructs a 20-element 4x5 color transform matrix for RGBA pixels with safe parameter bounds.
     */
    fun createColorMatrixValues(
        mode: PictureMode,
        customSat: Float = 1.18f,
        customCon: Float = 1.06f,
        customWarmth: Float = 0.03f
    ): FloatArray {
        return try {
            val (rawSat, rawCon, rawWarmth) = when (mode) {
                PictureMode.BALANCED -> Triple(1.18f, 1.06f, 0.03f)
                PictureMode.NATURAL -> Triple(1.0f, 1.0f, 0.0f)
                PictureMode.VIVID -> Triple(1.42f, 1.15f, 0.0f)
                PictureMode.CINEMATIC -> Triple(1.05f, 1.10f, 0.08f)
                PictureMode.CUSTOM -> Triple(customSat, customCon, customWarmth)
            }

            // Sanitize and clamp values to safe bounds
            val sat = rawSat.coerceIn(0.0f, 3.0f)
            val con = rawCon.coerceIn(0.1f, 3.0f)
            val warmth = rawWarmth.coerceIn(-0.5f, 0.5f)

            // Standard sRGB luminance weights
            val rw = 0.2126f
            val gw = 0.7152f
            val bw = 0.0722f

            val invSat = 1f - sat
            val rSat = rw * invSat
            val gSat = gw * invSat
            val bSat = bw * invSat

            // Contrast scaling centered around midtone 128 (0.5 normalized)
            val contrastOffset = 128f * (1f - con)

            // Warmth channel offsets
            val redWarmth = warmth * 255f
            val blueWarmth = -warmth * 255f

            val rRowR = (rSat + sat) * con
            val rRowG = gSat * con
            val rRowB = bSat * con

            val gRowR = rSat * con
            val gRowG = (gSat + sat) * con
            val gRowB = bSat * con

            val bRowR = rSat * con
            val bRowG = gSat * con
            val bRowB = (bSat + sat) * con

            floatArrayOf(
                rRowR, rRowG, rRowB, 0f, contrastOffset + redWarmth,
                gRowR, gRowG, gRowB, 0f, contrastOffset,
                bRowR, bRowG, bRowB, 0f, contrastOffset + blueWarmth,
                0f,    0f,    0f,    1f, 0f
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error calculating color matrix values, falling back to identity matrix", e)
            // Identity 4x5 matrix
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }
    }

    /**
     * Compose ColorFilter for AsyncImage / Image components with safe fallback.
     */
    fun getComposeColorFilter(
        modeKey: String,
        customSat: Float = 1.18f,
        customCon: Float = 1.06f,
        customWarmth: Float = 0.03f,
        enabled: Boolean = true
    ): ColorFilter? {
        if (!enabled) return null
        return try {
            val mode = PictureMode.fromKey(modeKey)
            if (mode == PictureMode.NATURAL) return null
            val matrixValues = createColorMatrixValues(mode, customSat, customCon, customWarmth)
            ColorFilter.colorMatrix(ComposeColorMatrix(matrixValues))
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating Compose ColorFilter", e)
            null
        }
    }

    /**
     * Android Paint / View ColorMatrixColorFilter for ExoPlayer View / Hardware views with safe fallback.
     */
    fun getAndroidColorFilter(
        modeKey: String,
        customSat: Float = 1.18f,
        customCon: Float = 1.06f,
        customWarmth: Float = 0.03f,
        enabled: Boolean = true
    ): AndroidColorMatrixColorFilter? {
        if (!enabled) return null
        return try {
            val mode = PictureMode.fromKey(modeKey)
            if (mode == PictureMode.NATURAL) return null
            val matrixValues = createColorMatrixValues(mode, customSat, customCon, customWarmth)
            AndroidColorMatrixColorFilter(AndroidColorMatrix(matrixValues))
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating Android ColorFilter", e)
            null
        }
    }

    /**
     * Formats effective multipliers for UI status display.
     */
    fun getEffectiveParameters(
        modeKey: String,
        customSat: Float = 1.18f,
        customCon: Float = 1.06f,
        customWarmth: Float = 0.03f
    ): Triple<Float, Float, Float> {
        return try {
            val mode = PictureMode.fromKey(modeKey)
            when (mode) {
                PictureMode.BALANCED -> Triple(1.18f, 1.06f, 0.03f)
                PictureMode.NATURAL -> Triple(1.0f, 1.0f, 0.0f)
                PictureMode.VIVID -> Triple(1.42f, 1.15f, 0.0f)
                PictureMode.CINEMATIC -> Triple(1.05f, 1.10f, 0.08f)
                PictureMode.CUSTOM -> Triple(customSat, customCon, customWarmth)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error getting effective parameters", e)
            Triple(1.0f, 1.0f, 0.0f)
        }
    }
}
