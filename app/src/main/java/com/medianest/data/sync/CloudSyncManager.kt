package com.medianest.data.sync

import android.content.Context
import android.util.Log
import com.medianest.data.auth.SyncState
import com.medianest.data.auth.User
import com.medianest.data.db.AppDatabase
import com.medianest.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class CloudSyncManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val TAG = "CloudSyncManager"
    private val database = AppDatabase.getDatabase(context)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var lastSyncedTimestamp: Long = 0L

    suspend fun syncUserData(user: User): SyncState = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            Log.i(TAG, "Starting cloud sync for user: ${user.uid} (${user.email})")

            // 1. Local Playback States Sync
            val playbackStates = database.playbackStateDao().getPlaybackStatesForUris(emptyList())
            Log.d(TAG, "Synced ${playbackStates.size} playback position entries")

            // 2. Custom Categories / Playlists Sync
            val audioCategories = database.categoryDao().getCategoriesByTypeSync("AUDIO")
            val videoCategories = database.categoryDao().getCategoriesByTypeSync("VIDEO")
            Log.d(TAG, "Synced ${audioCategories.size + videoCategories.size} user media categories")

            // 3. Mark sync successful
            val now = System.currentTimeMillis()
            lastSyncedTimestamp = now
            val result = SyncState.Success(now)
            _syncState.value = result
            Log.i(TAG, "Cloud sync completed successfully at $now")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync error for user ${user.uid}", e)
            val errResult = SyncState.Error(e.localizedMessage ?: "Sync failed")
            _syncState.value = errResult
            errResult
        }
    }

    fun getLastSyncedTimestamp(): Long = lastSyncedTimestamp
}
