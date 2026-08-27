/*
package com.medianest.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FFmpegNativeIntegrationTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var engine: FFmpegPlaybackEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        
        mockkStatic(System::class)
        every { System.loadLibrary(any()) } just Runs
        
        engine = spyk(FFmpegPlaybackEngine(context), recordPrivateCalls = true)
        val ptrField: Field = FFmpegPlaybackEngine::class.java.getDeclaredField("nativeContextPtr")
        ptrField.isAccessible = true
        ptrField.set(engine, 1L)

        mockkStatic(ParcelFileDescriptor::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `probe correctly opens and closes file descriptor`() {
        val uri = Uri.parse("content://media/external/video/media/1")
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockDupPfd = mockk<ParcelFileDescriptor>(relaxed = true)
        
        every { contentResolver.openFileDescriptor(uri, "r") } returns mockPfd
        every { ParcelFileDescriptor.dup(any()) } returns mockDupPfd
        
        try {
            engine.probe(uri)
        } catch (e: UnsatisfiedLinkError) {}
        
        verify { mockDupPfd.close() }
        verify { mockPfd.close() }
    }
}
*/
