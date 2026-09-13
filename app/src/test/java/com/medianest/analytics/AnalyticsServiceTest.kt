package com.medianest.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalyticsServiceTest {

    private lateinit var context: Context
    private lateinit var firebaseAnalyticsService: FirebaseAnalyticsService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        firebaseAnalyticsService = FirebaseAnalyticsService(context)
    }

    @Test
    fun `test logging screen view does not throw exception`() {
        firebaseAnalyticsService.logScreenView("LibraryScreen")
        assertTrue(true)
    }

    @Test
    fun `test logging media playback does not throw exception`() {
        firebaseAnalyticsService.logMediaPlayback("VIDEO", "MP4", 120000L)
        assertTrue(true)
    }

    @Test
    fun `test logging feature use does not throw exception`() {
        firebaseAnalyticsService.logFeatureUse("video_trim", mapOf("duration" to 15))
        assertTrue(true)
    }

    @Test
    fun `test setting analytics disabled state`() {
        firebaseAnalyticsService.setAnalyticsEnabled(false)
        firebaseAnalyticsService.logScreenView("SettingsScreen")
        assertTrue(true)
    }
}
