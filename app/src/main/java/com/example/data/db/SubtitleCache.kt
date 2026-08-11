package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subtitle_cache", primaryKeys = ["query", "provider"])
data class SubtitleCache(
    val query: String,
    val provider: String,
    val jsonResults: String, // Stored as JSON string
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SubtitleCacheDao {
    @Query("SELECT * FROM subtitle_cache WHERE `query` = :query AND provider = :provider LIMIT 1")
    suspend fun getCachedResults(query: String, provider: String): SubtitleCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: SubtitleCache)

    @Query("DELETE FROM subtitle_cache WHERE timestamp < :expiryTime")
    suspend fun clearExpiredCache(expiryTime: Long)

    @Query("DELETE FROM subtitle_cache")
    suspend fun clearAll()
}
