package com.medianest.util

import android.content.Context

object ImageExclusionManager {
    private const val PREFS_NAME = "medianest_image_filter_exclusions"

    /**
     * Excludes a media item from a specific filter tab ID (e.g. "SCREENSHOTS", "WALLPAPERS", "COOKING").
     */
    fun excludeFromFilter(context: Context, mediaUri: String, filterId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(filterId)
        prefs.edit().putStringSet(mediaUri, current).apply()

        // Also remove from user tags if previously tagged
        ImageTagManager.removeTag(context, mediaUri, filterId)
    }

    /**
     * Checks whether a media item has been explicitly excluded from a filter tab.
     */
    fun isExcludedFromFilter(context: Context, mediaUri: String, filterId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(mediaUri, emptySet())?.contains(filterId) == true
    }

    /**
     * Restores an item back to a filter tab.
     */
    fun restoreToFilter(context: Context, mediaUri: String, filterId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(filterId)
        prefs.edit().putStringSet(mediaUri, current).apply()
    }
}
