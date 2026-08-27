package com.medianest.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var testFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testFile = tempFolder.newFile("test_media.mp4")
        testFile.writeText("test content")
    }

    @Test
    fun `test move file to trash`() {
        val item = MediaItem(
            id = 1L,
            uri = Uri.fromFile(testFile),
            title = "test_media.mp4",
            mimeType = "video/mp4",
            type = MediaType.VIDEO,
            size = testFile.length()
        )

        val success = TrashManager.moveToTrash(context, item)
        assertTrue(success)
        assertFalse("Original file should be moved", testFile.exists())
        
        val trashedItems = TrashManager.getTrashedItems(context)
        assertEquals(1, trashedItems.size)
        assertEquals("test_media.mp4", trashedItems[0].title)
    }

    @Test
    fun `test restore file from trash`() {
        val item = MediaItem(
            id = 1L,
            uri = Uri.fromFile(testFile),
            title = "test_media.mp4",
            mimeType = "video/mp4",
            type = MediaType.VIDEO,
            size = testFile.length()
        )

        TrashManager.moveToTrash(context, item)
        val trashedItem = TrashManager.getTrashedItems(context)[0]
        
        val restored = TrashManager.restoreItem(context, trashedItem)
        assertTrue(restored)
        assertTrue("File should be restored to original path", testFile.exists())
        assertEquals(0, TrashManager.getTrashedItems(context).size)
    }

    @Test
    fun `test delete permanently`() {
        val item = MediaItem(
            id = 1L,
            uri = Uri.fromFile(testFile),
            title = "test_media.mp4",
            mimeType = "video/mp4",
            type = MediaType.VIDEO,
            size = testFile.length()
        )

        TrashManager.moveToTrash(context, item)
        val trashedItem = TrashManager.getTrashedItems(context)[0]
        val trashedPath = trashedItem.trashedPath
        
        val deleted = TrashManager.deletePermanently(context, trashedItem)
        assertTrue(deleted)
        assertFalse(File(trashedPath).exists())
        assertEquals(0, TrashManager.getTrashedItems(context).size)
    }

    @Test
    fun `test empty trash`() {
        val item1 = MediaItem(1L, Uri.fromFile(testFile), "test1.mp4", "video/mp4", MediaType.VIDEO, size = 10)
        val file2 = tempFolder.newFile("test2.mp4")
        val item2 = MediaItem(2L, Uri.fromFile(file2), "test2.mp4", "video/mp4", MediaType.VIDEO, size = 10)
        
        TrashManager.moveToTrash(context, item1)
        TrashManager.moveToTrash(context, item2)
        
        assertEquals(2, TrashManager.getTrashedItems(context).size)
        
        val emptied = TrashManager.emptyTrash(context)
        assertTrue(emptied)
        assertEquals(0, TrashManager.getTrashedItems(context).size)
    }
}
