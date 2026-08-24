package com.medianest.ui.image.hybrid

/**
 * Configuration parameters for the Image Viewer.
 */
data class HybridImageViewerConfig(
    /** Maximum scale factor allowed during pinch/zoom gesture. */
    val maxZoom: Float = 5.0f,

    /** Minimum scale factor allowed during pinch/zoom gesture. */
    val minZoom: Float = 1.0f
)

