package com.medianest.util

import com.arthenica.ffmpegkit.FFmpegKit
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

/**
 * Tests command building logic in MediaProcessorEngine.
 */
class MediaProcessorEngineTest {

    @Before
    fun setup() {
        mockkStatic(FFmpegKit::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test buildConvertCommand with lossless copy generates -c copy`() {
        val input = "/storage/emulated/0/Movies/sample.mkv"
        val output = "/storage/emulated/0/Movies/MediaNest_Studio/sample_converted.mp4"

        val cmd = MediaProcessorEngine.buildConvertCommand(
            resolvedInput = input,
            outputFilePath = output,
            outputFormat = "mp4",
            isLosslessCopy = true
        )

        assertTrue("Command should contain input path", cmd.contains("-i \"$input\""))
        assertTrue("Command should contain -c copy", cmd.contains("-c copy"))
        assertTrue("Command should contain -movflags +faststart", cmd.contains("-movflags +faststart"))
    }

    @Test
    fun `test buildConvertCommand with H265 HEVC generates libx265 and crf`() {
        val input = "video.avi"
        val output = "video_converted.mp4"

        val cmd = MediaProcessorEngine.buildConvertCommand(
            resolvedInput = input,
            outputFilePath = output,
            outputFormat = "mp4",
            isLosslessCopy = false,
            videoCodec = "libx265",
            audioCodec = "aac",
            qualityCrf = 18,
            audioBitrateKbps = 256
        )

        assertTrue(cmd.contains("-c:v libx265"))
        assertTrue(cmd.contains("-crf 18"))
        assertTrue(cmd.contains("-tag:v hvc1"))
    }

    @Test
    fun `test buildConvertCommand with AV1 video codec generates libsvtav1 and 10bit pixel format`() {
        val input = "sample.mov"
        val output = "sample_av1.mp4"

        val cmd = MediaProcessorEngine.buildConvertCommand(
            resolvedInput = input,
            outputFilePath = output,
            outputFormat = "mp4",
            isLosslessCopy = false,
            videoCodec = "av1",
            qualityCrf = 24,
            audioBitrateKbps = 192
        )

        assertTrue("Should use AV1 (mapped to libsvtav1)", cmd.contains("-c:v libsvtav1"))
        assertTrue("Should use 10-bit pixel format", cmd.contains("-pix_fmt yuv420p10le"))
        assertTrue(cmd.contains("-c:a aac -b:a 192k"))
    }

    @Test
    fun `test buildCompressCommand with Discord 25MB target limit calculates bitrates`() {
        val input = "huge_video.mp4"
        val output = "huge_compressed.mp4"

        val cmd = MediaProcessorEngine.buildCompressCommand(
            resolvedInput = input,
            outputFilePath = output,
            targetMode = "LIMIT_SIZE",
            targetLimitMb = 25,
            durationSecs = 120.0,
            resolutionScale = "720P"
        )

        assertTrue(cmd.contains("-vf \"scale='min(1280,iw)':-2\""))
        assertTrue(cmd.contains("-c:v libsvtav1") || cmd.contains("-c:v libx265"))
        assertTrue(cmd.contains("-b:v"))
    }

    @Test
    fun `test buildCompressCommand with AV1 and AUTO mode`() {
        val input = "video.mp4"
        val output = "video_av1_compressed.mp4"

        val cmdAuto = MediaProcessorEngine.buildCompressCommand(
            resolvedInput = input,
            outputFilePath = output,
            targetMode = "AUTO",
            crf = 26,
            videoCodec = "libsvtav1"
        )
        // Note: Engine uses libsvtav1 for AV1 now
        assertTrue("Should use libsvtav1 for AV1 AUTO", cmdAuto.contains("-c:v libsvtav1"))
        assertTrue(cmdAuto.contains("-pix_fmt yuv420p10le"))

        val cmdAv1 = MediaProcessorEngine.buildCompressCommand(
            resolvedInput = input,
            outputFilePath = output,
            targetMode = "AV1",
            crf = 28
        )
        assertTrue(cmdAv1.contains("-c:v libsvtav1"))
    }

    @Test
    fun `test buildCropCommand produces correct aspect ratio filters`() {
        val input = "clip.mp4"
        val output = "clip_cropped.mp4"

        val cmd916 = MediaProcessorEngine.buildCropCommand(input, output, "9_16")
        assertTrue(cmd916.contains("crop='min(iw,ih*9/16)'"))

        val cmd11 = MediaProcessorEngine.buildCropCommand(input, output, "1_1")
        assertTrue(cmd11.contains("crop='min(iw,ih)'"))
    }

    @Test
    fun `test state management and cancel`() {
        MediaProcessorEngine.resetState()
        assertEquals(MediaProcessorEngine.ProcessingState.Idle, MediaProcessorEngine.processingState.value)

        MediaProcessorEngine.cancelCurrent()
        assertEquals(MediaProcessorEngine.ProcessingState.Cancelled, MediaProcessorEngine.processingState.value)
    }
}
