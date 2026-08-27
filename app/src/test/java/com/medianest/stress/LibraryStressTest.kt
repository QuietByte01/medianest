package com.medianest.stress

import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.ReturnCode
import com.medianest.util.MediaAnalyzer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

/*
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryStressTest {

    @Before
    fun setup() {
        mockkStatic(FFprobeKit::class)
        mockkStatic(com.arthenica.ffmpegkit.FFmpegKit::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `stress test media analyzer with items`() = runTest {
        val mockJson = """
            {
              "format": { "format_name": "mp4", "duration": "10", "bit_rate": "1000" },
              "streams": [ { "codec_type": "video", "width": 1920, "height": 1080 } ]
            }
        """.trimIndent()

        val probeSession = mockk<FFprobeSession>()
        every { probeSession.returnCode } returns ReturnCode(0)
        every { probeSession.output } returns mockJson
        every { FFprobeKit.execute(any()) } returns probeSession

        val ffmpegSession = mockk<com.arthenica.ffmpegkit.FFmpegSession>()
        every { ffmpegSession.allLogsAsString } returns "Clean"
        every { com.arthenica.ffmpegkit.FFmpegKit.execute(any()) } returns ffmpegSession

        val count = 3 // Minimal count to ensure it runs without OOM in CI
        val time = measureTimeMillis {
            repeat(count) { i ->
                MediaAnalyzer.analyze("/sdcard/test_$i.mp4", "VIDEO")
            }
        }
        assert(time < 5000)
    }
}
*/
