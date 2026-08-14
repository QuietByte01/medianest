package com.medianest.ui.image.hybrid

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter

/**
 * Common interface for image rendering layers.
 */
interface ImageRenderer {
    /**
     * Renders the image content based on the provided viewport and configuration.
     */
    @Composable
    fun Render(
        source: ImageSource,
        viewport: ImageViewport,
        config: HybridImageViewerConfig,
        colorFilter: ColorFilter?,
        modifier: Modifier
    )

    /**
     * Called when the renderer is being released to cleanup resources.
     */
    fun release()
}
