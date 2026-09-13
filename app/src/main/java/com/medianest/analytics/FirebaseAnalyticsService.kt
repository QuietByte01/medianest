package com.medianest.analytics

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Analytics implementation of [AnalyticsService].
 * Automatically attaches device metadata (model, brand, OS version) anonymously
 * and safely handles environments without Firebase configuration.
 */
class FirebaseAnalyticsService(private val context: Context) : AnalyticsService {

    private val firebaseAnalytics: FirebaseAnalytics? by lazy {
        try {
            FirebaseAnalytics.getInstance(context.applicationContext).apply {
                setUserProperty("device_model", Build.MODEL)
                setUserProperty("device_manufacturer", Build.MANUFACTURER)
                setUserProperty("android_version", Build.VERSION.RELEASE)
                setUserProperty("sdk_int", Build.VERSION.SDK_INT.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Analytics not initialized: ${e.message}")
            null
        }
    }

    private var isEnabled = true

    override fun logScreenView(screenName: String, screenClass: String?) {
        if (!isEnabled) return
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass ?: screenName)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Screen view logged: $screenName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log screen view", e)
        }
    }

    override fun logMediaPlayback(mediaType: String, format: String, durationMs: Long) {
        if (!isEnabled) return
        try {
            val bundle = Bundle().apply {
                putString("media_type", mediaType)
                putString("format", format)
                putLong("duration_ms", durationMs)
                putString("device_model", Build.MODEL)
            }
            firebaseAnalytics?.logEvent("media_playback", bundle)
            Log.d(TAG, "Media playback logged: type=$mediaType, format=$format, duration=${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log media playback", e)
        }
    }

    override fun logFeatureUse(featureName: String, params: Map<String, Any>) {
        if (!isEnabled) return
        try {
            val bundle = Bundle().apply {
                putString("feature_name", featureName)
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent("feature_used", bundle)
            Log.d(TAG, "Feature usage logged: $featureName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log feature use", e)
        }
    }

    override fun setAnalyticsEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
            Log.d(TAG, "Analytics collection enabled state set to: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set analytics collection state", e)
        }
    }

    companion object {
        private const val TAG = "FirebaseAnalyticsSvc"
    }
}
