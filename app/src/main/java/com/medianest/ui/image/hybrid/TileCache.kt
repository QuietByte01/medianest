package com.medianest.ui.image.hybrid

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TileCache(context: Context) {
    // Determine safe memory limits based on device RAM class
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryClass = activityManager.memoryClass // e.g., 256MB
    
    // Allocate ~1/8th of available app memory for L1/L2 tile caching
    private val maxCacheBytes = (memoryClass * 1024 * 1024) / 8

    // L1 Cache: Direct HardwareBuffers for zero-copy GL uploading
    private val l1Cache = object : LruCache<String, HardwareBuffer>(maxCacheBytes / 2) {
        override fun sizeOf(key: String, value: HardwareBuffer): Int {
            // Approximate bytes
            return value.width * value.height * 4
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: HardwareBuffer,
            newValue: HardwareBuffer?
        ) {
            oldValue.close() // Release native memory
        }
    }

    // L2 Cache: Standard Bitmaps waiting to be promoted
    private val l2Cache = object : LruCache<String, Bitmap>(maxCacheBytes / 2) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount
        }
    }

    private val mutex = Mutex()

    suspend fun putL1(tileId: String, buffer: HardwareBuffer) {
        mutex.withLock {
            l1Cache.put(tileId, buffer)
        }
    }

    suspend fun getL1(tileId: String): HardwareBuffer? {
        return mutex.withLock {
            l1Cache.get(tileId)
        }
    }

    suspend fun putL2(tileId: String, bitmap: Bitmap) {
        mutex.withLock {
            l2Cache.put(tileId, bitmap)
        }
    }

    suspend fun getL2(tileId: String): Bitmap? {
        return mutex.withLock {
            l2Cache.get(tileId)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            l1Cache.evictAll()
            l2Cache.evictAll()
        }
    }
}
