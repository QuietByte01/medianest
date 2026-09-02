package com.medianest.util

import android.content.Context

object ImageTagManager {
    private const val PREFS_NAME = "medianest_image_user_tags"

    fun getTags(context: Context, mediaUri: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(mediaUri, emptySet()) ?: emptySet()
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
        return isAdded
    }

    fun removeTag(context: Context, mediaUri: String, tagId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(mediaUri, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(tagId)) {
            prefs.edit().putStringSet(mediaUri, current).apply()
        }
    }

    fun hasTag(context: Context, mediaUri: String, tagId: String): Boolean {
        return getTags(context, mediaUri).contains(tagId)
    }
}
