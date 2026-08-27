package com.medianest.player

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCapabilityInspectorTest {

    @Test
    fun `inspect returns requiresFFmpegFallback true for AVI files`() {
        val uri = Uri.parse("content://media/external/video/media/1.avi")
        val result = MediaCapabilityInspector.inspect(uri)
        assertTrue(result.requiresFFmpegFallback)
    }

    @Test
    fun `inspect returns requiresFFmpegFallback true for OPUS files`() {
        val uri = Uri.parse("content://media/external/audio/media/1.opus")
        val result = MediaCapabilityInspector.inspect(uri)
        assertTrue(result.requiresFFmpegFallback)
    }

    @Test
    fun `inspect returns requiresFFmpegFallback true for probe result with AC3 in AVI`() {
        val uri = Uri.parse("content://media/external/video/media/1")
        val probeResult = "AVI|H264|AC3"
        val result = MediaCapabilityInspector.inspect(uri, probeResult)
        assertTrue(result.requiresFFmpegFallback)
    }

    @Test
    fun `inspect returns requiresFFmpegFallback true for probe result with MPEG4 and AVI`() {
        val uri = Uri.parse("content://media/external/video/media/1")
        val probeResult = "AVI|MPEG4|MP3"
        val result = MediaCapabilityInspector.inspect(uri, probeResult)
        assertTrue(result.requiresFFmpegFallback)
    }

    @Test
    fun `inspect returns requiresFFmpegFallback false for standard MP4`() {
        val uri = Uri.parse("content://media/external/video/media/1.mp4")
        val result = MediaCapabilityInspector.inspect(uri)
        assertFalse(result.requiresFFmpegFallback)
    }

    @Test
    fun `inspect returns requiresFFmpegFallback false for standard MP3`() {
        val uri = Uri.parse("content://media/external/audio/media/1.mp3")
        val result = MediaCapabilityInspector.inspect(uri)
        assertFalse(result.requiresFFmpegFallback)
    }
}
