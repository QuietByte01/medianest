package com.medianest.ui.videoeditor.data

import android.graphics.Bitmap
import androidx.collection.LruCache

class ThumbnailCache(maxSizeBytes: Int) {
    private val cache = object : LruCache<Long, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: Long, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun get(timestampMs: Long): Bitmap? {
        return cache.get(timestampMs)
    }

    fun put(timestampMs: Long, bitmap: Bitmap) {
        cache.put(timestampMs, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
