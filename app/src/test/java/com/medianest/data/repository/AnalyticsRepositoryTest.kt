package com.medianest.data.repository

import android.net.Uri
import com.medianest.data.db.AnalyticsDao
import com.medianest.data.db.FileStatSnapshot
import com.medianest.data.db.FormatStat
import com.medianest.data.db.MediaType
import com.medianest.data.db.SelectiveHiddenFolderDao
import com.medianest.data.model.MediaItem
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalyticsRepositoryTest {

    private val analyticsDao = mockk<AnalyticsDao>(relaxed = true)
    private val mediaStoreRepository = mockk<MediaStoreRepository>()
    private val selectiveHiddenFolderDao = mockk<SelectiveHiddenFolderDao>()
    private lateinit var repository: AnalyticsRepository

    @Before
    fun setup() {
        repository = AnalyticsRepository(analyticsDao, mediaStoreRepository, selectiveHiddenFolderDao)
    }

    @Test
    fun `test scanAndSaveAnalytics calculates correctly`() = runTest {
        val images = listOf(
            MediaItem(1, Uri.parse("content://img1"), "img1.jpg", "image/jpeg", MediaType.IMAGE, size = 1000),
            MediaItem(2, Uri.parse("content://img2"), "img2.png", "image/png", MediaType.IMAGE, size = 2000)
        )
        val videos = listOf(
            MediaItem(3, Uri.parse("content://vid1"), "vid1.mp4", "video/mp4", MediaType.VIDEO, size = 5000)
        )
        val audio = listOf(
            MediaItem(4, Uri.parse("content://aud1"), "aud1.mp3", "audio/mpeg", MediaType.AUDIO, size = 500)
        )

        val snapshotSlot = slot<FileStatSnapshot>()
        val statsSlot = slot<List<FormatStat>>()

        coEvery { analyticsDao.saveAnalyticsData(capture(snapshotSlot), capture(statsSlot)) } just Runs

        repository.scanAndSaveAnalytics(images, videos, audio)

        val snapshot = snapshotSlot.captured
        assertEquals(4, snapshot.totalCount)
        assertEquals(8500L, snapshot.totalSize)
        assertEquals(2, snapshot.imageCount)
        assertEquals(3000L, snapshot.imageSize)
        assertEquals(1, snapshot.videoCount)
        assertEquals(5000L, snapshot.videoSize)
        assertEquals(1, snapshot.audioCount)
        assertEquals(500L, snapshot.audioSize)

        val stats = statsSlot.captured
        assertEquals(4, stats.size)
        
        val jpgStat = stats.find { it.extension == "JPG" }
        assertEquals(1, jpgStat?.fileCount)
        assertEquals(1000L, jpgStat?.sizeBytes)

        val pngStat = stats.find { it.extension == "PNG" }
        assertEquals(1, pngStat?.fileCount)
        assertEquals(2000L, pngStat?.sizeBytes)

        val mp4Stat = stats.find { it.extension == "MP4" }
        assertEquals(1, mp4Stat?.fileCount)
        assertEquals(5000L, mp4Stat?.sizeBytes)

        val mp3Stat = stats.find { it.extension == "MP3" }
        assertEquals(1, mp3Stat?.fileCount)
        assertEquals(500L, mp3Stat?.sizeBytes)
    }

    @Test
    fun `test refreshAnalytics calls repository and dao`() = runTest {
        coEvery { selectiveHiddenFolderDao.getAllHiddenFoldersList() } returns emptyList()
        coEvery { mediaStoreRepository.getImages(any(), any()) } returns emptyList()
        coEvery { mediaStoreRepository.getVideos(any(), any()) } returns emptyList()
        coEvery { mediaStoreRepository.getAudio(any(), any()) } returns emptyList()

        repository.refreshAnalytics(showHidden = false)

        coVerify { mediaStoreRepository.getImages(emptySet(), false) }
        coVerify { mediaStoreRepository.getVideos(emptySet(), false) }
        coVerify { mediaStoreRepository.getAudio(emptySet(), false) }
        coVerify { analyticsDao.saveAnalyticsData(any(), any()) }
    }
}
