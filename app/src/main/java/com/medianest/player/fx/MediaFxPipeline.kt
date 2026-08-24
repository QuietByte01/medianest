package com.medianest.player.fx

import androidx.compose.ui.graphics.ColorMatrix
import com.medianest.ui.components.media.MediaEffect

object MediaFxPipeline {

    /**
     * Returns a Jetpack Compose ColorMatrix corresponding to the specified MediaEffect.
     * This is used for static image rendering (e.g. thumbnails, photo viewer) to match
     * the mathematical transformations happening in the GLSL shader for video.
     */
    fun getComposeColorMatrix(effect: MediaEffect): ColorMatrix {
        val matrix = ColorMatrix()
        when (effect) {
            MediaEffect.OFF, MediaEffect.NORMAL, MediaEffect.ORIGINAL -> {
                // Identity matrix
            }
            MediaEffect.TRUE_COLOR, MediaEffect.WARM -> {
                // Warm White-Point Shift
                matrix.setToScale(1.05f, 0.98f, 0.90f, 1f)
            }
            MediaEffect.CINEMA -> {
                // Teal & Orange approximation
                val arr = floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f, // R
                    0f, 1.05f, 0.05f, 0f, 5f, // G
                    0f, 0.1f, 1.15f, 0f, 15f, // B
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.BW, MediaEffect.NOIR -> {
                // True monochrome luminance weights (Rec 709)
                val arr = floatArrayOf(
                    0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                    0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                    0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.VIVID -> {
                // Smart saturation approximation
                val arr = floatArrayOf(
                    1.2f, -0.1f, -0.1f, 0f, 10f,
                    -0.1f, 1.2f, -0.1f, 0f, 10f,
                    -0.1f, -0.1f, 1.2f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.NIGHT_VISION -> {
                // Blue light filter
                matrix.setToScale(1.0f, 0.9f, 0.6f, 1f)
            }
            MediaEffect.VINTAGE, MediaEffect.VINTAGE_CRT -> {
                // 35mm film / vintage
                val arr = floatArrayOf(
                    1.05f, 0.1f, 0.1f, 0f, 15f,
                    0.05f, 0.95f, 0.05f, 0f, 5f,
                    0.1f, 0.1f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.SHARPEN, MediaEffect.HIGH_CONTRAST -> {
                // Contrast boost (HDR simulator)
                val contrast = 1.2f
                val offset = (1f - contrast) * 255f / 2f
                val arr = floatArrayOf(
                    contrast, 0f, 0f, 0f, offset,
                    0f, contrast, 0f, 0f, offset,
                    0f, 0f, contrast, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.BALANCED -> {
                // Balanced (S-Curve approximation using mild contrast reduction and brightness bump)
                val contrast = 0.9f
                val offset = (1f - contrast) * 255f / 2f + 5f
                val arr = floatArrayOf(
                    contrast, 0f, 0f, 0f, offset,
                    0f, contrast, 0f, 0f, offset,
                    0f, 0f, contrast, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.NATURAL -> {
                // Natural (Saturation Normalization approximation)
                val arr = floatArrayOf(
                    0.8f, 0.1f, 0.1f, 0f, 0f,
                    0.1f, 0.8f, 0.1f, 0f, 0f,
                    0.1f, 0.1f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.COOL -> matrix.setToScale(0.9f, 0.95f, 1.1f, 1f)
            MediaEffect.CYBERPUNK -> matrix.setToScale(1.2f, 0.8f, 1.2f, 1f)
            MediaEffect.SEPIA -> {
                val arr = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                for (i in arr.indices) { matrix.values[i] = arr[i] }
            }
            MediaEffect.DREAMY -> matrix.setToScale(1.1f, 1.0f, 1.2f, 1f)
            else -> {}
        }
        return matrix
    }

    fun getComposeColorFilter(effect: MediaEffect): androidx.compose.ui.graphics.ColorFilter? {
        if (effect == MediaEffect.OFF || effect == MediaEffect.NORMAL || effect == MediaEffect.ORIGINAL) return null
        return androidx.compose.ui.graphics.ColorFilter.colorMatrix(getComposeColorMatrix(effect))
    }

    fun getAndroidColorFilter(effect: MediaEffect): android.graphics.ColorFilter? {
        if (effect == MediaEffect.OFF || effect == MediaEffect.NORMAL || effect == MediaEffect.ORIGINAL) return null
        val composeMatrix = getComposeColorMatrix(effect)
        val androidMatrix = android.graphics.ColorMatrix(composeMatrix.values)
        return android.graphics.ColorMatrixColorFilter(androidMatrix)
    }
}
