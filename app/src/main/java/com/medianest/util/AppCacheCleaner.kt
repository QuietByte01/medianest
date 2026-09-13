package com.medianest.util

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.medianest.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class)
object AppCacheCleaner {

    suspend fun clearSelectedCache(
        context: Context,
        db: AppDatabase,
        clearHistory: Boolean = true,
        clearThumbnails: Boolean = true,
        clearNetworkMetadata: Boolean = true,
        clearTempFiles: Boolean = true
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Playback & Search History
                if (clearHistory) {
                    try {
                        db.playbackStateDao().clearAllHistory()
                        db.metadataCacheDao().clearCache()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2. Image & Video Thumbnails
                if (clearThumbnails) {
                    try {
                        ThumbnailManager.clearCache()
                        ThumbnailManager.clearDiskCache(context)
                        val loader = Coil.imageLoader(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 3. Online Network & Artist Metadata
                if (clearNetworkMetadata) {
                    try {
                        ArtistImageUtils.clearCache()
                        MediaMetadataUtils.clearCache()
                        ImageTagManager.invalidateCache()
                        ImageExclusionManager.invalidateCache()
                        db.locationCacheDao().clearCache()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 4. Temporary Buffers & Debug Logs
                if (clearTempFiles) {
                    try {
                        MediaAnalyzer.clearCache()
                        val cacheDir = context.cacheDir
                        if (cacheDir != null && cacheDir.exists()) {
                            cacheDir.listFiles()?.forEach { file ->
                                if (file.name != "lib") { // keep native libs dir
                                    file.deleteRecursively()
                                }
                            }
                        }
                        val codeCache = context.codeCacheDir
                        if (codeCache != null && codeCache.exists()) {
                            codeCache.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearAllAppCacheAndHistory(
        context: Context,
        db: AppDatabase
    ) {
        clearSelectedCache(
            context = context,
            db = db,
            clearHistory = true,
            clearThumbnails = true,
            clearNetworkMetadata = true,
            clearTempFiles = true
        )
    }
}
