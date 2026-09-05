package com.medianest.util

import android.content.Context

object ImageTagManager {
    private const val PREFS_NAME = "medianest_image_user_tags"

    @Volatile
    private var cachedTags: Map<String, Set<String>>? = null

    private fun getOrLoadTags(context: Context): Map<String, Set<String>> {
        cachedTags?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val map = HashMap<String, Set<String>>(all.size)
        for ((key, value) in all) {
            if (value is Set<*>) {
                @Suppress("UNCHECKED_CAST")
                map[key] = value as Set<String>
            }
        }
        cachedTags = map
        return map
    }

    fun invalidateCache() {
        cachedTags = null
    }

    fun getTags(context: Context, mediaUri: String): Set<String> {
        val map = getOrLoadTags(context)
        return map[mediaUri] ?: emptySet()
    }

    fun toggleTag(context: Context, mediaUri: String, tagId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        val isAdded = if (current.contains(tagId)) {
            current.remove(tagId)
            false
        } else {
            current.add(tagId)
            true
        }
        prefs.edit().putStringSet(mediaUri, current).apply()
        invalidateCache()
        return isAdded
    }

    fun removeTag(context: Context, mediaUri: String, tagId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(tagId)) {
            prefs.edit().putStringSet(mediaUri, current).apply()
            invalidateCache()
        }
    }

    fun hasTag(context: Context, mediaUri: String, tagId: String): Boolean {
        return getTags(context, mediaUri).contains(tagId)
    }
}
