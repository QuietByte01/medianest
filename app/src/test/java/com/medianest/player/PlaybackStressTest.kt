package com.medianest.player

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PlaybackStressTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var manager: ExoPlayerManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        Dispatchers.setMain(testDispatcher)

        mockPlayer = mockk<ExoPlayer>(relaxed = true)
        val mockBuilder = mockk<ExoPlayer.Builder>(relaxed = true)

        every { mockBuilder.setMediaSourceFactory(any()) } returns mockBuilder
        every { mockBuilder.setLoadControl(any()) } returns mockBuilder
        every { mockBuilder.setAudioAttributes(any(), any()) } returns mockBuilder
        every { mockBuilder.setLooper(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockPlayer

        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().setMediaSourceFactory(any()) } returns mockBuilder

        val instanceField: Field = ExoPlayerManager::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)

        mockkObject(MediaCapabilityInspector)
        every { MediaCapabilityInspector.inspect(any(), any()) } returns MediaCapabilityInspector.MediaProfile(
            container = "MP4",
            videoCodec = "H264",
            audioCodec = "AAC",
            requiresFFmpegFallback = false
        )

        manager = ExoPlayerManager.getInstance(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testRapidNextAndPreviousTrackTransitions() = runTest(testDispatcher) {
        val testQueue = (1L..20L).map { i ->
            MediaItem(
                id = i,
                title = "Video $i.mp4",
                uri = Uri.parse("content://media/external/video/media/$i"),
                mimeType = "video/mp4",
                type = MediaType.VIDEO,
                durationMs = 60000L,
                size = 1000000L,
                dateAdded = 0L,
                dateModified = 0L
            )
        }

        // Start playback
        manager.playMediaList(testQueue, 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, manager.playerState.value.queueIndex)
        assertEquals(1L, manager.playerState.value.currentItem?.id)

        // Rapid next skipping across 30 iterations
        for (step in 1..30) {
            manager.next()
            testDispatcher.scheduler.advanceUntilIdle()
            val expectedIdx = step % testQueue.size
            assertEquals(expectedIdx, manager.playerState.value.queueIndex)
            assertEquals("Media3", manager.playerState.value.activeEngineName)
        }

        // Rapid previous skipping across 30 iterations
        for (step in 1..30) {
            manager.previous()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("Media3", manager.playerState.value.activeEngineName)
        }

        verify(atLeast = 60) { mockPlayer.prepare() }
        verify(atLeast = 60) { mockPlayer.play() }
    }

    @Test
    fun testRapidPlayPauseTogglingWithoutStateLoss() = runTest(testDispatcher) {
        val item = MediaItem(
            id = 101L,
            title = "Test.mp4",
            uri = Uri.parse("content://media/external/video/media/101"),
            mimeType = "video/mp4",
            type = MediaType.VIDEO,
            durationMs = 120000L,
            size = 5000000L,
            dateAdded = 0L,
            dateModified = 0L
        )

        manager.playMediaList(listOf(item), 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle pause and play 25 times
        for (i in 1..25) {
            manager.pause()
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse("Should be paused on iteration $i", manager.playerState.value.isPlaying)

            manager.play()
            testDispatcher.scheduler.advanceUntilIdle()
            // Media3 engine was NOT stopped into STATE_IDLE
            verify(exactly = 0) { mockPlayer.stop() }
        }
    }

    @Test
    fun testRapidSeekingAndScrubbing() = runTest(testDispatcher) {
        val item = MediaItem(
            id = 102L,
            title = "SeekTest.mp4",
            uri = Uri.parse("content://media/external/video/media/102"),
            mimeType = "video/mp4",
            type = MediaType.VIDEO,
            durationMs = 300000L,
            size = 10000000L,
            dateAdded = 0L,
            dateModified = 0L
        )

        manager.playMediaList(listOf(item), 0)
        testDispatcher.scheduler.advanceUntilIdle()

        for (pos in listOf(10000L, 50000L, 120000L, 250000L, 5000L, 0L)) {
            manager.seekTo(pos)
            testDispatcher.scheduler.advanceUntilIdle()
            verify { mockPlayer.seekTo(pos) }
        }
    }
}
