package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaCategoryDao {
    @Query("SELECT * FROM media_categories WHERE type = :type ORDER BY sortOrder ASC, createdAt DESC")
    fun getCategoriesByType(type: String): Flow<List<MediaCategory>>

    @Query("SELECT * FROM media_categories WHERE type = :type ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getCategoriesByTypeSync(type: String): List<MediaCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: MediaCategory): Long

    @Update
    suspend fun updateCategory(category: MediaCategory)

    @Delete
    suspend fun deleteCategory(category: MediaCategory)

    @Query("DELETE FROM media_categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryCrossRef(ref: CategoryMediaCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryCrossRefs(refs: List<CategoryMediaCrossRef>)

    @Query("DELETE FROM category_media_cross_ref WHERE categoryId = :categoryId AND mediaUri = :mediaUri")
    suspend fun removeMediaFromCategory(categoryId: Long, mediaUri: String)

    @Query("DELETE FROM category_media_cross_ref WHERE categoryId = :categoryId")
    suspend fun clearCategoryMedia(categoryId: Long)

    @Query("SELECT mediaUri FROM category_media_cross_ref WHERE categoryId = :categoryId ORDER BY positionInCategory ASC")
    fun getMediaUrisForCategory(categoryId: Long): Flow<List<String>>

    @Query("SELECT categoryId FROM category_media_cross_ref WHERE mediaUri = :mediaUri")
    fun getCategoriesForMedia(mediaUri: String): Flow<List<Long>>

    @Query("SELECT * FROM category_media_cross_ref")
    fun getAllCrossRefs(): Flow<List<CategoryMediaCrossRef>>

    @Query("SELECT COUNT(*) FROM category_media_cross_ref WHERE categoryId = :categoryId AND mediaUri = :mediaUri")
    suspend fun countCategoryMediaCrossRef(categoryId: Long, mediaUri: String): Int

    /** Find an existing playlist by exact name + type — used to avoid creating duplicates on re-import */
    @Query("SELECT * FROM media_categories WHERE name = :name AND type = :type LIMIT 1")
    suspend fun getCategoryByNameAndType(name: String, type: String): MediaCategory?

    /** Synchronous version of getMediaUrisForCategory — used for duplicate-song check */
    @Query("SELECT mediaUri FROM category_media_cross_ref WHERE categoryId = :categoryId")
    suspend fun getMediaUrisForCategorySync(categoryId: Long): List<String>
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_states WHERE mediaUri = :mediaUri")
    suspend fun getPlaybackState(mediaUri: String): PlaybackState?

    @Query("SELECT * FROM playback_states WHERE mediaUri = :mediaUri")
    fun observePlaybackState(mediaUri: String): Flow<PlaybackState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackState(state: PlaybackState)

    @Query("SELECT * FROM playback_states ORDER BY lastPlayedAt DESC LIMIT 100")
    fun getRecentlyPlayed(): Flow<List<PlaybackState>>

    @Query("SELECT * FROM playback_states WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT 100")
    fun getMostPlayed(): Flow<List<PlaybackState>>

    @Query("DELETE FROM playback_states")
    suspend fun clearAllHistory()
}

@Dao
interface AudioMetadataCacheDao {
    @Query("SELECT * FROM audio_metadata_cache WHERE audioUri = :audioUri")
    suspend fun getCache(audioUri: String): AudioMetadataCache?

    @Query("SELECT * FROM audio_metadata_cache")
    suspend fun getAllCache(): List<AudioMetadataCache>

    @Query("SELECT * FROM audio_metadata_cache")
    fun getAllCacheFlow(): Flow<List<AudioMetadataCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCache(cache: AudioMetadataCache)

    @Query("DELETE FROM audio_metadata_cache")
    suspend fun clearCache()
}

@Dao
interface HiddenFolderDao {
    @Query("SELECT folderPath FROM hidden_folders WHERE mediaType = :mediaType")
    fun getHiddenFolderPaths(mediaType: String): Flow<List<String>>

    @Query("SELECT folderPath FROM hidden_folders WHERE mediaType = :mediaType")
    suspend fun getHiddenFolderPathsList(mediaType: String): List<String>

    @Query("SELECT * FROM hidden_folders")
    fun getAllHiddenFolders(): Flow<List<HiddenFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideFolder(folder: HiddenFolder)

    @Query("DELETE FROM hidden_folders WHERE folderPath = :folderPath AND mediaType = :mediaType")
    suspend fun unhideFolder(folderPath: String, mediaType: String)
}

@Dao
interface SelectiveHiddenFolderDao {
    @Query("SELECT * FROM selective_hidden_folders WHERE mediaType = :mediaType AND isHidden = 1")
    fun getHiddenFolders(mediaType: String): Flow<List<SelectiveHiddenFolder>>

    @Query("SELECT * FROM selective_hidden_folders WHERE mediaType = :mediaType AND isHidden = 1")
    suspend fun getHiddenFoldersList(mediaType: String): List<SelectiveHiddenFolder>

    @Query("SELECT * FROM selective_hidden_folders WHERE isHidden = 1")
    fun getAllHiddenFolders(): Flow<List<SelectiveHiddenFolder>>

    @Query("SELECT * FROM selective_hidden_folders WHERE isHidden = 1")
    suspend fun getAllHiddenFoldersList(): List<SelectiveHiddenFolder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(folder: SelectiveHiddenFolder)

    @Query("DELETE FROM selective_hidden_folders WHERE (folderPath = :path OR folderName = :path) AND mediaType = :mediaType")
    suspend fun unhideFolder(path: String, mediaType: String)
}

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM file_stat_snapshots WHERE id = 1")
    fun getLatestSnapshot(): Flow<FileStatSnapshot?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: FileStatSnapshot)

    @Query("SELECT * FROM format_stats ORDER BY sizeBytes DESC, fileCount DESC")
    fun getAllFormatStats(): Flow<List<FormatStat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormatStats(stats: List<FormatStat>)

    @Query("DELETE FROM format_stats")
    suspend fun clearFormatStats()

    @Transaction
    suspend fun saveAnalyticsData(snapshot: FileStatSnapshot, stats: List<FormatStat>) {
        insertSnapshot(snapshot)
        clearFormatStats()
        insertFormatStats(stats)
    }
}

@Dao
interface LocationCacheDao {
    @Query("SELECT * FROM location_cache WHERE mediaUri = :uri")
    suspend fun getLocation(uri: String): LocationCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocation(cache: LocationCache)

    @Query("DELETE FROM location_cache WHERE mediaUri = :uri")
    suspend fun deleteLocation(uri: String)

    @Query("DELETE FROM location_cache")
    suspend fun clearCache()
}

