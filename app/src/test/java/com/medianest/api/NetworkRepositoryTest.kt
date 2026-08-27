package com.medianest.api

import com.medianest.MediaNestApp
import com.medianest.data.db.SubtitleCacheDao
import com.medianest.data.repository.NetworkRepository
import com.medianest.data.settings.SettingsManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkRepositoryTest {

    private lateinit var client: OkHttpClient
    private lateinit var subtitleCacheDao: SubtitleCacheDao
    private lateinit var repository: NetworkRepository
    private lateinit var mockApp: MediaNestApp
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        client = mockk()
        subtitleCacheDao = mockk(relaxed = true)
        repository = NetworkRepository(client, subtitleCacheDao)
        
        mockApp = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        
        mockkObject(MediaNestApp.Companion)
        every { MediaNestApp.instance } returns mockApp
        every { mockApp.settingsManager } returns settingsManager
        
        // Mock settings flows
        every { settingsManager.lastSubtitleSearchDate } returns flowOf("")
        every { settingsManager.dailySubtitleSearchCount } returns flowOf(0)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fetchSyncedLyrics returns lyrics on success`() = runTest {
        val mockResponse = """
            {
              "syncedLyrics": "[00:10.00] Line 1\n[00:20.00] Line 2"
            }
        """.trimIndent()

        val response = Response.Builder()
            .request(Request.Builder().url("https://lrclib.net/api/get").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(mockResponse.toResponseBody("application/json".toMediaType()))
            .build()

        val mockCall = mockk<okhttp3.Call>()
        every { mockCall.execute() } returns response
        every { client.newCall(any()) } returns mockCall

        val result = repository.fetchSyncedLyrics("Song", "Artist", "Album", false)
        assertTrue(result!!.contains("Line 1"))
    }

    @Test
    fun `fetchSyncedLyrics returns fallback on failure`() = runTest {
        val mockCall = mockk<okhttp3.Call>()
        every { mockCall.execute() } throws IOException("Network error")
        every { client.newCall(any()) } returns mockCall

        val result = repository.fetchSyncedLyrics("Song", "Artist", "Album", false)
        // Verify it contains the fallback text pattern
        assertTrue(result!!.contains("Listening to Song"))
    }

    @Test
    fun `parseLrcLyrics parses correctly`() {
        val lrc = "[00:01.50] Hello World\n[00:02.00] Second line"
        val parsed = repository.parseLrcLyrics(lrc)
        
        assertEquals(2, parsed.size)
        assertEquals(1500L, parsed[0].timeMs)
        assertEquals("Hello World", parsed[0].text)
        assertEquals(2000L, parsed[1].timeMs)
    }

    @Test
    fun `searchOnlineSubtitles returns empty list when no provider found`() = runTest {
        val mockCall = mockk<okhttp3.Call>()
        every { mockCall.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://api.opensubtitles.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("".toResponseBody())
            .build()
        every { client.newCall(any()) } returns mockCall

        val results = repository.searchOnlineSubtitles("Movie.mp4", "en", false)
        assertTrue(results.isEmpty())
    }
}
