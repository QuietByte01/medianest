package com.medianest.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioMetadataUtilsTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(MediaMetadataRetriever::class)
    }

    @Test
    fun `test extractMetadata with valid metadata`() {
        val uri = Uri.parse("content://media/audio/1")
        
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(context, uri) } just Runs
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) } returns "Song Title"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) } returns "Artist Name"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) } returns "Album Name"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) } returns "180000"
        every { anyConstructed<MediaMetadataRetriever>().embeddedPicture } returns null
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs

        val item = AudioMetadataUtils.extractMetadata(context, uri)

        assertEquals("Song Title", item.title)
        assertEquals("Artist Name", item.artist)
        assertEquals("Album Name", item.album)
        assertEquals(180000L, item.durationMs)
    }

    @Test
    fun `test resolveTitle fallback to filename`() {
        val uri = Uri.parse("file:///sdcard/Music/MySong.mp3")
        
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(context, uri) } just Runs
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(any()) } returns null
        every { anyConstructed<MediaMetadataRetriever>().embeddedPicture } returns null
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs

        val item = AudioMetadataUtils.extractMetadata(context, uri)

        // Should fallback to lastPathSegment without extension
        assertEquals("MySong", item.title)
    }

    @Test
    fun `test resolveTitle with digit-only title hint`() {
        val uri = Uri.parse("content://media/audio/12345")
        
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(context, uri) } just Runs
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(any()) } returns null
        every { anyConstructed<MediaMetadataRetriever>().embeddedPicture } returns null
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs

        val item = AudioMetadataUtils.extractMetadata(context, uri, rawTitleHint = "12345.mp3")

        // Should ignore pure digit hint and use "Audio Track" or URI seg if available
        assertEquals("Audio Track", item.title)
    }
}
