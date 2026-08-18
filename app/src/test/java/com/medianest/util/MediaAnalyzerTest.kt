package com.medianest.util

import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaAnalyzerTest {

    @Before
    fun setup() {
        mockkStatic(FFprobeKit::class)
        mockkStatic(FFmpegKit::class)
    }

    @Test
    fun `test analyze returns correct metadata from JSON`() = runTest {
        val testPath = "/sdcard/test.mp4"
        val mockJson = """
            {
              "format": {
                "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
                "duration": "120.5",
                "bit_rate": "5000000",
                "nb_streams": 2
              },
              "streams": [
                {
                  "codec_type": "video",
                  "codec_name": "h264",
                  "width": 1920,
                  "height": 1080,
                  "r_frame_rate": "30/1",
                  "bit_rate": "4500000"
                },
                {
                  "codec_type": "audio",
                  "codec_name": "aac",
                  "sample_rate": "44100",
                  "channels": 2
                }
              ]
            }
        """.trimIndent()

        val probeSession = mockk<FFprobeSession>()
        every { probeSession.returnCode } returns ReturnCode(0)
        every { probeSession.output } returns mockJson
        every { FFprobeKit.execute(any()) } returns probeSession

        val ffmpegSession = mockk<FFmpegSession>()
        every { ffmpegSession.allLogsAsString } returns "Clean"
        every { FFmpegKit.execute(any()) } returns ffmpegSession

        val report = MediaAnalyzer.analyze(testPath, "VIDEO")

        assertNotNull(report.format)
        assertEquals("mov, mp4, m4a, 3gp, 3g2, mj2", report.format?.containerFormat)
        assertEquals(120.5, report.format?.duration!!, 0.01)
        assertEquals(5000000L, report.format?.bitrate)

        assertNotNull(report.videoStream)
        assertEquals("H264", report.videoStream?.codecName)
        assertEquals(1920, report.videoStream?.width)
        assertEquals(1080, report.videoStream?.height)
        assertEquals(30.0, report.videoStream?.avgFpsDecimal!!, 0.01)

        assertEquals(1, report.audioStreams.size)
        assertEquals("AAC (Advanced Audio Coding)", report.audioStreams[0].codecName)
        assertEquals(44100, report.audioStreams[0].sampleRate)

        assertFalse(report.diagnostics.hasErrors)
    }

    @Test
    fun `test analyze detects corruption`() = runTest {
        val testPath = "/sdcard/corrupt.mp4"
        
        val probeSession = mockk<FFprobeSession>()
        every { probeSession.returnCode } returns ReturnCode(0)
        every { probeSession.output } returns "{ \"format\": {}, \"streams\": [] }"
        every { FFprobeKit.execute(any()) } returns probeSession

        val ffmpegSession = mockk<FFmpegSession>()
        every { ffmpegSession.allLogsAsString } returns "Error: corrupt bitstream at frame 100"
        every { FFmpegKit.execute(any()) } returns ffmpegSession

        val report = MediaAnalyzer.analyze(testPath, "VIDEO")

        assertTrue(report.diagnostics.corruptedFramesDetected)
        assertTrue(report.diagnostics.warnings.any { it.contains("corrupt", ignoreCase = true) })
    }

    @Test
    fun `test analyze handles FFprobe failure`() = runTest {
        val testPath = "/sdcard/bad.mp4"
        
        val probeSession = mockk<FFprobeSession>()
        every { probeSession.returnCode } returns ReturnCode(1)
        every { probeSession.failStackTrace } returns "Severe error"
        every { FFprobeKit.execute(any()) } returns probeSession

        val report = MediaAnalyzer.analyze(testPath, "VIDEO")

        assertTrue(report.diagnostics.hasErrors)
        assertTrue(report.diagnostics.errors.any { it.contains("FFprobe failed", ignoreCase = true) })
    }
}
