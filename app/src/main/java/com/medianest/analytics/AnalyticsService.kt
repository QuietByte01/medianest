package com.medianest.analytics

/**
 * Service interface for handling anonymous app telemetry and usage analytics.
 */
interface AnalyticsService {
    /**
     * Logs a screen view event when navigating to a screen.
     */
    fun logScreenView(screenName: String, screenClass: String? = null)

    /**
     * Logs media playback engagement metrics (type, format, duration).
     */
    fun logMediaPlayback(mediaType: String, format: String, durationMs: Long)

    /**
     * Logs feature usage events (e.g. video trim, playlist creation, EQ toggle).
     */
    fun logFeatureUse(featureName: String, params: Map<String, Any> = emptyMap())

    /**
     * Dynamically enables or disables analytics collection based on user preference.
     */
    fun setAnalyticsEnabled(enabled: Boolean)
}
