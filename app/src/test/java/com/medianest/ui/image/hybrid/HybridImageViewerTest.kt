package com.medianest.ui.image.hybrid

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HybridImageViewerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `ImageSource FromUri opens input stream correctly`() {
        val file = File(context.cacheDir, "test_image.jpg")
        file.writeText("test data")
        
        val uri = Uri.fromFile(file)
        val source = ImageSource.FromUri(uri)
        
        val inputStream = source.openInputStream(context)
        assertNotNull(inputStream)
        assertEquals("test data", inputStream?.bufferedReader()?.readText())
        inputStream?.close()
    }

    @Test
    fun `ImageSource FromFile opens input stream correctly`() {
        val file = File(context.cacheDir, "test_file.png")
        file.writeText("file data")
        
        val source = ImageSource.FromFile(file)
        
        val inputStream = source.openInputStream(context)
        assertNotNull(inputStream)
        assertEquals("file data", inputStream?.bufferedReader()?.readText())
        inputStream?.close()
    }

    @Test
    fun `ImageSource FromByteArray opens input stream correctly`() {
        val data = "byte data".toByteArray()
        val source = ImageSource.FromByteArray(data)
        
        val inputStream = source.openInputStream(context)
        assertNotNull(inputStream)
        assertEquals("byte data", inputStream?.bufferedReader()?.readText())
        inputStream?.close()
    }

    @Test
    fun `ImageSource keys are unique and consistent`() {
        val uri = Uri.parse("content://media/1")
        val source1 = ImageSource.FromUri(uri)
        val source2 = ImageSource.FromUri(uri)
        assertEquals(source1.key, source2.key)

        val file = File("/path/to/image.jpg")
        val source3 = ImageSource.FromFile(file)
        assertEquals("/path/to/image.jpg", source3.key)

        val bytes = byteArrayOf(1, 2, 3)
        val source4 = ImageSource.FromByteArray(bytes, "custom_id")
        assertEquals("bytes_custom_id", source4.key)
    }

    @Test
    fun `HybridImageViewerConfig default values`() {
        val config = HybridImageViewerConfig()
        assertEquals(10.0f, config.maxZoom)
        assertEquals(1.0f, config.minZoom)
    }
}
