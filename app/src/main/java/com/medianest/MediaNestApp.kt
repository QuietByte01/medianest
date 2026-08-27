package com.medianest

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.medianest.data.db.AppDatabase
import com.medianest.data.settings.SettingsManager

class MediaNestApp : Application() {
    
    lateinit var database: AppDatabase
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var artistMetadataRepository: com.medianest.data.repository.ArtistMetadataRepository
        private set

    val exoPlayerManager by lazy { com.medianest.player.ExoPlayerManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Logger Crash Handler FIRST to catch any subsequent init failures
        com.medianest.util.Logger.installCrashHandler()
        
        database = AppDatabase.getDatabase(this)
        settingsManager = SettingsManager(this)
        artistMetadataRepository = com.medianest.data.repository.ArtistMetadataRepository(this)

        // Initialize Android Hardware Engine & Capability Detection
        val hwCaps = com.medianest.hardware.AndroidHardwareEngine.detectCapabilities(this)
        val hwMemConfig = com.medianest.hardware.AndroidHardwareEngine.getRecommendedMemoryConfig(hwCaps.ramTier)

        val imageLoader = ImageLoader.Builder(this)
            .allowHardware(true) // Enable Hardware Bitmaps stored in VRAM for zero-copy UI rendering
            .components {
                add(com.medianest.util.SemaphoreVideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())      // SVG image rendering support
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
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
