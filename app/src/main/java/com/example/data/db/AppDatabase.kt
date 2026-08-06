package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaCategory::class,
        CategoryMediaCrossRef::class,
        PlaybackState::class,
        AudioMetadataCache::class,
        HiddenFolder::class,
        SelectiveHiddenFolder::class,
        FileStatSnapshot::class,
        FormatStat::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): MediaCategoryDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun metadataCacheDao(): AudioMetadataCacheDao
    abstract fun hiddenFolderDao(): HiddenFolderDao
    abstract fun selectiveHiddenFolderDao(): SelectiveHiddenFolderDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medianest_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
