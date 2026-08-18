package com.medianest.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.medianest.data.db.MediaType
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: MediaStoreRepository
    private lateinit var contentResolver: ContentResolver
    private lateinit var shadowContentResolver: ShadowContentResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = MediaStoreRepository(context)
        contentResolver = context.contentResolver
        shadowContentResolver = Shadows.shadowOf(contentResolver)
        
        mockkStatic(android.os.Environment::class)
        every { android.os.Environment.isExternalStorageManager() } returns false
    }

    @Test
    fun `test getImages returns mapped items`() = runTest {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Images.Media._ID, 1L)
            put(MediaStore.Images.Media.DISPLAY_NAME, "test.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.SIZE, 1024L)
            put(MediaStore.Images.Media.DATE_ADDED, 123456789L)
            put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, "Camera")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Camera/")
        }
        contentResolver.insert(uri, values)

        val images = repository.getImages()
        
        assertEquals(1, images.size)
        val item = images[0]
        assertEquals(1L, item.id)
        assertEquals("test.jpg", item.title)
        assertEquals(MediaType.IMAGE, item.type)
        assertTrue(item.bucketName == "Camera" || item.bucketName == "Pictures")
    }

    @Test
    fun `test hidden folder filtering in getImages`() = runTest {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        // Item in visible folder
        contentResolver.insert(uri, ContentValues().apply {
            put(MediaStore.Images.Media._ID, 1L)
            put(MediaStore.Images.Media.DISPLAY_NAME, "visible.jpg")
            put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, "Camera")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera/")
        })
        
        // Item in hidden folder
        contentResolver.insert(uri, ContentValues().apply {
            put(MediaStore.Images.Media._ID, 2L)
            put(MediaStore.Images.Media.DISPLAY_NAME, "hidden.jpg")
            put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, "Secret")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Secret/")
        })

        val hiddenFolders = setOf("Secret")
        
        val images = repository.getImages(hiddenFolders = hiddenFolders, showHidden = false)
        assertEquals(1, images.size)
        assertEquals("visible.jpg", images[0].title)

        val allImages = repository.getImages(hiddenFolders = hiddenFolders, showHidden = true)
        assertEquals(2, allImages.size)
    }

    @Test
    fun `test resolveSiblingsForUri for unknown URI`() = runTest {
        val targetUri = Uri.parse("content://com.example/test.mp4")
        val items = repository.resolveSiblingsForUri(targetUri, "video/mp4")
        
        assertEquals(1, items.size)
        assertEquals(targetUri, items[0].uri)
        assertEquals(MediaType.VIDEO, items[0].type)
    }
}
