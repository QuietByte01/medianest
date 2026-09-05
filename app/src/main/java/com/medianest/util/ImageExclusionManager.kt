package com.medianest.util

import android.content.Context

object ImageExclusionManager {
    private const val PREFS_NAME = "medianest_image_filter_exclusions"

    @Volatile
    private var cachedExclusions: Map<String, Set<String>>? = null

    private fun getOrLoadExclusions(context: Context): Map<String, Set<String>> {
        cachedExclusions?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val map = HashMap<String, Set<String>>(all.size)
        for ((key, value) in all) {
            if (value is Set<*>) {
                @Suppress("UNCHECKED_CAST")
                map[key] = value as Set<String>
            }
        }
        cachedExclusions = map
        return map
    }

    fun invalidateCache() {
        cachedExclusions = null
    }

    /**
     * Excludes a media item from a specific filter tab ID (e.g. "SCREENSHOTS", "WALLPAPERS", "COOKING").
     */
    fun excludeFromFilter(context: Context, mediaUri: String, filterId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(filterId)
        prefs.edit().putStringSet(mediaUri, current).apply()
        invalidateCache()

        // Also remove from user tags if previously tagged
        ImageTagManager.removeTag(context, mediaUri, filterId)
    }

    /**
     * Checks whether a media item has been explicitly excluded from a filter tab.
     */
    fun isExcludedFromFilter(context: Context, mediaUri: String, filterId: String): Boolean {
        val map = getOrLoadExclusions(context)
        return map[mediaUri]?.contains(filterId) == true
    }

    /**
     * Restores an item back to a filter tab.
     */
    fun restoreToFilter(context: Context, mediaUri: String, filterId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(filterId)
        prefs.edit().putStringSet(mediaUri, current).apply()
        invalidateCache()
    }
}
