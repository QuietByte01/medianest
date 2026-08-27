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
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaAnalyzerTest {

    @Before
    fun setup() {
        mockkStatic(FFprobeKit::class)
        mockkStatic(FFmpegKit::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
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
                  "width": 3840,
                  "height": 2160,
                  "r_frame_rate": "60/1",
                  "bit_rate": "45000000",
                  "color_transfer": "smpte2084",
                  "color_primaries": "bt2020",
                  "profile": "Main 10"
                },
                {
                  "codec_type": "audio",
                  "codec_name": "truehd",
                  "sample_rate": "48000",
                  "channels": 8,
                  "channel_layout": "7.1",
                  "side_data_list": [
                    { "side_data_type": "atmos" }
                  ]
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

        assertNotNull(report.videoStream)
        assertEquals("H264", report.videoStream?.codecName)
        assertEquals(3840, report.videoStream?.width)
        assertEquals(2160, report.videoStream?.height)
        assertTrue(report.videoStream?.isHdr == true)

        assertEquals(1, report.audioStreams.size)
        assertTrue(report.audioStreams[0].isSpatialAudio)
        assertEquals(8, report.audioStreams[0].channels)

        // Verify Tech Badges
        assertTrue(report.techBadges.contains("4K"))
        assertTrue(report.techBadges.contains("HDR"))
        assertTrue(report.techBadges.contains("SPATIAL AUDIO"))
        assertTrue(report.techBadges.contains("7.1CH"))
        assertTrue(report.techBadges.contains("DOLBY TRUEHD"))
    }

    @Test
    fun `test detectTechBadges for IMAX and Blu-Ray`() = runTest {
        val testPath = "/sdcard/movie.mkv"
        val mockJson = """
            {
              "format": {
                "format_name": "matroska,webm",
                "bit_rate": "30000000"
              },
              "streams": [
                {
                  "codec_type": "video",
                  "width": 1920,
                  "height": 1440,
                  "display_aspect_ratio": "1.43:1"
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

        assertTrue(report.techBadges.contains("IMAX"))
        assertTrue(report.techBadges.contains("BLU-RAY"))
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
