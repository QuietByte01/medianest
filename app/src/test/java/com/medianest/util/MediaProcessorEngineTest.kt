package com.medianest.util

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MediaProcessorEngineTest {

    @Before
    fun setup() {
        mockkStatic(FFmpegKit::class)
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
        assertTrue("Command should target output file", cmd.contains("\"$output\""))
    }

    @Test
    fun `test buildConvertCommand with H265 HEVC generates libx265 and crf`() {
        val input = "/storage/emulated/0/Movies/video.avi"
        val output = "/storage/emulated/0/Movies/MediaNest_Studio/video_converted.mp4"

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

        assertTrue(cmd.contains("-c:v libx265 -crf 18"))
        assertTrue(cmd.contains("-c:a aac -b:a 256k"))
        assertTrue(cmd.contains("-tag:v hvc1"))
    }

    @Test
    fun `test buildConvertCommand with audio only flac and mp3`() {
        val input = "/storage/emulated/0/Music/song.wav"
        val outputFlac = "/storage/emulated/0/Music/MediaNest_Studio/song_converted.flac"

        val cmdFlac = MediaProcessorEngine.buildConvertCommand(
            resolvedInput = input,
            outputFilePath = outputFlac,
            outputFormat = "flac",
            isLosslessCopy = false
        )
        assertTrue(cmdFlac.contains("-vn -c:a flac"))

        val outputMp3 = "/storage/emulated/0/Music/MediaNest_Studio/song_converted.mp3"
        val cmdMp3 = MediaProcessorEngine.buildConvertCommand(
            resolvedInput = input,
            outputFilePath = outputMp3,
            outputFormat = "mp3",
            isLosslessCopy = false,
            audioBitrateKbps = 320
        )
        assertTrue(cmdMp3.contains("-vn -c:a libmp3lame -b:a 320k"))
    }

    @Test
    fun `test buildCompressCommand with Discord 25MB target limit calculates bitrates`() {
        val input = "/storage/emulated/0/Movies/huge_video.mp4"
        val output = "/storage/emulated/0/Movies/MediaNest_Studio/huge_compressed.mp4"

        val cmd = MediaProcessorEngine.buildCompressCommand(
            resolvedInput = input,
            outputFilePath = output,
            targetMode = "LIMIT_SIZE",
            targetLimitMb = 25,
            durationSecs = 120.0,
            resolutionScale = "720P"
        )

        assertTrue(cmd.contains("-vf \"scale='min(1280,iw)':-2\""))
        assertTrue(cmd.contains("-c:v libx265"))
        assertTrue(cmd.contains("-b:v"))
        assertTrue(cmd.contains("-maxrate"))
        assertTrue(cmd.contains("-bufsize"))
        assertTrue(cmd.contains("-movflags +faststart"))
    }

    @Test
    fun `test buildCompressCommand with percentage reduction applies CRF`() {
        val input = "/storage/emulated/0/Movies/video.mp4"
        val output = "/storage/emulated/0/Movies/MediaNest_Studio/video_compressed.mp4"

        val cmd75 = MediaProcessorEngine.buildCompressCommand(
            resolvedInput = input,
            outputFilePath = output,
            targetMode = "PERCENT",
            targetPercentage = 75
        )
        assertTrue(cmd75.contains("-crf 30"))

        val cmd50 = MediaProcessorEngine.buildCompressCommand(
            resolvedInput = input,
            outputFilePath = output,
            targetMode = "PERCENT",
            targetPercentage = 50
        )
        assertTrue(cmd50.contains("-crf 26"))
    }

    @Test
    fun `test buildCropCommand produces correct aspect ratio filters`() {
        val input = "/storage/emulated/0/Movies/clip.mp4"
        val output = "/storage/emulated/0/Movies/MediaNest_Studio/clip_cropped.mp4"

        val cmd916 = MediaProcessorEngine.buildCropCommand(
            resolvedInput = input,
            outputFilePath = output,
            cropPreset = "9_16"
        )
        assertTrue(cmd916.contains("crop='min(iw,ih*9/16)':'min(ih,iw*16/9)'"))

        val cmd11 = MediaProcessorEngine.buildCropCommand(
            resolvedInput = input,
            outputFilePath = output,
            cropPreset = "1_1"
        )
        assertTrue(cmd11.contains("crop='min(iw,ih)':'min(iw,ih)'"))

        val cmd219 = MediaProcessorEngine.buildCropCommand(
            resolvedInput = input,
            outputFilePath = output,
            cropPreset = "21_9"
        )
        assertTrue(cmd219.contains("crop='min(iw,ih*21/9)':'min(ih,iw*9/21)'"))
    }

    @Test
    fun `test state management and cancel`() {
        MediaProcessorEngine.resetState()
        assertEquals(MediaProcessorEngine.ProcessingState.Idle, MediaProcessorEngine.processingState.value)

        MediaProcessorEngine.cancelCurrent()
        assertEquals(MediaProcessorEngine.ProcessingState.Cancelled, MediaProcessorEngine.processingState.value)

        MediaProcessorEngine.resetState()
        assertEquals(MediaProcessorEngine.ProcessingState.Idle, MediaProcessorEngine.processingState.value)
    }
}
