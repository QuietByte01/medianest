package com.medianest.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medianest.data.db.AnalyticsDao
import com.medianest.data.db.SelectiveHiddenFolderDao
import com.medianest.data.repository.AnalyticsRepository
import com.medianest.data.repository.MediaStoreRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaWorkflowIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var mediaStoreRepository: MediaStoreRepository
    private lateinit var analyticsDao: AnalyticsDao
    private lateinit var selectiveHiddenFolderDao: SelectiveHiddenFolderDao
    private lateinit var analyticsRepository: AnalyticsRepository

    @Before
    fun setup() {
        mediaStoreRepository = mockk()
        analyticsDao = mockk(relaxed = true)
        selectiveHiddenFolderDao = mockk()
        analyticsRepository = AnalyticsRepository(analyticsDao, mediaStoreRepository, selectiveHiddenFolderDao)

        coEvery { selectiveHiddenFolderDao.getAllHiddenFoldersList() } returns emptyList()
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `refreshAnalytics workflow - scan and save`() = runTest {
        coEvery { mediaStoreRepository.getImages(any(), any()) } returns listOf(
            mockk {
                every { title } returns "image1.jpg"
                every { size } returns 1024L
                every { mimeType } returns "image/jpeg"
                every { relativePath } returns null
                every { uri } returns mockk()
                every { type } returns com.medianest.data.db.MediaType.IMAGE
            }
        )
        coEvery { mediaStoreRepository.getVideos(any(), any()) } returns emptyList()
        coEvery { mediaStoreRepository.getAudio(any(), any()) } returns emptyList()

        analyticsRepository.refreshAnalytics(showHidden = false)

        coVerify { analyticsDao.saveAnalyticsData(any(), any()) }
    }
}
*/
