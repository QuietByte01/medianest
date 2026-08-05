package com.example

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.db.AppDatabase
import com.example.data.settings.SettingsManager

class MediaNestApp : Application() {
    
    lateinit var database: AppDatabase
        private set

    lateinit var settingsManager: SettingsManager
        private set

    val exoPlayerManager by lazy { com.example.player.ExoPlayerManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        settingsManager = SettingsManager(this)

        // Initialize Android Hardware Engine & Capability Detection
        val hwCaps = com.example.hardware.AndroidHardwareEngine.detectCapabilities(this)
        val hwMemConfig = com.example.hardware.AndroidHardwareEngine.getRecommendedMemoryConfig(hwCaps.ramTier)

        val imageLoader = ImageLoader.Builder(this)
            .allowHardware(true) // Enable Hardware Bitmaps stored in VRAM for zero-copy UI rendering
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (hwCaps.totalRamMb <= 2048) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_thumbnail_cache"))
                    .maxSizeBytes(hwMemConfig.thumbnailCacheMb.toLong() * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }

    companion object {
        lateinit var instance: MediaNestApp
            private set
    }
}
