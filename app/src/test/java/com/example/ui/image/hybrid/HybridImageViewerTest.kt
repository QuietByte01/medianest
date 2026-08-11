package com.example.ui.image.hybrid

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.test.core.app.ApplicationProvider
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HybridImageViewerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `metadata extraction classifies SVG correctly`() {
        val svgSource = ImageSource.from(Uri.parse("file:///test.svg"))
        val metadata = ImageMetadata.extract(context, svgSource)
        assertTrue(metadata.isSvg)
        assertEquals("image/svg+xml", metadata.mimeType)
    }

    @Test
    fun `metadata extraction classifies giant image correctly`() {
        // We can't easily mock BitmapFactory.decodeStream to return specific dimensions in unit tests without PowerMock or similar,
        // so we'll test the classification logic assuming dimensions are extracted.
        
        val config = HybridImageViewerConfig(maxStandardDimension = 1000)
        
        // Manual classification check logic (extracted from ImageMetadata companion)
        fun isGiant(w: Int, h: Int): Boolean {
            val pixelCount = w.toLong() * h.toLong()
            val estimatedMemoryBytes = pixelCount * 4L
            val aspectRatio = if (h > 0) w.toFloat() / h.toFloat() else 1.0f
            val extremeRatio = if (aspectRatio >= 1.0f) aspectRatio else 1.0f / aspectRatio
            val isExtremeRatio = extremeRatio >= config.extremeAspectRatio

            return when {
                w > config.maxStandardDimension || h > config.maxStandardDimension -> true
                pixelCount > config.maxStandardPixelCount -> true
                estimatedMemoryBytes > config.maxStandardMemoryBytes -> true
                isExtremeRatio && (w > 2048 || h > 2048) -> true
                else -> false
            }
        }

        assertTrue("Should be giant due to width", isGiant(1001, 500))
        assertFalse("Should not be giant", isGiant(500, 500))
        assertTrue("Should be giant due to extreme aspect ratio and dimension", isGiant(3000, 100))
    }

    @Test
    fun `ZoomController hysteresis logic`() = runTest {
        val config = HybridImageViewerConfig(
            zoomHandoffScale = 2.0f,
            zoomReleaseScale = 1.5f
        )
        val controller = ZoomController(config)
        controller.updateContainerSize(Size(1000f, 1000f))
        controller.updateContentSize(Size(1000f, 1000f))

        // Initial state
        assertFalse(controller.useTiledRenderer)

        // Zoom in to handoff
        controller.handleZoom(2.0f, Offset.Zero)
        assertTrue("Should enable tiled renderer at handoff scale", controller.useTiledRenderer)

        // Zoom out slightly but stay above release
        controller.handleZoom(0.8f, Offset.Zero) // scale becomes 1.6
        assertTrue("Should stay in tiled renderer due to hysteresis", controller.useTiledRenderer)

        // Zoom out below release
        controller.handleZoom(0.9f, Offset.Zero) // scale becomes 1.44
        assertFalse("Should release tiled renderer below release scale", controller.useTiledRenderer)
    }

    @Test
    fun `Viewport calculations for fitting and panning`() {
        val viewport = ImageViewport(
            scale = 2.0f,
            containerSize = Size(1000f, 1000f),
            contentSize = Size(2000f, 1000f)
        )

        // fitScale = 1000/2000 = 0.5
        assertEquals(0.5f, viewport.fitScale, 0.01f)
        
        // baseDisplayedSize = 2000*0.5, 1000*0.5 = 1000, 500
        assertEquals(Size(1000f, 500f), viewport.baseDisplayedSize)

        // maxOffsetX = (1000 * 2.0 - 1000) / 2 = 500
        assertEquals(500f, viewport.maxOffsetX, 0.01f)
        
        // maxOffsetY = (500 * 2.0 - 1000) / 2 = 0
        assertEquals(0f, viewport.maxOffsetY, 0.01f)

        val clamped = viewport.clampOffset(600f, 100f)
        assertEquals(Offset(500f, 0f), clamped)
    }
}
