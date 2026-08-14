package com.medianest.ui.image.hybrid

/**
 * Configuration parameters for the Hybrid Image Viewer.
 */
data class HybridImageViewerConfig(
    /** Maximum single dimension (width or height) in pixels for standard bitmap rendering before considering tiled rendering. */
    val maxStandardDimension: Int = 4096,

    /** Maximum total pixel count (width * height) for standard bitmap rendering. */
    val maxStandardPixelCount: Long = 16_777_216L, // 4096 * 4096

    /** Maximum estimated decoded memory in bytes (width * height * 4 for ARGB_8888). Default: 64 MB. */
    val maxStandardMemoryBytes: Long = 67_108_864L,

    /** Extreme aspect ratio threshold (e.g. 3.0 = 3:1 or 1:3). Must be combined with dimension/memory requirements. */
    val extremeAspectRatio: Float = 3.0f,

    /** Zoom scale threshold at which tiled rendering is activated for normal-sized images during zoom. */
    val zoomHandoffScale: Float = 1.5f,

    /** Zoom scale threshold at which tiled rendering is released back to standard rendering. Must be < zoomHandoffScale for hysteresis. */
    val zoomReleaseScale: Float = 1.15f,

    /** Delay in milliseconds before releasing tiled decoder resources after zooming back out below release threshold. */
    val tiledCleanupDelayMs: Long = 3000L,

    /** Duration in milliseconds for crossfading between standard and tiled layers during handoff. */
    val handoffCrossfadeDurationMs: Long = 300L,

    /** Preferred size of decoded image tiles in pixels. */
    val tileSizePx: Int = 512,

    /** Maximum scale factor allowed during pinch/zoom gesture. */
    val maxZoomScale: Float = 10.0f,

    /** Minimum scale factor allowed during pinch/zoom gesture. */
    val minZoomScale: Float = 1.0f
)
