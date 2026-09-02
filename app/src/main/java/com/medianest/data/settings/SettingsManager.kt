package com.medianest.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medianest_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme") // DARK, LIGHT, SYSTEM
        val KEY_ACCENT_COLOR = intPreferencesKey("accent_color") // Hex int
        val KEY_GRID_GAP_DP = intPreferencesKey("grid_gap_dp")
        val KEY_GRID_SIZE_LEVEL = intPreferencesKey("grid_size_level")
        val KEY_ROUNDED_CORNERS_ENABLED = booleanPreferencesKey("rounded_corners_enabled")
        val KEY_GRID_CORNER_RADIUS_DP = intPreferencesKey("grid_corner_radius_dp")
        val KEY_GLASSMORPHISM_ENABLED = booleanPreferencesKey("glassmorphism_enabled")
        val KEY_LARGE_IMAGE_GRID = booleanPreferencesKey("large_image_grid")
        
        val KEY_DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val KEY_DEFAULT_CROP_MODE = stringPreferencesKey("default_crop_mode") // FIT, CROP, STRETCH
        val KEY_DEFAULT_AUDIO_BOOST = intPreferencesKey("default_audio_boost")
        
        val KEY_SHOW_PLAYBACK_NOTIFICATION = booleanPreferencesKey("show_playback_notification")
        val KEY_SHOW_VIDEO_NOTIFICATION = booleanPreferencesKey("show_video_notification")
        val KEY_HARDWARE_ACCELERATION_ENABLED = booleanPreferencesKey("hardware_acceleration_enabled")
        val KEY_SHOW_STATUS_BAR_IN_PLAYBACK = booleanPreferencesKey("show_status_bar_in_playback")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_FORCE_LANDSCAPE = booleanPreferencesKey("force_landscape")
        
        val KEY_AUTO_FETCH_LYRICS = booleanPreferencesKey("auto_fetch_lyrics")
        val KEY_SHOW_AUDIO_VISUALIZER = booleanPreferencesKey("show_audio_visualizer")
        val KEY_SUBTITLE_LANG = stringPreferencesKey("subtitle_lang")
        
        val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val KEY_HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        val KEY_SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val KEY_HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val KEY_ENABLE_ANALYTICS_TAB = booleanPreferencesKey("enable_analytics_tab")
        val KEY_ENABLE_TRASH = booleanPreferencesKey("enable_trash")
        val KEY_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val KEY_VERBOSE_LOGGING_ENABLED = booleanPreferencesKey("verbose_logging_enabled")
        val KEY_SHOW_PLAYER_DEBUG_INFO = booleanPreferencesKey("show_player_debug_info")
        val KEY_SHOW_IMAGE_DEBUG_INFO = booleanPreferencesKey("show_image_debug_info")
        val KEY_SHOW_AUDIO_DEBUG_INFO = booleanPreferencesKey("show_audio_debug_info")
        val KEY_SHOW_LIBRARY_DEBUG_INFO = booleanPreferencesKey("show_library_debug_info")

        val KEY_AUDIO_BACKGROUND_PLAY = booleanPreferencesKey("audio_background_play")
        val KEY_VIDEO_BACKGROUND_PLAY = booleanPreferencesKey("video_background_play")

        val KEY_AUDIO_REPEAT_MODE = intPreferencesKey("audio_repeat_mode")
        val KEY_VIDEO_REPEAT_MODE = intPreferencesKey("video_repeat_mode")
        val KEY_AUDIO_SHUFFLE_MODE = booleanPreferencesKey("audio_shuffle_mode")
        val KEY_VIDEO_SHUFFLE_MODE = booleanPreferencesKey("video_shuffle_mode")

        val KEY_PICTURE_MODE_ENABLED = booleanPreferencesKey("picture_mode_enabled")
        val KEY_DECODER_MODE = stringPreferencesKey("decoder_mode") // AUTO, HARDWARE, SOFTWARE
        val KEY_PICTURE_MODE = stringPreferencesKey("picture_mode") // DEVICE_DEFAULT, BALANCED, NATURAL, VIVID, CINEMATIC, CUSTOM
        val KEY_HDR_PLAYBACK_ENABLED = booleanPreferencesKey("hdr_playback_enabled")
        val KEY_FILM_GRAIN_ENABLED = booleanPreferencesKey("film_grain_enabled")
        val KEY_FILM_GRAIN_INTENSITY = floatPreferencesKey("film_grain_intensity")
        val KEY_CUSTOM_SATURATION = floatPreferencesKey("custom_saturation")
        val KEY_CUSTOM_CONTRAST = floatPreferencesKey("custom_contrast")
        val KEY_CUSTOM_WARMTH = floatPreferencesKey("custom_warmth")
        val KEY_APPLY_PICTURE_MODE_TO_THUMBNAILS = booleanPreferencesKey("apply_picture_mode_to_thumbnails")

        val KEY_AUDIO_SORT_FIELD = stringPreferencesKey("audio_sort_field")
        val KEY_AUDIO_SORT_ASCENDING = booleanPreferencesKey("audio_sort_ascending")
        val KEY_IMAGE_SORT_FIELD = stringPreferencesKey("image_sort_field")
        val KEY_IMAGE_SORT_ASCENDING = booleanPreferencesKey("image_sort_ascending")
        val KEY_VIDEO_SORT_FIELD = stringPreferencesKey("video_sort_field")
        val KEY_VIDEO_SORT_ASCENDING = booleanPreferencesKey("video_sort_ascending")
        val KEY_UNINTERRUPTED_MODE = booleanPreferencesKey("uninterrupted_mode")
        val KEY_AUTO_RESUME_ON_BLUETOOTH = booleanPreferencesKey("auto_resume_on_bluetooth")
        
        val KEY_AUTO_PLAY_VIDEO_PREVIEWS = booleanPreferencesKey("auto_play_video_previews")
        val KEY_AUTO_PLAY_GIF_PREVIEWS = booleanPreferencesKey("auto_play_gif_previews")
        val KEY_REMEMBER_VIDEO_POSITION = booleanPreferencesKey("remember_video_position")

        val KEY_DAILY_SUBTITLE_SEARCH_COUNT = intPreferencesKey("daily_subtitle_search_count")
        val KEY_LAST_SUBTITLE_SEARCH_DATE = stringPreferencesKey("last_subtitle_search_date")
        val KEY_FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
    }

    val theme: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_THEME] ?: "DARK" }
    val accentColor: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_ACCENT_COLOR] ?: 0xFF818CF8.toInt() }
    val gridGapDp: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_GRID_GAP_DP] ?: 8 }
    val gridSizeLevel: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_GRID_SIZE_LEVEL] ?: 1 }
    val roundedCornersEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_ROUNDED_CORNERS_ENABLED] ?: true }
    val gridCornerRadiusDp: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_GRID_CORNER_RADIUS_DP] ?: 8 }
    val glassmorphismEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_GLASSMORPHISM_ENABLED] ?: true }
    val largeImageGrid: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_LARGE_IMAGE_GRID] ?: false }

    val decoderMode: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_DECODER_MODE] ?: "AUTO" }
    val pictureModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_PICTURE_MODE_ENABLED] ?: true }
    val pictureMode: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_PICTURE_MODE] ?: "DEVICE_DEFAULT" }
    val rememberVideoPosition: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_REMEMBER_VIDEO_POSITION] ?: true }
    val hdrPlaybackEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_HDR_PLAYBACK_ENABLED] ?: true }
    val filmGrainEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_FILM_GRAIN_ENABLED] ?: false }
    val filmGrainIntensity: Flow<Float> = context.dataStore.data.map { prefs -> prefs[KEY_FILM_GRAIN_INTENSITY] ?: 0.15f }
    val enableTrash: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_ENABLE_TRASH] ?: true }
    val customSaturation: Flow<Float> = context.dataStore.data.map { prefs -> prefs[KEY_CUSTOM_SATURATION] ?: 1.18f }
    val customContrast: Flow<Float> = context.dataStore.data.map { prefs -> prefs[KEY_CUSTOM_CONTRAST] ?: 1.06f }
    val customWarmth: Flow<Float> = context.dataStore.data.map { prefs -> prefs[KEY_CUSTOM_WARMTH] ?: 0.03f }
    val applyPictureModeToThumbnails: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_APPLY_PICTURE_MODE_TO_THUMBNAILS] ?: false }

    val audioSortField: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_AUDIO_SORT_FIELD] ?: "Name" }
    val audioSortAscending: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUDIO_SORT_ASCENDING] ?: true }
    val imageSortField: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_IMAGE_SORT_FIELD] ?: "Date" }
    val imageSortAscending: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_IMAGE_SORT_ASCENDING] ?: false }
    val videoSortField: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_VIDEO_SORT_FIELD] ?: "Date" }
    val videoSortAscending: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_VIDEO_SORT_ASCENDING] ?: false }
    val uninterruptedMode: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_UNINTERRUPTED_MODE] ?: false }
    val autoResumeOnBluetooth: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUTO_RESUME_ON_BLUETOOTH] ?: false }

    val dailySubtitleSearchCount: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_DAILY_SUBTITLE_SEARCH_COUNT] ?: 0 }
    val lastSubtitleSearchDate: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_LAST_SUBTITLE_SEARCH_DATE] ?: "" }

    val defaultSpeed: Flow<Float> = context.dataStore.data.map { prefs -> prefs[KEY_DEFAULT_SPEED] ?: 1.0f }
    val defaultCropMode: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_DEFAULT_CROP_MODE] ?: "FIT" }
    val defaultAudioBoost: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_DEFAULT_AUDIO_BOOST] ?: 0 }

    val showPlaybackNotification: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SHOW_PLAYBACK_NOTIFICATION] ?: true }
    val showVideoNotification: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SHOW_VIDEO_NOTIFICATION] ?: true }
    val hardwareAccelerationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_HARDWARE_ACCELERATION_ENABLED] ?: true }
    val showStatusBarInPlayback: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SHOW_STATUS_BAR_IN_PLAYBACK] ?: false }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_KEEP_SCREEN_ON] ?: true }
    val forceLandscape: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_FORCE_LANDSCAPE] ?: false }

    val autoFetchLyrics: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUTO_FETCH_LYRICS] ?: true }
    val showAudioVisualizer: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SHOW_AUDIO_VISUALIZER] ?: false }
    val subtitleLang: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_SUBTITLE_LANG] ?: "en" }

    val offlineMode: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_OFFLINE_MODE] ?: false }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_APP_LOCK_ENABLED] ?: false }
    val appLockPin: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_APP_LOCK_PIN] ?: "" }
    val hideFromRecents: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_HIDE_FROM_RECENTS] ?: false }
    val showHiddenFiles: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_SHOW_HIDDEN_FILES] ?: false }
    val hiddenFolders: Flow<Set<String>> = context.dataStore.data.map { prefs -> prefs[KEY_HIDDEN_FOLDERS] ?: emptySet() }
    val enableAnalyticsTab: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_ENABLE_ANALYTICS_TAB] ?: true }
    val developerModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_DEVELOPER_MODE_ENABLED] ?: false }
    val verboseLoggingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> (prefs[KEY_DEVELOPER_MODE_ENABLED] ?: false) && (prefs[KEY_VERBOSE_LOGGING_ENABLED] ?: false) }
    val showPlayerDebugInfo: Flow<Boolean> = context.dataStore.data.map { prefs -> (prefs[KEY_DEVELOPER_MODE_ENABLED] ?: false) && (prefs[KEY_SHOW_PLAYER_DEBUG_INFO] ?: false) }
    val showImageDebugInfo: Flow<Boolean> = context.dataStore.data.map { prefs -> (prefs[KEY_DEVELOPER_MODE_ENABLED] ?: false) && (prefs[KEY_SHOW_IMAGE_DEBUG_INFO] ?: false) }
    val showAudioDebugInfo: Flow<Boolean> = context.dataStore.data.map { prefs -> (prefs[KEY_DEVELOPER_MODE_ENABLED] ?: false) && (prefs[KEY_SHOW_AUDIO_DEBUG_INFO] ?: false) }
    val showLibraryDebugInfo: Flow<Boolean> = context.dataStore.data.map { prefs -> (prefs[KEY_DEVELOPER_MODE_ENABLED] ?: false) && (prefs[KEY_SHOW_LIBRARY_DEBUG_INFO] ?: true) }

    val audioBackgroundPlay: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUDIO_BACKGROUND_PLAY] ?: true }
    val videoBackgroundPlay: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_VIDEO_BACKGROUND_PLAY] ?: false }

    val audioRepeatMode: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_AUDIO_REPEAT_MODE] ?: 0 } // REPEAT_MODE_OFF
    val videoRepeatMode: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_VIDEO_REPEAT_MODE] ?: 0 } // REPEAT_MODE_OFF
    val audioShuffleMode: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUDIO_SHUFFLE_MODE] ?: false }
    val videoShuffleMode: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_VIDEO_SHUFFLE_MODE] ?: false }

    val autoPlayVideoPreviews: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUTO_PLAY_VIDEO_PREVIEWS] ?: true }
    val autoPlayGifPreviews: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_AUTO_PLAY_GIF_PREVIEWS] ?: true }

    val isFirstLaunchCompleted: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_FIRST_LAUNCH_COMPLETED] ?: false }

    suspend fun setFirstLaunchCompleted(completed: Boolean) = context.dataStore.edit { it[KEY_FIRST_LAUNCH_COMPLETED] = completed }

    suspend fun setAutoPlayVideoPreviews(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_PLAY_VIDEO_PREVIEWS] = enabled }
    suspend fun setAutoPlayGifPreviews(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_PLAY_GIF_PREVIEWS] = enabled }

    suspend fun setTheme(theme: String) = context.dataStore.edit { it[KEY_THEME] = theme }
    suspend fun setAccentColor(color: Int) = context.dataStore.edit { it[KEY_ACCENT_COLOR] = color }
    suspend fun setGridGapDp(gap: Int) = context.dataStore.edit { it[KEY_GRID_GAP_DP] = gap }
    suspend fun setGridSizeLevel(level: Int) = context.dataStore.edit { it[KEY_GRID_SIZE_LEVEL] = level }
    suspend fun setRoundedCornersEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_ROUNDED_CORNERS_ENABLED] = enabled }
    suspend fun setGridCornerRadiusDp(radius: Int) = context.dataStore.edit { it[KEY_GRID_CORNER_RADIUS_DP] = radius }
    suspend fun setGlassmorphismEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_GLASSMORPHISM_ENABLED] = enabled }
    suspend fun setLargeImageGrid(large: Boolean) = context.dataStore.edit { it[KEY_LARGE_IMAGE_GRID] = large }

    suspend fun setDefaultSpeed(speed: Float) = context.dataStore.edit { it[KEY_DEFAULT_SPEED] = speed }
    suspend fun setDefaultCropMode(mode: String) = context.dataStore.edit { it[KEY_DEFAULT_CROP_MODE] = mode }
    suspend fun setDefaultAudioBoost(boost: Int) = context.dataStore.edit { it[KEY_DEFAULT_AUDIO_BOOST] = boost }

    suspend fun setShowPlaybackNotification(show: Boolean) = context.dataStore.edit { it[KEY_SHOW_PLAYBACK_NOTIFICATION] = show }
    suspend fun setShowVideoNotification(show: Boolean) = context.dataStore.edit { it[KEY_SHOW_VIDEO_NOTIFICATION] = show }
    suspend fun setHardwareAccelerationEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_HARDWARE_ACCELERATION_ENABLED] = enabled }
    suspend fun setShowStatusBarInPlayback(show: Boolean) = context.dataStore.edit { it[KEY_SHOW_STATUS_BAR_IN_PLAYBACK] = show }
    suspend fun setKeepScreenOn(keep: Boolean) = context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = keep }
    suspend fun setForceLandscape(force: Boolean) = context.dataStore.edit { it[KEY_FORCE_LANDSCAPE] = force }

    suspend fun setAutoFetchLyrics(auto: Boolean) = context.dataStore.edit { it[KEY_AUTO_FETCH_LYRICS] = auto }
    suspend fun setShowAudioVisualizer(show: Boolean) = context.dataStore.edit { it[KEY_SHOW_AUDIO_VISUALIZER] = show }
    suspend fun setSubtitleLang(lang: String) = context.dataStore.edit { it[KEY_SUBTITLE_LANG] = lang }

    suspend fun setOfflineMode(offline: Boolean) = context.dataStore.edit { it[KEY_OFFLINE_MODE] = offline }
    suspend fun setAppLockEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    suspend fun setAppLockPin(pin: String) = context.dataStore.edit { it[KEY_APP_LOCK_PIN] = hashPin(pin) }
    suspend fun setHideFromRecents(hide: Boolean) = context.dataStore.edit { it[KEY_HIDE_FROM_RECENTS] = hide }

    fun hashPin(pin: String): String {
        if (pin.isBlank()) return ""
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("Medianest_Salt_2026_$pin".toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin
        }
    }
    suspend fun setShowHiddenFiles(show: Boolean) = context.dataStore.edit { it[KEY_SHOW_HIDDEN_FILES] = show }
    suspend fun setHiddenFolders(folders: Set<String>) = context.dataStore.edit { it[KEY_HIDDEN_FOLDERS] = folders }
    suspend fun setEnableAnalyticsTab(enabled: Boolean) = context.dataStore.edit { it[KEY_ENABLE_ANALYTICS_TAB] = enabled }
    suspend fun setEnableTrash(enabled: Boolean) = context.dataStore.edit { it[KEY_ENABLE_TRASH] = enabled }
    suspend fun setDeveloperModeEnabled(enabled: Boolean) = context.dataStore.edit {
        it[KEY_DEVELOPER_MODE_ENABLED] = enabled
        if (!enabled) {
            it[KEY_SHOW_PLAYER_DEBUG_INFO] = false
            it[KEY_SHOW_IMAGE_DEBUG_INFO] = false
            it[KEY_SHOW_AUDIO_DEBUG_INFO] = false
            it[KEY_SHOW_LIBRARY_DEBUG_INFO] = false
            it[KEY_VERBOSE_LOGGING_ENABLED] = false
        }
    }
    suspend fun setVerboseLoggingEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_VERBOSE_LOGGING_ENABLED] = enabled }
    suspend fun setShowPlayerDebugInfo(enabled: Boolean) = context.dataStore.edit { it[KEY_SHOW_PLAYER_DEBUG_INFO] = enabled }
    suspend fun setShowImageDebugInfo(enabled: Boolean) = context.dataStore.edit { it[KEY_SHOW_IMAGE_DEBUG_INFO] = enabled }
    suspend fun setShowAudioDebugInfo(enabled: Boolean) = context.dataStore.edit { it[KEY_SHOW_AUDIO_DEBUG_INFO] = enabled }
    suspend fun setShowLibraryDebugInfo(enabled: Boolean) = context.dataStore.edit { it[KEY_SHOW_LIBRARY_DEBUG_INFO] = enabled }

    suspend fun setAudioBackgroundPlay(enabled: Boolean) = context.dataStore.edit { it[KEY_AUDIO_BACKGROUND_PLAY] = enabled }
    suspend fun setVideoBackgroundPlay(enabled: Boolean) = context.dataStore.edit { it[KEY_VIDEO_BACKGROUND_PLAY] = enabled }

    suspend fun setAudioRepeatMode(mode: Int) = context.dataStore.edit { it[KEY_AUDIO_REPEAT_MODE] = mode }
    suspend fun setVideoRepeatMode(mode: Int) = context.dataStore.edit { it[KEY_VIDEO_REPEAT_MODE] = mode }
    suspend fun setAudioShuffleMode(enabled: Boolean) = context.dataStore.edit { it[KEY_AUDIO_SHUFFLE_MODE] = enabled }
    suspend fun setVideoShuffleMode(enabled: Boolean) = context.dataStore.edit { it[KEY_VIDEO_SHUFFLE_MODE] = enabled }

    suspend fun setDecoderMode(mode: String) = context.dataStore.edit { it[KEY_DECODER_MODE] = mode }
    suspend fun setHdrPlaybackEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_HDR_PLAYBACK_ENABLED] = enabled }
    suspend fun setPictureModeEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_PICTURE_MODE_ENABLED] = enabled }
    suspend fun setPictureMode(mode: String) = context.dataStore.edit { it[KEY_PICTURE_MODE] = mode }
    suspend fun setRememberVideoPosition(remember: Boolean) = context.dataStore.edit { it[KEY_REMEMBER_VIDEO_POSITION] = remember }
    suspend fun setFilmGrainEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_FILM_GRAIN_ENABLED] = enabled }
    suspend fun setFilmGrainIntensity(intensity: Float) = context.dataStore.edit { it[KEY_FILM_GRAIN_INTENSITY] = intensity }
    suspend fun setCustomSaturation(sat: Float) = context.dataStore.edit { it[KEY_CUSTOM_SATURATION] = sat }
    suspend fun setCustomContrast(con: Float) = context.dataStore.edit { it[KEY_CUSTOM_CONTRAST] = con }
    suspend fun setCustomWarmth(warmth: Float) = context.dataStore.edit { it[KEY_CUSTOM_WARMTH] = warmth }
    suspend fun setApplyPictureModeToThumbnails(apply: Boolean) = context.dataStore.edit { it[KEY_APPLY_PICTURE_MODE_TO_THUMBNAILS] = apply }

    suspend fun setAudioSortField(field: String) = context.dataStore.edit { it[KEY_AUDIO_SORT_FIELD] = field }
    suspend fun setAudioSortAscending(ascending: Boolean) = context.dataStore.edit { it[KEY_AUDIO_SORT_ASCENDING] = ascending }
    suspend fun setImageSortField(field: String) = context.dataStore.edit { it[KEY_IMAGE_SORT_FIELD] = field }
    suspend fun setImageSortAscending(ascending: Boolean) = context.dataStore.edit { it[KEY_IMAGE_SORT_ASCENDING] = ascending }
    suspend fun setVideoSortField(field: String) = context.dataStore.edit { it[KEY_VIDEO_SORT_FIELD] = field }
    suspend fun setVideoSortAscending(ascending: Boolean) = context.dataStore.edit { it[KEY_VIDEO_SORT_ASCENDING] = ascending }
    suspend fun setUninterruptedMode(enabled: Boolean) = context.dataStore.edit { it[KEY_UNINTERRUPTED_MODE] = enabled }
    suspend fun setAutoResumeOnBluetooth(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_RESUME_ON_BLUETOOTH] = enabled }

    suspend fun setDailySubtitleSearchCount(count: Int) = context.dataStore.edit { it[KEY_DAILY_SUBTITLE_SEARCH_COUNT] = count }
    suspend fun setLastSubtitleSearchDate(date: String) = context.dataStore.edit { it[KEY_LAST_SUBTITLE_SEARCH_DATE] = date }
}
